package br.com.begood.bmp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.begood.bmp.player.Banda
import br.com.begood.bmp.player.ConfigEq
import br.com.begood.bmp.player.Equalizador
import br.com.begood.bmp.player.TipoFiltro
import kotlin.math.log10
import kotlin.math.pow

private fun freqParaPos(f: Float) = (log10(f / 20f) / 3f).coerceIn(0f, 1f)
private fun posParaFreq(p: Float) = 20f * 1000f.pow(p)
private fun textoFreq(f: Float) = if (f >= 1000) "%.1f kHz".format(f / 1000f).replace(".0 ", " ") else "${f.toInt()} Hz"
private fun textoDb(d: Float) = (if (d > 0) "+" else "") + "%.1f dB".format(d)

/** Equalizador paramétrico: cada banda tem tipo, frequência, ganho e largura (Q) livres. */
@Composable
fun TelaEqualizador() {
    val cfg by Equalizador.config.collectAsState()
    val meus by Equalizador.presetsUsuario.collectAsState()
    var salvando by remember { mutableStateOf(false) }
    fun mudar(nova: ConfigEq) = Equalizador.atualizar(nova)
    fun mudarBanda(i: Int, b: Banda) = mudar(cfg.copy(bandas = cfg.bandas.toMutableList().also { it[i] = b }))

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Equalizador paramétrico", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Vale para alto-falante, fone com fio e Bluetooth.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = cfg.ligado, onCheckedChange = { mudar(cfg.copy(ligado = it)) })
            }
        }
        item { Curva(cfg) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Equalizador.presetsFixos + meus, key = { (if (it.doUsuario) "u:" else "f:") + it.nome }) { p ->
                    FilterChip(
                        selected = p.config.bandas == cfg.bandas && p.config.preamp == cfg.preamp,
                        onClick = { mudar(p.config.copy(ligado = true)) },
                        label = { Text(if (p.doUsuario) "★ ${p.nome}" else p.nome) },
                    )
                }
                item { OutlinedButton(onClick = { salvando = true }) { Text("Salvar como…") } }
            }
            if (meus.isNotEmpty()) Text(
                "Para apagar um preset seu, escolha-o e toque em \"Apagar preset\" abaixo.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            meus.firstOrNull { it.config.bandas == cfg.bandas && it.config.preamp == cfg.preamp }?.let { p ->
                TextButton(onClick = { Equalizador.apagarPreset(p.nome) }) { Icon(Icons.Filled.Delete, null); Text(" Apagar preset \"${p.nome}\"") }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pré-amplificação", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(textoDb(cfg.preamp), color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = { mudar(cfg.copy(preamp = Equalizador.preampSugerido(cfg))) }) { Text("Auto") }
                    }
                    Slider(value = cfg.preamp, onValueChange = { mudar(cfg.copy(preamp = (it * 2).toInt() / 2f)) }, valueRange = -Equalizador.GANHO_MAX..6f)
                    Text("Se o som distorcer com os reforços, toque em Auto.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        itemsIndexed(cfg.bandas) { i, b ->
            CartaoBanda(
                numero = i + 1, banda = b,
                onMudar = { mudarBanda(i, it) },
                onRemover = if (cfg.bandas.size > 1) ({ mudar(cfg.copy(bandas = cfg.bandas.filterIndexed { j, _ -> j != i })) }) else null,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { mudar(cfg.copy(bandas = cfg.bandas + Banda(TipoFiltro.Pico, 1000f, 0f, 1f))) },
                    enabled = cfg.bandas.size < Equalizador.MAX_BANDAS,
                ) { Icon(Icons.Filled.Add, null); Text(" Adicionar banda") }
                TextButton(onClick = { mudar(Equalizador.presetsFixos[0].config.copy(ligado = cfg.ligado)) }) { Text("Zerar") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (salvando) {
        var nome by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { salvando = false },
            title = { Text("Salvar preset") },
            text = { OutlinedTextField(nome, { nome = it.take(24) }, label = { Text("Nome") }, singleLine = true) },
            confirmButton = { TextButton(enabled = nome.isNotBlank(), onClick = { Equalizador.salvarPreset(nome.trim()); salvando = false }) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = { salvando = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CartaoBanda(numero: Int, banda: Banda, onMudar: (Banda) -> Unit, onRemover: (() -> Unit)?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Banda $numero", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${textoFreq(banda.freq)} · ${textoDb(banda.ganho)}", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Switch(checked = banda.ativa, onCheckedChange = { onMudar(banda.copy(ativa = it)) })
                if (onRemover != null) IconButton(onClick = onRemover) { Icon(Icons.Filled.Delete, "Remover banda") }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TipoFiltro.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = banda.tipo == t, onClick = { onMudar(banda.copy(tipo = t)) },
                        shape = SegmentedButtonDefaults.itemShape(i, TipoFiltro.entries.size),
                    ) { Text(t.nome, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                }
            }
            Linha("Frequência", textoFreq(banda.freq))
            Slider(value = freqParaPos(banda.freq), onValueChange = { onMudar(banda.copy(freq = arredondarFreq(posParaFreq(it)))) })
            Linha("Ganho", textoDb(banda.ganho))
            Slider(value = banda.ganho, onValueChange = { onMudar(banda.copy(ganho = (it * 2).toInt() / 2f)) }, valueRange = -Equalizador.GANHO_MAX..Equalizador.GANHO_MAX)
            Linha("Largura (Q)", "%.2f".format(banda.q) + if (banda.q < 0.8f) " · larga" else if (banda.q > 3f) " · estreita" else "")
            // Q de 0,1 a 10 em escala logarítmica
            Slider(value = (log10(banda.q / 0.1f) / 2f).coerceIn(0f, 1f),
                onValueChange = { onMudar(banda.copy(q = ((0.1f * 100f.pow(it)) * 100).toInt() / 100f)) })
        }
    }
}

private fun arredondarFreq(f: Float): Float = when {
    f < 100 -> f.toInt().toFloat()
    f < 1000 -> (f / 5).toInt() * 5f
    f < 10000 -> (f / 50).toInt() * 50f
    else -> (f / 100).toInt() * 100f
}

@Composable
private fun Linha(rotulo: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(rotulo, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Desenho da curva de resposta (20 Hz a 20 kHz, ±15 dB). */
@Composable
private fun Curva(cfg: ConfigEq) {
    val cor = if (cfg.ligado) Ambar else Color(0xFF6B7180)
    val grade = Color(0xFF2B303B)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(8.dp)) {
            Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                val w = size.width; val h = size.height
                val maxDb = Equalizador.GANHO_MAX
                fun y(db: Double) = (h / 2 - (db / maxDb) * (h / 2)).toFloat().coerceIn(0f, h)
                for (f in listOf(50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f)) {
                    val x = freqParaPos(f) * w
                    drawLine(grade, Offset(x, 0f), Offset(x, h))
                }
                for (db in listOf(-10.0, -5.0, 0.0, 5.0, 10.0)) drawLine(if (db == 0.0) Color(0xFF3A404D) else grade, Offset(0f, y(db)), Offset(w, y(db)))
                val caminho = Path()
                val passos = 160
                for (i in 0..passos) {
                    val p = i / passos.toFloat()
                    val db = Equalizador.respostaDb(cfg, posParaFreq(p).toDouble())
                    val pt = Offset(p * w, y(db))
                    if (i == 0) caminho.moveTo(pt.x, pt.y) else caminho.lineTo(pt.x, pt.y)
                }
                drawPath(caminho, cor, style = Stroke(width = 3.dp.toPx()))
                for (b in cfg.bandas) if (b.ativa) {
                    drawCircle(cor, radius = 5.dp.toPx(), center = Offset(freqParaPos(b.freq) * w, y(Equalizador.respostaDb(cfg, b.freq.toDouble()))))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("20", "100", "1k", "10k", "20k").forEachIndexed { i, t ->
                    Text(t, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (i < 4) Modifier.weight(1f) else Modifier)
                }
            }
        }
    }
}
