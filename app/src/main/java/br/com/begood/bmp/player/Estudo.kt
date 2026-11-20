package br.com.begood.bmp.player

import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import kotlin.math.pow

/**
 * Modo estudo: velocidade sem mudar o tom, tom sem mudar a velocidade e repetição de um trecho (A/B).
 *
 * Fica no mesmo processo do PlayerService, que registra o ExoPlayer aqui. A volta do B para o A é avisada
 * pelo próprio ExoPlayer (PlayerMessage), então é exata e funciona com a tela apagada, no fone ou no carro.
 * Tudo roda na thread principal. A velocidade e o tom valem para todas as músicas até voltar ao normal;
 * o trecho A/B é apagado ao trocar de música.
 */
@OptIn(UnstableApi::class)
object Estudo {
    var velocidade by mutableFloatStateOf(1f); private set
    var semitons by mutableIntStateOf(0); private set
    var cents by mutableIntStateOf(0); private set

    var pontoA by mutableStateOf<Long?>(null); private set
    var pontoB by mutableStateOf<Long?>(null); private set
    var repetir by mutableStateOf(false); private set
    var voltas by mutableIntStateOf(0); private set

    val alterado get() = velocidade != 1f || semitons != 0 || cents != 0
    val temTrecho get() = pontoA != null && pontoB != null

    private var player: ExoPlayer? = null
    private var mensagem: PlayerMessage? = null
    private var musicaDoTrecho: String? = null
    private val handler = android.os.Handler(Looper.getMainLooper())

    /** Rede de segurança: em alguns MP3 (taxa variável) o aviso do B pode não chegar. Confere a posição a cada 50 ms. */
    private val vigia = object : Runnable {
        override fun run() {
            val p = player
            val b = pontoB
            if (p != null && b != null && repetir && p.isPlaying && p.currentPosition in (b + 30)..(b + 2000)) chegouNoB()
            if (repetir && player != null) handler.postDelayed(this, 50)
        }
    }

    private val ouvinte = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // A mesma música repetindo (repetir uma) mantém o trecho; outra música apaga.
            if (mediaItem?.mediaId != musicaDoTrecho) limparAB()
        }
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // A fila mudou (música tirada/adicionada antes): a posição da música na fila pode ter mudado.
            if (temTrecho) reprogramar()
        }
    }

    fun registrar(p: ExoPlayer) {
        player = p
        p.addListener(ouvinte)
        aplicarParametros()
    }

    fun soltar(p: ExoPlayer) {
        if (player !== p) return
        mensagem?.cancel(); mensagem = null
        handler.removeCallbacks(vigia)
        p.removeListener(ouvinte)
        player = null
    }

    // ---------- Velocidade e tom ----------

    fun mudarVelocidade(v: Float) { velocidade = (Math.round(v * 100) / 100f).coerceIn(0.25f, 2f); aplicarParametros() }
    fun mudarSemitons(s: Int) { semitons = s.coerceIn(-12, 12); aplicarParametros() }
    fun mudarCents(c: Int) { cents = c.coerceIn(-50, 50); aplicarParametros() }
    fun voltarAoNormal() { velocidade = 1f; semitons = 0; cents = 0; aplicarParametros() }

    private fun aplicarParametros() {
        val tom = 2.0.pow(semitons / 12.0 + cents / 1200.0).toFloat()
        player?.playbackParameters = PlaybackParameters(velocidade, tom)
    }

    // ---------- A/B ----------

    fun marcarA() { val p = player ?: return; definirA(p.currentPosition) }
    fun marcarB() { val p = player ?: return; definirB(p.currentPosition) }

    private fun definirA(ms: Long) {
        val p = player ?: return
        musicaDoTrecho = p.currentMediaItem?.mediaId
        val b = pontoB
        pontoA = ms.coerceIn(0, if (b != null) b - 100 else duracao(p))
        reprogramar()
    }

    private fun definirB(ms: Long) {
        val p = player ?: return
        musicaDoTrecho = p.currentMediaItem?.mediaId
        val a = pontoA ?: 0L
        if (pontoA == null) pontoA = 0L
        pontoB = ms.coerceIn(a + 100, duracao(p).coerceAtLeast(a + 100))
        if (!repetir) { repetir = true; voltas = 0 }
        reprogramar()
    }

    fun ajustarA(deltaMs: Long) { pontoA?.let { definirA(it + deltaMs) } }
    fun ajustarB(deltaMs: Long) { pontoB?.let { definirB(it + deltaMs) } }

    fun alternarRepetir() {
        if (!temTrecho) return
        repetir = !repetir
        if (repetir) voltas = 0
        reprogramar()
    }

    fun irParaA() { val a = pontoA ?: return; player?.seekTo(a) }

    fun limparAB() {
        pontoA = null; pontoB = null; repetir = false; voltas = 0; musicaDoTrecho = null
        reprogramar()
    }

    private fun duracao(p: ExoPlayer) = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE / 4

    private fun reprogramar() {
        mensagem?.cancel()
        mensagem = null
        handler.removeCallbacks(vigia)
        val p = player ?: return
        val a = pontoA ?: return
        val b = pontoB ?: return
        if (!repetir || p.currentMediaItem?.mediaId != musicaDoTrecho) return
        if (p.currentPosition > b + 50) p.seekTo(a)
        mensagem = p.createMessage { _, _ -> chegouNoB() }
            .setLooper(Looper.getMainLooper())
            .setPosition(p.currentMediaItemIndex, b)
            .setDeleteAfterDelivery(false)
            .send()
        handler.postDelayed(vigia, 50)
    }

    private fun chegouNoB() {
        val p = player ?: return
        val a = pontoA ?: return
        val b = pontoB ?: return
        if (!repetir || p.currentMediaItem?.mediaId != musicaDoTrecho) return
        // O aviso pode chegar duas vezes na mesma passagem; se já voltamos ao A, ignora.
        if (p.currentPosition < b - 150) return
        voltas++
        p.seekTo(a)
    }
}
