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
 *     DOCVOICE2
 *     <totalPaginas>
 *     <paginasComTexto>
 *     <idioma do OCR, ou «-» se ninguém reconheceu nada>
 *     <folhas reconhecidas, separadas por vírgula; linha vazia se nenhuma>
 *     <quantos parágrafos>
 *     <pagina> <indiceNaPagina> <comprimento em caracteres>
 *     <texto>
 *     ... (repete)
 *
 * As duas linhas do OCR entraram no passo 6. Servem para duas coisas: não
 * voltar a reconhecer o que já foi reconhecido, e saber em que idioma foi —
 * um documento lido como português pode ter de se repetir em russo, e sem o
 * saber não há como oferecer isso a quem lê.
 *
 * Ficheiros DOCVOICE1, escritos antes do passo 6, continuam a ler-se: dão
 * idioma nenhum e nenhuma folha reconhecida, que é a verdade sobre eles.
 */
object FormatoDeTexto {

    private const val CABECALHO = "DOCVOICE2"
    private const val CABECALHO_ANTIGO = "DOCVOICE1"
    private const val SEM_IDIOMA = "-"

    data class TextoGuardado(
        val totalPaginas: Int,
        val paginasComTexto: Int,
        val paragrafos: List<Paragraph>,
        /** Código do Tesseract — «por», «rus» — ou null se nunca houve OCR. */
        val idiomaOcr: String? = null,
        /** Folhas que saíram do reconhecimento, não do PDF. */
        val paginasReconhecidas: Set<Int> = emptySet()
    )

    fun escrever(guardado: TextoGuardado): String {
        val sb = StringBuilder()
        sb.append(CABECALHO).append('\n')
        sb.append(guardado.totalPaginas).append('\n')
        sb.append(guardado.paginasComTexto).append('\n')
        sb.append(guardado.idiomaOcr ?: SEM_IDIOMA).append('\n')
        sb.append(guardado.paginasReconhecidas.sorted().joinToString(",")).append('\n')
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

        val versao = linha()
        if (versao != CABECALHO && versao != CABECALHO_ANTIGO) return null
        val totalPaginas = linha().toInt()
        val paginasComTexto = linha().toInt()

        var idiomaOcr: String? = null
        var reconhecidas: Set<Int> = emptySet()
        if (versao == CABECALHO) {
            idiomaOcr = linha().let { if (it == SEM_IDIOMA || it.isBlank()) null else it }
            reconhecidas = linha()
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .toSet()
        }

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
        TextoGuardado(totalPaginas, paginasComTexto, paragrafos, idiomaOcr, reconhecidas)
    }.getOrNull()
}
