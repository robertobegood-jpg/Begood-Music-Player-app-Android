package br.com.begood.bmp.data

/** Uma linha da letra; [ms] é o momento em que ela é cantada (só nas letras sincronizadas). */
data class LinhaLetra(val ms: Long?, val texto: String)

/** De onde veio a letra. */
enum class FonteLetra(val descricao: String) {
    Tag("tags da música"), Arquivo("arquivo .lrc/.txt"), Salva("letra salva no app"), Online("LRCLIB (internet)")
}

/** Letra pronta para mostrar. */
class Letra(val linhas: List<LinhaLetra>, val sincronizada: Boolean, val textoOriginal: String = "", val fonte: FonteLetra? = null) {
    val vazia: Boolean get() = linhas.none { it.texto.isNotBlank() }

    /** Índice da linha que está sendo cantada em [posicaoMs] (-1 antes da primeira). */
    fun linhaAtual(posicaoMs: Long): Int {
        if (!sincronizada) return -1
        var atual = -1
        for (i in linhas.indices) {
            val t = linhas[i].ms ?: continue
            if (t <= posicaoMs) atual = i else break
        }
        return atual
    }

    companion object {
        val VAZIA = Letra(emptyList(), false)
        private val TEMPO = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
        private val META = Regex("""^\[(ar|ti|al|au|by|re|ve|length|offset|la|id|#):.*]$""", RegexOption.IGNORE_CASE)

        fun ehSincronizada(texto: String): Boolean = TEMPO.containsMatchIn(texto)

        /** Lê texto puro ou .lrc (detecta sozinho). */
        fun ler(textoBruto: String?, fonte: FonteLetra? = null): Letra {
            val texto = textoBruto.orEmpty().replace("\r\n", "\n").replace('\r', '\n')
            if (texto.isBlank()) return VAZIA
            if (ehSincronizada(texto)) lerLrc(texto, fonte).takeUnless { it.vazia }?.let { return it }
            return Letra(texto.lines().map { LinhaLetra(null, it.trim()) }, false, texto, fonte)
        }

        /** Formato .lrc: "[01:23.45]texto" (uma linha pode ter vários tempos). */
        private fun lerLrc(texto: String, fonte: FonteLetra?): Letra {
            var deslocamento = 0L
            val saida = ArrayList<LinhaLetra>()
            for (bruta in texto.lines()) {
                val linha = bruta.trim()
                if (linha.isEmpty()) continue
                if (META.matches(linha)) {
                    if (linha.startsWith("[offset:", true)) deslocamento = linha.substringAfter(':').trimEnd(']').trim().toLongOrNull() ?: 0L
                    continue
                }
                val tempos = TEMPO.findAll(linha).toList()
                if (tempos.isEmpty()) continue
                val frase = linha.substring(tempos.last().range.last + 1).trim()
                    .replace(Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>"""), "") // marcações palavra a palavra
                for (t in tempos) {
                    val (min, seg, frac) = t.destructured
                    val fracMs = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                    val ms = min.toLong() * 60_000 + seg.toLong() * 1000 + fracMs - deslocamento
                    saida.add(LinhaLetra(ms.coerceAtLeast(0), frase))
                }
            }
            saida.sortBy { it.ms }
            return Letra(saida, saida.isNotEmpty(), texto, fonte)
        }
    }
}
