package pt.docvoice.leitura

import android.net.Uri
import pt.docvoice.text.Paragraph
import pt.docvoice.text.indiceDeParagrafo

/** Limiar de digitalização: menos de 70% das páginas com texto = provável scan. */
const val LIMIAR_TEXTO = 70

data class Documento(
    val nome: String,
    val uri: Uri,
    /** Bytes do ficheiro. Com o nome, é o que identifica o documento — ver RegistoDocumento. */
    val tamanho: Long,
    val totalPaginas: Int,
    val paginasComTexto: Int,
    val paragrafos: List<Paragraph>
) {
    val percentagemComTexto: Int
        get() = if (totalPaginas == 0) 0 else paginasComTexto * 100 / totalPaginas
    val provavelDigitalizacao: Boolean
        get() = percentagemComTexto < LIMIAR_TEXTO

    /**
     * Volta a encontrar a posição guardada. Se aquele parágrafo já não existir
     * — o corte mudou, o OCR acrescentou texto — cai no início da mesma folha,
     * que é onde a vista de quem lê se reorienta.
     */
    fun indiceDe(pagina: Int, indiceNaPagina: Int): Int =
        indiceDeParagrafo(paragrafos, pagina, indiceNaPagina)
}
