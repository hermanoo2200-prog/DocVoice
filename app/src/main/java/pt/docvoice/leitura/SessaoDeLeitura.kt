package pt.docvoice.leitura

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pt.docvoice.text.DetectorDeIdioma
import pt.docvoice.text.TrocosDeFala
import pt.docvoice.text.Idioma
import java.util.Locale

/**
 * A sessão de leitura vive no processo, não no ecrã: minimizar a aplicação
 * não a interrompe. O serviço em primeiro plano (LeituraService) mantém-na viva
 * com o ecrã apagado; a interface limita-se a olhar para este estado.
 */
object SessaoDeLeitura {

    /** Que voz acabou por ser usada. O aviso de pt-BR mostra-se uma vez, não a cada parágrafo. */
    enum class Voz { PORTUGUES_EUROPEU, PORTUGUES_BRASIL, RUSSO, INGLES, OUTRA, NENHUMA }

    /** Uma voz do sistema, já com etiqueta legível. */
    data class VozDisponivel(val nome: String, val etiqueta: String)

    data class Estado(
        val documento: Documento? = null,
        val indice: Int = 0,
        val aLer: Boolean = false,
        val voz: Voz = Voz.NENHUMA,
        val avisoVozVisto: Boolean = false,
        val velocidade: Float = 1f,
        val idioma: Idioma = Idioma.DESCONHECIDO,
        val vozes: List<VozDisponivel> = emptyList(),
        val vozActual: String? = null,
        /** A língua do documento foi reconhecida mas o telemóvel não tem voz para ela. */
        val semVozParaOIdioma: Boolean = false
    ) {
        val paragrafoActual get() = documento?.paragrafos?.getOrNull(indice)
        val total get() = documento?.paragrafos?.size ?: 0
        val mostrarAvisoVoz get() = voz == Voz.PORTUGUES_BRASIL && !avisoVozVisto
    }

    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    private var tts: TextToSpeech? = null
    private var prontoParaFalar = false
    private var falarAssimQuePronto = false
    private var contexto: Context? = null

    /** Voz preferida por língua, vinda das preferências guardadas. */
    private val vozesPreferidas = mutableMapOf<String, String>()

    private var gestorAudio: AudioManager? = null
    private var pedidoDeFoco: AudioFocusRequest? = null

    // --- ciclo de vida ------------------------------------------------------

    fun preparar(ctx: Context) {
        if (tts != null) return
        contexto = ctx.applicationContext
        gestorAudio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        tts = TextToSpeech(ctx.applicationContext) { arranque ->
            if (arranque == TextToSpeech.SUCCESS) {
                prontoParaFalar = true
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts?.setOnUtteranceProgressListener(ouvinte)
                tts?.setSpeechRate(_estado.value.velocidade)
                aplicarIdioma(_estado.value.idioma)
                if (falarAssimQuePronto) {
                    falarAssimQuePronto = false
                    tocar()
                }
            } else {
                _estado.value = _estado.value.copy(voz = Voz.NENHUMA, aLer = false)
            }
        }
    }

    fun libertar() {
        largarFoco()
        tts?.stop()
        tts?.shutdown()
        tts = null
        prontoParaFalar = false
        _estado.value = _estado.value.copy(aLer = false)
    }

    // --- documento ----------------------------------------------------------

    fun carregar(documento: Documento, indiceInicial: Int = 0) {
        tts?.stop()
        val indice = indiceInicial.coerceIn(0, (documento.paragrafos.size - 1).coerceAtLeast(0))
        val idioma = DetectorDeIdioma.detectar(
            documento.paragrafos.take(30).joinToString(" ") { it.texto }
        )
        _estado.value = _estado.value.copy(
            documento = documento,
            indice = indice,
            aLer = false,
            idioma = idioma,
            avisoVozVisto = false
        )
        aplicarIdioma(idioma)
    }

    // --- língua e voz -------------------------------------------------------

    /**
     * Regra da casa: português europeu, nunca brasileiro por omissão.
     * Mas recusar ler é pior do que ler com sotaque — cai para pt-BR com aviso único.
     */
    private fun aplicarIdioma(idioma: Idioma) {
        val motor = tts ?: return

        val tentativas: List<Pair<Locale, Voz>> = when (idioma) {
            Idioma.RUSSO -> listOf(Locale("ru", "RU") to Voz.RUSSO)
            Idioma.INGLES -> listOf(Locale("en", "GB") to Voz.INGLES, Locale("en", "US") to Voz.INGLES)
            else -> listOf(
                Locale("pt", "PT") to Voz.PORTUGUES_EUROPEU,
                Locale("pt", "BR") to Voz.PORTUGUES_BRASIL
            )
        }

        var escolhida: Pair<Locale, Voz>? = null
        for (tentativa in tentativas) {
            if (motor.isLanguageAvailable(tentativa.first) >= TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                escolhida = tentativa
                break
            }
        }
        if (escolhida == null) {
            for (tentativa in tentativas) {
                if (motor.isLanguageAvailable(tentativa.first) >= TextToSpeech.LANG_AVAILABLE) {
                    escolhida = tentativa
                    break
                }
            }
        }

        if (escolhida == null) {
            _estado.value = _estado.value.copy(
                voz = Voz.NENHUMA,
                vozes = emptyList(),
                vozActual = null,
                semVozParaOIdioma = idioma != Idioma.DESCONHECIDO
            )
            return
        }

        motor.language = escolhida.first
        val disponiveis = vozesDe(escolhida.first.language)
        val preferida = vozesPreferidas[escolhida.first.language]
            ?.takeIf { nome -> disponiveis.any { it.nome == nome } }
        if (preferida != null) aplicarVozPorNome(preferida)

        _estado.value = _estado.value.copy(
            voz = escolhida.second,
            vozes = disponiveis,
            vozActual = preferida ?: motor.voice?.name,
            semVozParaOIdioma = false
        )
    }

    /**
     * Vozes que exigem rede ficam de fora: a aplicação não tem permissão de
     * Internet, e uma voz assim não diria uma palavra.
     */
    private fun vozesDe(lingua: String): List<VozDisponivel> {
        val motor = tts ?: return emptyList()
        val todas: Set<Voice> = runCatching { motor.voices }.getOrNull() ?: return emptyList()
        return todas
            .filter { it.locale.language.equals(lingua, ignoreCase = true) }
            .filterNot { it.isNetworkConnectionRequired }
            .sortedBy { it.name }
            .let { ordenadas ->
                // «Português (Portugal)», não «pt-PT»: quem escolhe a voz não
                // tem de saber códigos de língua. O número só entra quando o
                // mesmo idioma traz mais do que uma voz — e conta dentro desse
                // idioma, senão via-se «Brasil · 1, · 3, · 4», que não diz nada.
                val contadas = HashMap<String, Int>()
                ordenadas.map { v ->
                    val nomeDaLingua = runCatching {
                        v.locale.getDisplayName(v.locale).replaceFirstChar { c -> c.uppercase() }
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                        ?: v.locale.country.ifBlank { v.locale.language }
                    val quantasIguais = ordenadas.count { it.locale == v.locale }
                    val ordem = contadas.merge(nomeDaLingua, 1, Int::plus) ?: 1
                    val etiqueta = if (quantasIguais > 1) "$nomeDaLingua · $ordem" else nomeDaLingua
                    VozDisponivel(nome = v.name, etiqueta = etiqueta)
                }
            }
    }

    private fun aplicarVozPorNome(nome: String) {
        val motor = tts ?: return
        val voz = runCatching { motor.voices }.getOrNull()?.firstOrNull { it.name == nome } ?: return
        motor.voice = voz
    }

    fun definirVoz(nome: String) {
        aplicarVozPorNome(nome)
        _estado.value = _estado.value.copy(vozActual = nome)
        if (_estado.value.aLer) refazerTroco()
    }

    /** Chamada pelas preferências guardadas, antes ou depois de o motor arrancar. */
    fun lembrarVozPreferida(lingua: String, nome: String) {
        vozesPreferidas[lingua] = nome
        val estado = _estado.value
        if (estado.vozes.any { it.nome == nome } && estado.vozActual != nome) definirVoz(nome)
    }

    // --- velocidade ---------------------------------------------------------

    fun definirVelocidade(valor: Float) {
        val velocidade = valor.coerceIn(0.75f, 2f)
        if (velocidade == _estado.value.velocidade && tts != null) return
        _estado.value = _estado.value.copy(velocidade = velocidade)
        tts?.setSpeechRate(velocidade)
        if (_estado.value.aLer) refazerTroco()
    }

    // --- comandos -----------------------------------------------------------

    fun alternar() = if (_estado.value.aLer) pausar() else tocar()

    fun tocar() {
        val estado = _estado.value
        if (estado.documento == null || estado.total == 0) return
        if (!prontoParaFalar) { falarAssimQuePronto = true; preparar(contexto ?: return); return }
        if (!pedirFoco()) return
        _estado.value = estado.copy(aLer = true)
        falarActual(TextToSpeech.QUEUE_FLUSH)
    }

    fun pausar() {
        geracao++            // o que vier do motor a partir daqui já não conta
        idEmCurso = null
        tts?.stop()
        largarFoco()
        _estado.value = _estado.value.copy(aLer = false)
    }

    fun saltar(passos: Int) = irPara(_estado.value.indice + passos)

    fun irPara(indice: Int) {
        val estado = _estado.value
        if (estado.total == 0) return
        val novo = indice.coerceIn(0, estado.total - 1)
        _estado.value = estado.copy(indice = novo)
        if (estado.aLer) falarActual(TextToSpeech.QUEUE_FLUSH)
    }

    fun avisoVozVisto() {
        _estado.value = _estado.value.copy(avisoVozVisto = true)
    }

    // --- fala ---------------------------------------------------------------

    /**
     * Cada fala leva uma marca de geração no seu identificador.
     *
     * Sempre que cortamos de propósito — saltar de parágrafo, mudar de voz ou
     * de velocidade — o `QUEUE_FLUSH` interrompe a fala em curso, e o motor
     * anuncia essa interrupção. Uns motores chamam-lhe fim, outros chamam-lhe
     * erro. Sem a marca de geração não há como distinguir esse aviso tardio,
     * que é de uma fala já abandonada, do aviso da fala que está mesmo a
     * decorrer: tratá-los por igual fazia a leitura saltar um parágrafo ou
     * parar de vez.
     */
    private var geracao = 0
    private var idEmCurso: String? = null

    /**
     * Um parágrafo comprido vai ao motor em troços (ver [TrocosDeFala]).
     * No ecrã continua a ser um bloco só; quem ouve não dá pelo corte, porque
     * ele cai em pontuação e o troço seguinte entra em fila sem pausa.
     */
    private var trocos: List<String> = emptyList()

    /** Qual troço está a sair pelo altifalante neste momento. */
    private var trocoAFalar = 0

    private fun falarActual(modo: Int) {
        val paragrafo = _estado.value.paragrafoActual ?: return
        if (modo == TextToSpeech.QUEUE_FLUSH) geracao++
        trocos = TrocosDeFala.partir(paragrafo.texto)
        trocoAFalar = 0
        enfileirar(desde = 0, modo = modo)
    }

    /**
     * Põe na fila do motor **todos** os troços de uma vez.
     *
     * Antes entregava-se um troço e esperava-se que acabasse para entregar o
     * seguinte: o tempo que o motor leva a preparar a fala ouvia-se como
     * silêncio no meio do parágrafo — meio segundo de cada vez, medido em
     * emulador, e mais num telemóvel lento. Com a fila cheia, o motor prepara
     * o troço seguinte enquanto diz o actual.
     */
    private fun enfileirar(desde: Int, modo: Int) {
        val paragrafo = _estado.value.paragrafoActual ?: return
        val inicio = desde
        if (inicio > trocos.lastIndex) return
        for (i in inicio..trocos.lastIndex) {
            val id = "${paragrafo.id}#$geracao/$i"
            tts?.speak(trocos[i], if (i == inicio) modo else TextToSpeech.QUEUE_ADD, null, id)
        }
        // É o fim do último troço que faz mudar de parágrafo.
        idEmCurso = "${paragrafo.id}#$geracao/${trocos.lastIndex}"
    }

    /**
     * Muda de voz ou de velocidade sem recomeçar o parágrafo do princípio:
     * volta a enfileirar a partir do troço que está a ser dito.
     */
    private fun refazerTroco() {
        geracao++
        enfileirar(desde = trocoAFalar, modo = TextToSpeech.QUEUE_FLUSH)
    }

    private val ouvinte = object : UtteranceProgressListener() {
        /** Guarda qual troço começou agora, para o caso de ser preciso refazer. */
        override fun onStart(utteranceId: String?) {
            val indice = utteranceId?.substringAfterLast('/')?.toIntOrNull() ?: return
            if (utteranceId.contains("#$geracao/")) trocoAFalar = indice
        }

        override fun onDone(utteranceId: String?) {
            val estado = _estado.value
            if (!estado.aLer) return
            // Só o último troço do parágrafo manda seguir; os do meio já têm
            // o seguinte na fila do motor e não precisam de nada.
            if (utteranceId != idEmCurso) return
            if (estado.indice + 1 >= estado.total) {
                largarFoco()
                _estado.value = estado.copy(aLer = false) // fim do documento
                return
            }
            _estado.value = estado.copy(indice = estado.indice + 1)
            falarActual(TextToSpeech.QUEUE_ADD)
        }

        @Deprecated("substituído pela variante com código de erro")
        override fun onError(utteranceId: String?) = falhou(utteranceId)

        override fun onError(utteranceId: String?, errorCode: Int) = falhou(utteranceId)

        /**
         * Só pára se quem falhou for a fala que está mesmo a decorrer.
         * Um corte nosso chega a alguns motores como erro — e isso não é
         * motivo para calar o que o leitor pediu.
         */
        private fun falhou(utteranceId: String?) {
            if (utteranceId != idEmCurso) return
            pausar()
        }
    }

    // --- foco de áudio ------------------------------------------------------

    /**
     * Chamada, alarme, outro leitor: calar.
     * E ficar calado — quem volta a ler é a pessoa, não a aplicação.
     * Uma voz que arranca sozinha no bolso depois da chamada é intrusiva.
     */
    private val ouvinteDeFoco = AudioManager.OnAudioFocusChangeListener { mudanca ->
        when (mudanca) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> pausar()
        }
    }

    private fun pedirFoco(): Boolean {
        val gestor = gestorAudio ?: return true
        val pedido = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(ouvinteDeFoco)
            .build()
        pedidoDeFoco = pedido
        return gestor.requestAudioFocus(pedido) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun largarFoco() {
        val gestor = gestorAudio ?: return
        pedidoDeFoco?.let { gestor.abandonAudioFocusRequest(it) }
        pedidoDeFoco = null
    }
}
