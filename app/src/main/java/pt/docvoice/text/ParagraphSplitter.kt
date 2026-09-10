package pt.docvoice.text

/**
 * Corta o texto de cada página em blocos de leitura.
 *
 * Regras fechadas antes de escrever o código:
 *  - cortar só em pontuação, nunca dentro da frase;
 *  - ordem de preferência: ponto final -> ponto e vírgula -> dois pontos;
 *  - se a frase mesmo assim passar dos 350, deixa passar — um bloco longo
 *    é melhor do que um pensamento partido ao meio;
 *  - numeração por página, não corrida.
 *
 * Puro Kotlin, sem Android: dá para testar na JVM.
 */
object ParagraphSplitter {

    const val ALVO_MIN = 300
    const val ALVO_MAX = 350

    fun split(paginas: List<PageText>): List<Paragraph> =
        paginas.flatMap { splitPagina(it.numero, it.texto) }

    fun splitPagina(pagina: Int, cru: String): List<Paragraph> {
        val saida = ArrayList<Paragraph>()
        var indice = 0
        for (bloco in blocos(cru)) {
            for (texto in empacotar(frases(bloco))) {
                saida += Paragraph(pagina, indice++, texto)
            }
        }
        return saida
    }

    // --- normalização -------------------------------------------------------

    private val LINHA_VAZIA = Regex("\\n\\s*\\n+")
    private val HIFEN_FIM_LINHA = Regex("(\\p{L})-\\n(\\p{Ll})")
    private val ESPACOS = Regex("[ \\t\\u00A0]+")

    /** Linha em branco é fronteira de bloco — separa artigos, alíneas, cabeçalhos. */
    internal fun blocos(cru: String): List<String> {
        val t = cru.replace("\r\n", "\n").replace('\r', '\n').replace(HIFEN_FIM_LINHA, "$1$2")
        return t.split(LINHA_VAZIA)
            .map { it.replace('\n', ' ').replace(ESPACOS, " ").trim() }
            .filter { it.isNotEmpty() }
    }

    // --- frases -------------------------------------------------------------

    /** Abreviaturas correntes em português jurídico: o ponto aqui não fecha frase. */
    private val ABREVIATURAS = setOf(
        "art", "arts", "artº", "al", "als", "n", "nº", "cf", "cfr", "fl", "fls",
        "p", "pp", "pág", "págs", "proc", "dr", "dra", "drs", "sr", "sra", "srs",
        "ex", "exmo", "exma", "etc", "séc", "ed", "cap", "vol", "dl", "dec",
        "rel", "ac", "lda", "min", "máx", "obs", "ref", "seg", "supra", "infra"
    )

    private fun fechaFrase(t: String, i: Int): Boolean {
        val c = t[i]
        if (c != '.' && c != '!' && c != '?' && c != '…') return false
        // 15.000 · 1.2 — ponto entre dígitos não fecha nada
        if (c == '.' && i > 0 && t[i - 1].isDigit() && i + 1 < t.length && t[i + 1].isDigit()) return false

        var j = i + 1
        while (j < t.length && (t[j] == '"' || t[j] == '»' || t[j] == '\'' || t[j] == ')')) j++
        if (j >= t.length) return true
        if (t[j] != ' ') return false
        while (j < t.length && t[j] == ' ') j++
        if (j >= t.length) return true
        if (t[j].isLowerCase()) return false

        if (c == '.') {
            var k = i - 1
            val sb = StringBuilder()
            while (k >= 0 && (t[k].isLetter() || t[k] == 'º' || t[k] == 'ª')) { sb.append(t[k]); k-- }
            val palavra = sb.reverse().toString().lowercase()
            if (palavra.length == 1 && palavra[0].isLetter()) return false // inicial de um nome: "J. M."
            if (palavra in ABREVIATURAS) return false
        }
        return true
    }

    internal fun frases(bloco: String): List<String> {
        val cortes = ArrayList<String>()
        var inicio = 0
        var i = 0
        while (i < bloco.length) {
            if (fechaFrase(bloco, i)) {
                var fim = i + 1
                while (fim < bloco.length && (bloco[fim] == '"' || bloco[fim] == '»' || bloco[fim] == ')')) fim++
                cortes += bloco.substring(inicio, fim).trim()
                inicio = fim
                i = fim
            } else i++
        }
        if (inicio < bloco.length) {
            bloco.substring(inicio).trim().takeIf { it.isNotEmpty() }?.let { cortes += it }
        }
        return cortes.filter { it.isNotEmpty() }.flatMap { partirLonga(it) }
    }

    /** Frase acima do máximo: tenta ponto e vírgula, depois dois pontos, depois deixa passar. */
    private fun partirLonga(frase: String): List<String> {
        if (frase.length <= ALVO_MAX) return listOf(frase)
        val porPontoEVirgula = partirEm(frase, ';')
        if (porPontoEVirgula.size > 1) return porPontoEVirgula.flatMap { partirEmDoisPontos(it) }
        return partirEmDoisPontos(frase)
    }

    private fun partirEmDoisPontos(t: String): List<String> {
        if (t.length <= ALVO_MAX) return listOf(t)
        val r = partirEm(t, ':')
        return if (r.size > 1) r else listOf(t) // deixa passar
    }

    private fun partirEm(t: String, sinal: Char): List<String> {
        val res = ArrayList<String>()
        var inicio = 0
        for (i in t.indices) {
            if (t[i] == sinal) {
                res += t.substring(inicio, i + 1).trim()
                inicio = i + 1
            }
        }
        if (inicio < t.length) t.substring(inicio).trim().takeIf { it.isNotEmpty() }?.let { res += it }
        return res.filter { it.isNotEmpty() }
    }

    // --- empacotamento ------------------------------------------------------

    /** Junta frases inteiras até chegar ao alvo. Nunca parte uma unidade. */
    internal fun empacotar(unidades: List<String>): List<String> {
        val saida = ArrayList<String>()
        val buf = StringBuilder()
        for (u in unidades) {
            when {
                buf.isEmpty() -> buf.append(u)
                buf.length + 1 + u.length <= ALVO_MAX -> buf.append(' ').append(u)
                else -> { saida += buf.toString(); buf.setLength(0); buf.append(u) }
            }
            if (buf.length >= ALVO_MIN) { saida += buf.toString(); buf.setLength(0) }
        }
        if (buf.isNotEmpty()) saida += buf.toString()
        return saida
    }
}
