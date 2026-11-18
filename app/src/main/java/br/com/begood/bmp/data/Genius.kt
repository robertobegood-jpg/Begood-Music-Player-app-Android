package br.com.begood.bmp.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Playlist "Genius", no mesmo esquema do iTunes:
 * - a parte pesada (descobrir o que combina com o quê) vem da Last.fm, que tem os hábitos de milhões de ouvintes;
 * - o resultado fica guardado no celular (genius.json), então montar a playlist depois funciona sem internet;
 * - com internet, busca o que faltar e atualiza o que tiver mais de [VALIDADE_DIAS] dias.
 *
 * Precisa de uma chave gratuita da API da Last.fm (Configurações > Genius).
 */
object Genius {
    const val SITE_CHAVE = "https://www.last.fm/api/account/create"
    private const val API = "https://ws.audioscrobbler.com/2.0/"
    private const val VALIDADE_DIAS = 90L
    private const val DIA = 24L * 60 * 60 * 1000

    private class Guardado(val quando: Long, val itens: List<Parecido>)

    private lateinit var arquivo: File
    private val faixas = HashMap<String, Guardado>()
    private val artistas = HashMap<String, Guardado>()

    /** Erro de chave (inválida ou suspensa): não adianta continuar pedindo. */
    class ChaveInvalida(msg: String) : Exception(msg)

    @Synchronized
    fun iniciar(ctx: Context) {
        if (::arquivo.isInitialized) return
        arquivo = File(ctx.applicationContext.filesDir, "genius.json")
        runCatching {
            if (!arquivo.exists()) return@runCatching
            val o = JSONObject(arquivo.readText())
            ler(o.optJSONObject("faixas"), faixas)
            ler(o.optJSONObject("artistas"), artistas)
        }
    }

    private fun ler(o: JSONObject?, destino: HashMap<String, Guardado>) {
        if (o == null) return
        for (k in o.keys()) {
            val g = o.optJSONObject(k) ?: continue
            val arr = g.optJSONArray("s") ?: JSONArray()
            destino[k] = Guardado(g.optLong("t"), (0 until arr.length()).mapNotNull { i ->
                arr.optJSONArray(i)?.let { Parecido(it.optString(0), it.optString(1), it.optDouble(2, 0.0)) }
            })
        }
    }

    @Synchronized
    private fun gravar() {
        fun json(m: Map<String, Guardado>) = JSONObject().apply {
            for ((k, g) in m) put(k, JSONObject().put("t", g.quando).put("s", JSONArray().apply {
                g.itens.forEach { put(JSONArray().put(it.artista).put(it.titulo).put(Math.round(it.nota * 1000) / 1000.0)) }
            }))
        }
        Arquivos.gravarSeguro(arquivo, JSONObject().put("faixas", json(faixas)).put("artistas", json(artistas)).toString())
    }

    val temChave: Boolean get() = Preferencias.chaveLastFm.isNotBlank()

    @Synchronized
    fun resumo(): Pair<Int, Int> = artistas.size to faixas.size

    @Synchronized
    fun apagarDados() {
        faixas.clear(); artistas.clear(); gravar()
    }

    /** Artista principal da faixa (sem os convidados "feat."), que é como a Last.fm conhece. */
    fun artistaPrincipal(m: Musica): String =
        Biblioteca.separarArtistas(m.artista).firstOrNull() ?: m.artistaDoAlbum

    private fun velho(g: Guardado?) = g == null || System.currentTimeMillis() - g.quando > VALIDADE_DIAS * DIA

    // ---------- Last.fm ----------

    private fun pedir(parametros: Map<String, String>): JSONObject {
        val chave = Preferencias.chaveLastFm.trim()
        if (chave.isEmpty()) throw ChaveInvalida("Falta a chave da Last.fm (Configurações > Genius).")
        val url = API.toHttpUrl().newBuilder().apply {
            parametros.forEach { (k, v) -> addQueryParameter(k, v) }
            addQueryParameter("api_key", chave)
            addQueryParameter("format", "json")
        }.build()
        Rede.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val corpo = r.body?.string().orEmpty()
            val o = runCatching { JSONObject(corpo) }.getOrNull()
            val codigo = o?.optInt("error", 0) ?: 0
            // 10 = chave inválida, 26 = chave suspensa, 4/9 = autenticação
            if (codigo in setOf(4, 9, 10, 26)) throw ChaveInvalida("A Last.fm recusou a chave: ${o?.optString("message")}")
            if (codigo == 29) error("Muitos pedidos seguidos para a Last.fm; tente daqui a pouco.")
            if (o == null) error(if (r.isSuccessful) "Resposta estranha da Last.fm." else "A Last.fm respondeu ${r.code}")
            return o // erro 6 (não achou) volta como objeto sem lista: dá lista vazia
        }
    }

    /** A Last.fm devolve um objeto sozinho quando há só 1 resultado; aqui vira sempre lista. */
    private fun lista(o: JSONObject?, nome: String): List<JSONObject> = when (val v = o?.opt(nome)) {
        is JSONArray -> (0 until v.length()).mapNotNull { v.optJSONObject(it) }
        is JSONObject -> listOf(v)
        else -> emptyList()
    }

    private fun buscarFaixasNaRede(artista: String, titulo: String): List<Parecido> {
        val o = pedir(mapOf("method" to "track.getSimilar", "artist" to artista, "track" to titulo, "autocorrect" to "1", "limit" to "150"))
        return lista(o.optJSONObject("similartracks"), "track").mapNotNull { t ->
            val nome = t.txt("name")
            val art = t.optJSONObject("artist")?.txt("name").orEmpty()
            if (nome.isEmpty() || art.isEmpty()) null else Parecido(art, nome, t.optString("match").toDoubleOrNull() ?: 0.0)
        }
    }

    private fun buscarArtistasNaRede(artista: String): List<Parecido> {
        val o = pedir(mapOf("method" to "artist.getSimilar", "artist" to artista, "autocorrect" to "1", "limit" to "60"))
        return lista(o.optJSONObject("similarartists"), "artist").mapNotNull { a ->
            val nome = a.txt("name")
            if (nome.isEmpty()) null else Parecido(nome, "", a.optString("match").toDoubleOrNull() ?: 0.0)
        }
    }

    /** Parecidos da música: do celular, ou da internet se faltar/estiver velho (e [online]). */
    private fun parecidosFaixa(artista: String, titulo: String, online: Boolean): List<Parecido>? {
        val k = GeniusMotor.chaveFaixa(artista, titulo)
        val g = synchronized(this) { faixas[k] }
        if (online && velho(g)) {
            val novo = runCatching { buscarFaixasNaRede(artista, titulo) }
                .onFailure { if (it is ChaveInvalida) throw it }.getOrNull()
            if (novo != null) { synchronized(this) { faixas[k] = Guardado(System.currentTimeMillis(), novo) }; return novo }
        }
        return g?.itens
    }

    private fun parecidosArtista(artista: String, online: Boolean): List<Parecido>? {
        val k = GeniusMotor.chaveArtista(artista)
        val g = synchronized(this) { artistas[k] }
        if (online && velho(g)) {
            val novo = runCatching { buscarArtistasNaRede(artista) }
                .onFailure { if (it is ChaveInvalida) throw it }.getOrNull()
            if (novo != null) { synchronized(this) { artistas[k] = Guardado(System.currentTimeMillis(), novo) }; return novo }
        }
        return g?.itens
    }

    // ---------- Playlist ----------

    class Resultado(val musicas: List<Musica>, val fonte: Fonte, val aviso: String?)

    enum class Fonte { LastFm, SoArtista, Local }

    /**
     * Monta a playlist a partir de [semente]. Tenta a internet só se tiver chave;
     * sem internet usa o que já está guardado; sem nada guardado, usa gênero e época.
     */
    suspend fun playlist(semente: Musica, lib: Biblioteca, tamanho: Int): Resultado = withContext(Dispatchers.IO) {
        val artista = artistaPrincipal(semente)
        var aviso: String? = null
        var online = temChave
        fun <R> tentar(bloco: () -> R?): R? = try { bloco() } catch (e: ChaveInvalida) {
            aviso = e.message; online = false; null
        }
        val pf = tentar { parecidosFaixa(artista, semente.titulo, online) }.orEmpty()
        val pa = tentar { parecidosArtista(artista, online) }.orEmpty()
        if (online) gravar()
        val fonte = when {
            pf.isNotEmpty() -> Fonte.LastFm
            pa.isNotEmpty() -> Fonte.SoArtista
            else -> Fonte.Local
        }
        if (aviso == null && fonte == Fonte.Local) aviso = when {
            !temChave -> "Sem a chave da Last.fm: a playlist foi feita pelo gênero e pela época. Configure em Configurações > Genius."
            else -> "A Last.fm não tem dados desta música (ou sem internet): playlist feita pelo gênero e pela época."
        }
        val musicas = GeniusMotor.montar(
            semente = semente,
            biblioteca = lib.musicas,
            idDe = { it.id },
            infoDe = { m -> GeniusMotor.Info(Biblioteca.separarArtistas(m.artista).ifEmpty { listOf(m.artistaDoAlbum) }, m.titulo, m.genero, m.ano) },
            faixasParecidas = pf,
            artistasParecidos = pa,
            tamanho = tamanho,
        )
        Resultado(musicas, fonte, aviso)
    }

    /**
     * Baixa e guarda os dados de todos os artistas da biblioteca e das músicas mais ouvidas e favoritas,
     * para o Genius funcionar sem internet. [progresso] recebe (feitos, total). Pode ser cancelado.
     * Devolve quantos itens foram atualizados.
     */
    suspend fun baixarTudo(lib: Biblioteca, progresso: (Int, Int) -> Unit): Int = withContext(Dispatchers.IO) {
        val nomes = lib.musicas.map(::artistaPrincipal).filter { it.isNotBlank() }
            .distinctBy { GeniusMotor.chaveArtista(it) }
            .filter { velho(synchronized(this@Genius) { artistas[GeniusMotor.chaveArtista(it)] }) }
        val musicas = (Estatisticas.favoritas(lib) + Estatisticas.maisTocadas(lib, 200)).distinctBy { it.id }
            .filter { velho(synchronized(this@Genius) { faixas[GeniusMotor.chaveFaixa(artistaPrincipal(it), it.titulo)] }) }
        val tarefas: List<() -> Unit> =
            nomes.map { n -> { val r = buscarArtistasNaRede(n); synchronized(this@Genius) { artistas[GeniusMotor.chaveArtista(n)] = Guardado(System.currentTimeMillis(), r) } } } +
                musicas.map { m -> {
                    val a = artistaPrincipal(m)
                    val r = buscarFaixasNaRede(a, m.titulo)
                    synchronized(this@Genius) { faixas[GeniusMotor.chaveFaixa(a, m.titulo)] = Guardado(System.currentTimeMillis(), r) }
                } }
        val total = tarefas.size
        var feitos = 0
        var falhasSeguidas = 0
        try {
            for (t in tarefas) {
                coroutineContext.ensureActive()
                try {
                    t(); falhasSeguidas = 0
                } catch (e: ChaveInvalida) {
                    throw e
                } catch (e: Exception) {
                    // Uma falha solta (um artista com nome estranho) passa; várias seguidas = sem internet.
                    if (++falhasSeguidas >= 5) throw IllegalStateException(Rede.mensagem(e), e)
                }
                feitos++; progresso(feitos, total)
                if (feitos % 25 == 0) gravar()
                delay(220) // a Last.fm pede no máximo ~5 pedidos por segundo
            }
        } finally {
            gravar()
        }
        feitos
    }
}
