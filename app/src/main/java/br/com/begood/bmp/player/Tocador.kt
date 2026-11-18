package br.com.begood.bmp.player

import android.content.ComponentName
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import br.com.begood.bmp.data.Musica
import com.google.common.util.concurrent.ListenableFuture

/** Item da fila, para mostrar nas telas. */
data class ItemFila(val id: String, val titulo: String, val artista: String, val album: String, val capa: String?) {
    val idMusica: Long? get() = Itens.idMusica(id)
}

/**
 * Ponte entre as telas e o PlayerService: manda tocar, pausar, pular, e expõe o estado
 * (música atual, tocando ou não, aleatório, repetir, fila) para o Compose.
 */
class Tocador(context: Context) {
    private val app = context.applicationContext
    private var futuro: ListenableFuture<MediaController>? = null
    private var controle: MediaController? = null
    private val pendentes = ArrayList<(MediaController) -> Unit>()

    var atual by mutableStateOf<ItemFila?>(null); private set
    var tocando by mutableStateOf(false); private set
    var carregando by mutableStateOf(false); private set
    var aleatorio by mutableStateOf(false); private set
    var repetir by mutableStateOf(Player.REPEAT_MODE_OFF); private set
    var fila by mutableStateOf<List<ItemFila>>(emptyList()); private set
    var indiceAtual by mutableStateOf(0); private set
    var erro by mutableStateOf<String?>(null); private set

    val posicao: Long get() = controle?.currentPosition ?: 0L
    val duracao: Long get() = controle?.duration?.takeIf { it > 0 } ?: 0L

    private val ouvinte = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) =
            atualizar(refazerFila = events.contains(Player.EVENT_TIMELINE_CHANGED))
        override fun onPlayerError(error: PlaybackException) {
            erro = "Não foi possível tocar este arquivo (${error.errorCodeName})."
        }
    }

    fun conectar() {
        if (futuro != null) return
        val token = SessionToken(app, ComponentName(app, PlayerService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        futuro = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: run { futuro = null; return@addListener }
            controle = c
            c.addListener(ouvinte)
            atualizar()
            pendentes.toList().forEach { it(c) }
            pendentes.clear()
        }, ContextCompat.getMainExecutor(app))
    }

    fun desconectar() {
        controle?.removeListener(ouvinte)
        futuro?.let { MediaController.releaseFuture(it) }
        futuro = null; controle = null
    }

    private fun comControle(acao: (MediaController) -> Unit) {
        val c = controle
        if (c != null) acao(c) else { pendentes.add(acao); conectar() }
    }

    private fun atualizar(refazerFila: Boolean = true) {
        val c = controle ?: return
        tocando = c.isPlaying
        carregando = c.playbackState == Player.STATE_BUFFERING && c.playWhenReady
        aleatorio = c.shuffleModeEnabled
        repetir = c.repeatMode
        indiceAtual = c.currentMediaItemIndex
        atual = c.currentMediaItem?.let(::paraItem)
        if (refazerFila) fila = (0 until c.mediaItemCount).map { paraItem(c.getMediaItemAt(it)) }
        if (c.isPlaying) erro = null
    }

    private fun paraItem(m: MediaItem) = ItemFila(
        id = m.mediaId,
        titulo = m.mediaMetadata.title?.toString().orEmpty(),
        artista = m.mediaMetadata.artist?.toString().orEmpty(),
        album = m.mediaMetadata.albumTitle?.toString().orEmpty(),
        capa = m.mediaMetadata.artworkUri?.toString(),
    )

    /** Troca a fila pela lista e começa da posição [inicio]. Listas enormes são cortadas (máx. [LIMITE]). */
    fun tocar(lista: List<Musica>, inicio: Int = 0, embaralhar: Boolean = false) {
        if (lista.isEmpty()) return
        var fonte = lista
        var comeco = inicio.coerceIn(0, lista.lastIndex)
        if (embaralhar) {
            fonte = lista.shuffled().take(LIMITE); comeco = 0
        } else if (lista.size > LIMITE) {
            val de = (comeco - LIMITE / 2).coerceIn(0, lista.size - LIMITE)
            fonte = lista.subList(de, de + LIMITE); comeco -= de
        }
        val itens = fonte.map { Itens.musica(it) }
        comControle { c ->
            c.shuffleModeEnabled = false
            c.setMediaItems(itens, comeco, 0L)
            c.prepare()
            c.play()
        }
    }

    fun adicionarNaFila(m: Musica) = comControle { c ->
        c.addMediaItem(Itens.musica(m))
        if (c.mediaItemCount == 1) { c.prepare(); c.play() }
    }

    fun tocarASeguir(m: Musica) = comControle { c ->
        if (c.mediaItemCount == 0) { c.setMediaItem(Itens.musica(m)); c.prepare(); c.play() }
        else c.addMediaItem(c.currentMediaItemIndex + 1, Itens.musica(m))
    }

    fun removerDaFila(i: Int) = comControle { c -> if (i in 0 until c.mediaItemCount) c.removeMediaItem(i) }

    fun tocarPausar() = comControle { c ->
        if (c.isPlaying) c.pause() else {
            if (c.mediaItemCount == 0) return@comControle
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun proxima() = comControle { it.seekToNext() }
    fun anterior() = comControle { it.seekToPrevious() }
    fun irPara(ms: Long) = comControle { it.seekTo(ms) }
    fun tocarIndice(i: Int) = comControle { it.seekToDefaultPosition(i); it.play() }
    fun alternarAleatorio() = comControle { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun alternarRepetir() = comControle {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Onde o som está saindo agora (nome do aparelho Bluetooth, fone com fio ou alto-falante). */
    fun saidaAtual(): String {
        val am = app.getSystemService(AudioManager::class.java) ?: return ""
        val saidas = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        saidas.firstOrNull { PlayerService.ehBluetooth(it.type) && it.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            ?.let { return "Bluetooth: ${it.productName}" }
        if (saidas.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }) return "Fone com fio"
        if (saidas.any { it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE }) return "Saída USB"
        return "Alto-falante do celular"
    }

    companion object {
        const val LIMITE = 1000
    }
}
