package br.com.begood.bmp.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Cliente HTTP único do app (letras, dados de álbum, capas). */
object Rede {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { ch ->
            ch.proceed(ch.request().newBuilder().header("User-Agent", "BegoodMusicPlayer/1.0 (Android)").build())
        }
        .build()

    /** GET que devolve o corpo como texto, ou null se deu 404. Lança erro para falhas de rede. */
    fun texto(url: String, parametros: Map<String, String> = emptyMap()): String? {
        val u = url.toHttpUrl().newBuilder().apply { parametros.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        http.newCall(Request.Builder().url(u).build()).execute().use { r ->
            if (r.code == 404) return null
            if (!r.isSuccessful) error("O serviço respondeu ${r.code}")
            return r.body?.string()
        }
    }

    fun json(url: String, parametros: Map<String, String> = emptyMap()): JSONObject? =
        texto(url, parametros)?.let(::JSONObject)

    fun jsonLista(url: String, parametros: Map<String, String> = emptyMap()): JSONArray? =
        texto(url, parametros)?.let(::JSONArray)

    fun bytes(url: String): ByteArray? =
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) null else r.body?.bytes()
        }

    fun mensagem(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "Sem internet."
        is java.net.SocketTimeoutException -> "A internet está lenta; tente de novo."
        else -> e.message ?: "Erro ao acessar a internet."
    }
}

/** Texto de um campo JSON, ou "" (o org.json devolve "null" como texto em alguns casos). */
fun JSONObject.txt(nome: String): String = if (isNull(nome)) "" else optString(nome).trim()
