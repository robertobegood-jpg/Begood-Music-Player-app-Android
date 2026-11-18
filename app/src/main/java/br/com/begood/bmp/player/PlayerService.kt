package br.com.begood.bmp.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import br.com.begood.bmp.data.Capas
import br.com.begood.bmp.data.Estatisticas
import br.com.begood.bmp.data.Preferencias
import br.com.begood.bmp.data.Repositorio
import br.com.begood.bmp.ui.MainActivity
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

/**
 * Toca as músicas em segundo plano. O Media3 cuida da notificação, da tela de bloqueio, do foco de áudio
 * e dos comandos de fones e carros Bluetooth (AVRCP: play/pause/próxima, título e capa no visor).
 * Também deixa o carro navegar pela biblioteca (álbuns, artistas, músicas) e retoma a última fila
 * quando o botão play do fone/carro é apertado com o app fechado.
 */
@OptIn(UnstableApi::class)
class PlayerService : MediaLibraryService() {
    private var sessao: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private val trabalho: ListeningExecutorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    private val principal = Handler(Looper.getMainLooper())
    private var audio: AudioManager? = null

    override fun onCreate() {
        super.onCreate()
        Repositorio.iniciar(this)

        // O equalizador entra na cadeia de áudio do player (vale para alto-falante, fone e Bluetooth).
        val renderizadores = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(ProcessadorEq()))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
        }
        player = ExoPlayer.Builder(this, renderizadores)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pausa ao tirar o fone ou desconectar o Bluetooth
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED)) salvarFila()
                // Nova música (ou a mesma repetindo): ainda não contou como tocada.
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) contou = false
            }
        })
        principal.postDelayed(vigia, 5000)

        val abrirApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        sessao = MediaLibrarySession.Builder(this, player, Callback())
            .setSessionActivity(abrirApp)
            .setBitmapLoader(CacheBitmapLoader(Capas.CarregadorMedia3(this, DataSourceBitmapLoader(this))))
            .build()

        observarBluetooth()
    }

    // ---------- Contagem de reproduções (playlists "Mais tocadas" e "Tocadas recentemente") ----------
    private var contou = false
    private val vigia = object : Runnable {
        override fun run() {
            contarSeTocou()
            principal.postDelayed(this, 5000)
        }
    }

    /** Conta a música como tocada depois de 30 s (ou da metade, se ela for mais curta). */
    private fun contarSeTocou() {
        if (contou || !player.isPlaying) return
        val duracao = player.duration
        val minimo = if (duracao > 0) minOf(30_000L, duracao / 2) else 30_000L
        if (player.currentPosition < minimo) return
        contou = true
        val id = player.currentMediaItem?.mediaId?.let(Itens::idMusica) ?: return
        val musica = Repositorio.biblioteca.value?.porId?.get(id) ?: return
        Estatisticas.registrarReproducao(musica.caminho)
    }

    private fun salvarFila() {
        if (player.mediaItemCount == 0) return
        val ids = (0 until player.mediaItemCount).mapNotNull { Itens.idMusica(player.getMediaItemAt(it).mediaId) }
        Preferencias.salvarFila(ids, player.currentMediaItemIndex, player.currentPosition)
    }

    // ---------- Bluetooth: retomar ao conectar ----------
    private var bluetoothPronto = false
    private val dispositivos = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            // A primeira chamada lista o que já estava conectado; só reage a conexões novas.
            if (!bluetoothPronto) return
            if (!Preferencias.retomarBluetooth) return
            if (added.none { it.isSink && ehBluetooth(it.type) }) return
            principal.postDelayed({
                if (!player.isPlaying && player.mediaItemCount > 0) {
                    if (player.playbackState == Player.STATE_IDLE) player.prepare()
                    player.play()
                }
            }, 2000)
        }
    }

    private fun observarBluetooth() {
        audio = getSystemService(AudioManager::class.java)
        audio?.registerAudioDeviceCallback(dispositivos, principal)
        principal.postDelayed({ bluetoothPronto = true }, 1500)
    }

    private inner class Callback : MediaLibrarySession.Callback {
        /** As telas e o carro mandam só o id; aqui montamos o item completo com o arquivo. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = trabalho.submit<MutableList<MediaItem>> {
            val lib = runBlocking { Repositorio.garantir() }
            mediaItems.mapNotNull { item ->
                Itens.idMusica(item.mediaId)?.let { lib.porId[it] }?.let { Itens.musica(it) }
            }.toMutableList()
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = trabalho.submit<MediaSession.MediaItemsWithStartPosition> {
            val lib = runBlocking { Repositorio.garantir() }
            val unico = mediaItems.singleOrNull()?.mediaId
            val expandido = unico?.let { Itens.expandir(lib, it) }
            if (expandido != null) {
                MediaSession.MediaItemsWithStartPosition(expandido.first.map { Itens.musica(it) }, expandido.second, C.TIME_UNSET)
            } else {
                val itens = mediaItems.mapNotNull { item -> Itens.idMusica(item.mediaId)?.let { lib.porId[it] }?.let { Itens.musica(it) } }
                MediaSession.MediaItemsWithStartPosition(itens, startIndex.coerceIn(0, (itens.size - 1).coerceAtLeast(0)), startPositionMs)
            }
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = trabalho.submit<MediaSession.MediaItemsWithStartPosition> {
            val lib = runBlocking { Repositorio.garantir() }
            val salva = Preferencias.lerFila()
            val itens = salva?.first.orEmpty().mapNotNull { lib.porId[it] }.map { Itens.musica(it) }
            if (itens.isEmpty()) {
                // Sem fila salva: toca a biblioteca embaralhada.
                MediaSession.MediaItemsWithStartPosition(lib.musicas.shuffled().take(Tocador.LIMITE).map { Itens.musica(it) }, 0, 0)
            } else {
                MediaSession.MediaItemsWithStartPosition(itens, salva!!.second.coerceIn(0, itens.lastIndex), salva.third)
            }
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            com.google.common.util.concurrent.Futures.immediateFuture(LibraryResult.ofItem(Itens.pasta(Itens.RAIZ, "Begood Music Player"), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = trabalho.submit<LibraryResult<ImmutableList<MediaItem>>> {
            val lib = runBlocking { Repositorio.garantir() }
            val todos = Itens.filhos(lib, parentId)
            val de = (page.toLong() * pageSize).coerceAtMost(todos.size.toLong()).toInt()
            val ate = (de.toLong() + pageSize).coerceAtMost(todos.size.toLong()).toInt()
            LibraryResult.ofItemList(ImmutableList.copyOf(todos.subList(de, ate)), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = trabalho.submit<LibraryResult<MediaItem>> {
            val lib = runBlocking { Repositorio.garantir() }
            Itens.item(lib, mediaId)?.let { LibraryResult.ofItem(it, null) } ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = sessao

    override fun onTaskRemoved(rootIntent: Intent?) {
        salvarFila()
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        salvarFila()
        principal.removeCallbacks(vigia)
        audio?.unregisterAudioDeviceCallback(dispositivos)
        sessao?.run {
            player.release()
            release()
        }
        sessao = null
        trabalho.shutdown()
        super.onDestroy()
    }

    companion object {
        fun ehBluetooth(tipo: Int): Boolean =
            tipo == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || tipo == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= 31 && (tipo == AudioDeviceInfo.TYPE_BLE_HEADSET || tipo == AudioDeviceInfo.TYPE_BLE_SPEAKER)) ||
                (Build.VERSION.SDK_INT >= 33 && tipo == AudioDeviceInfo.TYPE_BLE_BROADCAST)
    }
}
