package pt.docvoice.ocr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pt.docvoice.pdf.RasterizadorDePaginas
import java.io.Closeable

/**
 * O [LeitorDeFolha] a sério: desenha a folha com o PdfRenderer e dá-a ao
 * Tesseract. Tudo dentro do telemóvel — ver [FicheirosDeIdioma].
 *
 * Nada aqui fala com a rede. O motor lê um ficheiro de idioma que veio dentro
 * da aplicação e escreve o resultado na memória; não há caminho nenhum para
 * fora, e é assim de propósito.
 */
class LeitorTesseract(
    resolver: ContentResolver,
    uri: Uri,
    private val caminhoDeDados: String,
    private val idioma: IdiomaOcr,
    private val dpi: Int = RasterizadorDePaginas.DPI_NORMAL
) : LeitorDeFolha, Closeable {

    private val rasterizador = RasterizadorDePaginas(resolver, uri)

    private val motor: TessBaseAPI by lazy {
        TessBaseAPI().also { api ->
            if (!api.init(caminhoDeDados, idioma.codigo)) {
                api.recycle()
                error("o motor de leitura não arrancou com o idioma ${idioma.codigo}")
            }
            // Folha inteira com detecção de blocos: é o que serve para uma
            // página de processo, que tem cabeçalho, corpo e às vezes tabela.
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
        }
    }

    override suspend fun ler(pagina: Int): String = withContext(Dispatchers.Default) {
        val imagem: Bitmap = rasterizador.desenhar(pagina, dpi) ?: return@withContext ""
        try {
            motor.setImage(imagem)
            motor.utF8Text ?: ""
        } finally {
            // 34 MB por folha: devolver já, não à espera do recolector.
            motor.clear()
            imagem.recycle()
        }
    }

    /**
     * Interrompe a folha que está a ser lida neste momento.
     *
     * Chamado de outra linha de execução quando alguém carrega em parar: sem
     * isto, cancelar a tarefa só tem efeito **entre** folhas, e uma folha densa
     * pode demorar dezenas de segundos a acabar. Com isto, pára agora.
     */
    fun interromper() {
        runCatching { motor.stop() }
    }

    override fun close() {
        runCatching { motor.recycle() }
        runCatching { rasterizador.close() }
        Log.i("DocVoice", "motor de leitura fechado")
    }
}
