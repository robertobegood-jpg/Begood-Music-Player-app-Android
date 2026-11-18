package br.com.begood.bmp.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/**
 * Onde o app procura a letra, nesta ordem:
 * 1. letra salva no app (achada na internet ou colada pelo usuário);
 * 2. tags do arquivo de música;
 * 3. arquivo .lrc ou .txt com o mesmo nome, na mesma pasta;
 * 4. internet (LRCLIB, gratuito, com letras sincronizadas) — quando o usuário pede ou se a busca automática está ligada.
 */
object Letras {
    private fun arquivoSalvo(ctx: Context, m: Musica): File {
        val nome = Integer.toHexString(m.caminho.lowercase().hashCode()) + "_" + m.caminho.length
        return File(File(ctx.filesDir, "letras").apply { mkdirs() }, "$nome.txt")
    }

    fun local(ctx: Context, m: Musica): Letra {
        runCatching { arquivoSalvo(ctx, m).takeIf { it.isFile }?.readText() }.getOrNull()
            ?.let { Letra.ler(it, FonteLetra.Salva) }?.takeUnless { it.vazia }?.let { return it }
        Tags.lerLetra(ctx, m)?.let { Letra.ler(it, FonteLetra.Tag) }?.takeUnless { it.vazia }?.let { return it }
        val base = File(m.caminho)
        for (ext in listOf("lrc", "txt")) {
            val f = File(base.parentFile, base.nameWithoutExtension + "." + ext)
            runCatching { if (f.isFile) f.readText() else null }.getOrNull()
                ?.let { Letra.ler(it, FonteLetra.Arquivo) }?.takeUnless { it.vazia }?.let { return it }
        }
        return Letra.VAZIA
    }

    fun salvar(ctx: Context, m: Musica, texto: String) {
        val f = arquivoSalvo(ctx, m)
        if (texto.isBlank()) f.delete() else f.writeText(texto)
    }

    fun temSalva(ctx: Context, m: Musica) = arquivoSalvo(ctx, m).isFile

    fun lerSalva(ctx: Context, m: Musica): String? =
        runCatching { arquivoSalvo(ctx, m).takeIf { it.isFile }?.readText() }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Depois de gravada dentro da música, a cópia do app não é mais necessária. */
    fun apagarSalva(ctx: Context, m: Musica) { arquivoSalvo(ctx, m).delete() }

    /** Tira "(Remastered 2011)", "- Live", "[Bonus Track]"... para achar a letra mais fácil. */
    private fun limparTitulo(t: String) = t
        .replace(Regex("""\s*[(\[][^)\]]*(remaster|live|ao vivo|bonus|version|versão|mix|edit|mono|stereo|deluxe)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+-\s+.*(remaster|live|ao vivo|version|versão|mix|edit).*$""", RegexOption.IGNORE_CASE), "")
        .trim()

    /** Busca a letra no LRCLIB. Devolve [Letra.VAZIA] se não achou; lança erro se não há internet. */
    fun online(m: Musica): Letra {
        val artista = m.artista.ifEmpty { m.albumArtista }
        if (artista.isEmpty()) return Letra.VAZIA
        val titulo = limparTitulo(m.titulo)
        val seg = (m.duracaoMs / 1000).toInt()

        val exata = Rede.json(
            "https://lrclib.net/api/get",
            buildMap {
                put("artist_name", artista); put("track_name", titulo)
                if (m.album.isNotEmpty()) put("album_name", m.album)
                if (seg > 0) put("duration", seg.toString())
            },
        )
        paraLetra(exata)?.let { return it }

        val lista = Rede.jsonLista("https://lrclib.net/api/search", mapOf("track_name" to titulo, "artist_name" to artista))
            ?: return Letra.VAZIA
        val candidatos = (0 until lista.length()).map { lista.getJSONObject(it) }
            .filter { it.txt("syncedLyrics").isNotEmpty() || it.txt("plainLyrics").isNotEmpty() || it.optBoolean("instrumental") }
        val melhor = candidatos.minByOrNull { c ->
            val d = if (seg > 0) abs(c.optDouble("duration", 0.0) - seg) else 0.0
            d - (if (c.txt("syncedLyrics").isNotEmpty()) 3 else 0) // prefere sincronizada
        }
        return paraLetra(melhor) ?: Letra.VAZIA
    }

    private fun paraLetra(o: JSONObject?): Letra? {
        if (o == null) return null
        if (o.optBoolean("instrumental")) return Letra.ler("(Instrumental)", FonteLetra.Online)
        val texto = o.txt("syncedLyrics").ifEmpty { o.txt("plainLyrics") }
        return Letra.ler(texto, FonteLetra.Online).takeUnless { it.vazia }
    }
}
