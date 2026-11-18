package br.com.begood.bmp.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import okio.Buffer
import java.io.File
import java.util.concurrent.Executors

/**
 * Capas dos álbuns: lidas da imagem embutida no arquivo de música (ou de um cover.jpg/folder.jpg na pasta).
 * Endereço usado nas telas e na notificação: bmpcapa://musica/{id}?m={modificado}
 */
object Capas {
    const val ESQUEMA = "bmpcapa"
    private val NOMES = listOf("cover.jpg", "folder.jpg", "front.jpg", "albumart.jpg", "cover.png", "folder.png")

    fun endereco(id: Long, modificado: Long) = "$ESQUEMA://musica/$id?m=$modificado"

    private fun idDo(endereco: String): Long? =
        Uri.parse(endereco).takeIf { it.scheme == ESQUEMA }?.lastPathSegment?.toLongOrNull()

    /** Bytes da imagem da capa, ou null se a música não tem capa. */
    fun bytes(ctx: Context, endereco: String): ByteArray? {
        val id = idDo(endereco) ?: return null
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        val embutida = runCatching {
            val r = MediaMetadataRetriever()
            try { r.setDataSource(ctx, uri); r.embeddedPicture } finally { r.release() }
        }.getOrNull()
        if (embutida != null) return embutida
        // Imagem solta na pasta do álbum
        val caminho = Repositorio.biblioteca.value?.porId?.get(id)?.caminho ?: return null
        val pasta = File(caminho).parentFile ?: return null
        for (nome in NOMES) {
            val f = File(pasta, nome)
            val b = runCatching { if (f.isFile) f.readBytes() else null }.getOrNull()
            if (b != null) return b
        }
        return null
    }

    fun reduzir(bytes: ByteArray, maximo: Int): Bitmap? {
        val opc = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opc)
        var amostra = 1
        while (opc.outWidth / (amostra * 2) >= maximo && opc.outHeight / (amostra * 2) >= maximo) amostra *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = amostra })
    }

    /** Coil (imagens das telas): entende o endereço bmpcapa://. */
    class FabricaCoil(private val ctx: Context) : Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != ESQUEMA) return null
            return Fetcher {
                val b = bytes(ctx, data.toString()) ?: error("Sem capa")
                SourceFetchResult(
                    source = ImageSource(Buffer().write(b), options.fileSystem),
                    mimeType = null,
                    dataSource = DataSource.DISK,
                ) as FetchResult
            }
        }
    }

    class ChaveCoil : Keyer<coil3.Uri> {
        override fun key(data: coil3.Uri, options: Options): String? = if (data.scheme == ESQUEMA) data.toString() else null
    }

    /** Media3 (notificação, tela de bloqueio, Bluetooth/carro): também entende bmpcapa://. */
    @OptIn(UnstableApi::class)
    class CarregadorMedia3(private val ctx: Context, private val outro: BitmapLoader) : BitmapLoader {
        private val executor: ListeningExecutorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

        override fun supportsMimeType(mimeType: String): Boolean = outro.supportsMimeType(mimeType)

        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = executor.submit<Bitmap> {
            reduzir(data, 720) ?: error("Imagem inválida")
        }

        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
            if (uri.scheme == ESQUEMA) executor.submit<Bitmap> {
                val b = bytes(ctx, uri.toString()) ?: error("Sem capa")
                reduzir(b, 720) ?: error("Imagem inválida")
            } else outro.loadBitmap(uri)
    }
}
