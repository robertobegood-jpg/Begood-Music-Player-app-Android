package br.com.begood.bmp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.begood.bmp.player.Estudo
import kotlin.math.abs
import kotlin.math.roundToInt

/** Resumo curto do modo estudo ("A/B 1:02–1:15 · 75% · +2 semitons"), ou null se está tudo normal. */
fun resumoEstudo(): String? {
    val partes = buildList {
        if (Estudo.temTrecho) add("A/B ${tempoDecimos(Estudo.pontoA!!)}–${tempoDecimos(Estudo.pontoB!!)}" + if (Estudo.repetir) "" else " (parado)")
        if (Estudo.velocidade != 1f) add("${(Estudo.velocidade * 100).roundToInt()}%")
        if (Estudo.semitons != 0 || Estudo.cents != 0) add(nomeTom())
    }
    return partes.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun nomeTom(): String = buildString {
    val s = Estudo.semitons
    if (s != 0) append("%+d %s".format(s, if (abs(s) > 1) "semitons" else "semitom"))
    if (Estudo.cents != 0) { if (isNotEmpty()) append(" "); append("%+d cents".format(Estudo.cents)) }
    if (isEmpty()) append("tom original")
}

private fun tempoDecimos(ms: Long): String {
    val t = ms.coerceAtLeast(0)
    return "%d:%02d,%d".format(t / 60000, (t / 1000) % 60, (t / 100) % 10)
}

/** Painel "Estudo" do Tocando agora: repetir trecho, velocidade e tom. */
@Composable
fun PainelEstudo(modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Repetir trecho (A/B)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        PontoAB("A", Estudo.pontoA, { Estudo.marcarA() }, { Estudo.ajustarA(it) })
        PontoAB("B", Estudo.pontoB, { Estudo.marcarB() }, { Estudo.ajustarB(it) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Repetir o trecho", modifier = Modifier.weight(1f))
            if (Estudo.repetir) Text("voltas: ${Estudo.voltas}   ", color = MaterialTheme.colorScheme.primary)
            Switch(checked = Estudo.repetir, onCheckedChange = { Estudo.alternarRepetir() }, enabled = Estudo.temTrecho)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Estudo.irParaA() }, enabled = Estudo.pontoA != null, shape = RectangleShape, modifier = Modifier.weight(1f)) { Text("Ir para o A") }
            OutlinedButton(onClick = { Estudo.limparAB() }, enabled = Estudo.pontoA != null, shape = RectangleShape, modifier = Modifier.weight(1f)) { Text("Limpar A/B") }
        }
        Text("Toque em A no começo do trecho e em B no fim. O trecho some ao trocar de música.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("Velocidade (sem mudar o tom)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Contador("${(Estudo.velocidade * 100).roundToInt()}%", { Estudo.mudarVelocidade(Estudo.velocidade - 0.05f) }, { Estudo.mudarVelocidade(Estudo.velocidade + 0.05f) })
        Slider(value = Estudo.velocidade, onValueChange = { Estudo.mudarVelocidade(Math.round(it * 20) / 20f) }, valueRange = 0.25f..2f)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (v in listOf(50, 75, 100, 125)) {
                val sel = (Estudo.velocidade * 100).roundToInt() == v
                val margem = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp)
                if (sel) Button(onClick = {}, shape = RectangleShape, contentPadding = margem, modifier = Modifier.weight(1f)) { Text("$v%", maxLines = 1, softWrap = false) }
                else OutlinedButton(onClick = { Estudo.mudarVelocidade(v / 100f) }, shape = RectangleShape, contentPadding = margem, modifier = Modifier.weight(1f)) { Text("$v%", maxLines = 1, softWrap = false) }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("Tom (sem mudar a velocidade)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Contador(if (Estudo.semitons == 0) "original" else "%+d".format(Estudo.semitons),
            { Estudo.mudarSemitons(Estudo.semitons - 1) }, { Estudo.mudarSemitons(Estudo.semitons + 1) }, rotulo = "Semitons")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ajuste fino", modifier = Modifier.weight(1f))
            Text("%+d cents".format(Estudo.cents), color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = Estudo.cents.toFloat(), onValueChange = { Estudo.mudarCents(it.roundToInt()) }, valueRange = -50f..50f)
        Text("Velocidade e tom valem para todas as músicas até você voltar ao normal.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { Estudo.voltarAoNormal() }, enabled = Estudo.alterado, shape = RectangleShape, modifier = Modifier.align(Alignment.End)) {
            Text("Voltar velocidade e tom ao normal")
        }
    }
}

@Composable
private fun PontoAB(rotulo: String, ms: Long?, marcar: () -> Unit, ajustar: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(onClick = marcar, shape = RectangleShape, modifier = Modifier.width(60.dp)) { Text(rotulo, fontWeight = FontWeight.Bold) }
        Text(ms?.let { tempoDecimos(it) } ?: "—", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { ajustar(-100) }, enabled = ms != null, shape = RectangleShape) { Text("−0,1") }
        OutlinedButton(onClick = { ajustar(100) }, enabled = ms != null, shape = RectangleShape) { Text("+0,1") }
    }
}

@Composable
private fun Contador(texto: String, menos: () -> Unit, mais: () -> Unit, rotulo: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (rotulo != null) Text(rotulo, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = menos, shape = RectangleShape, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Remove, "Menos") }
        Text(texto, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 72.dp))
        FilledTonalIconButton(onClick = mais, shape = RectangleShape, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Add, "Mais") }
    }
}
