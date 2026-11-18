package br.com.begood.bmp.data

/** Uma faixa de um álbum achado na internet. */
data class FaixaOnline(val titulo: String, val numero: Int, val disco: Int)

/** Um álbum achado na internet (iTunes ou Deezer, gratuitos e sem cadastro). */
data class AlbumOnline(
    val fonte: String,
    val id: String,
    val titulo: String,
    val artista: String,
    val ano: String,
    val genero: String,
    val capaMini: String,
    val capaGrande: String,
    val totalFaixas: Int,
)

/** Busca dados do álbum (nome, ano, gênero, faixas) e capa em alta resolução, a partir do artista e do álbum. */
object BuscaOnline {

    /** Junta os resultados do iTunes e do Deezer. Só dá erro se os dois falharem. */
    fun buscar(artista: String, album: String): List<AlbumOnline> {
        val termo = "$artista $album".trim()
        if (termo.isEmpty()) return emptyList()
        val erros = ArrayList<Throwable>()
        val itunes = runCatching { itunes(termo) }.onFailure { erros.add(it) }.getOrDefault(emptyList())
        val deezer = runCatching { deezer(artista, album) }.onFailure { erros.add(it) }.getOrDefault(emptyList())
        if (erros.size == 2) throw erros[0]
        // Coloca primeiro os que batem com o nome do álbum digitado.
        val alvo = normalizar(album)
        return (itunes + deezer).sortedByDescending { a ->
            val t = normalizar(a.titulo)
            (if (t == alvo) 2 else if (alvo.isNotEmpty() && t.contains(alvo)) 1 else 0) +
                (if (normalizar(a.artista) == normalizar(artista)) 1 else 0)
        }
    }

    private fun itunes(termo: String): List<AlbumOnline> {
        val r = Rede.json("https://itunes.apple.com/search", mapOf("term" to termo, "entity" to "album", "limit" to "12", "country" to "BR"))
            ?: return emptyList()
        val lista = r.optJSONArray("results") ?: return emptyList()
        return (0 until lista.length()).map { lista.getJSONObject(it) }.map { o ->
            val arte = o.txt("artworkUrl100")
            AlbumOnline(
                fonte = "iTunes",
                id = o.optLong("collectionId").toString(),
                titulo = o.txt("collectionName"),
                artista = o.txt("artistName"),
                ano = o.txt("releaseDate").take(4),
                genero = o.txt("primaryGenreName"),
                capaMini = arte.replace("100x100bb", "300x300bb"),
                capaGrande = arte.replace("100x100bb", "1200x1200bb"),
                totalFaixas = o.optInt("trackCount"),
            )
        }
    }

    private fun deezer(artista: String, album: String): List<AlbumOnline> {
        val q = if (artista.isNotBlank() && album.isNotBlank()) "artist:\"$artista\" album:\"$album\"" else "$artista $album".trim()
        var lista = Rede.json("https://api.deezer.com/search/album", mapOf("q" to q, "limit" to "12"))?.optJSONArray("data")
        if ((lista == null || lista.length() == 0) && q.contains("artist:")) {
            lista = Rede.json("https://api.deezer.com/search/album", mapOf("q" to "$artista $album", "limit" to "12"))?.optJSONArray("data")
        }
        lista ?: return emptyList()
        return (0 until lista.length()).map { lista.getJSONObject(it) }.map { o ->
            AlbumOnline(
                fonte = "Deezer",
                id = o.optLong("id").toString(),
                titulo = o.txt("title"),
                artista = o.optJSONObject("artist")?.txt("name").orEmpty(),
                ano = "", // vem nos detalhes
                genero = "",
                capaMini = o.txt("cover_medium"),
                capaGrande = o.txt("cover_xl").ifEmpty { o.txt("cover_big") },
                totalFaixas = o.optInt("nb_tracks"),
            )
        }
    }

    /** Detalhes que faltam (ano e gênero do Deezer) e a lista de faixas. */
    fun detalhes(a: AlbumOnline): Pair<AlbumOnline, List<FaixaOnline>> = when (a.fonte) {
        "iTunes" -> {
            val r = Rede.json("https://itunes.apple.com/lookup", mapOf("id" to a.id, "entity" to "song", "country" to "BR"))
            val lista = r?.optJSONArray("results")
            val faixas = if (lista == null) emptyList() else (0 until lista.length()).map { lista.getJSONObject(it) }
                .filter { it.txt("wrapperType") == "track" }
                .map { FaixaOnline(it.txt("trackName"), it.optInt("trackNumber"), it.optInt("discNumber", 1)) }
            a to faixas
        }
        else -> {
            val o = Rede.json("https://api.deezer.com/album/${a.id}")
            if (o == null) a to emptyList() else {
                val genero = o.optJSONObject("genres")?.optJSONArray("data")?.optJSONObject(0)?.txt("name").orEmpty()
                val t = o.optJSONObject("tracks")?.optJSONArray("data")
                val faixas = if (t == null) emptyList() else (0 until t.length()).map { i ->
                    val f = t.getJSONObject(i)
                    FaixaOnline(f.txt("title"), f.optInt("track_position", i + 1).takeIf { it > 0 } ?: (i + 1), f.optInt("disk_number", 1))
                }
                a.copy(ano = o.txt("release_date").take(4), genero = genero) to faixas
            }
        }
    }

    fun baixarCapa(a: AlbumOnline): ByteArray? =
        runCatching { Rede.bytes(a.capaGrande) }.getOrNull() ?: Rede.bytes(a.capaMini)

    /** Acha a faixa online que corresponde à música (pelo título; se não achar, pela posição). */
    fun faixaDe(m: Musica, faixas: List<FaixaOnline>): FaixaOnline? {
        val t = normalizar(m.titulo).replace(Regex("[^a-z0-9]"), "")
        if (t.isNotEmpty()) {
            faixas.firstOrNull { normalizar(it.titulo).replace(Regex("[^a-z0-9]"), "") == t }?.let { return it }
            faixas.firstOrNull {
                val o = normalizar(it.titulo).replace(Regex("[^a-z0-9]"), "")
                o.isNotEmpty() && (o.startsWith(t) || t.startsWith(o))
            }?.let { return it }
        }
        return null
    }
}
