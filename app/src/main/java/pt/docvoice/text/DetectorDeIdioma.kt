package pt.docvoice.text

/** As três línguas que a primeira versão sabe ler. */
enum class Idioma(val etiqueta: String) {
    PORTUGUES("pt"),
    RUSSO("ru"),
    INGLES("en"),
    DESCONHECIDO("")
}

/**
 * Adivinha a língua do documento pelo texto já extraído.
 *
 * Sem biblioteca e sem rede: alfabeto primeiro (cirílico decide sozinho),
 * depois palavras curtas de uso corrente e os acentos que só o português tem.
 * Não é para acertar em tudo — é para não pôr uma voz russa a ler português,
 * que foi exactamente o que aconteceu nas versões de browser.
 */
object DetectorDeIdioma {

    private const val AMOSTRA_MAXIMA = 20_000
    private const val LETRAS_MINIMAS = 40
    private const val PALAVRAS_MINIMAS = 20

    private val PALAVRAS_PT = setOf(
        "de", "do", "da", "dos", "das", "que", "não", "uma", "um", "para", "com",
        "por", "como", "mais", "este", "esta", "foi", "ser", "tem", "nos", "na",
        "no", "os", "as", "em", "ao", "aos", "à", "às", "pela", "pelo", "são",
        "também", "entre", "artigo", "nº", "sobre", "seu", "sua", "quando"
    )

    private val PALAVRAS_EN = setOf(
        "the", "and", "of", "to", "in", "that", "is", "for", "with", "was", "as",
        "on", "are", "by", "this", "from", "at", "it", "be", "or", "which", "shall"
    )

    private const val ACENTOS_PT = "ãõçáéíóúâêôàÃÕÇÁÉÍÓÚÂÊÔÀ"

    fun detectar(texto: String): Idioma {
        val amostra = texto.take(AMOSTRA_MAXIMA)

        var cirilico = 0
        var latino = 0
        for (c in amostra) {
            if (!c.isLetter()) continue
            if (c in 'Ѐ'..'ӿ') cirilico++ else latino++
        }
        val letras = cirilico + latino
        if (letras < LETRAS_MINIMAS) return Idioma.DESCONHECIDO
        if (cirilico.toFloat() / letras > 0.30f) return Idioma.RUSSO

        val palavras = amostra.lowercase().split(Regex("[^\\p{L}º]+")).filter { it.isNotEmpty() }
        if (palavras.size < PALAVRAS_MINIMAS) return Idioma.DESCONHECIDO

        val pt = palavras.count { it in PALAVRAS_PT }
        val en = palavras.count { it in PALAVRAS_EN }
        val acentos = amostra.count { it in ACENTOS_PT }

        val pontosPt = pt * 2 + acentos
        val pontosEn = en * 2

        return when {
            pt >= 3 && pontosPt > pontosEn -> Idioma.PORTUGUES
            en >= 3 && pontosEn > pontosPt -> Idioma.INGLES
            else -> Idioma.DESCONHECIDO
        }
    }
}
