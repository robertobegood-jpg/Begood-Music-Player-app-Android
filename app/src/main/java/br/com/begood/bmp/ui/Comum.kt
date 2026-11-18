package br.com.begood.bmp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

val Ambar = Color(0xFFE0A030)
val Fundo = Color(0xFF14161C)

val CoresEscuras = darkColorScheme(
    primary = Ambar,
    onPrimary = Color(0xFF1E1400),
    secondary = Color(0xFFF5D08A),
    secondaryContainer = Color(0xFF4A3A16),
    onSecondaryContainer = Color(0xFFF5D08A),
    background = Fundo,
    surface = Fundo,
    surfaceVariant = Color(0xFF262A33),
    surfaceContainer = Color(0xFF1C1F26),
    surfaceContainerHigh = Color(0xFF232731),
    surfaceContainerHighest = Color(0xFF2B303B),
)

/**
 * Capa do álbum, com um disco desenhado quando não há imagem.
 * [modelo] pode ser o endereço bmpcapa:// de uma música, uma URL da internet ou os bytes da imagem.
 */
@Composable
fun Capa(modelo: Any?, modifier: Modifier = Modifier, forma: Shape = MaterialTheme.shapes.small, tamanhoIcone: Dp = 40.dp) {
    val semCapa = @Composable {
        Box(Modifier.fillMaxSize().background(Color(0xFF262A33)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Album, null, tint = Color(0xFF6B7180), modifier = Modifier.size(tamanhoIcone))
        }
    }
    Box(modifier.clip(forma)) {
        if (modelo == null) semCapa() else SubcomposeAsyncImage(
            model = modelo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { semCapa() },
            error = { semCapa() },
        )
    }
}

@Composable
fun Titulo(t: String, modifier: Modifier = Modifier) =
    Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
