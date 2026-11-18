package br.com.begood.bmp.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * Quantas vezes cada música tocou, quando tocou por último e quais são as favoritas.
 * Tudo guardado pelo caminho do arquivo (não muda quando o Android reindexa as músicas).
 * Uma música conta como "tocada" depois de 30 segundos (ou metade, se for mais curta).
 */
object Estatisticas {
    data class Registro(val vezes: Int, val ultima: Long)

    private lateinit var arquivo: File
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val registros = HashMap<String, Registro>()
    /** Caminho -> quando foi favoritada. */
    private val favoritas = LinkedHashMap<String, Long>()
    private var salvar: Job? = null

    /** Muda a cada alteração, para as telas recalcularem as playlists automáticas. */
    private val _versao = MutableStateFlow(0)
    val versao: StateFlow<Int> = _versao.asStateFlow()

    @Synchronized
    fun iniciar(ctx: Context) {
        if (::arquivo.isInitialized) return
        arquivo = File(ctx.applicationContext.filesDir, "estatisticas.json")
        runCatching {
            if (!arquivo.exists()) return@runCatching
            val o = JSONObject(arquivo.readText())
            o.optJSONObject("tocadas")?.let { t ->
                for (k in t.keys()) t.optJSONArray(k)?.let { registros[k] = Registro(it.optInt(0), it.optLong(1)) }
            }
            o.optJSONObject("favoritas")?.let { f -> for (k in f.keys()) favoritas[k] = f.optLong(k) }
        }
    }

    private fun chave(caminho: String) = caminho.lowercase()

    @Synchronized
    fun registrarReproducao(caminho: String) {
        val k = chave(caminho)
        val r = registros[k]
        registros[k] = Registro((r?.vezes ?: 0) + 1, System.currentTimeMillis())
        mudou()
    }

    @Synchronized
    fun ehFavorita(caminho: String): Boolean = chave(caminho) in favoritas

    /** Liga/desliga a favorita. Devolve true se ficou favorita. */
    @Synchronized
    fun alternarFavorita(caminho: String): Boolean {
        val k = chave(caminho)
        val agora = if (favoritas.remove(k) == null) { favoritas[k] = System.currentTimeMillis(); true } else false
        mudou()
        return agora
    }

    @Synchronized
    fun registro(caminho: String): Registro? = registros[chave(caminho)]

    @Synchronized
    fun maisTocadas(lib: Biblioteca, limite: Int = 100): List<Musica> =
        registros.entries.asSequence()
            .filter { it.value.vezes > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, Registro>> { it.value.vezes }.thenByDescending { it.value.ultima })
            .mapNotNull { lib.porCaminho[it.key] }.take(limite).toList()

    @Synchronized
    fun tocadasRecentemente(lib: Biblioteca, limite: Int = 100): List<Musica> =
        registros.entries.asSequence()
            .sortedByDescending { it.value.ultima }
            .mapNotNull { lib.porCaminho[it.key] }.take(limite).toList()

    @Synchronized
    fun favoritas(lib: Biblioteca): List<Musica> =
        favoritas.entries.sortedByDescending { it.value }.mapNotNull { lib.porCaminho[it.key] }

    fun adicionadasRecentemente(lib: Biblioteca, limite: Int = 100): List<Musica> =
        lib.musicas.sortedByDescending { if (it.adicionado > 0) it.adicionado else it.modificado }.take(limite)

    @Synchronized
    fun zerarHistorico() {
        registros.clear()
        mudou()
    }

    private fun mudou() {
        _versao.value++
        salvar?.cancel()
        // Junta várias mudanças seguidas numa gravação só.
        salvar = escopo.launch { delay(800); gravar() }
    }

    private fun gravar() {
        val texto = synchronized(this) {
            val t = JSONObject()
            for ((k, r) in registros) t.put(k, org.json.JSONArray().put(r.vezes).put(r.ultima))
            val f = JSONObject()
            for ((k, v) in favoritas) f.put(k, v)
            JSONObject().put("tocadas", t).put("favoritas", f).toString()
        }
        Arquivos.gravarSeguro(arquivo, texto)
    }
}

/** Grava num arquivo temporário e troca no fim: se o app fechar no meio, o arquivo antigo continua inteiro. */
object Arquivos {
    fun gravarSeguro(destino: File, texto: String) {
        runCatching {
            val tmp = File(destino.parentFile, destino.name + ".tmp")
            tmp.writeText(texto)
            if (!tmp.renameTo(destino)) { destino.delete(); tmp.renameTo(destino) }
        }
    }
}
