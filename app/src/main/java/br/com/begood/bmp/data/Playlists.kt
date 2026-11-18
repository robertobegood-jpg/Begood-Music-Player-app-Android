package br.com.begood.bmp.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Playlist feita pelo usuário. Guarda os caminhos dos arquivos (a ordem é a da playlist; pode repetir música). */
data class Playlist(val id: String, val nome: String, val caminhos: List<String>, val alterada: Long)

/** Playlists automáticas (calculadas na hora, não ficam no arquivo). */
enum class Automatica(val id: String, val nome: String, val descricao: String) {
    Favoritas("auto:favoritas", "Favoritas", "Músicas marcadas com o coração"),
    MaisTocadas("auto:mais", "Mais tocadas", "As 100 que você mais ouviu"),
    Recentes("auto:recentes", "Tocadas recentemente", "As últimas 100 que tocaram"),
    Novas("auto:novas", "Adicionadas recentemente", "As 100 últimas que entraram no celular");

    companion object {
        fun de(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** Guarda as playlists em playlists.json, dentro do app. */
object Playlists {
    private lateinit var arquivo: File
    private val _lista = MutableStateFlow<List<Playlist>>(emptyList())
    val lista: StateFlow<List<Playlist>> = _lista.asStateFlow()

    @Synchronized
    fun iniciar(ctx: Context) {
        if (::arquivo.isInitialized) return
        arquivo = File(ctx.applicationContext.filesDir, "playlists.json")
        _lista.value = runCatching {
            if (!arquivo.exists()) return@runCatching emptyList()
            val arr = JSONObject(arquivo.readText()).getJSONArray("playlists")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val c = o.getJSONArray("caminhos")
                Playlist(o.getString("id"), o.getString("nome"), (0 until c.length()).map { c.getString(it) }, o.optLong("alterada"))
            }
        }.getOrDefault(emptyList())
    }

    fun buscar(id: String): Playlist? = _lista.value.firstOrNull { it.id == id }

    /** Nome livre: se já existir, acrescenta " (2)", " (3)"... */
    fun nomeLivre(nome: String, ignorar: String? = null): String {
        val base = nome.trim().ifEmpty { "Nova playlist" }
        val usados = _lista.value.filter { it.id != ignorar }.map { normalizar(it.nome) }.toSet()
        if (normalizar(base) !in usados) return base
        var n = 2
        while (normalizar("$base ($n)") in usados) n++
        return "$base ($n)"
    }

    @Synchronized
    fun criar(nome: String, caminhos: List<String> = emptyList()): Playlist {
        val p = Playlist(UUID.randomUUID().toString(), nomeLivre(nome), caminhos, System.currentTimeMillis())
        salvar(_lista.value + p)
        return p
    }

    @Synchronized
    fun renomear(id: String, nome: String) = alterar(id) { it.copy(nome = nomeLivre(nome, ignorar = id)) }

    @Synchronized
    fun apagar(id: String) = salvar(_lista.value.filter { it.id != id })

    /** Acrescenta no fim as que ainda não estão na playlist. Devolve quantas entraram. */
    @Synchronized
    fun adicionar(id: String, caminhos: List<String>): Int {
        var entraram = 0
        alterar(id) { p ->
            val ja = p.caminhos.mapTo(HashSet()) { it.lowercase() }
            val novos = caminhos.filter { ja.add(it.lowercase()) }
            entraram = novos.size
            p.copy(caminhos = p.caminhos + novos)
        }
        return entraram
    }

    @Synchronized
    fun removerPosicao(id: String, posicao: Int) = alterar(id) { p ->
        if (posicao !in p.caminhos.indices) p else p.copy(caminhos = p.caminhos.toMutableList().apply { removeAt(posicao) })
    }

    @Synchronized
    fun mover(id: String, de: Int, para: Int) = alterar(id) { p ->
        if (de !in p.caminhos.indices || para !in p.caminhos.indices) p
        else p.copy(caminhos = p.caminhos.toMutableList().apply { add(para, removeAt(de)) })
    }

    @Synchronized
    fun tirarAusentes(id: String, lib: Biblioteca) = alterar(id) { p ->
        p.copy(caminhos = p.caminhos.filter { lib.porCaminho[it.lowercase()] != null })
    }

    private fun alterar(id: String, f: (Playlist) -> Playlist) =
        salvar(_lista.value.map { if (it.id == id) f(it).copy(alterada = System.currentTimeMillis()) else it })

    private fun salvar(nova: List<Playlist>) {
        _lista.value = nova
        val arr = JSONArray()
        for (p in nova) arr.put(
            JSONObject().put("id", p.id).put("nome", p.nome).put("alterada", p.alterada).put("caminhos", JSONArray(p.caminhos))
        )
        Arquivos.gravarSeguro(arquivo, JSONObject().put("playlists", arr).toString())
    }

    /** Músicas da playlist (manual ou automática) que existem na biblioteca, na ordem. */
    fun musicas(id: String, lib: Biblioteca): List<Musica> = when (Automatica.de(id)) {
        Automatica.Favoritas -> Estatisticas.favoritas(lib)
        Automatica.MaisTocadas -> Estatisticas.maisTocadas(lib)
        Automatica.Recentes -> Estatisticas.tocadasRecentemente(lib)
        Automatica.Novas -> Estatisticas.adicionadasRecentemente(lib)
        null -> buscar(id)?.caminhos.orEmpty().mapNotNull { lib.porCaminho[it.lowercase()] }
    }

    fun nome(id: String): String? = Automatica.de(id)?.nome ?: buscar(id)?.nome
}
