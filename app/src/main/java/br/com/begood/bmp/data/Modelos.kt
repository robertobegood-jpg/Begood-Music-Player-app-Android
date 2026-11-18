package br.com.begood.bmp.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** Uma música do celular, lida do MediaStore (o índice de mídia do Android). */
data class Musica(
    val id: Long,
    val titulo: String,
    val artista: String,
    val album: String,
    val albumArtista: String,
    val faixa: Int,
    val disco: Int,
    val ano: String,
    val genero: String,
    val duracaoMs: Long,
    val caminho: String,
    val modificado: Long,
    /** Quando o arquivo entrou no celular (segundos), para a playlist "Adicionadas recentemente". */
    val adicionado: Long = 0L,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val artistaDoAlbum: String get() = albumArtista.ifEmpty { artista }
    val duracaoTexto: String get() = formatarDuracao(duracaoMs)
    val pasta: String get() = File(caminho).parent.orEmpty()
    val nomeArquivo: String get() = File(caminho).name

    /** Endereço da capa embutida no arquivo (lido pelo carregador de capas do app). */
    val capa: String get() = Capas.endereco(id, modificado)
}

class Album(
    val chave: String,
    val titulo: String,
    val artista: String,
    val ano: String,
    val genero: String,
    val musicas: List<Musica>,
) {
    val capa: String? get() = musicas.firstOrNull()?.capa
    val duracaoTotal: Long get() = musicas.sumOf { it.duracaoMs }
}

class Grupo(val nome: String, val albuns: List<Album>, val musicas: List<Musica>)

fun formatarDuracao(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val seg = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, seg) else "%d:%02d".format(m, seg)
}

/** Texto sem acentos e em minúsculas, para a busca. */
fun normalizar(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

/** Toda a biblioteca já organizada em álbuns, artistas e gêneros. */
class Biblioteca(musicasBrutas: List<Musica>) {
    private val collator = Collator.getInstance(Locale("pt", "BR")).apply { strength = Collator.PRIMARY }

    val musicas: List<Musica> = musicasBrutas.sortedWith { a, b ->
        val c = collator.compare(a.titulo, b.titulo)
        if (c != 0) c else collator.compare(a.artista, b.artista)
    }
    val porId: Map<Long, Musica> = musicas.associateBy { it.id }
    /** Pelo caminho do arquivo (minúsculo): as playlists guardam o caminho, que não muda ao reindexar. */
    val porCaminho: Map<String, Musica> by lazy { musicas.associateBy { it.caminho.lowercase() } }
    val albuns: List<Album>
    val porChave: Map<String, Album>
    /** Artistas do álbum (o principal de cada álbum; convidados não entram aqui). */
    val artistas: List<Grupo>
    /** Todos os artistas que aparecem nas faixas, inclusive convidados ("feat."). */
    val artistasFaixa: List<Grupo>
    val generos: List<Grupo>
    private val albumDaMusica = HashMap<Long, Album>()
    private val indiceBusca: List<String>

    init {
        val agrupado = LinkedHashMap<String, MutableList<Musica>>()
        for (m in musicas) {
            // Mesmo nome de álbum em pastas diferentes (ex.: "Greatest Hits") vira álbuns separados.
            val chave = if (m.album.isBlank()) SEM_ALBUM
            else normalizar(m.album) + "|" + normalizar(m.albumArtista.ifEmpty { pastaDoAlbum(m.pasta) })
            agrupado.getOrPut(chave) { mutableListOf() }.add(m)
        }
        albuns = agrupado.map { (chave, lista) -> montarAlbum(chave, lista) }
            .sortedWith { a, b ->
                val c = collator.compare(a.artista, b.artista)
                if (c != 0) c else {
                    val y = a.ano.compareTo(b.ano)
                    if (y != 0) y else collator.compare(a.titulo, b.titulo)
                }
            }
        porChave = albuns.associateBy { it.chave }
        for (a in albuns) for (m in a.musicas) albumDaMusica[m.id] = a

        artistas = albuns.filter { it.chave != SEM_ALBUM }
            .groupBy { normalizar(it.artista) }
            .map { (_, lista) -> Grupo(lista.first().artista, lista, lista.flatMap { it.musicas }) }
            .sortedWith { a, b -> collator.compare(a.nome, b.nome) }
        artistasFaixa = agrupar { separarArtistas(it.artista) }
        generos = agrupar { m -> m.genero.split(';', '/').map { it.trim() } }
        indiceBusca = musicas.map { normalizar("${it.titulo} ${it.artista} ${it.albumArtista} ${it.album}") }
    }

    private fun montarAlbum(chave: String, lista: List<Musica>): Album {
        val ordenadas = lista.sortedWith(compareBy<Musica>({ it.disco }, { it.faixa }, { normalizar(it.titulo) }))
        val primeira = ordenadas.first()
        val doAlbum = ordenadas.map { it.albumArtista }.filter { it.isNotEmpty() }.distinctBy { normalizar(it) }
        // Sem a tag "artista do álbum": o principal é quem aparece em pelo menos metade das faixas
        // (sem contar os convidados "feat."); senão, "Vários artistas".
        val principais = ordenadas.mapNotNull { separarArtistas(it.artista).firstOrNull() }
        val maisFrequente = principais.groupingBy { normalizar(it) }.eachCount().maxByOrNull { it.value }
            ?.takeIf { it.value * 2 >= ordenadas.size }?.key
        val principal = maisFrequente?.let { k -> principais.first { normalizar(it) == k } }
        val artista = when {
            chave == SEM_ALBUM -> "Vários artistas"
            doAlbum.size == 1 -> doAlbum[0]
            doAlbum.size > 1 -> "Vários artistas"
            principal != null -> principal
            principais.isEmpty() -> "Artista desconhecido"
            else -> "Vários artistas"
        }
        val genero = ordenadas.mapNotNull { it.genero.takeIf { g -> g.isNotBlank() } }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
        return Album(
            chave = chave,
            titulo = if (chave == SEM_ALBUM) "Músicas sem álbum" else primeira.album,
            artista = artista,
            ano = ordenadas.firstNotNullOfOrNull { it.ano.takeIf { y -> y.isNotBlank() } }?.take(4).orEmpty(),
            genero = genero,
            musicas = ordenadas,
        )
    }

    private fun agrupar(nomes: (Musica) -> List<String>): List<Grupo> {
        val musicasPorNome = LinkedHashMap<String, Pair<String, MutableList<Musica>>>()
        for (m in musicas) for (nome in nomes(m)) {
            if (nome.isBlank()) continue
            musicasPorNome.getOrPut(normalizar(nome)) { nome to mutableListOf() }.second.add(m)
        }
        return musicasPorNome.values.map { (nome, lista) ->
            val ids = lista.mapTo(HashSet()) { it.id }
            val albunsDoGrupo = lista.mapNotNull { albumDaMusica[it.id] }.distinct()
            Grupo(nome, albunsDoGrupo, albunsDoGrupo.flatMap { a -> a.musicas.filter { it.id in ids } })
        }.sortedWith { a, b -> collator.compare(a.nome, b.nome) }
    }

    private val artistaPorNome by lazy { artistas.associateBy { normalizar(it.nome) } }
    private val artistaFaixaPorNome by lazy { artistasFaixa.associateBy { normalizar(it.nome) } }
    fun artistaFaixa(nome: String): Grupo? = artistaFaixaPorNome[normalizar(nome)]
    private val generoPorNome by lazy { generos.associateBy { normalizar(it.nome) } }
    fun artista(nome: String): Grupo? = artistaPorNome[normalizar(nome)]
    fun genero(nome: String): Grupo? = generoPorNome[normalizar(nome)]
    fun albumDe(m: Musica): Album? = albumDaMusica[m.id]

    fun buscar(texto: String, limite: Int = 200): List<Musica> {
        val termos = normalizar(texto).split(' ').filter { it.isNotBlank() }
        if (termos.isEmpty()) return emptyList()
        val saida = ArrayList<Musica>()
        for (i in musicas.indices) {
            if (termos.all { indiceBusca[i].contains(it) }) {
                saida.add(musicas[i]); if (saida.size >= limite) break
            }
        }
        return saida
    }

    fun buscarAlbuns(texto: String, limite: Int = 60): List<Album> {
        val termos = normalizar(texto).split(' ').filter { it.isNotBlank() }
        if (termos.isEmpty()) return emptyList()
        return albuns.asSequence().filter { a ->
            val alvo = normalizar("${a.titulo} ${a.artista}")
            termos.all { alvo.contains(it) }
        }.take(limite).toList()
    }

    companion object {
        const val SEM_ALBUM = "\u0000sem-album"
        /** Onde começam os convidados: "feat.", "ft.", "featuring", "part.", "participação de", "com". */
        private val CONVIDADO = Regex("""\s*[(\[]?\s*\b(feat\.?|ft\.?|featuring|part\.|participação( especial)?( de)?|com)\s+""", RegexOption.IGNORE_CASE)
        private val SEPARADOR = Regex("""\s*(,|&| e | and | x )\s*""", RegexOption.IGNORE_CASE)

        /** "Cyndi Lauper feat. Fulano & Beltrano; Ciclano" -> ["Cyndi Lauper", "Fulano", "Beltrano", "Ciclano"]. */
        fun separarArtistas(texto: String): List<String> {
            if (texto.isBlank()) return emptyList()
            val partes = ArrayList<String>()
            for (bloco in texto.split(';', '/')) {
                val m = CONVIDADO.find(bloco)
                if (m == null || m.range.first == 0) { partes.add(bloco); continue }
                partes.add(bloco.substring(0, m.range.first))
                // Só os convidados são separados por "&", "," etc. (o principal pode ser "Simon & Garfunkel").
                partes.addAll(bloco.substring(m.range.last + 1).trim().trimEnd(')', ']').split(SEPARADOR))
            }
            return partes.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { normalizar(it) }
        }

        private val PASTA_DISCO = Regex("""^(cd|disc|disco|disk)\s*\d+$""", RegexOption.IGNORE_CASE)

        /** Álbum duplo em subpastas "CD1", "CD2"... conta como um só. */
        fun pastaDoAlbum(pasta: String): String {
            val f = File(pasta)
            return if (PASTA_DISCO.matches(f.name)) f.parent.orEmpty() else pasta
        }
    }
}
