package pt.docvoice.text

/**
 * Uma marca posta por quem ouve: «este parágrafo interessa».
 *
 * A posição guarda-se por folha e ordem dentro dela, exactamente como em
 * [Paragraph] — nunca por contagem corrida. É o que faz com que uma marca
 * posta antes do OCR continue no sítio certo depois de o OCR acrescentar
 * texto e o corte dos parágrafos mudar.
 *
 * A [nota] é opcional de propósito: marcar tem de ser um gesto só, a ouvir,
 * sem parar nada. Escrever vem depois, se vier.
 */
data class Marca(
    val pagina: Int,
    val indiceNaPagina: Int,
    val nota: String = "",
    /** Quando foi posta, em milissegundos. Serve para desempatar a ordem. */
    val quando: Long = 0L
) {
    val id: String get() = "$pagina:$indiceNaPagina"
}

/** Pela ordem do documento, que é a ordem por que se relê — não pela de marcação. */
fun List<Marca>.porOrdemDoDocumento(): List<Marca> =
    sortedWith(compareBy({ it.pagina }, { it.indiceNaPagina }))

/**
 * Põe ou tira a marca daquele parágrafo. O mesmo botão faz as duas coisas:
 * a ouvir, ninguém quer procurar qual é o botão de desmarcar.
 */
fun List<Marca>.alternar(pagina: Int, indiceNaPagina: Int, agora: Long): List<Marca> {
    val existe = any { it.pagina == pagina && it.indiceNaPagina == indiceNaPagina }
    return if (existe) filterNot { it.pagina == pagina && it.indiceNaPagina == indiceNaPagina }
    else this + Marca(pagina, indiceNaPagina, quando = agora)
}

/** Escreve a nota numa marca que já existe. Sem a marca, não faz nada. */
fun List<Marca>.comNota(pagina: Int, indiceNaPagina: Int, nota: String): List<Marca> =
    map { if (it.pagina == pagina && it.indiceNaPagina == indiceNaPagina) it.copy(nota = nota.trim()) else it }

/** Quantos caracteres do parágrafo chegam para reconhecer o sítio sem lá ir. */
const val PRINCIPIO_DA_MARCA = 140

/** O princípio do parágrafo, numa linha só e sem cortar a meio de uma palavra. */
fun principioDe(texto: String, limite: Int = PRINCIPIO_DA_MARCA): String {
    val numaLinha = texto.replace(Regex("\\s+"), " ").trim()
    if (numaLinha.length <= limite) return numaLinha
    val corte = numaLinha.lastIndexOf(' ', limite)
    return numaLinha.take(if (corte > limite / 2) corte else limite).trimEnd() + "…"
}

/**
 * A lista toda em texto, para o botão de copiar.
 *
 * Simples de propósito: isto vai parar a uma mensagem, a um documento ou a um
 * caderno, e o que serve lá é texto que se lê, não uma tabela que se desmancha.
 */
fun listaParaCopiar(
    nomeDoDocumento: String,
    marcas: List<Marca>,
    textoDoParagrafo: (Marca) -> String?
): String {
    val emOrdem = marcas.porOrdemDoDocumento()
    val sb = StringBuilder()
    sb.append(nomeDoDocumento).append('\n')
    sb.append("— ").append(emOrdem.size).append(emOrdem.size.let { if (it == 1) " marca" else " marcas" })
    sb.append('\n')
    emOrdem.forEachIndexed { i, m ->
        sb.append('\n').append(i + 1).append(". folha ").append(m.pagina).append('\n')
        val texto = textoDoParagrafo(m)
        if (!texto.isNullOrBlank()) sb.append(principioDe(texto)).append('\n')
        if (m.nota.isNotBlank()) sb.append("— ").append(m.nota).append('\n')
    }
    return sb.toString()
}
