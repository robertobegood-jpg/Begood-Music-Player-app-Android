package br.com.begood.bmp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import br.com.begood.bmp.BuildConfig
import br.com.begood.bmp.data.Genius
import br.com.begood.bmp.data.Preferencias
import br.com.begood.bmp.data.Repositorio

@Composable
fun TelaConfiguracoes(vm: AppViewModel) {
    var retomar by remember { mutableStateOf(Preferencias.retomarBluetooth) }
    var letraAuto by remember { mutableStateOf(Preferencias.letraAutomatica) }
    // Recalcula as listas quando a biblioteca muda.
    val lib = vm.biblioteca
    val encontradas = remember(lib) { Repositorio.pastasEncontradas() }

    val escolherPasta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val caminho = Repositorio.caminhoDaArvore(uri)
        if (caminho == null) vm.aviso = "Não consegui usar essa pasta. Escolha uma pasta da memória do celular ou do cartão SD."
        else vm.adicionarPasta(caminho)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Titulo("Pastas de música") }
        item {
            Text(
                if (vm.pastas.isEmpty()) "Nenhuma pasta escolhida: o app usa a pasta padrão Music (memória interna e cartão SD)."
                else "O app mostra só as músicas destas pastas (e subpastas):",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        items(vm.pastas, key = { "p:$it" }) { p ->
            ListItem(
                leadingContent = { Icon(Icons.Filled.Folder, null, tint = Ambar) },
                headlineContent = { Text(p.substringAfterLast('/').ifEmpty { p }) },
                supportingContent = { Text("$p · ${Repositorio.quantidadeEm(p)} música(s)") },
                trailingContent = { IconButton(onClick = { vm.removerPasta(p) }) { Icon(Icons.Filled.Delete, "Remover pasta") } },
            )
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { runCatching { escolherPasta.launch(null) } }) { Icon(Icons.Filled.CreateNewFolder, null); Text(" Adicionar pasta") }
                if (vm.pastas.isNotEmpty()) TextButton(onClick = { vm.pastas.toList().forEach(vm::removerPasta) }) { Text("Usar a pasta padrão") }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.escanear() }, enabled = !vm.escaneando) { Icon(Icons.Filled.Refresh, null); Text(" Escanear agora") }
                if (vm.escaneando) CircularProgressIndicator(Modifier.size(22.dp))
                Text("${lib?.musicas?.size ?: 0} músicas na biblioteca", style = MaterialTheme.typography.bodySmall)
            }
        }
        val sugeridas = encontradas.filter { e -> vm.pastas.none { it.equals(e.caminho, true) } }
        if (sugeridas.isNotEmpty()) {
            item { Titulo("Pastas com músicas encontradas no celular") }
            items(sugeridas, key = { "e:" + it.caminho }) { e ->
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Folder, null) },
                    headlineContent = { Text(e.caminho.substringAfterLast('/')) },
                    supportingContent = { Text("${e.caminho} · ${e.quantidade} música(s)") },
                    trailingContent = { IconButton(onClick = { vm.adicionarPasta(e.caminho) }) { Icon(Icons.Filled.Add, "Adicionar") } },
                )
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)); Titulo("Bluetooth") }
        item {
            ListItem(
                leadingContent = { Icon(Icons.Filled.Bluetooth, null, tint = Ambar) },
                headlineContent = { Text("Voltar a tocar ao conectar") },
                supportingContent = { Text("Quando um fone, caixa de som ou carro Bluetooth conectar, continua a música de onde parou.") },
                trailingContent = { Switch(retomar, { retomar = it; Preferencias.retomarBluetooth = it }) },
            )
        }
        item {
            Text(
                "Sempre ativo: pausa ao desconectar o fone, botões de play/pausa/próxima do fone e do carro, " +
                    "nome da música e capa no visor do carro, navegação por álbuns e artistas pelo rádio do carro " +
                    "(e Android Auto) e o botão play do carro retoma a última fila mesmo com o app fechado.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)); Titulo("Letras") }
        item {
            ListItem(
                leadingContent = { Icon(Icons.Filled.Lyrics, null, tint = Ambar) },
                headlineContent = { Text("Buscar letra na internet sozinho") },
                supportingContent = { Text("Se a música não tiver letra, procura no LRCLIB (gratuito, com letras sincronizadas).") },
                trailingContent = { Switch(letraAuto, { letraAuto = it; Preferencias.letraAutomatica = it }) },
            )
        }
        item {
            // Quantas letras estão guardadas só no app (recalcula ao mudar a biblioteca e depois de gravar).
            var pendentes by remember { mutableStateOf<Int?>(null) }
            LaunchedEffect(lib, vm.gravando) { if (!vm.gravando) pendentes = vm.musicasComLetraSalva().size }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (val n = pendentes) {
                        null -> "Contando letras salvas no app…"
                        0 -> "Todas as letras encontradas já estão dentro das músicas."
                        else -> "$n letra(s) estão salvas só no app (achadas na internet ou coladas). " +
                            "Grave-as nas músicas para não perder ao reinstalar o app e para aparecerem em outros players."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.gravarLetrasSalvas() }, enabled = (pendentes ?: 0) > 0 && !vm.gravando) {
                        Icon(Icons.Filled.Save, null); Text(" Gravar letras nas músicas")
                    }
                    if (vm.gravando) CircularProgressIndicator(Modifier.size(22.dp))
                }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)); Titulo("Genius (playlists que combinam)") }
        item { ConfigGenius(vm) }

        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Begood Music Player (B.M.P)", style = MaterialTheme.typography.titleSmall)
                Text("Versão ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("Dados de álbum e capas: iTunes e Deezer. Letras: LRCLIB. Genius: Last.fm.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Chave da Last.fm e download dos dados para o Genius funcionar sem internet. */
@Composable
private fun ConfigGenius(vm: AppViewModel) {
    var chave by remember { mutableStateOf(Preferencias.chaveLastFm) }
    var apagar by remember { mutableStateOf(false) }
    // Recalcula o resumo quando termina um download ou depois de apagar.
    var resumo by remember { mutableStateOf(Genius.resumo()) }
    LaunchedEffect(vm.geniusProgresso, apagar) { if (vm.geniusProgresso == null) resumo = Genius.resumo() }
    val abrir = LocalUriHandler.current
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Igual ao Genius do iPod/iTunes: a Last.fm sabe quais músicas os ouvintes escutam juntas. " +
                "O app guarda essas informações no celular e depois monta as playlists mesmo sem internet. " +
                "Precisa de uma chave grátis da Last.fm: crie a conta, preencha \"Application name\" com qualquer nome " +
                "e copie a \"API key\". Sem chave, o Genius usa só o gênero e a época.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = chave, onValueChange = { chave = it.trim(); Preferencias.chaveLastFm = chave },
            singleLine = true, label = { Text("Chave da API da Last.fm") },
            leadingIcon = { Icon(Icons.Filled.Key, null) },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { runCatching { abrir.openUri(Genius.SITE_CHAVE) } }) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Text(" Criar chave grátis na Last.fm")
        }
        Text("Guardado no celular: ${resumo.first} artista(s) e ${resumo.second} música(s).", style = MaterialTheme.typography.bodySmall)
        val progresso = vm.geniusProgresso
        if (progresso != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(22.dp))
            Text(progresso, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.pararGenius() }) { Text("Parar") }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.baixarGenius() }, enabled = chave.isNotBlank()) { Icon(Icons.Filled.CloudDownload, null); Text(" Baixar para usar offline") }
            if (resumo.first + resumo.second > 0) TextButton(onClick = { apagar = true }) { Text("Apagar") }
        }
        Text(
            "Baixa os artistas parecidos de todos os artistas da biblioteca e as músicas parecidas das favoritas e mais tocadas " +
                "(uns 4 pedidos por segundo; biblioteca grande leva alguns minutos). As outras músicas são buscadas na hora " +
                "em que você usa o Genius com internet, e ficam guardadas.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (apagar) AlertDialog(
        onDismissRequest = { apagar = false },
        title = { Text("Apagar dados do Genius?") },
        text = { Text("Os dados baixados da Last.fm serão apagados. Dá para baixar de novo depois.") },
        confirmButton = { TextButton(onClick = { Genius.apagarDados(); apagar = false }) { Text("Apagar") } },
        dismissButton = { TextButton(onClick = { apagar = false }) { Text("Cancelar") } },
    )
}
