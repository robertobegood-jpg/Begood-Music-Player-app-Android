package br.com.begood.bmp.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import br.com.begood.bmp.data.Album
import br.com.begood.bmp.data.Automatica
import br.com.begood.bmp.data.Playlists
import br.com.begood.bmp.data.Biblioteca
import br.com.begood.bmp.data.Musica

/**
 * Converte músicas e álbuns em itens do Media3. Os ids também servem para a navegação
 * pelo rádio do carro (Bluetooth/AVRCP) e Android Auto:
 *  - "raiz", "albuns", "artistas", "musicas"   pastas fixas
 *  - "alb:{chave}"  álbum      "art:{nome}"  artista
 *  - "a:{chave}#{id}"  música dentro de um álbum (toca o álbum a partir dela)
 *  - "t:{id}"  música da lista "Todas" (toca todas a partir dela)
 *  - "playlists", "pl:{id}"  playlists;  "p:{idPlaylist}#{id}"  música dentro de uma playlist
 *  - "{id}"  música (usado pelas telas do app)
 */
object Itens {
    const val RAIZ = "raiz"
    const val ALBUNS = "albuns"
    const val ARTISTAS = "artistas"
    const val MUSICAS = "musicas"
    const val PLAYLISTS = "playlists"

    fun musica(m: Musica, id: String = m.id.toString()): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(m.titulo)
            .setArtist(m.artista.ifEmpty { m.albumArtista })
            .setAlbumTitle(m.album)
            .setAlbumArtist(m.albumArtista.ifEmpty { null })
            .setGenre(m.genero.ifEmpty { null })
            .setTrackNumber(m.faixa.takeIf { it > 0 })
            .setDiscNumber(m.disco.takeIf { it > 0 })
            .setDurationMs(m.duracaoMs.takeIf { it > 0 })
            .setArtworkUri(Uri.parse(m.capa))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
        return MediaItem.Builder().setMediaId(id).setUri(m.uri).setMediaMetadata(meta).build()
    }

    fun pasta(id: String, titulo: String, subtitulo: String? = null, capa: String? = null, tipo: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED): MediaItem =
        MediaItem.Builder().setMediaId(id).setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(titulo)
                .setSubtitle(subtitulo)
                .setArtist(subtitulo)
                .setArtworkUri(capa?.let(Uri::parse))
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .setMediaType(tipo)
                .build()
        ).build()

    fun album(a: Album): MediaItem = pasta("alb:${a.chave}", a.titulo, a.artista, a.capa, MediaMetadata.MEDIA_TYPE_ALBUM)

    /** Id da música dentro de qualquer um dos formatos acima. */
    fun idMusica(mediaId: String): Long? = when {
        mediaId.startsWith("a:") || mediaId.startsWith("p:") -> mediaId.substringAfterLast('#').toLongOrNull()
        mediaId.startsWith("t:") -> mediaId.removePrefix("t:").toLongOrNull()
        else -> mediaId.toLongOrNull()
    }

    /** Filhos de uma pasta da navegação. */
    fun filhos(lib: Biblioteca, id: String): List<MediaItem> = when {
        id == RAIZ -> listOf(
            pasta(ALBUNS, "Álbuns", tipo = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
            pasta(ARTISTAS, "Artistas", tipo = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
            pasta(MUSICAS, "Todas as músicas", tipo = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            pasta(PLAYLISTS, "Playlists", tipo = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
        )
        id == PLAYLISTS -> listasDoCarro(lib)
        id.startsWith("pl:") -> id.removePrefix("pl:").let { pl -> Playlists.musicas(pl, lib).map { musica(it, "p:$pl#${it.id}") } }
        id == ALBUNS -> lib.albuns.map(::album)
        id == ARTISTAS -> lib.artistas.map { g ->
            pasta("art:${g.nome}", g.nome, "${g.albuns.size} álbum(ns)", g.albuns.firstOrNull()?.capa, MediaMetadata.MEDIA_TYPE_ARTIST)
        }
        id == MUSICAS -> lib.musicas.map { musica(it, "t:${it.id}") }
        id.startsWith("alb:") -> lib.porChave[id.removePrefix("alb:")]?.let { a -> a.musicas.map { musica(it, "a:${a.chave}#${it.id}") } }.orEmpty()
        id.startsWith("art:") -> lib.artista(id.removePrefix("art:"))?.albuns?.map(::album).orEmpty()
        else -> emptyList()
    }

    /** Playlists para o carro: as do usuário e as automáticas (só as que têm músicas). */
    private fun listasDoCarro(lib: Biblioteca): List<MediaItem> {
        val minhas = Playlists.lista.value.map { it.id to it.nome }
        val automaticas = Automatica.entries.map { it.id to it.nome }
        return (minhas + automaticas).mapNotNull { (pl, nome) ->
            val musicas = Playlists.musicas(pl, lib)
            if (musicas.isEmpty()) null
            else pasta("pl:$pl", nome, "${musicas.size} música(s)", musicas.first().capa, MediaMetadata.MEDIA_TYPE_PLAYLIST)
        }
    }

    fun item(lib: Biblioteca, id: String): MediaItem? = when {
        id == RAIZ -> pasta(RAIZ, "Begood Music Player")
        id == ALBUNS || id == ARTISTAS || id == MUSICAS || id == PLAYLISTS -> filhos(lib, RAIZ).firstOrNull { it.mediaId == id }
        id.startsWith("pl:") -> listasDoCarro(lib).firstOrNull { it.mediaId == id }
        id.startsWith("alb:") -> lib.porChave[id.removePrefix("alb:")]?.let(::album)
        id.startsWith("art:") -> lib.artista(id.removePrefix("art:"))?.let { pasta(id, it.nome, null, it.albuns.firstOrNull()?.capa, MediaMetadata.MEDIA_TYPE_ARTIST) }
        else -> idMusica(id)?.let { lib.porId[it] }?.let { musica(it, id) }
    }

    /**
     * Quando o carro pede para tocar uma única música de um álbum (ou da lista "Todas"),
     * monta a fila inteira começando nela. Devolve a lista e o índice inicial.
     */
    fun expandir(lib: Biblioteca, id: String): Pair<List<Musica>, Int>? {
        val musicaId = idMusica(id) ?: return null
        val lista = when {
            id.startsWith("a:") -> lib.porChave[id.removePrefix("a:").substringBeforeLast('#')]?.musicas
            id.startsWith("t:") -> lib.musicas
            id.startsWith("p:") -> Playlists.musicas(id.removePrefix("p:").substringBeforeLast('#'), lib)
            else -> null
        } ?: return null
        val i = lista.indexOfFirst { it.id == musicaId }.coerceAtLeast(0)
        if (lista.size <= Tocador.LIMITE) return lista to i
        val de = (i - Tocador.LIMITE / 2).coerceIn(0, lista.size - Tocador.LIMITE)
        return lista.subList(de, de + Tocador.LIMITE) to (i - de)
    }
}
