package br.com.begood.bmp.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** Pasta com músicas encontrada no celular (para a tela de pastas). */
data class PastaEncontrada(val caminho: String, val quantidade: Int)

/**
 * Lê as músicas do celular pelo MediaStore (o índice de mídia do Android), filtra pelas pastas escolhidas
 * (ou pela pasta padrão "Music") e monta a [Biblioteca]. Atualiza sozinho quando arquivos mudam.
 */
object Repositorio {
    private lateinit var app: Context
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trava = Mutex()

    private val _biblioteca = MutableStateFlow<Biblioteca?>(null)
    val biblioteca: StateFlow<Biblioteca?> = _biblioteca.asStateFlow()
    private val _carregando = MutableStateFlow(false)
    val carregando: StateFlow<Boolean> = _carregando.asStateFlow()

    /** Todas as músicas do celular (sem filtro de pasta), da última leitura. */
    @Volatile private var todas: List<Musica> = emptyList()
    private var observando = false
    private var recarga: Job? = null

    private val EXTENSOES = setOf("mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "wma", "ape", "alac", "aiff", "aif", "mka", "dsf", "wv")
    private val PADRAO = Regex("""^/storage/(emulated/\d+|[^/]+)/Music/""", RegexOption.IGNORE_CASE)
    private val RAIZ = Regex("""^(/storage/emulated/\d+|/storage/[^/]+)/([^/]+)/""")

    fun iniciar(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        Preferencias.iniciar(app)
    }

    fun temPermissao(ctx: Context = app): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun recarregar(): Job = escopo.launch { carregarAgora() }

    /** Devolve a biblioteca, carregando se ainda não foi lida (usado pelo serviço do player). */
    suspend fun garantir(): Biblioteca = _biblioteca.value ?: carregarAgora()

    suspend fun carregarAgora(): Biblioteca = trava.withLock {
        _carregando.value = true
        try {
            todas = if (temPermissao()) lerMediaStore() else emptyList()
            val lib = Biblioteca(filtrar(todas))
            _biblioteca.value = lib
            observar()
            lib
        } finally {
            _carregando.value = false
        }
    }

    /** Pastas escolhidas pelo usuário, ou vazio quando usa a pasta padrão. */
    val usandoPadrao: Boolean get() = Preferencias.pastas.isEmpty()

    private fun filtrar(lista: List<Musica>): List<Musica> {
        val pastas = Preferencias.pastas.map { it.trimEnd('/') + "/" }
        return if (pastas.isEmpty()) lista.filter { PADRAO.containsMatchIn(it.caminho) }
        else lista.filter { m -> pastas.any { m.caminho.startsWith(it, ignoreCase = true) } }
    }

    /** Pastas do primeiro nível (Music, Download, ...) que têm músicas, com a quantidade. */
    fun pastasEncontradas(): List<PastaEncontrada> =
        todas.mapNotNull { m -> RAIZ.find(m.caminho)?.let { it.groupValues[1] + "/" + it.groupValues[2] } }
            .groupingBy { it }.eachCount()
            .map { PastaEncontrada(it.key, it.value) }
            .sortedByDescending { it.quantidade }

    fun quantidadeEm(pasta: String): Int {
        val p = pasta.trimEnd('/') + "/"
        return todas.count { it.caminho.startsWith(p, ignoreCase = true) }
    }

    private fun lerMediaStore(): List<Musica> {
        val r = app.contentResolver
        val base = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DATE_MODIFIED,
        )
        val extras = if (Build.VERSION.SDK_INT >= 30) arrayOf(
            MediaStore.Audio.Media.ALBUM_ARTIST, MediaStore.Audio.Media.GENRE, MediaStore.Audio.Media.DISC_NUMBER,
        ) else arrayOf("album_artist")
        val selecao = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND ${MediaStore.Audio.Media.IS_ALARM} = 0"
        val cursor = runCatching { r.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, base + extras, selecao, null, null) }.getOrNull()
            ?: runCatching { r.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, base, selecao, null, null) }.getOrNull()
            ?: return emptyList()
        val saida = ArrayList<Musica>(cursor.count)
        cursor.use { c ->
            fun idx(nome: String) = c.getColumnIndex(nome)
            val iId = idx(MediaStore.Audio.Media._ID); val iTit = idx(MediaStore.Audio.Media.TITLE)
            val iArt = idx(MediaStore.Audio.Media.ARTIST); val iAlb = idx(MediaStore.Audio.Media.ALBUM)
            val iFx = idx(MediaStore.Audio.Media.TRACK); val iAno = idx(MediaStore.Audio.Media.YEAR)
            val iDur = idx(MediaStore.Audio.Media.DURATION); val iCam = idx(MediaStore.Audio.Media.DATA)
            val iMod = idx(MediaStore.Audio.Media.DATE_MODIFIED); val iAA = idx("album_artist")
            val iGen = idx("genre"); val iDisc = idx("disc_number")
            while (c.moveToNext()) {
                val caminho = c.texto(iCam)
                if (caminho.isEmpty()) continue
                if (File(caminho).extension.lowercase() !in EXTENSOES) continue
                val faixaBruta = c.texto(iFx).substringBefore('/').trim().toIntOrNull() ?: 0
                // Androids antigos guardam disco*1000 + faixa no mesmo número.
                var disco = c.texto(iDisc).substringBefore('/').trim().toIntOrNull() ?: 0
                val faixa = if (faixaBruta >= 1000) { if (disco == 0) disco = faixaBruta / 1000; faixaBruta % 1000 } else faixaBruta
                saida.add(
                    Musica(
                        id = c.getLong(iId),
                        titulo = c.texto(iTit).ifEmpty { File(caminho).nameWithoutExtension },
                        artista = c.texto(iArt).semDesconhecido(),
                        album = c.texto(iAlb).semDesconhecido(),
                        albumArtista = c.texto(iAA).semDesconhecido(),
                        faixa = faixa,
                        disco = disco,
                        ano = c.texto(iAno).takeIf { it != "0" }.orEmpty(),
                        genero = c.texto(iGen).semDesconhecido(),
                        duracaoMs = if (iDur >= 0) c.getLong(iDur) else 0L,
                        caminho = caminho,
                        modificado = if (iMod >= 0) c.getLong(iMod) else 0L,
                    )
                )
            }
        }
        return saida
    }

    private fun Cursor.texto(i: Int): String = if (i < 0 || isNull(i)) "" else getString(i)?.trim().orEmpty()
    private fun String.semDesconhecido(): String = if (this == "<unknown>" || this == MediaStore.UNKNOWN_STRING) "" else this

    /** Recarrega sozinho quando o Android avisa que arquivos de áudio mudaram (com uma pequena espera). */
    private fun observar() {
        if (observando) return
        observando = true
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                recarga?.cancel()
                recarga = escopo.launch { delay(1500); carregarAgora() }
            }
        }
        runCatching { app.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, obs) }
    }

    /** Pastas que serão percorridas no "Escanear arquivos". */
    private fun pastasParaEscanear(): List<File> {
        val escolhidas = Preferencias.pastas
        if (escolhidas.isNotEmpty()) return escolhidas.map(::File)
        val internas = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        // Cartão SD: a partir de /storage/XXXX-XXXX/Android/data/... chega-se em /storage/XXXX-XXXX/Music
        val cartoes = app.getExternalFilesDirs(null).filterNotNull().mapNotNull { d ->
            d.absolutePath.substringBefore("/Android/").takeIf { it.isNotEmpty() }?.let { File(it, "Music") }
        }
        return (listOf(internas) + cartoes).distinctBy { it.absolutePath }
    }

    /**
     * Procura arquivos de música nas pastas que o Android ainda não indexou e pede para indexar.
     * Devolve quantos arquivos novos foram encontrados.
     */
    suspend fun escanearArquivos(): Int {
        val conhecidos = todas.mapTo(HashSet()) { it.caminho.lowercase() }
        val novos = ArrayList<String>()
        for (raiz in pastasParaEscanear()) percorrer(raiz, 0) { f ->
            if (f.extension.lowercase() in EXTENSOES && f.absolutePath.lowercase() !in conhecidos) novos.add(f.absolutePath)
        }
        // Também reindexa as pastas inteiras, para pegar tags alteradas por outros programas.
        val tudo = novos + pastasParaEscanear().filter { it.isDirectory }.map { it.absolutePath }
        if (tudo.isNotEmpty()) indexar(tudo)
        carregarAgora()
        return novos.size
    }

    private fun percorrer(dir: File, nivel: Int, achou: (File) -> Unit) {
        if (nivel > 12 || !dir.isDirectory) return
        val filhos = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (f in filhos) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                if (nivel == 0 && f.name.equals("Android", true)) continue
                percorrer(f, nivel + 1, achou)
            } else achou(f)
        }
    }

    /** Pede ao Android para (re)ler estes arquivos e espera terminar (no máximo 60 s). */
    suspend fun indexar(caminhos: List<String>) {
        if (caminhos.isEmpty()) return
        withTimeoutOrNull(60_000) {
            suspendCancellableCoroutine { cont ->
                val faltam = AtomicInteger(caminhos.size)
                MediaScannerConnection.scanFile(app, caminhos.toTypedArray(), null) { _, _ ->
                    if (faltam.decrementAndGet() == 0 && cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    /** Converte a pasta escolhida no seletor do Android (SAF) em caminho de arquivo. */
    fun caminhoDaArvore(uri: Uri): String? = runCatching {
        val id = DocumentsContract.getTreeDocumentId(uri)
        when (uri.authority) {
            "com.android.providers.downloads.documents" ->
                if (id.startsWith("raw:")) id.removePrefix("raw:")
                else Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            "com.android.externalstorage.documents" -> {
                val volume = id.substringBefore(':')
                val relativo = id.substringAfter(':', "").trim('/')
                val raiz = when (volume) {
                    "primary" -> Environment.getExternalStorageDirectory().absolutePath
                    "home" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
                    "raw" -> return@runCatching relativo.let { "/$it" }
                    else -> "/storage/$volume"
                }
                if (relativo.isEmpty()) raiz else "$raiz/$relativo"
            }
            else -> null
        }
    }.getOrNull()
}
