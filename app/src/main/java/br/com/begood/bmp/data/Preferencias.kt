package br.com.begood.bmp.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Configurações guardadas no celular (pastas, equalizador, última fila...). */
object Preferencias {
    private lateinit var prefs: SharedPreferences

    fun iniciar(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("bmp", Context.MODE_PRIVATE)
    }

    /** Pastas escolhidas para escanear (caminhos completos). Vazio = usar a pasta padrão (Music). */
    var pastas: List<String>
        get() = lerLista("pastas")
        set(v) = gravarLista("pastas", v.distinct())

    /** Volta a tocar sozinho quando um fone/carro Bluetooth conecta. */
    var retomarBluetooth: Boolean
        get() = prefs.getBoolean("retomarBluetooth", false)
        set(v) = prefs.edit().putBoolean("retomarBluetooth", v).apply()

    /** Procura a letra na internet sozinho quando a música não tem letra. */
    var letraAutomatica: Boolean
        get() = prefs.getBoolean("letraAutomatica", true)
        set(v) = prefs.edit().putBoolean("letraAutomatica", v).apply()

    var artistasDaFaixa: Boolean
        get() = prefs.getBoolean("artistasDaFaixa", false)
        set(v) = prefs.edit().putBoolean("artistasDaFaixa", v).apply()

    var equalizador: String?
        get() = prefs.getString("equalizador", null)
        set(v) = prefs.edit().putString("equalizador", v).apply()

    /** Chave (gratuita) da API da Last.fm, usada pelo Genius. */
    var chaveLastFm: String
        get() = prefs.getString("chaveLastFm", null).orEmpty()
        set(v) = prefs.edit().putString("chaveLastFm", v.trim()).apply()

    /** Tamanho preferido da playlist Genius (25, 50, 75 ou 100). */
    var tamanhoGenius: Int
        get() = prefs.getInt("tamanhoGenius", 50)
        set(v) = prefs.edit().putInt("tamanhoGenius", v).apply()

    var presetsEq: String?
        get() = prefs.getString("presetsEq", null)
        set(v) = prefs.edit().putString("presetsEq", v).apply()

    /** Última fila tocada, para retomar (inclusive pelo botão play do fone/carro com o app fechado). */
    fun salvarFila(ids: List<Long>, indice: Int, posicao: Long) {
        val o = JSONObject()
            .put("ids", JSONArray(ids))
            .put("indice", indice)
            .put("posicao", posicao)
        prefs.edit().putString("fila", o.toString()).apply()
    }

    fun lerFila(): Triple<List<Long>, Int, Long>? = runCatching {
        val o = JSONObject(prefs.getString("fila", null) ?: return null)
        val arr = o.getJSONArray("ids")
        val ids = (0 until arr.length()).map { arr.getLong(it) }
        Triple(ids, o.optInt("indice"), o.optLong("posicao"))
    }.getOrNull()

    private fun lerLista(chave: String): List<String> = runCatching {
        val arr = JSONArray(prefs.getString(chave, null) ?: return emptyList())
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    private fun gravarLista(chave: String, v: List<String>) =
        prefs.edit().putString(chave, JSONArray(v).toString()).apply()
}
