package pt.docvoice.text

/**
 * Parte um parágrafo em troços para o motor de voz — **só para falar**.
 * O que se vê no ecrã continua a ser o parágrafo inteiro.
 *
 * Porquê: a regra de corte do [ParagraphSplitter] deixa passar inteira a frase
 * que ultrapassa os 350 caracteres, porque um pensamento partido ao meio lê-se
 * pior do que um bloco comprido. Em texto jurídico há frases de mil e muitos
 * caracteres, e um pedido desse tamanho faz motores de voz calarem-se a meio
 * sem dizer porquê — foi o que aconteceu num telemóvel a sério, incluindo com
 * o ecrã apagado ao fim de dez minutos.
 *
 * Aqui corta-se pelo mesmo princípio do resto da aplicação: em pontuação,
 * nunca dentro de uma palavra.
 *
 * Puro Kotlin, sem Android: dá para testar na JVM.
 */
object TrocosDeFala {

    /**
     * Tamanho máximo de cada pedido ao motor.
     *
     * A plataforma aceita `TextToSpeech.getMaxSpeechInputLength()` (4000 na
     * prática), mas o limite anunciado e o limite que cada motor aguenta não
     * são a mesma coisa. Este valor anda perto do tamanho de um parágrafo
     * normal — assim os troços soam como os outros blocos e não se nota
     * o corte.
     */
    const val LIMITE = 320

    private const val MINIMO_UTIL = 40

    /** Fim de frase. */
    private val FORTE = charArrayOf('.', '!', '?', '…')

    /** Pausa dentro da frase, por ordem de preferência. */
    private val FRACA = charArrayOf(';', ':', ',')

    /**
     * Devolve sempre pelo menos um troço; a junção dos troços é o texto
     * original, tirando os espaços das emendas.
     */
    fun partir(texto: String, limite: Int = LIMITE): List<String> {
        val inteiro = texto.trim()
        if (inteiro.length <= limite) return if (inteiro.isEmpty()) emptyList() else listOf(inteiro)

        val saida = ArrayList<String>()
        var resto = inteiro
        while (resto.length > limite) {
            val corte = ondeCortar(resto, limite)
            saida += resto.substring(0, corte).trim()
            resto = resto.substring(corte).trim()
        }
        if (resto.isNotEmpty()) saida += resto
        return saida
    }

    /**
     * Última pontuação antes do limite; sem pontuação nenhuma, o último
     * espaço; sem espaço nenhum — uma palavra maior do que o limite, o que
     * em texto real não acontece — corta-se à força.
     */
    private fun ondeCortar(t: String, limite: Int): Int {
        val fim = minOf(limite, t.length)

        for (conjunto in listOf(FORTE, FRACA)) {
            var i = fim - 1
            while (i >= MINIMO_UTIL) {
                if (t[i] in conjunto && terminaMesmo(t, i)) return i + 1
                i--
            }
        }

        var i = fim - 1
        while (i >= MINIMO_UTIL) {
            if (t[i].isWhitespace()) return i
            i--
        }
        return fim
    }

    /**
     * Um ponto só corta se vier seguido de espaço e não pertencer a uma
     * abreviatura ou a um número — `art. 340.º`, `15.000`, `n.º 2` não são
     * fim de frase nenhum.
     */
    private fun terminaMesmo(t: String, i: Int): Boolean {
        if (i + 1 < t.length && !t[i + 1].isWhitespace()) return false
        if (t[i] != '.') return true
        if (i >= 1 && t[i - 1].isDigit()) return false

        var j = i - 1
        while (j >= 0 && t[j].isLetter()) j--
        val palavra = t.substring(j + 1, i).lowercase()
        return palavra !in ABREVIATURAS
    }

    /** As mesmas do [ParagraphSplitter]: aqui o ponto também não fecha frase. */
    private val ABREVIATURAS = setOf(
        "art", "arts", "artº", "al", "als", "n", "nº", "cf", "cfr", "fl", "fls",
        "p", "pp", "pág", "págs", "proc", "dr", "dra", "drs", "sr", "sra", "srs",
        "ex", "exmo", "exma", "etc", "séc", "ed", "cap", "vol", "dl", "dec",
        "rel", "ac", "lda", "min", "máx", "obs", "ref", "seg", "supra", "infra"
    )
}
