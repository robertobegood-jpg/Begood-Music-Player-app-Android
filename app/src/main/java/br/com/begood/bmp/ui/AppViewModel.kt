package br.com.begood.bmp.ui

import android.app.Application
import android.app.PendingIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import android.provider.OpenableColumns
import br.com.begood.bmp.data.Alteracao
import br.com.begood.bmp.data.Estatisticas
import br.com.begood.bmp.data.Genius
import br.com.begood.bmp.data.M3u
import br.com.begood.bmp.data.Playlists
import kotlinx.coroutines.Job
import br.com.begood.bmp.data.Biblioteca
import br.com.begood.bmp.data.Letras
import br.com.begood.bmp.data.Musica
import org.jaudiotagger.tag.FieldKey
import br.com.begood.bmp.data.Preferencias
import br.com.begood.bmp.data.Repositorio
import br.com.begood.bmp.data.Tags
import br.com.begood.bmp.player.Tocador
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Telas que entram por cima das abas (com "voltar"). */
sealed interface Tela {
    data class AlbumTela(val chave: String) : Tela
    /** [daFaixa] = artista das faixas (inclui participações); senão, artista do álbum. */
    data class ArtistaTela(val nome: String, val daFaixa: Boolean = false) : Tela
    data class GeneroTela(val nome: String) : Tela
    data object TocandoAgora : Tela
    data object EqualizadorTela : Tela
    data object Configuracoes : Tela
    /** Editor de tags de uma música ([ids] com 1 item) ou de um álbum inteiro. */
    data class EditarTags(val ids: List<Long>, val doAlbum: Boolean) : Tela
    /** Playlist do usuário ou automática ("auto:..."). */
    data class PlaylistTela(val id: String) : Tela
    /** Playlist Genius a partir da música [id]. */
    data class GeniusTela(val id: Long) : Tela
}

enum class Aba(val titulo: String) { Albuns("Álbuns"), Artistas("Artistas"), Musicas("Músicas"), Playlists("Playlists"), Generos("Gêneros"), Buscar("Buscar") }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val tocador = Tocador(app)

    var temPermissao by mutableStateOf(Repositorio.temPermissao(app)); private set
    var biblioteca by mutableStateOf<Biblioteca?>(null); private set
    var carregando by mutableStateOf(false); private set
    var escaneando by mutableStateOf(false); private set

    var aba by mutableStateOf(Aba.Albuns)
    val pilha = mutableStateListOf<Tela>()
    var busca by mutableStateOf("")
    /** Aba Artistas: false = por artista do álbum, true = por artista da faixa (com convidados). Fica lembrado. */
    var artistasDaFaixa by mutableStateOf(Preferencias.artistasDaFaixa)
        private set

    fun mudarModoArtistas(daFaixa: Boolean) {
        artistasDaFaixa = daFaixa
        Preferencias.artistasDaFaixa = daFaixa
    }

    /** Mensagem rápida no rodapé (snackbar). */
    var aviso by mutableStateOf<String?>(null)

    /** Pedido de confirmação do Android para alterar arquivos (editor de tags). A tela abre e devolve o resultado. */
    var pedidoGravacao by mutableStateOf<PendingIntent?>(null); private set
    private var gravacaoPendente: (suspend () -> Unit)? = null
    var gravando by mutableStateOf(false); private set

    // ---------- Playlists ----------
    var playlists by mutableStateOf(Playlists.lista.value); private set
    /** Muda quando contagens/favoritas mudam (recalcula as automáticas). */
    var versaoEstatisticas by mutableStateOf(0); private set
    /** Músicas esperando a escolha da playlist (abre a janela "Adicionar à playlist"). */
    var paraPlaylist by mutableStateOf<List<Long>?>(null)

    init {
        tocador.conectar()
        viewModelScope.launch { Playlists.lista.collect { playlists = it } }
        viewModelScope.launch { Estatisticas.versao.collect { versaoEstatisticas = it } }
        viewModelScope.launch { Repositorio.biblioteca.collect { biblioteca = it } }
        viewModelScope.launch { Repositorio.carregando.collect { carregando = it } }
        if (temPermissao) Repositorio.recarregar()
    }

    fun permissaoMudou() {
        temPermissao = Repositorio.temPermissao(getApplication())
        if (temPermissao) Repositorio.recarregar()
    }

    // ---------- Seleção de várias músicas (toque longo) ----------
    /** Ids das músicas marcadas, na ordem em que foram marcadas. */
    val selecao = mutableStateListOf<Long>()
    val selecionando: Boolean get() = selecao.isNotEmpty()

    fun alternarSelecao(id: Long) {
        if (!selecao.remove(id)) selecao.add(id)
    }

    fun selecionarTodas(ids: List<Long>) {
        val faltam = ids.filter { it !in selecao }
        if (faltam.isEmpty()) selecao.removeAll(ids.toSet()) else selecao.addAll(faltam)
    }

    fun limparSelecao() = selecao.clear()

    /** Músicas da lista que está na tela agora (para "selecionar todas"). */
    var listaAtual: List<Long> = emptyList()

    fun musicasSelecionadas(): List<Musica> = selecao.mapNotNull { biblioteca?.porId?.get(it) }

    fun abrir(t: Tela) {
        if (t !is Tela.EditarTags) selecao.clear()
        if (pilha.lastOrNull() != t) pilha.add(t)
    }

    /** Volta uma tela. Devolve false quando já está nas abas (aí o sistema fecha o app). */
    fun voltar(): Boolean {
        if (pilha.lastOrNull() !is Tela.EditarTags) selecao.clear()
        if (pilha.isEmpty()) return false
        pilha.removeAt(pilha.lastIndex)
        return true
    }

    // ---------- Pastas ----------
    var pastas by mutableStateOf(Preferencias.pastas); private set

    fun adicionarPasta(caminho: String) {
        pastas = (pastas + caminho.trimEnd('/')).distinct()
        Preferencias.pastas = pastas
        Repositorio.recarregar()
    }

    fun removerPasta(caminho: String) {
        pastas = pastas - caminho
        Preferencias.pastas = pastas
        Repositorio.recarregar()
    }

    fun escanear() {
        if (escaneando) return
        escaneando = true
        viewModelScope.launch {
            val novos = withContext(Dispatchers.IO) { runCatching { Repositorio.escanearArquivos() }.getOrDefault(0) }
            escaneando = false
            val total = biblioteca?.musicas?.size ?: 0
            aviso = if (novos > 0) "$novos arquivo(s) novo(s) encontrado(s). Biblioteca: $total músicas." else "Biblioteca atualizada: $total músicas."
        }
    }

    // ---------- Tags ----------
    /**
     * Grava tags. No Android 11+ o sistema pede uma confirmação antes (uma vez por grupo de arquivos).
     * [aoTerminar] recebe a lista de erros (vazia = deu tudo certo).
     */
    fun gravarTags(musicas: List<Musica>, aoTerminar: (List<String>) -> Unit = {}, alteracao: (Musica) -> Alteracao) {
        val tarefa: suspend () -> Unit = {
            gravando = true
            val erros = withContext(Dispatchers.IO) {
                val e = Tags.gravar(getApplication(), musicas, alteracao)
                Repositorio.indexar(musicas.map { it.caminho })
                Repositorio.carregarAgora()
                e
            }
            gravando = false
            aviso = if (erros.isEmpty()) "Tags salvas em ${musicas.size} arquivo(s)." else "Não foi possível salvar: ${erros.first()}"
            aoTerminar(erros)
        }
        val pedido = Tags.pedidoDePermissao(getApplication(), musicas)
        if (pedido == null) viewModelScope.launch { tarefa() }
        else { gravacaoPendente = tarefa; pedidoGravacao = pedido }
    }

    /** Músicas da biblioteca que têm letra guardada só no app (achada na internet ou colada). */
    suspend fun musicasComLetraSalva(): List<Musica> = withContext(Dispatchers.IO) {
        biblioteca?.musicas.orEmpty().filter { Letras.temSalva(getApplication(), it) }
    }

    /**
     * Grava dentro dos arquivos de música todas as letras guardadas no app (uma confirmação do Android para todas).
     * As que deram certo saem da cópia do app, porque agora estão na própria música.
     */
    fun gravarLetrasSalvas(aoTerminar: () -> Unit = {}) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val musicas = musicasComLetraSalva()
        if (musicas.isEmpty()) { aviso = "Não há letras salvas só no app."; aoTerminar(); return@launch }
        val textos = withContext(Dispatchers.IO) { musicas.associate { it.id to Letras.lerSalva(ctx, it) } }
        gravarTags(musicas, aoTerminar = { erros ->
            viewModelScope.launch(Dispatchers.IO) {
                musicas.filter { m -> erros.none { it.startsWith(m.nomeArquivo + ":") } }.forEach { Letras.apagarSalva(ctx, it) }
                withContext(Dispatchers.Main) {
                    aviso = if (erros.isEmpty()) "Letras gravadas em ${musicas.size} música(s)."
                    else "${musicas.size - erros.size} letra(s) gravada(s); ${erros.size} com erro: ${erros.first()}"
                    aoTerminar()
                }
            }
        }) { m -> Alteracao(mapOf(FieldKey.LYRICS to textos[m.id])) }
    }

    fun resultadoPermissaoGravacao(permitiu: Boolean) {
        pedidoGravacao = null
        val t = gravacaoPendente
        gravacaoPendente = null
        if (permitiu && t != null) viewModelScope.launch { t() }
        else if (!permitiu) aviso = "Sem permissão, as tags não foram alteradas."
    }

    private fun caminhos(ids: List<Long>): List<String> = ids.mapNotNull { biblioteca?.porId?.get(it)?.caminho }

    fun musicasDaPlaylist(id: String): List<Musica> = biblioteca?.let { Playlists.musicas(id, it) }.orEmpty()

    /** Adiciona as músicas à playlist e avisa quantas entraram. */
    fun adicionarNaPlaylist(idPlaylist: String, ids: List<Long>) {
        val nome = Playlists.nome(idPlaylist) ?: return
        val entraram = Playlists.adicionar(idPlaylist, caminhos(ids))
        val ja = ids.size - entraram
        aviso = when {
            entraram == 0 -> "Já estava(m) em \"$nome\"."
            ja > 0 -> "$entraram adicionada(s) a \"$nome\" ($ja já estava(m) lá)."
            else -> "$entraram música(s) adicionada(s) a \"$nome\"."
        }
        limparSelecao()
    }

    /** Cria a playlist (já com as músicas, se vierem) e devolve o id. */
    fun criarPlaylist(nome: String, ids: List<Long> = emptyList()): String {
        val p = Playlists.criar(nome, caminhos(ids))
        aviso = if (ids.isEmpty()) "Playlist \"${p.nome}\" criada." else "Playlist \"${p.nome}\" criada com ${p.caminhos.size} música(s)."
        limparSelecao()
        return p.id
    }

    fun renomearPlaylist(id: String, nome: String) = Playlists.renomear(id, nome)

    fun apagarPlaylist(id: String) {
        val nome = Playlists.nome(id)
        Playlists.apagar(id)
        if (pilha.lastOrNull() == Tela.PlaylistTela(id)) voltar()
        aviso = "Playlist \"$nome\" apagada."
    }

    fun removerDaPlaylist(id: String, posicao: Int) = Playlists.removerPosicao(id, posicao)
    fun moverNaPlaylist(id: String, de: Int, para: Int) = Playlists.mover(id, de, para)

    fun ehFavorita(m: Musica): Boolean = Estatisticas.ehFavorita(m.caminho)

    fun alternarFavorita(m: Musica) {
        aviso = if (Estatisticas.alternarFavorita(m.caminho)) "\"${m.titulo}\" nas favoritas." else "\"${m.titulo}\" saiu das favoritas."
    }

    /** Lê um .m3u escolhido pelo usuário e cria a playlist com as músicas encontradas. */
    fun importarM3u(uri: Uri) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val lib = biblioteca ?: return@launch
        val resultado = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Não consegui abrir o arquivo.")
                // .m3u8 é UTF-8; .m3u feito no Windows costuma vir em "ANSI" (Windows-1252), com acentos diferentes.
                val texto = runCatching {
                    Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                }.getOrElse { String(bytes, charset("windows-1252")) }
                val lido = M3u.ler(texto)
                if (lido.entradas.isEmpty()) error("O arquivo não tem músicas (é mesmo uma playlist .m3u?).")
                val achadas = M3u.resolver(lido.entradas, lib.musicas, { it.caminho }, { it.artista }, { it.titulo }).filterNotNull()
                val nome = lido.nome ?: nomeDoArquivo(uri)?.substringBeforeLast('.') ?: "Playlist importada"
                Triple(Playlists.criar(nome, achadas.map { it.caminho }), achadas.size, lido.entradas.size)
            }
        }
        resultado.onSuccess { (p, achadas, total) ->
            aviso = if (achadas == total) "Playlist \"${p.nome}\" importada com $total música(s)."
            else "Playlist \"${p.nome}\": $achadas de $total música(s) encontradas no celular."
            abrir(Tela.PlaylistTela(p.id))
        }.onFailure { aviso = it.message ?: "Não consegui importar a playlist." }
    }

    /** Grava a playlist num arquivo .m3u8 escolhido pelo usuário. */
    fun exportarM3u(id: String, uri: Uri) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val nome = Playlists.nome(id) ?: "Playlist"
        val musicas = musicasDaPlaylist(id)
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val texto = M3u.escrever(nome, musicas.map { M3u.Faixa(it.caminho, it.artista, it.titulo, it.duracaoMs) })
                ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(texto.toByteArray(Charsets.UTF_8)) } ?: error("sem arquivo")
            }.isSuccess
        }
        aviso = if (ok) "\"$nome\" exportada (${musicas.size} música(s))." else "Não consegui salvar o arquivo."
    }

    private fun nomeDoArquivo(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    // ---------- Genius ----------
    var geniusProgresso by mutableStateOf<String?>(null); private set
    private var geniusTrabalho: Job? = null

    /** Baixa os dados da Last.fm de toda a biblioteca, para o Genius funcionar sem internet. */
    fun baixarGenius() {
        val lib = biblioteca ?: return
        if (geniusTrabalho?.isActive == true) return
        geniusProgresso = "Preparando…"
        geniusTrabalho = viewModelScope.launch {
            val r = runCatching {
                Genius.baixarTudo(lib) { feitos, total -> geniusProgresso = "Baixando dados: $feitos de $total" }
            }
            geniusProgresso = null
            aviso = r.fold(
                { if (it == 0) "Os dados do Genius já estavam em dia." else "Genius pronto para usar sem internet ($it itens atualizados)." },
                { if (it is kotlinx.coroutines.CancellationException) "Download do Genius interrompido (o que já baixou fica guardado)." else it.message ?: "Erro ao baixar." },
            )
        }
    }

    fun pararGenius() { geniusTrabalho?.cancel() }

    override fun onCleared() {
        tocador.desconectar()
    }
}
