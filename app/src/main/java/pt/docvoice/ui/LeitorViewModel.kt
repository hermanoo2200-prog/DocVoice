package pt.docvoice.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.docvoice.R
import pt.docvoice.dados.ArquivoDeRecentes
import pt.docvoice.dados.ArquivoDeTextos
import pt.docvoice.dados.FormatoDeTexto
import pt.docvoice.dados.Preferencias
import pt.docvoice.dados.RegistoDocumento
import pt.docvoice.leitura.CacheDeDocumentos
import pt.docvoice.leitura.Documento
import pt.docvoice.leitura.SessaoDeLeitura
import pt.docvoice.pdf.PdfTextExtractor
import pt.docvoice.text.ParagraphSplitter

sealed interface EstadoLeitura {
    data object Vazio : EstadoLeitura
    data class AExtrair(val nome: String, val feitas: Int, val total: Int) : EstadoLeitura
    data class Aberto(val documento: Documento) : EstadoLeitura
    data class Falhou(val mensagem: String) : EstadoLeitura
}

/** Posição guardada: folha e ordem dentro dela. */
data class Posicao(val pagina: Int, val indiceNaPagina: Int)

class LeitorViewModel(app: Application) : AndroidViewModel(app) {

    private val arquivo = ArquivoDeRecentes(app)
    private val textos = ArquivoDeTextos(app)
    private val preferencias = Preferencias(app)

    private val _estado = MutableStateFlow<EstadoLeitura>(EstadoLeitura.Vazio)
    val estado: StateFlow<EstadoLeitura> = _estado.asStateFlow()

    val recentes: StateFlow<List<RegistoDocumento>> =
        arquivo.lista.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var trabalho: Job? = null

    init {
        // A leitura já ia a meio e a aplicação voltou do bolso: reencontrar o ecrã certo.
        SessaoDeLeitura.estado.value.documento?.let { _estado.value = EstadoLeitura.Aberto(it) }

        // Guarda-se texto só de documentos que estão na lista. O que caiu da
        // lista não tem que continuar gravado no telemóvel.
        viewModelScope.launch {
            runCatching { textos.limparOsQueSaíramDaLista(arquivo.lista.first().map { it.chave }) }
        }
    }

    /** Quanto ocupa o texto guardado, para se poder mostrar antes de apagar. */
    suspend fun espacoGuardado(): Long = textos.tamanho()

    /** O botão «apagar tudo o que está guardado». */
    fun apagarTudoOGuardado(aoAcabar: () -> Unit = {}) {
        viewModelScope.launch {
            textos.apagarTudo()
            CacheDeDocumentos.limpar()
            aoAcabar()
        }
    }

    fun abrir(uri: Uri, restaurar: Posicao? = null) {
        trabalho?.cancel()
        trabalho = viewModelScope.launch {
            val ctx = getApplication<Application>()
            val (nome, tamanho) = nomeETamanho(ctx, uri)

            // Já está aberto este mesmo documento: não há nada a fazer senão
            // voltar ao ecrã de leitura. Nem extrair, nem parar a voz —
            // carregar no documento que já se está a ouvir não pode calá-lo.
            val jaAberto = SessaoDeLeitura.estado.value.documento
            if (jaAberto != null && jaAberto.nome == nome && jaAberto.tamanho == tamanho) {
                _estado.value = EstadoLeitura.Aberto(jaAberto)
                return@launch
            }

            // Outro documento, mas já extraído nesta sessão: entra de imediato.
            val guardado = CacheDeDocumentos.procurar(nome, tamanho, uri)
            if (guardado != null) {
                SessaoDeLeitura.pausar()
                runCatching { guardarPermissao(ctx, uri) }
                val onde = restaurar
                    ?: arquivo.procurar(RegistoDocumento.chaveDe(nome, tamanho))
                        ?.let { Posicao(it.pagina, it.indiceNaPagina) }
                SessaoDeLeitura.carregar(
                    guardado,
                    onde?.let { guardado.indiceDe(it.pagina, it.indiceNaPagina) } ?: 0
                )
                _estado.value = EstadoLeitura.Aberto(guardado)
                return@launch
            }

            // Já foi extraído noutro dia e ficou guardado no telemóvel:
            // não se abre o PDF outra vez.
            val doDisco = runCatching {
                textos.ler(RegistoDocumento.chaveDe(nome, tamanho))
            }.getOrNull()
            if (doDisco != null && doDisco.paragrafos.isNotEmpty()) {
                SessaoDeLeitura.pausar()
                runCatching { guardarPermissao(ctx, uri) }
                val documento = Documento(
                    nome = nome,
                    uri = uri,
                    tamanho = tamanho,
                    totalPaginas = doDisco.totalPaginas,
                    paginasComTexto = doDisco.paginasComTexto,
                    paragrafos = doDisco.paragrafos
                )
                val onde = restaurar
                    ?: arquivo.procurar(RegistoDocumento.chaveDe(nome, tamanho))
                        ?.let { Posicao(it.pagina, it.indiceNaPagina) }
                CacheDeDocumentos.guardar(documento)
                SessaoDeLeitura.carregar(
                    documento,
                    onde?.let { documento.indiceDe(it.pagina, it.indiceNaPagina) } ?: 0
                )
                _estado.value = EstadoLeitura.Aberto(documento)
                return@launch
            }

            SessaoDeLeitura.pausar()
            _estado.value = EstadoLeitura.AExtrair(nome, 0, 0)
            try {
                guardarPermissao(ctx, uri)
                val paginas = PdfTextExtractor(ctx.contentResolver).extrair(uri) { feitas, total ->
                    _estado.value = EstadoLeitura.AExtrair(nome, feitas, total)
                }
                val documento = Documento(
                    nome = nome,
                    uri = uri,
                    tamanho = tamanho,
                    totalPaginas = paginas.size,
                    paginasComTexto = paginas.count { it.temTexto },
                    paragrafos = ParagraphSplitter.split(paginas)
                )
                // Sem posição dada (abriu-se pelo selector do sistema, não pela
                // lista de recentes), procura-se a que ficou guardada deste mesmo
                // documento. Sem isto, ir buscar outro PDF e voltar a este
                // recomeçava do princípio.
                val posicao = restaurar
                    ?: arquivo.procurar(RegistoDocumento.chaveDe(nome, tamanho))
                        ?.let { Posicao(it.pagina, it.indiceNaPagina) }
                val indice = posicao?.let { documento.indiceDe(it.pagina, it.indiceNaPagina) } ?: 0
                CacheDeDocumentos.guardar(documento)
                SessaoDeLeitura.carregar(documento, indice)
                _estado.value = EstadoLeitura.Aberto(documento)
                runCatching {
                    textos.guardar(
                        RegistoDocumento.chaveDe(nome, tamanho),
                        FormatoDeTexto.TextoGuardado(
                            totalPaginas = documento.totalPaginas,
                            paginasComTexto = documento.paginasComTexto,
                            paragrafos = documento.paragrafos
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.value = EstadoLeitura.Falhou(
                    ctx.getString(R.string.erro_abrir, e.message ?: e.javaClass.simpleName)
                )
            }
        }
    }

    fun abrirRecente(registo: RegistoDocumento) =
        abrir(Uri.parse(registo.uri), Posicao(registo.pagina, registo.indiceNaPagina))

    fun esquecer(registo: RegistoDocumento) {
        CacheDeDocumentos.esquecer(registo.chave)
        viewModelScope.launch {
            arquivo.esquecer(registo.chave)
            // Tirar da lista apaga o texto no mesmo gesto: não fica nada para trás.
            textos.apagar(registo.chave)
        }
    }

    fun mudarVelocidade(valor: Float) {
        SessaoDeLeitura.definirVelocidade(valor)
        viewModelScope.launch { preferencias.guardarVelocidade(valor) }
    }

    fun mudarVoz(nome: String) {
        SessaoDeLeitura.definirVoz(nome)
        viewModelScope.launch { preferencias.guardarVoz(linguaActual(), nome) }
    }

    private fun linguaActual(): String {
        val estado = SessaoDeLeitura.estado.value
        return when (estado.voz) {
            SessaoDeLeitura.Voz.RUSSO -> "ru"
            SessaoDeLeitura.Voz.INGLES -> "en"
            SessaoDeLeitura.Voz.PORTUGUES_EUROPEU, SessaoDeLeitura.Voz.PORTUGUES_BRASIL -> "pt"
            else -> estado.idioma.etiqueta.ifBlank { "pt" }
        }
    }

    /** Volta à lista sem mexer no que está a ser lido. */
    fun voltarALista() {
        _estado.value = EstadoLeitura.Vazio
    }

    /** Sem isto, o acesso ao ficheiro morre ao fechar a aplicação — e a lista de recentes fica inútil. */
    private fun guardarPermissao(ctx: Context, uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // alguns fornecedores não a dão; não é motivo para não ler agora
        }
    }

    private fun nomeETamanho(ctx: Context, uri: Uri): Pair<String, Long> {
        var nome: String? = null
        var tamanho = 0L
        val colunas = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val c: Cursor? = ctx.contentResolver.query(uri, colunas, null, null, null)
        c?.use {
            if (it.moveToFirst()) {
                val iNome = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (iNome >= 0) nome = it.getString(iNome)
                val iTamanho = it.getColumnIndex(OpenableColumns.SIZE)
                if (iTamanho >= 0 && !it.isNull(iTamanho)) tamanho = it.getLong(iTamanho)
            }
        }
        return (nome ?: uri.lastPathSegment ?: "documento.pdf") to tamanho
    }
}
