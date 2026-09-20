package pt.docvoice.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.Closeable
import kotlin.math.max

/**
 * Desenha folhas do PDF em imagem, para dar ao OCR.
 *
 * O PdfBox extrai o texto que lá está; quando não está lá nenhum, é o
 * [PdfRenderer] do sistema que desenha a fotografia da folha. São duas
 * ferramentas para dois trabalhos — era isto que estava anotado para o passo 6.
 *
 * Cuidados que não são detalhe:
 *
 *  - O PdfRenderer só deixa ter **uma folha aberta de cada vez** e não gosta de
 *    duas linhas de execução ao mesmo tempo. Por isso esta classe abre, desenha
 *    e fecha dentro da mesma chamada, e quem a usa usa-a de uma linha só.
 *  - Uma folha A4 a 300 pontos por polegada são 2480 x 3508 pixels. Em
 *    ARGB_8888 — o único formato que o render aceita — isso são 34 MB **por
 *    folha**. Num telemóvel antigo, que é o aparelho que importa aqui, isso
 *    chega para deitar a aplicação abaixo. Daí o tecto em [LADO_MAXIMO]: a
 *    resolução desce sozinha antes de haver problema, em vez de rebentar.
 *  - Quem chama **tem de** chamar `recycle()` na imagem mal acabe de a ler.
 */
class RasterizadorDePaginas(
    private val resolver: ContentResolver,
    private val uri: Uri
) : Closeable {

    private var descritor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    val totalPaginas: Int get() = abrir().pageCount

    private fun abrir(): PdfRenderer {
        renderer?.let { return it }
        val fd = resolver.openFileDescriptor(uri, "r")
            ?: error("o sistema não devolveu o ficheiro")
        descritor = fd
        return PdfRenderer(fd).also { renderer = it }
    }

    /**
     * Desenha a folha [numero] (1 é a primeira, como a folha do processo).
     * Devolve null se a folha não existir.
     */
    fun desenhar(numero: Int, pontosPorPolegada: Int = DPI_NORMAL): Bitmap? {
        val r = abrir()
        if (numero < 1 || numero > r.pageCount) return null
        r.openPage(numero - 1).use { folha ->
            // O PDF mede-se em pontos: 72 por polegada.
            var escala = pontosPorPolegada / 72f
            val maior = max(folha.width, folha.height) * escala
            if (maior > LADO_MAXIMO) {
                escala *= LADO_MAXIMO / maior
                Log.i("DocVoice", "folha $numero: resolução baixada para caber na memória")
            }
            val largura = max(1, (folha.width * escala).toInt())
            val altura = max(1, (folha.height * escala).toInt())

            val imagem = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
            // Fundo branco: o render desenha por cima e deixa transparente o
            // que a folha não pinta. Transparente, para o OCR, é preto.
            imagem.eraseColor(Color.WHITE)
            folha.render(imagem, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return imagem
        }
    }

    override fun close() {
        runCatching { renderer?.close() }
        runCatching { descritor?.close() }
        renderer = null
        descritor = null
    }

    companion object {
        /** O que o Tesseract quer para texto impresso. Abaixo disto erra muito. */
        const val DPI_NORMAL = 300

        /** Telemóvel antigo: menos resolução vale mais que aplicação fechada. */
        const val DPI_POUPADO = 200

        /**
         * Tecto do lado maior, em pixels. 3500 x 2475 em ARGB_8888 = 34 MB,
         * que é o limite do que se pede a um aparelho de 2 GB com a voz a
         * trabalhar ao lado.
         */
        const val LADO_MAXIMO = 3500f

        /** Quantos megabytes uma folha vai ocupar. Serve para decidir o DPI. */
        fun megabytes(largura: Int, altura: Int): Int =
            (largura.toLong() * altura * 4 / (1024 * 1024)).toInt()

        /** Resolução a usar consoante a memória que a aplicação ainda tem. */
        fun dpiPara(memoriaLivreMb: Long): Int =
            if (memoriaLivreMb < 96) DPI_POUPADO else DPI_NORMAL
    }
}
