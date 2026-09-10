package pt.docvoice.text

/**
 * Quantos caracteres com significado uma página tem de ter para contar
 * como «página com texto».
 *
 * Um documento digitalizado traz muitas vezes uma camada fina de texto em
 * algumas folhas — cabeçalho, número de folha, carimbo, marca de água — e
 * isso raramente passa de uma centena de caracteres. Uma folha escrita a
 * sério anda pelos milhares. Contar só «tem uma letra?» dava scans por
 * documentos de texto e desligava o OCR onde ele é preciso.
 */
const val MINIMO_DE_TEXTO_POR_PAGINA = 120

/** Texto cru de uma página, tal como saiu do PDF. */
data class PageText(val numero: Int, val texto: String) {
    /** Letras e algarismos: espaços, pontuação e quebras de linha não contam. */
    val caracteresUteis: Int get() = texto.count { it.isLetterOrDigit() }
    val temTexto: Boolean get() = caracteresUteis >= MINIMO_DE_TEXTO_POR_PAGINA
}

/**
 * Bloco de leitura. A posição guarda-se por [pagina] + [indiceNaPagina] —
 * nunca por contagem corrida, que se desvia quando o OCR acrescenta texto.
 */
data class Paragraph(
    val pagina: Int,        // 1-based, como a folha do processo
    val indiceNaPagina: Int, // 0-based
    val texto: String
) {
    val id: String get() = "$pagina:$indiceNaPagina"
}

/**
 * Volta a encontrar a posição guardada dentro de uma lista de parágrafos.
 * Se aquele parágrafo já não existir — o corte mudou, o OCR acrescentou texto —
 * cai no início da mesma folha, que é onde a vista de quem lê se reorienta.
 * Sem folha nenhuma que sirva, volta ao princípio.
 */
fun indiceDeParagrafo(paragrafos: List<Paragraph>, pagina: Int, indiceNaPagina: Int): Int {
    val exacto = paragrafos.indexOfFirst { it.pagina == pagina && it.indiceNaPagina == indiceNaPagina }
    if (exacto >= 0) return exacto
    val inicioDaFolha = paragrafos.indexOfFirst { it.pagina == pagina }
    return if (inicioDaFolha >= 0) inicioDaFolha else 0
}
