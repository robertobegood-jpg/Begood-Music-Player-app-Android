package br.com.begood.bmp.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import br.com.begood.bmp.data.Preferencias
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Tipo de filtro de cada banda do equalizador paramétrico. */
enum class TipoFiltro(val nome: String) { Pico("Pico"), GraveShelf("Graves (shelf)"), AgudoShelf("Agudos (shelf)") }

/** Uma banda: frequência central (Hz), ganho (dB), largura Q e tipo. */
data class Banda(val tipo: TipoFiltro, val freq: Float, val ganho: Float, val q: Float, val ativa: Boolean = true)

data class ConfigEq(val ligado: Boolean, val preamp: Float, val bandas: List<Banda>) {
    fun paraJson(): JSONObject = JSONObject()
        .put("ligado", ligado).put("preamp", preamp.toDouble())
        .put("bandas", JSONArray(bandas.map {
            JSONObject().put("tipo", it.tipo.name).put("freq", it.freq.toDouble()).put("ganho", it.ganho.toDouble())
                .put("q", it.q.toDouble()).put("ativa", it.ativa)
        }))

    companion object {
        fun deJson(o: JSONObject): ConfigEq {
            val arr = o.getJSONArray("bandas")
            return ConfigEq(
                ligado = o.optBoolean("ligado", true),
                preamp = o.optDouble("preamp", 0.0).toFloat(),
                bandas = (0 until arr.length()).map { i ->
                    val b = arr.getJSONObject(i)
                    Banda(
                        tipo = runCatching { TipoFiltro.valueOf(b.getString("tipo")) }.getOrDefault(TipoFiltro.Pico),
                        freq = b.getDouble("freq").toFloat(), ganho = b.getDouble("ganho").toFloat(),
                        q = b.getDouble("q").toFloat(), ativa = b.optBoolean("ativa", true),
                    )
                },
            )
        }
    }
}

data class Preset(val nome: String, val config: ConfigEq, val doUsuario: Boolean = false)

/**
 * Equalizador paramétrico de verdade (frequência, ganho e Q livres em cada banda), feito em software
 * dentro do player. Funciona igual no alto-falante, fone com fio e Bluetooth.
 */
object Equalizador {
    const val MAX_BANDAS = 10
    const val GANHO_MAX = 15f

    private fun bandasPadrao(vararg ganhos: Float): List<Banda> {
        val freqs = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)
        return freqs.mapIndexed { i, f ->
            val tipo = when (i) { 0 -> TipoFiltro.GraveShelf; freqs.lastIndex -> TipoFiltro.AgudoShelf; else -> TipoFiltro.Pico }
            Banda(tipo, f, ganhos.getOrElse(i) { 0f }, if (tipo == TipoFiltro.Pico) 1.0f else 0.7f)
        }
    }

    val presetsFixos = listOf(
        Preset("Plano", ConfigEq(true, 0f, bandasPadrao(0f, 0f, 0f, 0f, 0f))),
        Preset("Graves+", ConfigEq(true, -5f, bandasPadrao(6f, 2f, 0f, 0f, 0f))),
        Preset("Rock", ConfigEq(true, -4f, bandasPadrao(4f, -1f, -2f, 2f, 4f))),
        Preset("Pop", ConfigEq(true, -3f, bandasPadrao(-1f, 2f, 3f, 1f, -1f))),
        Preset("Vocal", ConfigEq(true, -3f, bandasPadrao(-2f, -1f, 3f, 2f, 0f))),
        Preset("Jazz", ConfigEq(true, -3f, bandasPadrao(3f, 1f, -1f, 1f, 3f))),
        Preset("Clássica", ConfigEq(true, -3f, bandasPadrao(3f, 0f, 0f, 0f, 3f))),
        Preset("Eletrônica", ConfigEq(true, -5f, bandasPadrao(5f, 1f, -1f, 2f, 4f))),
        Preset("Agudos+", ConfigEq(true, -5f, bandasPadrao(0f, 0f, 0f, 2f, 6f))),
    )

    private val _config = MutableStateFlow(carregar())
    val config: StateFlow<ConfigEq> = _config.asStateFlow()

    private val _presetsUsuario = MutableStateFlow(carregarPresets())
    val presetsUsuario: StateFlow<List<Preset>> = _presetsUsuario.asStateFlow()

    /** Muda a cada alteração; o processador de áudio recalcula os filtros quando vê um número novo. */
    @Volatile var versao = 0; private set

    private fun carregar(): ConfigEq = runCatching {
        ConfigEq.deJson(JSONObject(Preferencias.equalizador!!))
    }.getOrElse { presetsFixos[0].config.copy(ligado = false) }

    private fun carregarPresets(): List<Preset> = runCatching {
        val arr = JSONArray(Preferencias.presetsEq!!)
        (0 until arr.length()).map { arr.getJSONObject(it) }.map { Preset(it.getString("nome"), ConfigEq.deJson(it.getJSONObject("config")), true) }
    }.getOrDefault(emptyList())

    fun atualizar(nova: ConfigEq) {
        _config.value = nova
        versao++
        Preferencias.equalizador = nova.paraJson().toString()
    }

    fun salvarPreset(nome: String) {
        val lista = _presetsUsuario.value.filter { it.nome != nome } + Preset(nome, _config.value, true)
        gravarPresets(lista)
    }

    fun apagarPreset(nome: String) = gravarPresets(_presetsUsuario.value.filter { it.nome != nome })

    private fun gravarPresets(lista: List<Preset>) {
        _presetsUsuario.value = lista
        Preferencias.presetsEq = JSONArray(lista.map { JSONObject().put("nome", it.nome).put("config", it.config.paraJson()) }).toString()
    }

    /** Coeficientes do filtro (fórmulas do "Audio EQ Cookbook", de R. Bristow-Johnson), já divididos por a0. */
    fun coeficientes(b: Banda, taxa: Int): DoubleArray {
        val f = b.freq.toDouble().coerceIn(10.0, taxa * 0.45)
        val a = 10.0.pow(b.ganho / 40.0)
        val w0 = 2 * PI * f / taxa
        val cw = cos(w0)
        val alfa = sin(w0) / (2 * b.q.toDouble().coerceIn(0.1, 20.0))
        val raizA2alfa = 2 * sqrt(a) * alfa
        val c = when (b.tipo) {
            TipoFiltro.Pico -> doubleArrayOf(1 + alfa * a, -2 * cw, 1 - alfa * a, 1 + alfa / a, -2 * cw, 1 - alfa / a)
            TipoFiltro.GraveShelf -> doubleArrayOf(
                a * ((a + 1) - (a - 1) * cw + raizA2alfa), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - raizA2alfa),
                (a + 1) + (a - 1) * cw + raizA2alfa, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - raizA2alfa,
            )
            TipoFiltro.AgudoShelf -> doubleArrayOf(
                a * ((a + 1) + (a - 1) * cw + raizA2alfa), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - raizA2alfa),
                (a + 1) - (a - 1) * cw + raizA2alfa, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - raizA2alfa,
            )
        }
        val a0 = c[3]
        return doubleArrayOf(c[0] / a0, c[1] / a0, c[2] / a0, c[4] / a0, c[5] / a0)
    }

    /** Resposta em dB na frequência [f] (para desenhar a curva na tela). */
    fun respostaDb(cfg: ConfigEq, f: Double, taxa: Int = 48000): Double {
        var db = cfg.preamp.toDouble()
        val w = 2 * PI * f / taxa
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        for (b in cfg.bandas) {
            if (!b.ativa) continue
            val k = coeficientes(b, taxa)
            val numRe = k[0] + k[1] * c1 + k[2] * c2; val numIm = -(k[1] * s1 + k[2] * s2)
            val denRe = 1 + k[3] * c1 + k[4] * c2; val denIm = -(k[3] * s1 + k[4] * s2)
            db += 20 * log10(hypot(numRe, numIm) / hypot(denRe, denIm))
        }
        return db
    }

    /** Pré-amplificação sugerida para não distorcer (compensa o maior reforço da curva). */
    fun preampSugerido(cfg: ConfigEq): Float {
        var pico = 0.0
        var f = 20.0
        val semPre = cfg.copy(preamp = 0f)
        while (f < 20000) { pico = maxOf(pico, respostaDb(semPre, f)); f *= 1.06 }
        return (-pico).toFloat().coerceIn(-GANHO_MAX, 0f)
    }
}

/**
 * Processador de áudio do ExoPlayer que aplica as bandas do [Equalizador] (filtros biquad em série).
 * Aceita PCM de 16 bits e float; o resultado sai no mesmo formato.
 */
@OptIn(UnstableApi::class)
class ProcessadorEq : BaseAudioProcessor() {
    private var versaoAplicada = -1
    private var ligado = false
    private var ganhoPre = 1.0
    private var coef = Array(0) { DoubleArray(5) }
    private var estado = Array(0) { DoubleArray(0) } // [canal][banda*2]
    private var canais = 0
    private var taxa = 0
    private var flutuante = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        canais = inputAudioFormat.channelCount
        taxa = inputAudioFormat.sampleRate
        flutuante = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        versaoAplicada = -1
        return inputAudioFormat
    }

    private fun prepararSePreciso() {
        val v = Equalizador.versao
        if (v == versaoAplicada) return
        versaoAplicada = v
        val cfg = Equalizador.config.value
        val bandas = cfg.bandas.filter { it.ativa }
        ligado = cfg.ligado && (bandas.any { it.ganho != 0f } || cfg.preamp != 0f)
        ganhoPre = 10.0.pow(cfg.preamp / 20.0)
        val novos = Array(bandas.size) { Equalizador.coeficientes(bandas[it], taxa) }
        if (novos.size != coef.size || estado.size != canais) estado = Array(canais) { DoubleArray(novos.size * 2) }
        coef = novos
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val tamanho = inputBuffer.remaining()
        if (tamanho == 0) return
        prepararSePreciso()
        val saida = replaceOutputBuffer(tamanho)
        if (!ligado) {
            saida.put(inputBuffer)
            saida.flip()
            return
        }
        val entrada = inputBuffer.order(ByteOrder.nativeOrder())
        val nb = coef.size
        var canal = 0
        if (flutuante) {
            while (entrada.remaining() >= 4) {
                saida.putFloat(filtrar(entrada.float.toDouble(), canal, nb).coerceIn(-1.0, 1.0).toFloat())
                canal = (canal + 1) % canais
            }
        } else {
            while (entrada.remaining() >= 2) {
                val y = filtrar(entrada.short / 32768.0, canal, nb)
                saida.putShort((y.coerceIn(-1.0, 32767.0 / 32768.0) * 32768.0).toInt().toShort())
                canal = (canal + 1) % canais
            }
        }
        inputBuffer.position(inputBuffer.limit())
        saida.flip()
    }

    /** Passa a amostra por todas as bandas (forma direta transposta II). */
    private fun filtrar(x0: Double, canal: Int, nb: Int): Double {
        var x = x0 * ganhoPre
        val z = estado[canal]
        for (b in 0 until nb) {
            val k = coef[b]
            val i = b * 2
            val y = k[0] * x + z[i]
            z[i] = k[1] * x - k[3] * y + z[i + 1]
            z[i + 1] = k[2] * x - k[4] * y
            x = y
        }
        return x
    }

    override fun onFlush() {
        for (z in estado) z.fill(0.0)
    }

    override fun onReset() {
        estado = Array(0) { DoubleArray(0) }
        coef = Array(0) { DoubleArray(5) }
        versaoAplicada = -1
    }
}
