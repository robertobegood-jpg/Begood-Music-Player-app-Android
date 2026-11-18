package br.com.begood.bmp.data

import kotlin.random.Random

/** Uma música ou artista "parecido", vindo da Last.fm. [nota] vai de 0 a 1 (1 = muito parecido). */
data class Parecido(val artista: String, val titulo: String, val nota: Double)

/**
 * O cálculo da playlist Genius, sem internet: recebe a música escolhida, a biblioteca
 * e as listas de "parecidos" (já guardadas no celular) e escolhe as músicas.
 *
 * Pontuação de cada música da biblioteca:
 * - está na lista de músicas parecidas: 2 + nota (as mais fortes);
 * - é de um artista parecido: 1 + nota (até [porArtista] músicas de cada um);
 * - é do mesmo artista da escolhida: 0,9 (poucas, para não virar "só esse artista");
 * - sem dados da Last.fm: mesmo gênero (0,5) e mesma década (0,2), para ainda dar uma playlist.
 * No fim, espalha os artistas para não tocar dois iguais seguidos.
 */
object GeniusMotor {
    private val VERSAO = Regex(
        """\s*[(\[][^)\]]*\b(remaster\w*|live|ao vivo|vers[aã]o|version|mix|edit|mono|stereo|est[eé]reo|bonus|demo|ac[uú]stic\w*|acoustic|single|feat\.?|ft\.?|part\.?)\b[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val SUFIXO = Regex("""\s+-\s+.*\b(remaster\w*|live|ao vivo|version|vers[aã]o|mix|edit|mono|stereo|single|bonus)\b.*$""", RegexOption.IGNORE_CASE)
    private val ARTIGO = Regex("""^(the|os|as|o|a)\s+""")

    /** "The Beatles" e "Beatles" viram a mesma chave. */
    fun chaveArtista(a: String): String =
        normalizar(a).trim().replace(ARTIGO, "").replace('&', 'e').replace(Regex("[^\\p{L}\\p{N}]+"), "")

    /** "Help! (Remastered 2009)" e "Help!" viram a mesma chave. */
    fun chaveTitulo(t: String): String =
        normalizar(t.replace(VERSAO, "").replace(SUFIXO, "")).replace(Regex("[^\\p{L}\\p{N}]+"), "")

    fun chaveFaixa(artista: String, titulo: String) = chaveArtista(artista) + "|" + chaveTitulo(titulo)

    /** Dados de uma música da biblioteca que o cálculo precisa (assim ele não depende do Android). */
    data class Info(val artistas: List<String>, val titulo: String, val genero: String, val ano: String)

    fun <T> montar(
        semente: T,
        biblioteca: List<T>,
        idDe: (T) -> Any,
        infoDe: (T) -> Info,
        faixasParecidas: List<Parecido>,
        artistasParecidos: List<Parecido>,
        tamanho: Int,
        sorteio: Random = Random.Default,
    ): List<T> {
        val infoSemente = infoDe(semente)
        val artistasSemente = infoSemente.artistas.map(::chaveArtista).toSet()
        val faixaSemente = infoSemente.artistas.map { chaveFaixa(it, infoSemente.titulo) }.toSet()
        val notaFaixa = HashMap<String, Double>()
        for (p in faixasParecidas) notaFaixa.merge(chaveFaixa(p.artista, p.titulo), p.nota, ::maxOf)
        val notaArtista = HashMap<String, Double>()
        for (p in artistasParecidos) notaArtista.merge(chaveArtista(p.artista), p.nota, ::maxOf)
        val semDados = notaFaixa.isEmpty() && notaArtista.isEmpty()
        val decadaSemente = decada(infoSemente.ano)
        val generoSemente = normalizar(infoSemente.genero.substringBefore(';').substringBefore('/').trim())
        val porArtista = (tamanho / 12).coerceIn(2, 4)

        class Candidato(val item: T, val artista: String, val faixa: String, val nota: Double, val forte: Boolean)

        val candidatos = ArrayList<Candidato>()
        val idSemente = idDe(semente)
        for (m in biblioteca) {
            if (idDe(m) == idSemente) continue
            val info = infoDe(m)
            val artistas = info.artistas.map(::chaveArtista)
            val faixas = info.artistas.map { chaveFaixa(it, info.titulo) }
            if (faixas.any { it in faixaSemente }) continue // outra versão da mesma música
            val principal = artistas.firstOrNull().orEmpty()
            val nf = faixas.mapNotNull { notaFaixa[it] }.maxOrNull()
            val na = artistas.mapNotNull { notaArtista[it] }.maxOrNull()
            // Um pouco de sorte desempata e faz cada playlist sair diferente.
            val acaso = sorteio.nextDouble() * 0.15
            val nota = when {
                nf != null -> 2.0 + nf
                na != null -> 1.0 + na + acaso
                artistas.any { it in artistasSemente } -> 0.9 + acaso
                semDados -> {
                    var n = 0.0
                    if (generoSemente.isNotEmpty() && normalizar(info.genero).contains(generoSemente)) n += 0.5
                    if (decadaSemente != null && decada(info.ano) == decadaSemente) n += 0.2
                    if (n > 0) n + acaso else continue
                }
                else -> continue
            }
            candidatos.add(Candidato(m, principal, faixas.firstOrNull().orEmpty(), nota, forte = nf != null))
        }

        // Escolhe pelas maiores notas, sem repetir a mesma música e com limite por artista
        // (as músicas apontadas diretamente pela Last.fm passam um pouco do limite).
        // Se não completar o tamanho, vai soltando o limite (mas só com músicas que combinam).
        val ordenados = candidatos.sortedByDescending { it.nota }
        val escolhidos = ArrayList<Candidato>()
        val usadas = HashSet<String>()
        val pegos = HashSet<Int>()
        val contagem = HashMap<String, Int>()
        var folga = 0
        while (escolhidos.size < tamanho - 1) {
            val antes = escolhidos.size
            for ((i, c) in ordenados.withIndex()) {
                if (escolhidos.size >= tamanho - 1) break
                if (i in pegos || c.faixa in usadas) continue
                val base = if (c.artista in artistasSemente) (porArtista - 1).coerceAtLeast(1) else if (c.forte) porArtista + 1 else porArtista
                val n = contagem.getOrDefault(c.artista, 0)
                if (n >= base + folga) continue
                contagem[c.artista] = n + 1
                usadas.add(c.faixa); pegos.add(i)
                escolhidos.add(c)
            }
            // Cada volta deixa cada artista ter mais uma música, para continuar variado.
            if (escolhidos.size == antes && folga > tamanho) break
            folga++
        }
        // Mantém as que mais combinam no começo, mas sem o mesmo artista duas vezes seguidas.
        val primeiroArtista = chaveArtista(infoSemente.artistas.firstOrNull().orEmpty())
        return listOf(semente) + espalhar(escolhidos.sortedByDescending { it.nota }, { it.artista }, primeiroArtista).map { it.item }
    }

    /** Reordena para não ter o mesmo artista duas vezes seguidas (quando der). */
    fun <T> espalhar(lista: List<T>, artista: (T) -> String, anterior: String = ""): List<T> {
        val resto = lista.toMutableList()
        val saida = ArrayList<T>(lista.size)
        var ultimo = anterior
        while (resto.isNotEmpty()) {
            val i = resto.indexOfFirst { artista(it) != ultimo }.let { if (it < 0) 0 else it }
            val x = resto.removeAt(i)
            saida.add(x); ultimo = artista(x)
        }
        return saida
    }

    private fun decada(ano: String): Int? = ano.take(4).toIntOrNull()?.takeIf { it > 1000 }?.let { it / 10 }
}
