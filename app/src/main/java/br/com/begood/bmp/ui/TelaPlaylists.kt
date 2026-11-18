package br.com.begood.bmp.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.begood.bmp.data.Automatica
import br.com.begood.bmp.data.Biblioteca
import br.com.begood.bmp.data.Genius
import br.com.begood.bmp.data.Musica
import br.com.begood.bmp.data.Playlists
import br.com.begood.bmp.data.Preferencias
import br.com.begood.bmp.data.formatarDuracao
import java.text.Collator
import java.util.Locale

private val iconesAutomatica = mapOf(
    Automatica.Favoritas to Icons.Filled.Favorite,
    Automatica.MaisTocadas to Icons.AutoMirrored.Filled.TrendingUp,
    Automatica.Recentes to Icons.Filled.History,
    Automatica.Novas to Icons.Filled.NewReleases,
)

/** Aba Playlists: Genius, automáticas e as do usuário. */
@Composable
fun AbaPlaylists(vm: AppViewModel, lib: Biblioteca) {
    var criando by remember { mutableStateOf(false) }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importarM3u(it) } }
    val automaticas = remember(lib, vm.versaoEstatisticas) { Automatica.entries.associateWith { Playlists.musicas(it.id, lib) } }
    val collator = remember { Collator.getInstance(Locale("pt", "BR")).apply { strength = Collator.PRIMARY } }
    val minhas = remember(vm.playlists, lib) {
        vm.playlists.sortedWith { a, b -> collator.compare(a.nome, b.nome) }.map { it to Playlists.musicas(it.id, lib) }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { criando = true }) { Icon(Icons.Filled.Add, null); Text(" Nova playlist") }
                OutlinedButton(onClick = { runCatching { importar.launch(arrayOf("*/*")) } }) { Icon(Icons.Filled.FileDownload, null); Text(" Importar .m3u") }
            }
        }
        item { Titulo("Genius") }
        item {
            val atual = vm.tocador.atual?.idMusica?.let { lib.porId[it] }
            ListItem(
                leadingContent = { Icone(Icons.Filled.AutoAwesome) },
                headlineContent = { Text(if (atual != null) "Genius de \"${atual.titulo}\"" else "Playlist Genius") },
                supportingContent = {
                    Text(
                        if (atual != null) "Monta uma playlist com as músicas que combinam com a que está tocando. " +
                            "Para outra música, use o menu ⋮ dela > Playlist Genius."
                        else "Escolha uma música (menu ⋮ > Playlist Genius) e o app monta uma playlist com as que combinam com ela.",
                    )
                },
                modifier = Modifier.clickable {
                    if (atual != null) vm.abrir(Tela.GeniusTela(atual.id))
                    else vm.aviso = "Toque no menu ⋮ de uma música e escolha \"Playlist Genius\"."
                },
            )
        }
        item { Titulo("Automáticas") }
        items(Automatica.entries, key = { it.id }) { a ->
            val ms = automaticas[a].orEmpty()
            ListItem(
                leadingContent = { Icone(iconesAutomatica.getValue(a)) },
                headlineContent = { Text(a.nome) },
                supportingContent = { Text("${ms.size} música(s) · ${a.descricao}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.clickable { vm.abrir(Tela.PlaylistTela(a.id)) },
            )
        }
        item { Titulo("Minhas playlists (${minhas.size})") }
        if (minhas.isEmpty()) item {
            Text(
                "Nenhuma ainda. Toque em \"Nova playlist\", ou no menu ⋮ de uma música > \"Adicionar à playlist\". " +
                    "Com toque longo dá para marcar várias músicas de uma vez.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(minhas, key = { it.first.id }) { (p, ms) ->
            ListItem(
                leadingContent = { Capa(ms.firstOrNull()?.capa, Modifier.size(48.dp), tamanhoIcone = 24.dp) },
                headlineContent = { Text(p.nome, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${ms.size} música(s) · ${formatarDuracao(ms.sumOf { it.duracaoMs })}") },
                modifier = Modifier.clickable { vm.abrir(Tela.PlaylistTela(p.id)) },
            )
        }
    }

    if (criando) JanelaNome("Nova playlist", "", "Criar", fechar = { criando = false }) { nome ->
        val id = vm.criarPlaylist(nome)
        vm.abrir(Tela.PlaylistTela(id))
    }
}

@Composable
private fun Icone(icone: ImageVector, modifier: Modifier = Modifier.size(48.dp), tamanhoIcone: androidx.compose.ui.unit.Dp = 24.dp) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) { Icon(icone, null, tint = Ambar, modifier = Modifier.size(tamanhoIcone)) }
    }
}

/** Uma playlist aberta: tocar, organizar (subir/descer/tirar), renomear, exportar .m3u, apagar. */
@Composable
fun TelaPlaylist(vm: AppViewModel, lib: Biblioteca, id: String) {
    val auto = Automatica.de(id)
    val p = vm.playlists.firstOrNull { it.id == id }
    if (auto == null && p == null) { SumiuVoltar(vm); return }
    // (posição na playlist, música) — a posição conta também as músicas que não estão no celular agora.
    val itens = remember(p, lib, vm.versaoEstatisticas) {
        if (p == null) Playlists.musicas(id, lib).mapIndexed { i, m -> i to m }
        else p.caminhos.mapIndexedNotNull { i, c -> lib.porCaminho[c.lowercase()]?.let { i to it } }
    }
    val musicas = remember(itens) { itens.map { it.second } }
    val ausentes = (p?.caminhos?.size ?: 0) - itens.size
    val nome = p?.nome ?: auto?.nome.orEmpty()
    var organizar by remember { mutableStateOf(false) }
    var renomear by remember { mutableStateOf(false) }
    var apagar by remember { mutableStateOf(false) }
    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        uri?.let { vm.exportarM3u(id, it) }
    }
    SideEffect { vm.listaAtual = musicas.map { it.id } }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (auto != null) Icone(iconesAutomatica.getValue(auto), Modifier.size(100.dp), 48.dp)
                else Capa(musicas.firstOrNull()?.capa, Modifier.size(100.dp), tamanhoIcone = 48.dp)
                Column(Modifier.padding(start = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(nome, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${musicas.size} música(s) · ${formatarDuracao(musicas.sumOf { it.duracaoMs })}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (auto != null) Text(auto.descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opções da playlist") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (p != null) {
                            DropdownMenuItem(text = { Text(if (organizar) "Concluir organização" else "Organizar (mudar ordem)") },
                                leadingIcon = { Icon(if (organizar) Icons.Filled.Done else Icons.Filled.SwapVert, null) },
                                onClick = { menu = false; organizar = !organizar }, enabled = musicas.isNotEmpty())
                            DropdownMenuItem(text = { Text("Renomear") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                onClick = { menu = false; renomear = true })
                        } else {
                            DropdownMenuItem(text = { Text("Salvar como playlist minha") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                                onClick = { menu = false; vm.paraPlaylist = musicas.map { it.id } }, enabled = musicas.isNotEmpty())
                        }
                        DropdownMenuItem(text = { Text("Exportar .m3u") }, leadingIcon = { Icon(Icons.Filled.FileUpload, null) },
                            onClick = { menu = false; runCatching { exportar.launch("${nome.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.m3u8") } },
                            enabled = musicas.isNotEmpty())
                        if (p != null) DropdownMenuItem(text = { Text("Apagar playlist") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menu = false; apagar = true })
                    }
                }
            }
            if (organizar) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Use as setas para mudar a ordem e o X para tirar.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Button(onClick = { organizar = false }) { Text("Concluir") }
            } else if (musicas.isNotEmpty()) Box(Modifier.padding(horizontal = 16.dp)) { BotoesTocar(musicas, vm.tocador) }
            if (ausentes > 0 && p != null) Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$ausentes música(s) não estão no celular agora (arquivo movido, apagado ou cartão SD fora).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = { Playlists.tirarAusentes(id, lib) }) { Text("Tirar da lista") }
            }
            if (musicas.isEmpty()) Text(
                when (auto) {
                    Automatica.Favoritas -> "Nenhuma favorita ainda. Toque no coração em \"Tocando agora\" ou no menu ⋮ de uma música > Favoritar."
                    Automatica.MaisTocadas, Automatica.Recentes -> "Ainda não há músicas tocadas. Uma música conta depois de 30 segundos tocando."
                    else -> "Playlist vazia. Para adicionar: menu ⋮ de uma música > \"Adicionar à playlist\", ou toque longo para marcar várias."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp),
            )
            Spacer(Modifier.size(8.dp))
        }
        itemsIndexed(itens, key = { k, _ -> k }) { k, (pos, m) ->
            if (organizar && p != null) {
                ListItem(
                    headlineContent = { Text(m.titulo, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(m.artista, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Text("${k + 1}", modifier = Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { vm.moverNaPlaylist(id, pos, itens[k - 1].first) }, enabled = k > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Subir") }
                            IconButton(onClick = { vm.moverNaPlaylist(id, pos, itens[k + 1].first) }, enabled = k < itens.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "Descer") }
                            IconButton(onClick = { vm.removerDaPlaylist(id, pos) }) { Icon(Icons.Filled.Close, "Tirar da playlist") }
                        }
                    },
                )
                HorizontalDivider()
            } else {
                val tirar: (@Composable (() -> Unit) -> Unit)? = if (p == null) null else ({ fechar ->
                    DropdownMenuItem(text = { Text("Tirar desta playlist") }, leadingIcon = { Icon(Icons.Filled.Close, null) },
                        onClick = { fechar(); vm.removerDaPlaylist(id, pos) })
                })
                LinhaMusica(m, vm, capa = true, menuExtra = tirar, onClick = { vm.tocador.tocar(musicas, k) })
            }
        }
    }

    if (renomear && p != null) JanelaNome("Renomear playlist", p.nome, "Salvar", fechar = { renomear = false }) { vm.renomearPlaylist(id, it) }
    if (apagar && p != null) AlertDialog(
        onDismissRequest = { apagar = false },
        title = { Text("Apagar playlist?") },
        text = { Text("A playlist \"${p.nome}\" será apagada. As músicas continuam no celular.") },
        confirmButton = { TextButton(onClick = { apagar = false; vm.apagarPlaylist(id) }) { Text("Apagar") } },
        dismissButton = { TextButton(onClick = { apagar = false }) { Text("Cancelar") } },
    )
}

/** Playlist Genius montada a partir de [semente]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaGenius(vm: AppViewModel, lib: Biblioteca, semente: Musica) {
    var tamanho by remember { mutableIntStateOf(Preferencias.tamanhoGenius) }
    var vez by remember { mutableIntStateOf(0) }
    var resultado by remember { mutableStateOf<Genius.Resultado?>(null) }
    LaunchedEffect(semente.id, tamanho, vez) {
        resultado = null
        resultado = runCatching { Genius.playlist(semente, lib, tamanho) }
            .getOrElse { Genius.Resultado(listOf(semente), Genius.Fonte.Local, it.message ?: "Não consegui montar a playlist.") }
    }
    val r = resultado
    val musicas = r?.musicas.orEmpty()
    SideEffect { vm.listaAtual = musicas.map { it.id } }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Capa(semente.capa, Modifier.size(100.dp), tamanhoIcone = 48.dp)
                Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Ambar, modifier = Modifier.size(18.dp))
                        Text(" Genius a partir de", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(semente.titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(semente.artista, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                val opcoes = listOf(25, 50, 75, 100)
                opcoes.forEachIndexed { i, n ->
                    SegmentedButton(selected = tamanho == n, onClick = { tamanho = n; Preferencias.tamanhoGenius = n },
                        shape = SegmentedButtonDefaults.itemShape(i, opcoes.size)) { Text("$n") }
                }
            }
            if (r == null) Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(22.dp)); Text("Procurando músicas que combinam…")
            } else {
                Text(
                    when (r.fonte) {
                        Genius.Fonte.LastFm -> "Músicas que os ouvintes da Last.fm escutam junto com esta, entre as que você tem."
                        Genius.Fonte.SoArtista -> "A Last.fm não conhece esta faixa: playlist feita com artistas parecidos."
                        Genius.Fonte.Local -> "Playlist feita pelo gênero e pela época."
                    } + if (musicas.size < tamanho) " (${musicas.size} encontradas)" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                r.aviso?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { BotoesTocar(musicas, vm.tocador) }
                    IconButton(onClick = { vm.criarPlaylist("Genius: ${semente.titulo}", musicas.map { it.id }) }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Salvar como playlist")
                    }
                    IconButton(onClick = { vez++ }) { Icon(Icons.Filled.Refresh, "Montar de novo") }
                }
            }
        }
        if (r != null) itemsIndexed(musicas, key = { _, m -> m.id }) { i, m ->
            LinhaMusica(m, vm, capa = true, onClick = { vm.tocador.tocar(musicas, i) })
        }
    }
}

/** Janela "Adicionar à playlist": escolhe uma playlist existente ou cria uma nova com as músicas. */
@Composable
fun JanelaAdicionarPlaylist(vm: AppViewModel, ids: List<Long>, fechar: () -> Unit) {
    var nova by remember { mutableStateOf(vm.playlists.isEmpty()) }
    var nome by remember { mutableStateOf("") }
    val collator = remember { Collator.getInstance(Locale("pt", "BR")).apply { strength = Collator.PRIMARY } }
    AlertDialog(
        onDismissRequest = fechar,
        title = { Text(if (nova) "Nova playlist" else "Adicionar à playlist") },
        text = {
            Column {
                Text("${ids.size} música(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(8.dp))
                if (nova) OutlinedTextField(nome, { nome = it }, singleLine = true, label = { Text("Nome da playlist") }, modifier = Modifier.fillMaxWidth())
                else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        ListItem(leadingContent = { Icon(Icons.Filled.Add, null, tint = Ambar) }, headlineContent = { Text("Nova playlist") },
                            modifier = Modifier.clickable { nova = true })
                    }
                    items(vm.playlists.sortedWith { a, b -> collator.compare(a.nome, b.nome) }, key = { it.id }) { p ->
                        ListItem(
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                            headlineContent = { Text(p.nome, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${p.caminhos.size} música(s)") },
                            modifier = Modifier.clickable { vm.adicionarNaPlaylist(p.id, ids); fechar() },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (nova) TextButton(onClick = { vm.criarPlaylist(nome, ids); fechar() }, enabled = nome.isNotBlank()) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = fechar) { Text("Cancelar") } },
    )
}

@Composable
fun JanelaNome(titulo: String, inicial: String, botao: String, fechar: () -> Unit, ok: (String) -> Unit) {
    var nome by remember { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = fechar,
        title = { Text(titulo) },
        text = { OutlinedTextField(nome, { nome = it }, singleLine = true, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { fechar(); ok(nome.trim()) }, enabled = nome.isNotBlank()) { Text(botao) } },
        dismissButton = { TextButton(onClick = fechar) { Text("Cancelar") } },
    )
}
