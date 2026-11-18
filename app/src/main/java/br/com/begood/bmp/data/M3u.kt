package br.com.begood.bmp.data

import java.net.URLDecoder

/**
 * Playlists no formato .m3u / .m3u8 (texto simples, aceito por quase todos os players:
 * Winamp, VLC, foobar2000, Poweramp, MusicBee...).
 *
 * Exemplo:
 * ```
 * #EXTM3U
 * #PLAYLIST:Viagem
 * #EXTINF:215,Artista - Título
 * /storage/emulated/0/Music/Artista/Álbum/01 Título.mp3
 * ```
 */
object M3u {
    /** Uma linha de música do arquivo: o caminho como estava escrito e o "Artista - Título" do #EXTINF (se tiver). */
    data class Entrada(val caminho: String, val rotulo: String?)

    data class Lido(val nome: String?, val entradas: List<Entrada>)

    /** Uma faixa para gravar no arquivo. */
    data class Faixa(val caminho: String, val artista: String, val titulo: String, val duracaoMs: Long)

    fun ler(texto: String): Lido {
        var nome: String? = null
        var rotulo: String? = null
        val entradas = ArrayList<Entrada>()
        for (linhaBruta in texto.removePrefix("\uFEFF").lineSequence()) {
            val linha = linhaBruta.trim()
            when {
                linha.isEmpty() -> Unit
                linha.startsWith("#PLAYLIST:", ignoreCase = true) -> nome = linha.substringAfter(':').trim().ifEmpty { null }
                linha.startsWith("#EXTINF:", ignoreCase = true) -> rotulo = linha.substringAfter(',', "").trim().ifEmpty { null }
                linha.startsWith("#") -> Unit
                else -> { entradas.add(Entrada(linha, rotulo)); rotulo = null }
            }
        }
        return Lido(nome, entradas)
    }

    fun escrever(nome: String, faixas: List<Faixa>): String = buildString {
        append("#EXTM3U\n")
        append("#PLAYLIST:").append(nome.replace('\n', ' ')).append('\n')
        for (f in faixas) {
            val segundos = if (f.duracaoMs > 0) f.duracaoMs / 1000 else -1
            val rotulo = listOf(f.artista, f.titulo).filter { it.isNotBlank() }.joinToString(" - ")
            append("#EXTINF:").append(segundos).append(',').append(rotulo.replace('\n', ' ')).append('\n')
            append(f.caminho).append('\n')
        }
    }

    /** Deixa o caminho comparável: barras normais, sem "file://", sem %20, minúsculo. */
    fun limparCaminho(c: String): String {
        var s = c.trim()
        if (s.startsWith("file:", ignoreCase = true)) {
            s = s.substringAfter("file:").trimStart('/').let { "/$it" }
            s = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)
        }
        return s.replace('\\', '/').lowercase()
    }

    private fun finais(caminho: String, n: Int): String =
        caminho.split('/').filter { it.isNotEmpty() }.takeLast(n).joinToString("/")

    /**
     * Acha cada entrada do arquivo na biblioteca. Tenta, nesta ordem:
     * 1. o caminho exato; 2. as 3 últimas partes do caminho (Artista/Álbum/arquivo), depois as 2 últimas
     *    e o nome do arquivo (funciona com playlists feitas no PC, com "D:\Música\..." ou caminhos relativos);
     * 3. o "Artista - Título" do #EXTINF. Devolve null para o que não achou.
     */
    fun <T> resolver(
        entradas: List<Entrada>,
        biblioteca: List<T>,
        caminhoDe: (T) -> String,
        artistaDe: (T) -> String,
        tituloDe: (T) -> String,
    ): List<T?> {
        val exato = HashMap<String, T>()
        val porFinal = Array(3) { HashMap<String, MutableList<T>>() }
        val porRotulo = HashMap<String, MutableList<T>>()
        for (m in biblioteca) {
            val c = limparCaminho(caminhoDe(m))
            exato[c] = m
            for (n in 1..3) porFinal[n - 1].getOrPut(finais(c, n)) { mutableListOf() }.add(m)
            porRotulo.getOrPut(chaveRotulo("${artistaDe(m)} - ${tituloDe(m)}")) { mutableListOf() }.add(m)
        }
        return entradas.map { e ->
            val c = limparCaminho(e.caminho)
            exato[c]
                ?: (3 downTo 1).firstNotNullOfOrNull { n -> porFinal[n - 1][finais(c, n)]?.singleOrNull() }
                ?: e.rotulo?.let { porRotulo[chaveRotulo(it)]?.firstOrNull() }
        }
    }

    private fun chaveRotulo(s: String) = normalizar(s).replace(Regex("[^\\p{L}\\p{N}]+"), "")
}
