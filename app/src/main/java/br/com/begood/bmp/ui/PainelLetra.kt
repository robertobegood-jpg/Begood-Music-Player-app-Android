package br.com.begood.bmp.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.begood.bmp.data.Alteracao
import br.com.begood.bmp.data.FonteLetra
import br.com.begood.bmp.data.Letra
import br.com.begood.bmp.data.Letras
import br.com.begood.bmp.data.Musica
import br.com.begood.bmp.data.Preferencias
import br.com.begood.bmp.data.Rede
import br.com.begood.bmp.player.Tocador
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.tag.FieldKey

/** Estado da letra da música que está tocando. */
private sealed interface EstadoLetra {
    data object Carregando : EstadoLetra
    data object BuscandoOnline : EstadoLetra
    data class Pronta(val letra: Letra) : EstadoLetra
    data class Nenhuma(val mensagem: String? = null) : EstadoLetra
}

/**
 * Letra da música que está tocando. Procura no arquivo e, se não houver, na internet (LRCLIB).
 * Também permite pesquisar no Google, colar a letra copiada e gravar a letra nas tags.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PainelLetra(vm: AppViewModel, modifier: Modifier = Modifier, cabecalho: @Composable () -> Unit = {}) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    val id = vm.tocador.atual?.idMusica
    val musica = id?.let { vm.biblioteca?.porId?.get(it) }
    var recarregar by remember { mutableIntStateOf(0) }
    var estado by remember { mutableStateOf<EstadoLetra>(EstadoLetra.Carregando) }
    var editando by remember { mutableStateOf<String?>(null) }

    fun buscarOnline(m: Musica) = escopo.launch {
        estado = EstadoLetra.BuscandoOnline
        estado = try {
            val l = withContext(Dispatchers.IO) { Letras.online(m) }
            if (l.vazia) EstadoLetra.Nenhuma("Não achei a letra desta música na internet.")
            else {
                withContext(Dispatchers.IO) { Letras.salvar(ctx, m, l.textoOriginal) }
                EstadoLetra.Pronta(l)
            }
        } catch (e: Exception) {
            EstadoLetra.Nenhuma(Rede.mensagem(e))
        }
    }

    LaunchedEffect(id, recarregar) {
        estado = EstadoLetra.Carregando
        val m = musica ?: run { estado = EstadoLetra.Nenhuma(); return@LaunchedEffect }
        val local = withContext(Dispatchers.IO) { runCatching { Letras.local(ctx, m) }.getOrDefault(Letra.VAZIA) }
        if (!local.vazia) estado = EstadoLetra.Pronta(local)
        else if (Preferencias.letraAutomatica) buscarOnline(m)
        else estado = EstadoLetra.Nenhuma()
    }

    val aviso = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF8A8F9C), textAlign = TextAlign.Center)
    Column(modifier) {
        // Cabeçalho (título e artista) com o menu ⋮ das opções da letra.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { cabecalho() }
            val pronta = estado as? EstadoLetra.Pronta
            if (pronta != null && musica != null) Box {
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opções da letra") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        enabled = false, onClick = {},
                        text = { Text("Fonte: ${pronta.letra.fonte?.descricao ?: "—"}" + if (pronta.letra.sincronizada) " · sincronizada" else "") },
                        leadingIcon = { Icon(Icons.Filled.Info, null) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Editar letra") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menu = false; editando = pronta.letra.textoOriginal })
                    if (pronta.letra.fonte != FonteLetra.Tag) DropdownMenuItem(
                        text = { Text("Gravar na música") }, leadingIcon = { Icon(Icons.Filled.Save, null) },
                        onClick = { menu = false; vm.gravarTags(listOf(musica)) { Alteracao(mapOf(FieldKey.LYRICS to pronta.letra.textoOriginal)) } },
                    )
                    DropdownMenuItem(text = { Text("Buscar outra na internet") }, leadingIcon = { Icon(Icons.Filled.TravelExplore, null) },
                        onClick = { menu = false; buscarOnline(musica) })
                    DropdownMenuItem(text = { Text("Pesquisar no Google") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                        onClick = { menu = false; pesquisarNoGoogle(ctx, musica) })
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (val e = estado) {
                EstadoLetra.Carregando -> Text("Procurando a letra…", style = aviso)
                EstadoLetra.BuscandoOnline -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Buscando a letra na internet…", style = aviso)
                }
                is EstadoLetra.Nenhuma -> Column(
                    Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(e.mensagem ?: "Esta música não tem letra.", style = aviso)
                    if (musica != null) {
                        FilledTonalButton(onClick = { buscarOnline(musica) }) {
                            Icon(Icons.Filled.TravelExplore, null); Text("  Buscar na internet")
                        }
                        OutlinedButton(onClick = { pesquisarNoGoogle(ctx, musica) }) {
                            Icon(Icons.Filled.Search, null); Text("  Pesquisar no Google")
                        }
                        OutlinedButton(onClick = { editando = "" }) {
                            Icon(Icons.Filled.ContentPaste, null); Text("  Colar ou escrever a letra")
                        }
                        Text("Dica: no Google, copie a letra e volte aqui para colar.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8A8F9C))
                    }
                }
                is EstadoLetra.Pronta -> ListaLetra(e.letra, vm.tocador)
            }
        }
    }

    editando?.let { inicial ->
        EditorLetra(
            inicial = inicial,
            onCancelar = { editando = null },
            onSalvar = { texto ->
                editando = null
                if (musica != null) escopo.launch {
                    withContext(Dispatchers.IO) { Letras.salvar(ctx, musica, texto) }
                    recarregar++
                }
            },
        )
    }
}

fun pesquisarNoGoogle(ctx: android.content.Context, m: Musica) {
    val termo = "${m.artista.ifEmpty { m.albumArtista }} ${m.titulo} letra".trim()
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(termo)))) }
}

@Composable
private fun EditorLetra(inicial: String, onCancelar: () -> Unit, onSalvar: (String) -> Unit) {
    var texto by remember { mutableStateOf(inicial) }
    val area = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Letra") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { area.getText()?.text?.let { texto = it } }) {
                    Icon(Icons.Filled.ContentPaste, null); Text("  Colar da área de transferência")
                }
                OutlinedTextField(
                    value = texto, onValueChange = { texto = it },
                    placeholder = { Text("Cole ou digite a letra aqui. Letras .lrc com [mm:ss] ficam sincronizadas.") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 360.dp),
                )
                Text("Fica salva no app. Para gravar dentro do arquivo, use \"Gravar na música\" depois.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSalvar(texto) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

@Composable
private fun ListaLetra(letra: Letra, tocador: Tocador) {
    val lista = rememberLazyListState()
    var posicao by remember { mutableLongStateOf(0L) }
    // Quando a pessoa rola com o dedo, para de acompanhar por alguns segundos.
    var rolouEm by remember { mutableLongStateOf(0L) }
    val dedo = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) rolouEm = System.currentTimeMillis()
                return Offset.Zero
            }
        }
    }

    if (letra.sincronizada) LaunchedEffect(letra) {
        while (true) { posicao = tocador.posicao; delay(250) }
    }
    val atual = letra.linhaAtual(posicao + 200)
    LaunchedEffect(atual) {
        if (atual >= 0 && System.currentTimeMillis() - rolouEm > 4000) lista.animateScrollToItem((atual - 3).coerceAtLeast(0))
    }

    LazyColumn(state = lista, contentPadding = PaddingValues(vertical = 24.dp, horizontal = 8.dp), modifier = Modifier.fillMaxSize().nestedScroll(dedo)) {
        itemsIndexed(letra.linhas) { i, linha ->
            val cor by animateColorAsState(
                when {
                    !letra.sincronizada -> Color(0xFFE6E6E6)
                    i == atual -> Ambar
                    i < atual -> Color(0xFF8A8F9C)
                    else -> Color(0xFFE6E6E6)
                }, label = "corLinha",
            )
            Text(
                linha.texto.ifEmpty { " " },
                color = cor, fontSize = 20.sp, lineHeight = 27.sp, textAlign = TextAlign.Center,
                fontWeight = if (i == atual) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.fillMaxWidth()
                    .clickable(enabled = linha.ms != null) { linha.ms?.let { tocador.irPara(it); rolouEm = 0L } }
                    .padding(vertical = 4.dp),
            )
        }
    }
}
