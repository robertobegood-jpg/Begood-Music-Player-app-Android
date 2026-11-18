package br.com.begood.bmp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import br.com.begood.bmp.data.Album
import br.com.begood.bmp.data.Biblioteca
import br.com.begood.bmp.data.Grupo
import br.com.begood.bmp.data.Musica
import br.com.begood.bmp.data.formatarDuracao
import br.com.begood.bmp.player.Tocador
import kotlinx.coroutines.delay

@Composable
fun AppCelular(vm: AppViewModel, pedirPermissao: () -> Unit) {
    MaterialTheme(colorScheme = CoresEscuras) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val lib = vm.biblioteca
            when {
                !vm.temPermissao -> TelaPermissao(pedirPermissao)
                lib == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(); Text("Lendo as músicas do celular…")
                    }
                }
                else -> Principal(vm, lib)
            }
        }
    }
}

@Composable
private fun TelaPermissao(pedir: () -> Unit) {
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Filled.LibraryMusic, null, tint = Ambar, modifier = Modifier.size(80.dp))
            Text("Begood Music Player", style = MaterialTheme.typography.headlineMedium)
            Text("Para tocar suas músicas, o app precisa de permissão para ler os arquivos de áudio do celular.", textAlign = TextAlign.Center)
            Button(onClick = pedir) { Text("Permitir acesso às músicas") }
            Text(
                "Se você já negou antes, abra Configurações do Android > Apps > Begood Music Player > Permissões > Músicas e áudio.",
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val iconesAba = mapOf(
    Aba.Albuns to Icons.Filled.Album,
    Aba.Artistas to Icons.Filled.Person,
    Aba.Musicas to Icons.Filled.MusicNote,
    Aba.Generos to Icons.Filled.Category,
    Aba.Buscar to Icons.Filled.Search,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Principal(vm: AppViewModel, lib: Biblioteca) {
    val tela = vm.pilha.lastOrNull()
    BackHandler(enabled = tela != null) { vm.voltar() }
    // Declarado depois: tem prioridade. Com músicas marcadas, "voltar" só desmarca.
    BackHandler(enabled = vm.selecionando && tela !is Tela.EditarTags) { vm.limparSelecao() }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.aviso) { vm.aviso?.let { snack.showSnackbar(it); vm.aviso = null } }

    if (tela == Tela.TocandoAgora) {
        Box(Modifier.fillMaxSize()) {
            TocandoAgora(vm, onFechar = { vm.voltar() })
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).systemBarsPadding())
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            if (vm.selecionando && tela !is Tela.EditarTags) BarraSelecao(vm)
            else TopAppBar(
                title = {
                    Text(
                        when (tela) {
                            is Tela.AlbumTela -> lib.porChave[tela.chave]?.titulo ?: ""
                            is Tela.ArtistaTela -> tela.nome
                            is Tela.GeneroTela -> tela.nome
                            Tela.EqualizadorTela -> "Equalizador"
                            Tela.Configuracoes -> "Configurações"
                            is Tela.EditarTags -> when { tela.doAlbum -> "Editar álbum"; tela.ids.size > 1 -> "Editar ${tela.ids.size} músicas"; else -> "Editar tags" }
                            else -> if (vm.aba == Aba.Albuns) "B.M.P" else vm.aba.titulo
                        },
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (tela != null) IconButton(onClick = { vm.voltar() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    if (tela == null || tela is Tela.AlbumTela || tela is Tela.ArtistaTela || tela is Tela.GeneroTela) {
                        IconButton(onClick = { vm.abrir(Tela.EqualizadorTela) }) { Icon(Icons.Filled.Equalizer, "Equalizador") }
                    }
                    if (tela == null) IconButton(onClick = { vm.abrir(Tela.Configuracoes) }) { Icon(Icons.Filled.Settings, "Configurações") }
                    if (tela is Tela.AlbumTela) lib.porChave[tela.chave]?.let { a ->
                        Box {
                            var menu by remember { mutableStateOf(false) }
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mais opções") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Editar tags do álbum") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                    onClick = { menu = false; vm.abrir(Tela.EditarTags(a.musicas.map { it.id }, true)) })
                                DropdownMenuItem(text = { Text("Selecionar faixas") }, leadingIcon = { Icon(Icons.Filled.Checklist, null) },
                                    onClick = { menu = false; vm.selecionarTodas(a.musicas.map { it.id }) })
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (tela !is Tela.EditarTags) Column {
                MiniPlayer(vm.tocador) { vm.abrir(Tela.TocandoAgora) }
                if (tela == null || tela is Tela.AlbumTela || tela is Tela.ArtistaTela || tela is Tela.GeneroTela) NavigationBar {
                    Aba.entries.forEach { a ->
                        NavigationBarItem(
                            selected = tela == null && vm.aba == a,
                            onClick = { vm.limparSelecao(); vm.pilha.clear(); vm.aba = a },
                            icon = { Icon(iconesAba.getValue(a), null) },
                            label = { Text(a.titulo, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tela) {
                is Tela.AlbumTela -> lib.porChave[tela.chave]?.let { TelaAlbum(it, vm) } ?: SumiuVoltar(vm)
                is Tela.ArtistaTela -> (if (tela.daFaixa) lib.artistaFaixa(tela.nome) else lib.artista(tela.nome))?.let { TelaGrupo(it, vm, tela.daFaixa) } ?: SumiuVoltar(vm)
                is Tela.GeneroTela -> lib.genero(tela.nome)?.let { TelaGrupo(it, vm) } ?: SumiuVoltar(vm)
                Tela.EqualizadorTela -> TelaEqualizador()
                Tela.Configuracoes -> TelaConfiguracoes(vm)
                is Tela.EditarTags -> TelaTags(vm, tela.ids, tela.doAlbum)
                else -> if (lib.musicas.isEmpty()) BibliotecaVazia(vm) else when (vm.aba) {
                    Aba.Albuns -> GradeAlbuns(lib.albuns, vm)
                    Aba.Artistas -> ListaArtistas(vm, lib)
                    Aba.Musicas -> ListaMusicas(lib.musicas, vm)
                    Aba.Generos -> ListaGrupos(lib.generos) { vm.abrir(Tela.GeneroTela(it.nome)) }
                    Aba.Buscar -> TelaBusca(vm, lib)
                }
            }
        }
    }
}

/** O álbum/artista mudou de nome (ex.: depois de editar as tags): volta para a lista. */
@Composable
private fun SumiuVoltar(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.voltar() }
}

@Composable
private fun BibliotecaVazia(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Icon(Icons.Filled.LibraryMusic, null, tint = Ambar, modifier = Modifier.size(64.dp))
        Text(
            if (vm.pastas.isEmpty()) "Nenhuma música na pasta Music do celular." else "Nenhuma música nas pastas escolhidas.",
            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
        )
        Text("Escolha as pastas onde estão suas músicas ou escaneie de novo.", textAlign = TextAlign.Center)
        Button(onClick = { vm.abrir(Tela.Configuracoes) }) { Text("Escolher pastas") }
        FilledTonalButton(onClick = { vm.escanear() }, enabled = !vm.escaneando) { Text(if (vm.escaneando) "Escaneando…" else "Escanear agora") }
    }
}

@Composable
private fun CartaoAlbum(a: Album, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(6.dp)) {
        Capa(a.capa, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(a.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(listOf(a.artista, a.ano).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GradeAlbuns(albuns: List<Album>, vm: AppViewModel, cabecalho: (@Composable () -> Unit)? = null) {
    LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(8.dp), modifier = Modifier.fillMaxSize()) {
        if (cabecalho != null) item(span = { GridItemSpan(maxLineSpan) }) { cabecalho() }
        items(albuns, key = { it.chave }) { a -> CartaoAlbum(a) { vm.abrir(Tela.AlbumTela(a.chave)) } }
    }
}

@Composable
private fun ListaGrupos(grupos: List<Grupo>, onClick: (Grupo) -> Unit) {
    val estado = rememberLazyListState()
    LazyColumn(state = estado, modifier = Modifier.fillMaxSize()) {
        items(grupos, key = { it.nome }) { g ->
            ListItem(
                headlineContent = { Text(g.nome, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${g.albuns.size} álbum(ns) · ${g.musicas.size} música(s)") },
                leadingContent = { Capa(g.albuns.firstOrNull()?.capa, Modifier.size(48.dp), tamanhoIcone = 24.dp) },
                modifier = Modifier.clickable { onClick(g) },
            )
        }
    }
}

@Composable
private fun ListaMusicas(musicas: List<Musica>, vm: AppViewModel) {
    SideEffect { vm.listaAtual = musicas.map { it.id } }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${musicas.size} músicas", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                BotoesTocar(musicas, vm.tocador)
            }
        }
        itemsIndexed(musicas, key = { _, m -> m.id }) { i, m ->
            LinhaMusica(m, vm, capa = true, onClick = { vm.tocador.tocar(musicas, i) })
        }
    }
}

@Composable
private fun BotoesTocar(musicas: List<Musica>, tocador: Tocador) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { tocador.tocar(musicas) }) {
            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Tocar")
        }
        FilledTonalButton(onClick = { tocador.tocar(musicas, embaralhar = true) }) {
            Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Aleatório")
        }
    }
}

@Composable
private fun TelaAlbum(a: Album, vm: AppViewModel) {
    SideEffect { vm.listaAtual = a.musicas.map { it.id } }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Capa(a.capa, Modifier.size(140.dp), tamanhoIcone = 64.dp)
                Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(a.titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(a.artista, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(enabled = vm.biblioteca?.artista(a.artista) != null) { vm.abrir(Tela.ArtistaTela(a.artista)) })
                    Text(listOf(a.ano, a.genero, "${a.musicas.size} faixas", formatarDuracao(a.duracaoTotal)).filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(Modifier.padding(horizontal = 16.dp)) { BotoesTocar(a.musicas, vm.tocador) }
            Spacer(Modifier.height(8.dp))
        }
        faixas(a.musicas, vm, mostrarArtista = a.artista == "Vários artistas")
    }
}

private fun LazyListScope.faixas(musicas: List<Musica>, vm: AppViewModel, mostrarArtista: Boolean) {
    itemsIndexed(musicas, key = { _, m -> m.id }) { i, m ->
        LinhaMusica(m, vm, numero = if (m.faixa > 0) "${m.faixa}" else "•", mostrarArtista = mostrarArtista, onClick = { vm.tocador.tocar(musicas, i) })
    }
}

@OptIn(ExperimentalFoundationApi::class)
/** Uma música na lista, com o menu ⋮ (tocar a seguir, fila, editar tags, ir para o álbum). */
@Composable
private fun LinhaMusica(
    m: Musica, vm: AppViewModel, numero: String? = null, capa: Boolean = false, mostrarArtista: Boolean = true, onClick: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val tocandoEsta = vm.tocador.atual?.idMusica == m.id
    val marcada = m.id in vm.selecao
    ListItem(
        leadingContent = when {
            vm.selecionando -> ({
                Icon(
                    if (marcada) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                    tint = if (marcada) Ambar else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (capa) 48.dp else 28.dp).padding(if (capa) 10.dp else 2.dp),
                )
            })
            capa ->({ Capa(m.capa, Modifier.size(48.dp), tamanhoIcone = 24.dp) })
            numero != null -> ({ Text(numero, modifier = Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) })
            else -> null
        },
        headlineContent = { Text(m.titulo, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (tocandoEsta) FontWeight.Bold else null) },
        supportingContent = {
            val sub = if (capa) listOf(m.artista, m.album).filter { it.isNotEmpty() }.joinToString(" · ") else if (mostrarArtista) m.artista else ""
            if (sub.isNotEmpty()) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.duracaoTexto, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (!vm.selecionando) Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opções") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Tocar a seguir") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                            onClick = { menu = false; vm.tocador.tocarASeguir(m); vm.aviso = "\"${m.titulo}\" toca a seguir." })
                        DropdownMenuItem(text = { Text("Adicionar à fila") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                            onClick = { menu = false; vm.tocador.adicionarNaFila(m); vm.aviso = "Adicionada à fila." })
                        DropdownMenuItem(text = { Text("Editar tags") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menu = false; vm.abrir(Tela.EditarTags(listOf(m.id), false)) })
                        vm.biblioteca?.albumDe(m)?.let { a ->
                            if (vm.pilha.lastOrNull() != Tela.AlbumTela(a.chave)) DropdownMenuItem(
                                text = { Text("Ir para o álbum") }, leadingIcon = { Icon(Icons.Filled.Album, null) },
                                onClick = { menu = false; vm.abrir(Tela.AlbumTela(a.chave)) },
                            )
                        }
                    }
                }
            }
        },
        colors = when {
            marcada -> ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            tocandoEsta -> ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.primary)
            else -> ListItemDefaults.colors()
        },
        // Toque longo marca a música (para editar várias de uma vez); com marcação ativa, o toque marca/desmarca.
        modifier = Modifier.combinedClickable(
            onClick = { if (vm.selecionando) vm.alternarSelecao(m.id) else onClick() },
            onLongClick = { vm.alternarSelecao(m.id) },
        ),
    )
}

/** Barra do topo quando há músicas marcadas: tocar, pôr na fila, editar tags em lote, marcar todas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarraSelecao(vm: AppViewModel) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("${vm.selecao.size} selecionada(s)") },
        navigationIcon = { IconButton(onClick = { vm.limparSelecao() }) { Icon(Icons.Filled.Close, "Cancelar seleção") } },
        actions = {
            IconButton(onClick = { vm.abrir(Tela.EditarTags(vm.selecao.toList(), false)) }) { Icon(Icons.Filled.Edit, "Editar tags em lote") }
            IconButton(onClick = { vm.selecionarTodas(vm.listaAtual) }) { Icon(Icons.Filled.SelectAll, "Marcar todas") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mais opções") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Tocar") }, leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                        onClick = { menu = false; vm.tocador.tocar(vm.musicasSelecionadas()); vm.limparSelecao() })
                    DropdownMenuItem(text = { Text("Tocar a seguir") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                        onClick = {
                            menu = false
                            vm.musicasSelecionadas().reversed().forEach { vm.tocador.tocarASeguir(it) }
                            vm.aviso = "${vm.selecao.size} música(s) tocam a seguir."; vm.limparSelecao()
                        })
                    DropdownMenuItem(text = { Text("Adicionar à fila") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                        onClick = {
                            menu = false
                            vm.musicasSelecionadas().forEach { vm.tocador.adicionarNaFila(it) }
                            vm.aviso = "${vm.selecao.size} música(s) adicionadas à fila."; vm.limparSelecao()
                        })
                    DropdownMenuItem(text = { Text("Editar tags em lote") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menu = false; vm.abrir(Tela.EditarTags(vm.selecao.toList(), false)) })
                }
            }
        },
    )
}

@Composable
private fun TelaGrupo(g: Grupo, vm: AppViewModel, daFaixa: Boolean = false) {
    val lib = vm.biblioteca
    // No artista do álbum, mostra também os álbuns de outros em que ele participa como convidado.
    val participacoes = remember(g, lib) {
        if (daFaixa || lib == null) emptyList()
        else lib.artistaFaixa(g.nome)?.albuns.orEmpty().filter { a -> g.albuns.none { it.chave == a.chave } }
    }
    SideEffect { vm.listaAtual = g.musicas.map { it.id } }
    LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(8.dp), modifier = Modifier.fillMaxSize()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(8.dp)) {
                Text(
                    (if (daFaixa) "Artista das faixas · " else "Artista do álbum · ") + "${g.albuns.size} álbum(ns) · ${g.musicas.size} música(s)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                BotoesTocar(g.musicas, vm.tocador)
            }
        }
        items(g.albuns, key = { it.chave }) { a -> CartaoAlbum(a) { vm.abrir(Tela.AlbumTela(a.chave)) } }
        if (participacoes.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Titulo("Participações em outros álbuns") }
            items(participacoes, key = { "p" + it.chave }) { a -> CartaoAlbum(a) { vm.abrir(Tela.AlbumTela(a.chave)) } }
        }
        if (daFaixa) {
            item(span = { GridItemSpan(maxLineSpan) }) { Titulo("Músicas") }
            g.musicas.forEachIndexed { i, m ->
                item(key = "m" + m.id, span = { GridItemSpan(maxLineSpan) }) {
                    LinhaMusica(m, vm, capa = true, onClick = { vm.tocador.tocar(g.musicas, i) })
                }
            }
        }
    }
}

/** Aba Artistas: escolhe entre artista do álbum (principal) e artista das faixas (inclui convidados). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListaArtistas(vm: AppViewModel, lib: Biblioteca) {
    val daFaixa = vm.artistasDaFaixa
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            SegmentedButton(selected = !daFaixa, onClick = { vm.mudarModoArtistas(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                Text("Artista do álbum (${lib.artistas.size})", maxLines = 1)
            }
            SegmentedButton(selected = daFaixa, onClick = { vm.mudarModoArtistas(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                Text("Artista da faixa (${lib.artistasFaixa.size})", maxLines = 1)
            }
        }
        Box(Modifier.weight(1f)) {
            if (daFaixa) ListaGrupos(lib.artistasFaixa) { vm.abrir(Tela.ArtistaTela(it.nome, daFaixa = true)) }
            else ListaGrupos(lib.artistas) { vm.abrir(Tela.ArtistaTela(it.nome)) }
        }
    }
}

@Composable
private fun TelaBusca(vm: AppViewModel, lib: Biblioteca) {
    val texto = vm.busca
    val musicas = remember(texto, lib) { if (texto.length >= 2) lib.buscar(texto) else emptyList() }
    val albuns = remember(texto, lib) { if (texto.length >= 2) lib.buscarAlbuns(texto) else emptyList() }
    SideEffect { vm.listaAtual = musicas.map { it.id } }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = texto, onValueChange = { vm.busca = it }, singleLine = true,
                placeholder = { Text("Música, artista ou álbum") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (texto.isNotEmpty()) IconButton(onClick = { vm.busca = "" }) { Icon(Icons.Filled.Close, "Limpar") } },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
        if (albuns.isNotEmpty()) {
            item { Titulo("Álbuns") }
            items(albuns, key = { "a" + it.chave }) { a ->
                ListItem(
                    leadingContent = { Capa(a.capa, Modifier.size(48.dp), tamanhoIcone = 24.dp) },
                    headlineContent = { Text(a.titulo, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(a.artista, maxLines = 1) },
                    modifier = Modifier.clickable { vm.abrir(Tela.AlbumTela(a.chave)) },
                )
            }
        }
        if (musicas.isNotEmpty()) {
            item { Titulo("Músicas") }
            itemsIndexed(musicas, key = { _, m -> "m" + m.id }) { i, m ->
                LinhaMusica(m, vm, capa = true, onClick = { vm.tocador.tocar(musicas, i) })
            }
        }
        if (texto.length >= 2 && musicas.isEmpty() && albuns.isEmpty()) item { Text("Nada encontrado.", modifier = Modifier.padding(16.dp)) }
    }
}

@Composable
private fun MiniPlayer(tocador: Tocador, onAbrir: () -> Unit) {
    val atual = tocador.atual ?: return
    var progresso by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            progresso = if (tocador.duracao > 0) tocador.posicao.toFloat() / tocador.duracao else 0f
            delay(500)
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            LinearProgressIndicator(progress = { progresso }, modifier = Modifier.fillMaxWidth().height(2.dp))
            Row(Modifier.clickable(onClick = onAbrir).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Capa(atual.capa, Modifier.size(44.dp), tamanhoIcone = 22.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(atual.titulo, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(tocador.erro ?: atual.artista, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        color = if (tocador.erro != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { tocador.tocarPausar() }) {
                    Icon(if (tocador.tocando) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (tocador.tocando) "Pausar" else "Tocar")
                }
                IconButton(onClick = { tocador.proxima() }) { Icon(Icons.Filled.SkipNext, "Próxima") }
            }
        }
    }
}

@Composable
private fun TocandoAgora(vm: AppViewModel, onFechar: () -> Unit) {
    val tocador = vm.tocador
    val atual = tocador.atual
    var posicao by remember { mutableLongStateOf(0L) }
    var arrastando by remember { mutableStateOf<Float?>(null) }
    var modo by remember { mutableStateOf("capa") } // capa, fila ou letra
    var saida by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            posicao = tocador.posicao
            if (n++ % 6 == 0) saida = tocador.saidaAtual()
            delay(500)
        }
    }
    val duracao = tocador.duracao
    val musica = atual?.idMusica?.let { vm.biblioteca?.porId?.get(it) }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onFechar) { Icon(Icons.Filled.KeyboardArrowDown, "Fechar") }
            Text("Tocando agora", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { modo = if (modo == "letra") "capa" else "letra" }) {
                Icon(Icons.Filled.Lyrics, "Letra", tint = if (modo == "letra") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { modo = if (modo == "fila") "capa" else "fila" }) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, "Fila", tint = if (modo == "fila") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { vm.abrir(Tela.EqualizadorTela) }) { Icon(Icons.Filled.Equalizer, "Equalizador") }
            if (musica != null) Box {
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mais opções") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Editar tags") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menu = false; vm.abrir(Tela.EditarTags(listOf(musica.id), false)) })
                    vm.biblioteca?.albumDe(musica)?.let { a ->
                        DropdownMenuItem(text = { Text("Editar tags do álbum") }, leadingIcon = { Icon(Icons.Filled.Album, null) },
                            onClick = { menu = false; vm.abrir(Tela.EditarTags(a.musicas.map { it.id }, true)) })
                        DropdownMenuItem(text = { Text("Ir para o álbum") }, leadingIcon = { Icon(Icons.Filled.LibraryMusic, null) },
                            onClick = { menu = false; vm.pilha.clear(); vm.abrir(Tela.AlbumTela(a.chave)) })
                    }
                }
            }
        }
        if (atual == null) { Text("Nada tocando. Escolha um álbum ou uma música.", modifier = Modifier.padding(16.dp)); return@Column }

        when (modo) {
            "letra" -> {
                PainelLetra(vm, Modifier.weight(1f).fillMaxWidth()) {
                    Text(atual.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(atual.artista, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
            }
            "fila" -> {
                Text("Fila (${tocador.fila.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(8.dp))
                val estado = rememberLazyListState(initialFirstVisibleItemIndex = tocador.indiceAtual.coerceAtLeast(0))
                LazyColumn(state = estado, modifier = Modifier.weight(1f)) {
                    itemsIndexed(tocador.fila) { i, item ->
                        val esta = i == tocador.indiceAtual
                        ListItem(
                            leadingContent = { Capa(item.capa, Modifier.size(40.dp), tamanhoIcone = 20.dp) },
                            headlineContent = { Text(item.titulo, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (esta) FontWeight.Bold else null) },
                            supportingContent = { Text(item.artista, maxLines = 1) },
                            trailingContent = if (!esta) ({ IconButton(onClick = { tocador.removerDaFila(i) }) { Icon(Icons.Filled.Close, "Tirar da fila") } }) else null,
                            colors = if (esta) ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.primary) else ListItemDefaults.colors(),
                            modifier = Modifier.clickable { tocador.tocarIndice(i) },
                        )
                        HorizontalDivider()
                    }
                }
            }
            else -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Capa(atual.capa, Modifier.fillMaxWidth().aspectRatio(1f), forma = RoundedCornerShape(12.dp), tamanhoIcone = 120.dp)
                }
                Spacer(Modifier.height(16.dp))
                Text(atual.titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(atual.artista, atual.album).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = musica != null) {
                        musica?.let { m -> vm.biblioteca?.albumDe(m)?.let { a -> vm.pilha.clear(); vm.abrir(Tela.AlbumTela(a.chave)) } }
                    },
                )
                tocador.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        Slider(
            value = arrastando ?: if (duracao > 0) posicao.toFloat() / duracao else 0f,
            onValueChange = { arrastando = it },
            onValueChangeFinished = { arrastando?.let { tocador.irPara((it * duracao).toLong()) }; arrastando = null },
            enabled = duracao > 0,
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatarDuracao(arrastando?.let { (it * duracao).toLong() } ?: posicao), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            Text(formatarDuracao(duracao), style = MaterialTheme.typography.bodySmall)
        }
        Controles(tocador)
        if (saida.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(if (saida.startsWith("Bluetooth")) Icons.Filled.Bluetooth else Icons.Filled.Speaker, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text(" $saida", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun Controles(tocador: Tocador) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        BotaoIcone(Icons.Filled.Shuffle, "Aleatório", ativo = tocador.aleatorio) { tocador.alternarAleatorio() }
        BotaoIcone(Icons.Filled.SkipPrevious, "Anterior") { tocador.anterior() }
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(68.dp)) {
            IconButton(onClick = { tocador.tocarPausar() }) {
                Icon(if (tocador.tocando) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (tocador.tocando) "Pausar" else "Tocar",
                    tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
            }
        }
        BotaoIcone(Icons.Filled.SkipNext, "Próxima") { tocador.proxima() }
        BotaoIcone(
            if (tocador.repetir == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            "Repetir", ativo = tocador.repetir != Player.REPEAT_MODE_OFF,
        ) { tocador.alternarRepetir() }
    }
}

@Composable
private fun BotaoIcone(icone: ImageVector, descricao: String, ativo: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
        Icon(icone, descricao, modifier = Modifier.size(30.dp), tint = if (ativo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}
