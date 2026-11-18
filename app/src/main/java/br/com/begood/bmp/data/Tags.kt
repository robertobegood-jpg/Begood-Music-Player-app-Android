package br.com.begood.bmp.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.reference.PictureTypes
import java.io.File
import java.io.FileOutputStream
import java.util.logging.Level
import java.util.logging.Logger

/** Tags de uma música, como texto (para o editor). */
data class DadosTags(
    val titulo: String = "",
    val artista: String = "",
    val album: String = "",
    val albumArtista: String = "",
    val ano: String = "",
    val genero: String = "",
    val faixa: String = "",
    val disco: String = "",
    val compositor: String = "",
    val letra: String = "",
    val capa: ByteArray? = null,
) {
    override fun equals(other: Any?) = other is DadosTags && toString() == other.toString() && capa.contentEquals(other.capa)
    override fun hashCode() = toString().hashCode()
}

/**
 * O que mudar ao gravar. Campo null = não mexer; texto vazio = apagar o campo.
 * [capa] != null troca a capa; [removerCapa] apaga.
 */
class Alteracao(
    val campos: Map<FieldKey, String?> = emptyMap(),
    val capa: ByteArray? = null,
    val removerCapa: Boolean = false,
) {
    val vazia: Boolean get() = campos.values.all { it == null } && capa == null && !removerCapa
}

/** Leitura e gravação de tags nos arquivos de música (MP3, FLAC, M4A, OGG, WMA...). */
object Tags {
    fun iniciar() {
        TagOptionSingleton.getInstance().isAndroid = true
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    private fun pastaTemp(ctx: Context) = File(ctx.cacheDir, "tags").apply { mkdirs() }

    /** Abre o arquivo para leitura; se o acesso direto falhar, lê de uma cópia temporária. */
    private fun abrir(ctx: Context, m: Musica): AudioFile = runCatching { AudioFileIO.read(File(m.caminho)) }.getOrElse {
        val tmp = copiarParaTemp(ctx, m, "ler")
        try { AudioFileIO.read(tmp) } finally { tmp.delete() }
    }

    private fun copiarParaTemp(ctx: Context, m: Musica, sufixo: String): File {
        val ext = File(m.caminho).extension.ifEmpty { "mp3" }
        val tmp = File(pastaTemp(ctx), "${m.id}-$sufixo.$ext")
        ctx.contentResolver.openInputStream(m.uri)?.use { ent -> tmp.outputStream().use { ent.copyTo(it) } }
            ?: error("Não foi possível abrir ${m.nomeArquivo}")
        return tmp
    }

    fun ler(ctx: Context, m: Musica): DadosTags {
        val tag = abrir(ctx, m).tag ?: return DadosTags(titulo = m.titulo, artista = m.artista, album = m.album)
        fun f(k: FieldKey) = runCatching { tag.getFirst(k) }.getOrNull()?.trim().orEmpty()
        return DadosTags(
            titulo = f(FieldKey.TITLE),
            artista = f(FieldKey.ARTIST),
            album = f(FieldKey.ALBUM),
            albumArtista = f(FieldKey.ALBUM_ARTIST),
            ano = f(FieldKey.YEAR),
            genero = f(FieldKey.GENRE),
            faixa = f(FieldKey.TRACK),
            disco = f(FieldKey.DISC_NO),
            compositor = f(FieldKey.COMPOSER),
            letra = f(FieldKey.LYRICS),
            capa = runCatching { tag.firstArtwork?.binaryData }.getOrNull(),
        )
    }

    fun lerLetra(ctx: Context, m: Musica): String? =
        runCatching { abrir(ctx, m).tag?.getFirst(FieldKey.LYRICS) }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * No Android 11+, alterar arquivos de música que não foram criados pelo app exige uma confirmação do usuário.
     * Devolve o pedido de confirmação (para a tela abrir), ou null se já pode gravar.
     */
    fun pedidoDePermissao(ctx: Context, musicas: List<Musica>): PendingIntent? {
        if (Build.VERSION.SDK_INT < 30) return null
        val faltam = musicas.map { it.uri }.filter {
            ctx.checkUriPermission(it, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED
        }
        if (faltam.isEmpty()) return null
        return MediaStore.createWriteRequest(ctx.contentResolver, faltam)
    }

    /**
     * Grava a [alteracao] em cada música. O arquivo é editado numa cópia e só depois volta para o lugar
     * (se algo der errado no meio, o original é restaurado). Devolve os erros (vazio = tudo certo).
     */
    fun gravar(ctx: Context, musicas: List<Musica>, alteracao: (Musica) -> Alteracao): List<String> {
        val erros = ArrayList<String>()
        for (m in musicas) {
            val alt = alteracao(m)
            if (alt.vazia) continue
            var copia: File? = null
            var original: File? = null
            try {
                original = copiarParaTemp(ctx, m, "original")
                copia = copiarParaTemp(ctx, m, "editada")
                val af = AudioFileIO.read(copia)
                val tag = af.tagOrCreateAndSetDefault
                for ((campo, valor) in alt.campos) {
                    if (valor == null) continue
                    if (valor.isBlank()) runCatching { tag.deleteField(campo) } else tag.setField(campo, valor.trim())
                }
                if (alt.removerCapa || alt.capa != null) runCatching { tag.deleteArtworkField() }
                alt.capa?.let { bytes ->
                    val arte = ArtworkFactory.getNew()
                    arte.binaryData = bytes
                    arte.mimeType = if (bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) "image/png" else "image/jpeg"
                    arte.pictureType = PictureTypes.DEFAULT_ID
                    arte.description = ""
                    tag.setField(arte)
                }
                af.tag = tag
                af.commit()
                try {
                    escreverDeVolta(ctx, m, copia)
                } catch (e: Exception) {
                    runCatching { escreverDeVolta(ctx, m, original) }
                    throw e
                }
            } catch (e: Exception) {
                erros.add("${m.nomeArquivo}: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                copia?.delete(); original?.delete()
            }
        }
        return erros
    }

    private fun escreverDeVolta(ctx: Context, m: Musica, origem: File) {
        if (Build.VERSION.SDK_INT >= 30) {
            ctx.contentResolver.openOutputStream(m.uri, "wt")?.use { sai -> origem.inputStream().use { it.copyTo(sai) } }
                ?: error("Sem permissão para gravar")
        } else {
            FileOutputStream(m.caminho).use { sai -> origem.inputStream().use { it.copyTo(sai) } }
        }
    }
}
