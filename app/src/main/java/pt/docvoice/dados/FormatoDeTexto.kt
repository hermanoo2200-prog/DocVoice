package pt.docvoice.dados

import pt.docvoice.text.Paragraph

/**
 * Como o texto já extraído fica escrito em ficheiro.
 *
 * Sem JSON: o texto de um processo traz aspas, barras, quebras de linha e
 * acentos, e uma escapatória mal feita estraga o documento em silêncio. Aqui
 * cada parágrafo é anunciado pelo seu comprimento e a seguir vem cru — não há
 * nada para escapar, logo não há nada para enganar.
 *
 * Puro Kotlin, sem Android: dá para testar na JVM.
 *
 * Formato:
 *     DOCVOICE1
 *     <totalPaginas>
 *     <paginasComTexto>
 *     <quantos parágrafos>
 *     <pagina> <indiceNaPagina> <comprimento em caracteres>
 *     <texto>
 *     ... (repete)
 */
object FormatoDeTexto {

    private const val CABECALHO = "DOCVOICE1"

    data class TextoGuardado(
        val totalPaginas: Int,
        val paginasComTexto: Int,
        val paragrafos: List<Paragraph>
    )

    fun escrever(guardado: TextoGuardado): String {
        val sb = StringBuilder()
        sb.append(CABECALHO).append('\n')
        sb.append(guardado.totalPaginas).append('\n')
        sb.append(guardado.paginasComTexto).append('\n')
        sb.append(guardado.paragrafos.size).append('\n')
        for (p in guardado.paragrafos) {
            sb.append(p.pagina).append(' ')
                .append(p.indiceNaPagina).append(' ')
                .append(p.texto.length).append('\n')
            sb.append(p.texto).append('\n')
        }
        return sb.toString()
    }

    /** Devolve null a qualquer sinal de ficheiro truncado ou estragado. */
    fun ler(cru: String): TextoGuardado? = runCatching {
        var i = 0
        fun linha(): String {
            val fim = cru.indexOf('\n', i)
            if (fim < 0) error("ficheiro truncado")
            val s = cru.substring(i, fim)
            i = fim + 1
            return s
        }

        if (linha() != CABECALHO) return null
        val totalPaginas = linha().toInt()
        val paginasComTexto = linha().toInt()
        val quantos = linha().toInt()
        if (quantos < 0 || totalPaginas < 0) return null

        val paragrafos = ArrayList<Paragraph>(quantos)
        repeat(quantos) {
            val partes = linha().split(' ')
            if (partes.size != 3) error("cabeçalho de parágrafo estragado")
            val pagina = partes[0].toInt()
            val indice = partes[1].toInt()
            val comprimento = partes[2].toInt()
            if (i + comprimento > cru.length) error("ficheiro truncado")
            val texto = cru.substring(i, i + comprimento)
            i += comprimento
            if (i >= cru.length || cru[i] != '\n') error("comprimento não bate certo")
            i++
            paragrafos += Paragraph(pagina, indice, texto)
        }
        TextoGuardado(totalPaginas, paginasComTexto, paragrafos)
    }.getOrNull()
}
