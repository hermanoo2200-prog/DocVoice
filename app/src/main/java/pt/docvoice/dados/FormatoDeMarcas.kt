package pt.docvoice.dados

import pt.docvoice.text.Marca

/**
 * Como as marcas ficam escritas em ficheiro.
 *
 * Mesma ideia do [FormatoDeTexto], e pela mesma razão: a nota é escrita por
 * quem ouve, pode ter aspas, barras e quebras de linha, e uma escapatória mal
 * feita estraga-a em silêncio. Aqui a nota vem anunciada pelo comprimento e a
 * seguir vem crua — não há nada para escapar, logo não há nada para enganar.
 *
 * Formato:
 *     DOCVOICEMARCAS1
 *     <quantas>
 *     <pagina> <indiceNaPagina> <quando> <comprimento da nota>
 *     <nota>
 *     ... (repete)
 */
object FormatoDeMarcas {

    private const val CABECALHO = "DOCVOICEMARCAS1"

    fun escrever(marcas: List<Marca>): String {
        val sb = StringBuilder()
        sb.append(CABECALHO).append('\n')
        sb.append(marcas.size).append('\n')
        for (m in marcas) {
            sb.append(m.pagina).append(' ')
                .append(m.indiceNaPagina).append(' ')
                .append(m.quando).append(' ')
                .append(m.nota.length).append('\n')
            sb.append(m.nota).append('\n')
        }
        return sb.toString()
    }

    /** Null a qualquer sinal de ficheiro truncado ou estragado. */
    fun ler(cru: String): List<Marca>? = runCatching {
        var i = 0
        fun linha(): String {
            val fim = cru.indexOf('\n', i)
            if (fim < 0) error("ficheiro truncado")
            val s = cru.substring(i, fim)
            i = fim + 1
            return s
        }

        if (linha() != CABECALHO) return null
        val quantas = linha().toInt()
        if (quantas < 0) return null

        val marcas = ArrayList<Marca>(quantas)
        repeat(quantas) {
            val partes = linha().split(' ')
            if (partes.size != 4) error("cabeçalho de marca estragado")
            val pagina = partes[0].toInt()
            val indice = partes[1].toInt()
            val quando = partes[2].toLong()
            val comprimento = partes[3].toInt()
            if (i + comprimento > cru.length) error("ficheiro truncado")
            val nota = cru.substring(i, i + comprimento)
            i += comprimento
            if (i >= cru.length || cru[i] != '\n') error("comprimento não bate certo")
            i++
            marcas += Marca(pagina, indice, nota, quando)
        }
        marcas
    }.getOrNull()
}
