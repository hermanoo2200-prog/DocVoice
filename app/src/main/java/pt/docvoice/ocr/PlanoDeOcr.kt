package pt.docvoice.ocr

import pt.docvoice.leitura.LIMIAR_TEXTO
import pt.docvoice.text.PageText

/**
 * Idiomas que a aplicação sabe reconhecer.
 *
 * O código é o nome do ficheiro do Tesseract — `por.traineddata`,
 * `rus.traineddata` — e os ficheiros viajam dentro da aplicação. Não há
 * transferência nenhuma: o manifesto não pede autorização de rede, e isso
 * é regra, não preferência.
 */
enum class IdiomaOcr(val codigo: String) {
    PORTUGUES("por"),
    RUSSO("rus");

    companion object {
        fun porCodigo(codigo: String): IdiomaOcr? = entries.firstOrNull { it.codigo == codigo }
    }
}

/**
 * O que há para reconhecer num documento que já foi aberto.
 *
 * Quem decide se uma folha «tem texto» é [PageText.temTexto], com o limiar de
 * 120 caracteres úteis: um scan traz muitas vezes cabeçalho e número de folha
 * por cima da fotografia, e contar «tem uma letra?» dava scans por documentos
 * escritos — era assim que o OCR se desligava exactamente onde fazia falta.
 */
data class PlanoDeOcr(
    val paginasSemTexto: List<Int>,
    val paginasComTexto: List<Int>
) {
    val total: Int get() = paginasSemTexto.size + paginasComTexto.size

    val percentagemComTexto: Int
        get() = if (total == 0) 0 else paginasComTexto.size * 100 / total

    /** Todas as folhas já trazem texto: não há nada a reconhecer. */
    val nadaAFazer: Boolean get() = paginasSemTexto.isEmpty()

    /** Nem uma folha traz texto: é uma digitalização de ponta a ponta. */
    val digitalizacaoInteira: Boolean get() = total > 0 && paginasComTexto.isEmpty()

    /** Umas folhas trazem texto e outras não. Aqui quem lê é que escolhe. */
    val mistura: Boolean get() = paginasSemTexto.isNotEmpty() && paginasComTexto.isNotEmpty()

    /**
     * Vale a pena propor o reconhecimento sem ninguém o pedir?
     *
     * Só abaixo de [LIMIAR_TEXTO]. Um documento escrito com duas folhas de
     * fotografias no meio não deve pôr-se a perguntar nada — quem quiser
     * reconhecer essas duas vai buscá-lo ao menu.
     */
    val propor: Boolean get() = !nadaAFazer && percentagemComTexto < LIMIAR_TEXTO

    /** As folhas que o reconhecimento vai percorrer, pela ordem do documento. */
    fun paginasA(alcance: AlcanceDoOcr): List<Int> = when (alcance) {
        AlcanceDoOcr.SO_AS_QUE_FALTAM -> paginasSemTexto
        AlcanceDoOcr.TODAS -> (paginasSemTexto + paginasComTexto).sorted()
    }
}

/**
 * Até onde vai o reconhecimento quando o documento é uma mistura.
 *
 * [TODAS] existe porque a camada de texto de um scan mal feito às vezes passa
 * dos 120 caracteres e mesmo assim é lixo — letras trocadas, palavras coladas.
 * Nesse caso quem lê prefere mandar reconhecer o documento inteiro.
 */
enum class AlcanceDoOcr { SO_AS_QUE_FALTAM, TODAS }

/** Lê as folhas tal como saíram do PDF e diz o que há para fazer. */
fun planearOcr(paginas: List<PageText>): PlanoDeOcr {
    val sem = ArrayList<Int>()
    val com = ArrayList<Int>()
    for (p in paginas) (if (p.temTexto) com else sem) += p.numero
    return PlanoDeOcr(paginasSemTexto = sem, paginasComTexto = com)
}

/**
 * Junta o que o OCR leu ao que já estava no PDF.
 *
 * Uma folha reconhecida substitui a folha original por inteiro — não se
 * costuram os dois textos, que daria cabeçalhos a dobrar. Folhas que o
 * reconhecimento não tocou ficam exactamente como estavam, e a ordem das
 * folhas é sempre a do documento, nunca a ordem por que foram reconhecidas.
 *
 * Reconhecimento que devolveu folha em branco não conta: mais vale ficar com
 * a camada fina do scan do que trocá-la por nada.
 */
fun fundirPaginas(originais: List<PageText>, reconhecidas: Map<Int, String>): List<PageText> =
    originais.map { pagina ->
        val novo = reconhecidas[pagina.numero]
        if (novo.isNullOrBlank()) pagina else PageText(pagina.numero, novo)
    }

/**
 * Volta a montar as folhas a partir dos parágrafos guardados.
 *
 * O documento aberto guarda parágrafos, não folhas — é por parágrafos que a
 * voz anda. Mas para saber o que falta reconhecer é preciso olhar folha a
 * folha, e uma folha que não trouxe texto nenhum não deixou parágrafo nenhum
 * para trás. Daí percorrer 1..[totalPaginas] e não a lista de parágrafos: as
 * folhas em falta são precisamente as que interessam ao OCR.
 */
fun paginasDe(totalPaginas: Int, paragrafos: List<pt.docvoice.text.Paragraph>): List<PageText> {
    val porFolha = paragrafos.groupBy { it.pagina }
    return (1..totalPaginas).map { n ->
        PageText(n, porFolha[n].orEmpty().joinToString("\n") { it.texto })
    }
}
