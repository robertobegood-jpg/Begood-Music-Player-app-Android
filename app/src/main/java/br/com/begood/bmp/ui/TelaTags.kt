package br.com.begood.bmp.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.begood.bmp.data.Alteracao
import br.com.begood.bmp.data.AlbumOnline
import br.com.begood.bmp.data.BuscaOnline
import br.com.begood.bmp.data.Capas
import br.com.begood.bmp.data.DadosTags
import br.com.begood.bmp.data.FaixaOnline
import br.com.begood.bmp.data.Musica
import br.com.begood.bmp.data.Rede
import br.com.begood.bmp.data.Tags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.tag.FieldKey
import java.io.ByteArrayOutputStream

/**
 * Editor de tags. Com uma música edita tudo. Com várias (um álbum ou músicas marcadas = edição em lote)
 * edita o que é comum: campos com o mesmo valor em todas aparecem preenchidos; os diferentes ficam vazios
 * e só mudam se você digitar algo. Também pode numerar as faixas pelos dados da internet.
 */
@Composable
fun TelaTags(vm: AppViewModel, ids: List<Long>, doAlbum: Boolean) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    val musicas = remember(ids) { ids.mapNotNull { vm.biblioteca?.porId?.get(it) } }
    if (musicas.isEmpty()) { Text("Música não encontrada.", Modifier.padding(16.dp)); return }

    var original by remember { mutableStateOf<DadosTags?>(null) }
    var erroLeitura by remember { mutableStateOf<String?>(null) }
    var d by remember { mutableStateOf(DadosTags()) }
    var capaMudou by remember { mutableStateOf(false) }
    var faixasOnline by remember { mutableStateOf<List<FaixaOnline>>(emptyList()) }
    var numerarFaixas by remember { mutableStateOf(true) }
    var corrigirTitulos by remember { mutableStateOf(false) }
    var buscando by remember { mutableStateOf(false) }
    var aplicando by remember { mutableStateOf(false) }
    val varias = musicas.size > 1
    /** Campos que têm valores diferentes entre as músicas (só na edição de várias). */
    var variados by remember { mutableStateOf<Set<FieldKey>>(emptySet()) }

    LaunchedEffect(ids) {
        val r = withContext(Dispatchers.IO) {
            runCatching {
                if (!varias) Tags.ler(ctx, musicas.first()) to emptySet<FieldKey>()
                else {
                    val todas = musicas.map { m -> runCatching { Tags.ler(ctx, m) }.getOrElse { DadosTags(titulo = m.titulo, artista = m.artista, album = m.album) } }
                    val dif: MutableSet<FieldKey> = HashSet()
                    fun comum(k: FieldKey, f: (DadosTags) -> String): String {
                        val valores = todas.map { f(it).trim() }.distinct()
                        return if (valores.size == 1) valores[0] else { dif.add(k); "" }
                    }
                    val capas = todas.map { it.capa }
                    val capaComum = capas.first().takeIf { c -> capas.all { it.contentEquals(c) } }
                    if (capaComum == null && capas.any { it != null }) dif.add(FieldKey.COVER_ART)
                    DadosTags(
                        artista = comum(FieldKey.ARTIST) { it.artista },
                        album = comum(FieldKey.ALBUM) { it.album },
                        albumArtista = comum(FieldKey.ALBUM_ARTIST) { it.albumArtista },
                        ano = comum(FieldKey.YEAR) { it.ano },
                        genero = comum(FieldKey.GENRE) { it.genero },
                        disco = comum(FieldKey.DISC_NO) { it.disco },
                        compositor = comum(FieldKey.COMPOSER) { it.compositor },
                        capa = capaComum,
                    ) to dif.toSet()
                }
            }
        }
        r.onSuccess { (dados, dif) -> original = dados; d = dados; variados = dif }
            .onFailure { erroLeitura = "Não consegui ler as tags: ${it.message}" }
    }

    val escolherImagem = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) escopo.launch {
            val b = withContext(Dispatchers.IO) { runCatching { prepararImagem(ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }) }.getOrNull() }
            if (b != null) { d = d.copy(capa = b); capaMudou = true } else vm.aviso = "Não consegui abrir a imagem."
        }
    }

    if (original == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (erroLeitura != null) Text(erroLeitura!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                if (varias) Text("Lendo as tags de ${musicas.size} músicas…")
            }
        }
        return
    }

    fun salvar() {
        val o = original ?: return
        // Campo com valores diferentes e deixado vazio = não mexe.
        fun mudou(k: FieldKey, novo: String, velho: String) = when {
            k in variados && novo.isBlank() -> null
            novo.trim() != velho.trim() -> novo.trim()
            else -> null
        }
        val comuns = buildMap<FieldKey, String?> {
            put(FieldKey.ALBUM, mudou(FieldKey.ALBUM, d.album, o.album))
            put(FieldKey.ALBUM_ARTIST, mudou(FieldKey.ALBUM_ARTIST, d.albumArtista, o.albumArtista))
            put(FieldKey.YEAR, mudou(FieldKey.YEAR, d.ano, o.ano))
            put(FieldKey.GENRE, mudou(FieldKey.GENRE, d.genero, o.genero))
            put(FieldKey.ARTIST, mudou(FieldKey.ARTIST, d.artista, o.artista))
            put(FieldKey.DISC_NO, mudou(FieldKey.DISC_NO, d.disco, o.disco))
            put(FieldKey.COMPOSER, mudou(FieldKey.COMPOSER, d.compositor, o.compositor))
            if (!varias) {
                put(FieldKey.TITLE, mudou(FieldKey.TITLE, d.titulo, o.titulo))
                put(FieldKey.TRACK, mudou(FieldKey.TRACK, d.faixa, o.faixa))
                put(FieldKey.LYRICS, mudou(FieldKey.LYRICS, d.letra, o.letra))
            }
        }
        val capa = if (capaMudou) d.capa else null
        val removerCapa = capaMudou && d.capa == null
        vm.gravarTags(musicas, aoTerminar = { erros -> if (erros.isEmpty()) { vm.limparSelecao(); vm.voltar() } }) { m ->
            val porFaixa = HashMap(comuns)
            if (varias && faixasOnline.isNotEmpty()) {
                BuscaOnline.faixaDe(m, faixasOnline)?.let { f ->
                    if (numerarFaixas && f.numero > 0) {
                        porFaixa[FieldKey.TRACK] = f.numero.toString()
                        if (f.disco > 0 && faixasOnline.any { it.disco > 1 }) porFaixa[FieldKey.DISC_NO] = f.disco.toString()
                    }
                    if (corrigirTitulos && f.titulo.isNotBlank()) porFaixa[FieldKey.TITLE] = f.titulo
                }
            }
            Alteracao(porFaixa, capa, removerCapa)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                when {
                    doAlbum -> "Editando ${musicas.size} faixa(s) do álbum"
                    varias -> "Edição em lote: ${musicas.size} músicas. Campos com \"vários valores\" só mudam se você digitar algo."
                    else -> musicas.first().nomeArquivo
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Capa(d.capa, Modifier.size(140.dp), tamanhoIcone = 60.dp)
                    if (!capaMudou && FieldKey.COVER_ART in variados) Text("Capas diferentes", style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { escolherImagem.launch("image/*") }) { Icon(Icons.Filled.Image, null); Text(" Escolher imagem") }
                    if (d.capa != null) TextButton(onClick = { d = d.copy(capa = null); capaMudou = true }) { Icon(Icons.Filled.Delete, null); Text(" Remover capa") }
                }
            }
            FilledTonalButton(onClick = { buscando = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.TravelExplore, null); Text("  Atualizar pela internet (artista + álbum)")
            }
            val v = variados
            if (!varias) Campo("Título", d.titulo) { d = d.copy(titulo = it) }
            if (!doAlbum) Campo("Artista", d.artista, variado = FieldKey.ARTIST in v) { d = d.copy(artista = it) }
            Campo("Álbum", d.album, variado = FieldKey.ALBUM in v) { d = d.copy(album = it) }
            Campo("Artista do álbum", d.albumArtista, variado = FieldKey.ALBUM_ARTIST in v) { d = d.copy(albumArtista = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo("Ano", d.ano, Modifier.weight(1f), numerico = true, variado = FieldKey.YEAR in v) { d = d.copy(ano = it) }
                Campo("Gênero", d.genero, Modifier.weight(2f), variado = FieldKey.GENRE in v) { d = d.copy(genero = it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!varias) Campo("Faixa", d.faixa, Modifier.weight(1f), numerico = true) { d = d.copy(faixa = it) }
                Campo("Disco", d.disco, Modifier.weight(1f), numerico = true, variado = FieldKey.DISC_NO in v) { d = d.copy(disco = it) }
            }
            Campo("Compositor", d.compositor, variado = FieldKey.COMPOSER in v) { d = d.copy(compositor = it) }
            if (!varias) {
                OutlinedTextField(
                    value = d.letra, onValueChange = { d = d.copy(letra = it) }, label = { Text("Letra") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp),
                )
            }
            if (varias && faixasOnline.isNotEmpty()) {
                val achadas = musicas.count { BuscaOnline.faixaDe(it, faixasOnline) != null }
                Text("Faixas da internet: $achadas de ${musicas.size} músicas reconhecidas pelo título.", style = MaterialTheme.typography.bodySmall)
                Opcao("Preencher o número das faixas", numerarFaixas) { numerarFaixas = it }
                Opcao("Corrigir os títulos pelos da internet", corrigirTitulos) { corrigirTitulos = it }
            }
            Spacer(Modifier.height(8.dp))
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (vm.gravando) { CircularProgressIndicator(Modifier.size(24.dp)); Text("Salvando…", Modifier.weight(1f)) }
            else Text(
                "No Android 11 ou mais novo, o sistema pede confirmação para alterar os arquivos.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            Button(onClick = { salvar() }, enabled = !vm.gravando && !aplicando) { Icon(Icons.Filled.Save, null); Text(" Salvar") }
        }
    }

    if (buscando) DialogoBuscaAlbum(
        artistaInicial = d.albumArtista.ifEmpty { d.artista.ifEmpty { musicas.first().artistaDoAlbum } },
        albumInicial = d.album,
        onFechar = { buscando = false },
        onEscolher = { a ->
            buscando = false
            aplicando = true
            escopo.launch {
                try {
                    val (det, faixas) = withContext(Dispatchers.IO) { BuscaOnline.detalhes(a) }
                    val capa = withContext(Dispatchers.IO) { runCatching { BuscaOnline.baixarCapa(det)?.let(::prepararImagem) }.getOrNull() }
                    var novo = d.copy(
                        album = det.titulo.ifEmpty { d.album },
                        albumArtista = det.artista.ifEmpty { d.albumArtista },
                        ano = det.ano.ifEmpty { d.ano },
                        genero = det.genero.ifEmpty { d.genero },
                    )
                    if (!varias) {
                        if (novo.artista.isBlank()) novo = novo.copy(artista = det.artista)
                        BuscaOnline.faixaDe(musicas.first(), faixas)?.let { f ->
                            novo = novo.copy(faixa = f.numero.toString(), disco = if (faixas.any { it.disco > 1 }) f.disco.toString() else novo.disco)
                        }
                    }
                    if (capa != null) { novo = novo.copy(capa = capa); capaMudou = true }
                    d = novo
                    faixasOnline = faixas
                    vm.aviso = "Dados de \"${det.titulo}\" preenchidos. Confira e toque em Salvar."
                } catch (e: Exception) {
                    vm.aviso = Rede.mensagem(e)
                } finally {
                    aplicando = false
                }
            }
        },
    )
    if (aplicando) AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Baixando dados e capa…") }, text = { CircularProgressIndicator() })
}

@Composable
private fun Campo(
    rotulo: String, valor: String, modifier: Modifier = Modifier.fillMaxWidth(), numerico: Boolean = false, variado: Boolean = false,
    onMudar: (String) -> Unit,
) =
    OutlinedTextField(
        value = valor, onValueChange = onMudar, label = { Text(rotulo) }, singleLine = true, modifier = modifier,
        placeholder = if (variado) ({ Text("vários valores (não altera)", style = MaterialTheme.typography.bodySmall) }) else null,
        keyboardOptions = if (numerico) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
    )

@Composable
private fun Opcao(texto: String, marcado: Boolean, onMudar: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().clickable { onMudar(!marcado) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(marcado, onMudar); Text(texto)
    }

/** Busca o álbum no iTunes e no Deezer e mostra as opções com capa. */
@Composable
private fun DialogoBuscaAlbum(artistaInicial: String, albumInicial: String, onFechar: () -> Unit, onEscolher: (AlbumOnline) -> Unit) {
    var artista by remember { mutableStateOf(artistaInicial) }
    var album by remember { mutableStateOf(albumInicial) }
    var resultados by remember { mutableStateOf<List<AlbumOnline>?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }
    var carregando by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()
    fun buscar() = escopo.launch {
        carregando = true; erro = null
        try {
            resultados = withContext(Dispatchers.IO) { BuscaOnline.buscar(artista.trim(), album.trim()) }
            if (resultados.isNullOrEmpty()) erro = "Nada encontrado. Tente mudar o nome do artista ou do álbum."
        } catch (e: Exception) {
            erro = Rede.mensagem(e)
        }
        carregando = false
    }
    LaunchedEffect(Unit) { if (artista.isNotBlank() || album.isNotBlank()) buscar() }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Buscar álbum na internet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(artista, { artista = it }, label = { Text("Artista") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(album, { album = it }, label = { Text("Álbum") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { buscar() }, enabled = !carregando, modifier = Modifier.fillMaxWidth()) { Text("Buscar") }
                when {
                    carregando -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    erro != null -> Text(erro!!, color = MaterialTheme.colorScheme.error)
                    else -> LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(resultados.orEmpty(), key = { it.fonte + it.id }) { a ->
                            ListItem(
                                leadingContent = { Capa(a.capaMini, Modifier.size(56.dp), tamanhoIcone = 24.dp) },
                                headlineContent = { Text(a.titulo, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
                                supportingContent = {
                                    Text(listOf(a.artista, a.ano, if (a.totalFaixas > 0) "${a.totalFaixas} faixas" else "", a.fonte).filter { it.isNotEmpty() }.joinToString(" · "), maxLines = 2)
                                },
                                modifier = Modifier.clickable { onEscolher(a) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onFechar) { Text("Fechar") } },
    )
}

/** Deixa a capa num tamanho razoável (até 1200 px, JPEG) para não inchar o arquivo de música. */
private fun prepararImagem(bytes: ByteArray): ByteArray {
    val bmp = Capas.reduzir(bytes, 1200) ?: error("Imagem inválida")
    if (bytes.size < 600_000 && bmp.width <= 1600) return bytes
    val lado = minOf(1200, maxOf(bmp.width, bmp.height))
    val escala = lado.toFloat() / maxOf(bmp.width, bmp.height)
    val final = if (escala < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * escala).toInt(), (bmp.height * escala).toInt(), true) else bmp
    return ByteArrayOutputStream().use { s -> final.compress(Bitmap.CompressFormat.JPEG, 90, s); s.toByteArray() }
}
