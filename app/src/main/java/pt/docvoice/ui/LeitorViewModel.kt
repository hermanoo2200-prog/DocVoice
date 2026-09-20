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
import pt.docvoice.dados.ArquivoDeMarcas
import pt.docvoice.dados.ArquivoDeTextos
import pt.docvoice.dados.FormatoDeTexto
import pt.docvoice.dados.Preferencias
import pt.docvoice.dados.RegistoDocumento
import pt.docvoice.leitura.CacheDeDocumentos
import pt.docvoice.leitura.Documento
import pt.docvoice.leitura.SessaoDeLeitura
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import pt.docvoice.ocr.AlcanceDoOcr
import pt.docvoice.ocr.FicheirosDeIdioma
import pt.docvoice.ocr.IdiomaOcr
import pt.docvoice.ocr.LeitorTesseract
import pt.docvoice.ocr.PlanoDeOcr
import pt.docvoice.ocr.ProgressoDeOcr
import pt.docvoice.ocr.fundirPaginas
import pt.docvoice.ocr.paginasDe
import pt.docvoice.ocr.planearOcr
import pt.docvoice.ocr.reconhecer
import pt.docvoice.pdf.PdfTextExtractor
import pt.docvoice.text.Marca
import pt.docvoice.text.ParagraphSplitter
import pt.docvoice.text.alternar
import pt.docvoice.text.comNota
import pt.docvoice.text.listaParaCopiar
import pt.docvoice.text.porOrdemDoDocumento

sealed interface EstadoLeitura {
    data object Vazio : EstadoLeitura
    data class AExtrair(val nome: String, val feitas: Int, val total: Int) : EstadoLeitura
    data class Aberto(val documento: Documento) : EstadoLeitura
    data class Falhou(val mensagem: String) : EstadoLeitura
}

/** Posição guardada: folha e ordem dentro dela. */
data class Posicao(val pagina: Int, val indiceNaPagina: Int)

/** Em que pé vai o reconhecimento, para o ecrã. */
sealed interface EstadoDoOcr {
    data object Parado : EstadoDoOcr
    /** A copiar ficheiros de idioma e a arrancar o motor. Não tem percentagem. */
    data object APreparar : EstadoDoOcr
    data class AProcessar(val progresso: ProgressoDeOcr) : EstadoDoOcr
    /** Acabou. [folhas] é quantas trouxeram texto — pode ser menos que as pedidas. */
    data class Terminado(val folhas: Int) : EstadoDoOcr
    /** Parado a meio por quem lê. O que já saiu ficou guardado. */
    data class Interrompido(val folhas: Int) : EstadoDoOcr
    data class Falhou(val mensagem: String) : EstadoDoOcr
}

class LeitorViewModel(app: Application) : AndroidViewModel(app) {

    private val arquivo = ArquivoDeRecentes(app)
    private val textos = ArquivoDeTextos(app)
    private val marcasGuardadas = ArquivoDeMarcas(app)
    private val preferencias = Preferencias(app)

    private val _estado = MutableStateFlow<EstadoLeitura>(EstadoLeitura.Vazio)
    val estado: StateFlow<EstadoLeitura> = _estado.asStateFlow()

    val recentes: StateFlow<List<RegistoDocumento>> =
        arquivo.lista.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var trabalho: Job? = null

    // ── passo 6: reconhecimento ────────────────────────────────────────────
    private val ficheirosDeIdioma = FicheirosDeIdioma(app)
    private val _estadoOcr = MutableStateFlow<EstadoDoOcr>(EstadoDoOcr.Parado)
    val estadoOcr: StateFlow<EstadoDoOcr> = _estadoOcr.asStateFlow()

    /** O que há para reconhecer no documento aberto. Null quando não há documento. */
    private val _plano = MutableStateFlow<PlanoDeOcr?>(null)
    val plano: StateFlow<PlanoDeOcr?> = _plano.asStateFlow()

    /**
     * Alguém pediu o reconhecimento à mão, pelo cabeçalho.
     *
     * Existe por causa do caso que o limiar não apanha: um contrato de
     * quarenta folhas com duas fotografadas fica muito acima dos 70% e por
     * isso nunca se propõe sozinho — mas essas duas folhas continuam mudas.
     */
    private val _ocrPedido = MutableStateFlow(false)
    val ocrPedido: StateFlow<Boolean> = _ocrPedido.asStateFlow()

    // ── marcas ─────────────────────────────────────────────────────────────
    private val _marcas = MutableStateFlow<List<Marca>>(emptyList())
    /** Sempre pela ordem do documento: é a ordem por que se relê. */
    val marcas: StateFlow<List<Marca>> = _marcas.asStateFlow()

    private var trabalhoOcr: Job? = null
    /** O motor a trabalhar agora, para o poder interromper a meio de uma folha. */
    private var leitorAberto: LeitorTesseract? = null

    init {
        // A leitura já ia a meio e a aplicação voltou do bolso: reencontrar o ecrã certo.
        SessaoDeLeitura.estado.value.documento?.let {
            _estado.value = EstadoLeitura.Aberto(it); recalcularPlano(it)
        }

        // Guarda-se texto só de documentos que estão na lista. O que caiu da
        // lista não tem que continuar gravado no telemóvel.
        viewModelScope.launch {
            val chaves = arquivo.lista.first().map { it.chave }
            runCatching { textos.limparOsQueSaíramDaLista(chaves) }
            runCatching { marcasGuardadas.limparOsQueSaíramDaLista(chaves) }
        }
    }

    /** Quanto ocupa o texto guardado, para se poder mostrar antes de apagar. */
    suspend fun espacoGuardado(): Long = textos.tamanho() + marcasGuardadas.tamanho()

    /** O botão «apagar tudo o que está guardado». */
    fun apagarTudoOGuardado(aoAcabar: () -> Unit = {}) {
        viewModelScope.launch {
            textos.apagarTudo()
            marcasGuardadas.apagarTudo()
            _marcas.value = emptyList()
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
                recalcularPlano(jaAberto)
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
                recalcularPlano(guardado)
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
                recalcularPlano(documento)
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
                recalcularPlano(documento)
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

    /**
     * Olha o documento aberto e diz o que há para reconhecer. Chamado sempre
     * que um documento entra, venha ele do PDF, do cache ou do disco.
     */
    /**
     * Põe ou tira a marca do parágrafo que a voz está a ler agora.
     *
     * Um gesto só, e a leitura não pára: nada aqui toca na [SessaoDeLeitura].
     * É o botão que se carrega a ouvir, com o telemóvel no bolso ou na mão.
     */
    fun marcarOndeVai() {
        val onde = SessaoDeLeitura.estado.value.paragrafoActual ?: return
        alternarMarca(onde.pagina, onde.indiceNaPagina)
    }

    fun alternarMarca(pagina: Int, indiceNaPagina: Int) {
        val documento = (_estado.value as? EstadoLeitura.Aberto)?.documento ?: return
        _marcas.value = _marcas.value
            .alternar(pagina, indiceNaPagina, System.currentTimeMillis())
            .porOrdemDoDocumento()
        guardarMarcas(documento)
    }

    /** A linha que quem ouve escreve depois, se escrever. */
    fun escreverNota(pagina: Int, indiceNaPagina: Int, nota: String) {
        val documento = (_estado.value as? EstadoLeitura.Aberto)?.documento ?: return
        _marcas.value = _marcas.value.comNota(pagina, indiceNaPagina, nota)
        guardarMarcas(documento)
    }

    /** Carregar numa marca da lista leva a voz para lá. */
    fun irParaMarca(marca: Marca) {
        val documento = (_estado.value as? EstadoLeitura.Aberto)?.documento ?: return
        SessaoDeLeitura.irPara(documento.indiceDe(marca.pagina, marca.indiceNaPagina))
    }

    /** A lista toda em texto, para o botão de copiar. */
    fun listaDeMarcasParaCopiar(): String {
        val documento = (_estado.value as? EstadoLeitura.Aberto)?.documento ?: return ""
        val porId = documento.paragrafos.associateBy { it.id }
        return listaParaCopiar(documento.nome, _marcas.value) { porId[it.id]?.texto }
    }

    private fun guardarMarcas(documento: Documento) {
        viewModelScope.launch {
            runCatching {
                marcasGuardadas.guardar(
                    RegistoDocumento.chaveDe(documento.nome, documento.tamanho),
                    _marcas.value
                )
            }
        }
    }

    private fun carregarMarcas(documento: Documento?) {
        if (documento == null) { _marcas.value = emptyList(); return }
        viewModelScope.launch {
            _marcas.value = runCatching {
                marcasGuardadas.ler(RegistoDocumento.chaveDe(documento.nome, documento.tamanho))
            }.getOrDefault(emptyList()).porOrdemDoDocumento()
        }
    }

    private fun recalcularPlano(documento: Documento?) {
        carregarMarcas(documento)
        _plano.value = documento?.let {
            planearOcr(paginasDe(it.totalPaginas, it.paragrafos))
        }
        _estadoOcr.value = EstadoDoOcr.Parado
        _ocrPedido.value = false
    }

    /**
     * Manda reconhecer. [alcance] só interessa quando o documento é uma
     * mistura — umas folhas com texto e outras sem.
     *
     * Guarda folha a folha à medida que saem: parar a meio não deita fora o
     * que já foi lido, e voltar a mandar reconhecer continua de onde ficou,
     * porque as folhas que entretanto passaram a ter texto já não entram
     * no plano.
     */
    fun iniciarOcr(idioma: IdiomaOcr, alcance: AlcanceDoOcr = AlcanceDoOcr.SO_AS_QUE_FALTAM) {
        val documento = (_estado.value as? EstadoLeitura.Aberto)?.documento ?: return
        trabalhoOcr?.cancel()
        trabalhoOcr = viewModelScope.launch {
            val ctx = getApplication<Application>()
            val lido = LinkedHashMap<Int, String>()
            val folhas = paginasDe(documento.totalPaginas, documento.paragrafos)
            var leitor: LeitorTesseract? = null
            try {
                _estadoOcr.value = EstadoDoOcr.APreparar
                val prontos = ficheirosDeIdioma.preparar()
                if (idioma !in prontos) {
                    _estadoOcr.value = EstadoDoOcr.Falhou(
                        ctx.getString(R.string.ocr_sem_idioma)
                    )
                    return@launch
                }
                val aFazer = planearOcr(folhas).paginasA(alcance)
                if (aFazer.isEmpty()) {
                    _estadoOcr.value = EstadoDoOcr.Terminado(0)
                    return@launch
                }
                // A voz cala-se: o motor de leitura e o de voz a disputar o
                // processador num telemóvel antigo davam fala aos soluços.
                SessaoDeLeitura.pausar()
                _estadoOcr.value = EstadoDoOcr.AProcessar(ProgressoDeOcr(0, aFazer.size, 0))

                leitor = LeitorTesseract(
                    resolver = ctx.contentResolver,
                    uri = documento.uri,
                    caminhoDeDados = ficheirosDeIdioma.caminhoDeDados,
                    idioma = idioma
                )
                leitorAberto = leitor

                reconhecer(aFazer, leitor) { pagina, texto, progresso ->
                    if (texto.isNotBlank()) lido[pagina] = texto
                    _estadoOcr.value = EstadoDoOcr.AProcessar(progresso)
                }
                aplicarOcr(documento, folhas, lido, idioma)
                _estadoOcr.value = EstadoDoOcr.Terminado(lido.size)
            } catch (e: CancellationException) {
                // Parar é parar — mas o que já saiu do motor fica guardado.
                // NonCancellable porque este âmbito já vai a caminho de morrer.
                withContext(NonCancellable) {
                    if (lido.isNotEmpty()) aplicarOcr(documento, folhas, lido, idioma)
                    _estadoOcr.value = EstadoDoOcr.Interrompido(lido.size)
                }
                throw e
            } catch (e: Exception) {
                _estadoOcr.value = EstadoDoOcr.Falhou(
                    ctx.getString(R.string.ocr_falhou, e.message ?: e.javaClass.simpleName)
                )
            } finally {
                leitorAberto = null
                runCatching { leitor?.close() }
            }
        }
    }

    /**
     * Pára o reconhecimento agora, não no fim da folha.
     *
     * Primeiro trava o motor — uma folha densa demora dezenas de segundos e
     * cancelar a tarefa só tinha efeito entre folhas — e só depois cancela.
     */
    /** Fecha o aviso de acabado, parado ou falhado. */
    fun limparEstadoOcr() {
        _estadoOcr.value = EstadoDoOcr.Parado
        _ocrPedido.value = false
    }

    /** O botão do cabeçalho: mostrar a proposta mesmo acima do limiar. */
    fun pedirOcr() { _ocrPedido.value = true }
    fun fecharPedidoOcr() { _ocrPedido.value = false }

    fun pararOcr() {
        leitorAberto?.interromper()
        trabalhoOcr?.cancel()
    }

    /** Junta o reconhecido ao documento, guarda e volta a pôr quem lê onde estava. */
    private suspend fun aplicarOcr(
        documento: Documento,
        folhas: List<pt.docvoice.text.PageText>,
        lido: Map<Int, String>,
        idioma: IdiomaOcr
    ) {
        if (lido.isEmpty()) return
        val fundidas = fundirPaginas(folhas, lido)
        val novo = documento.copy(
            paginasComTexto = fundidas.count { it.temTexto },
            paragrafos = ParagraphSplitter.split(fundidas)
        )
        // Onde a voz ia. O corte mudou, por isso reencontra-se por folha.
        val onde = SessaoDeLeitura.estado.value.paragrafoActual
        val indice = onde?.let { novo.indiceDe(it.pagina, it.indiceNaPagina) } ?: 0

        CacheDeDocumentos.guardar(novo)
        SessaoDeLeitura.carregar(novo, indice)
        _estado.value = EstadoLeitura.Aberto(novo)

        val anterior = runCatching {
            textos.ler(RegistoDocumento.chaveDe(novo.nome, novo.tamanho))
        }.getOrNull()
        runCatching {
            textos.guardar(
                RegistoDocumento.chaveDe(novo.nome, novo.tamanho),
                FormatoDeTexto.TextoGuardado(
                    totalPaginas = novo.totalPaginas,
                    paginasComTexto = novo.paginasComTexto,
                    paragrafos = novo.paragrafos,
                    idiomaOcr = idioma.codigo,
                    paginasReconhecidas =
                        anterior?.paginasReconhecidas.orEmpty() + lido.keys
                )
            )
        }
        recalcularPlano(novo)
    }

    fun abrirRecente(registo: RegistoDocumento) =
        abrir(Uri.parse(registo.uri), Posicao(registo.pagina, registo.indiceNaPagina))

    fun esquecer(registo: RegistoDocumento) {
        CacheDeDocumentos.esquecer(registo.chave)
        viewModelScope.launch {
            arquivo.esquecer(registo.chave)
            // Tirar da lista apaga o texto no mesmo gesto: não fica nada para trás.
            textos.apagar(registo.chave)
            // Tirar o documento leva as marcas dele atrás, como leva o texto.
            marcasGuardadas.apagar(registo.chave)
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
