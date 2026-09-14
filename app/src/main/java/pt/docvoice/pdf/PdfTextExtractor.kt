package pt.docvoice.pdf

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import pt.docvoice.text.PageText
import kotlin.coroutines.coroutineContext

/**
 * Extrai o texto, página a página, com o PdfBox-Android.
 * O PdfRenderer do sistema não extrai texto nenhum — só desenha a página em bitmap;
 * entra no passo 6, para dar imagem ao OCR.
 */
class PdfTextExtractor(private val resolver: ContentResolver) {

    suspend fun extrair(uri: Uri, progresso: (feitas: Int, total: Int) -> Unit): List<PageText> =
        withContext(Dispatchers.IO) {
            val arranque = System.currentTimeMillis()
            val entrada = resolver.openInputStream(uri)
                ?: error("o sistema não devolveu o ficheiro")
            entrada.use { fluxo ->
                PDDocument.load(fluxo).use { doc ->
                    val total = doc.numberOfPages
                    val paginas = ArrayList<PageText>(total)
                    val stripper = PDFTextStripper().apply { sortByPosition = true }
                    var maisLenta = 0L
                    var folhaLenta = 0
                    for (n in 1..total) {
                        coroutineContext.ensureActive()
                        val antes = System.currentTimeMillis()
                        stripper.startPage = n
                        stripper.endPage = n
                        paginas += PageText(n, stripper.getText(doc))
                        val demorou = System.currentTimeMillis() - antes
                        if (demorou > maisLenta) { maisLenta = demorou; folhaLenta = n }
                        // Só as folhas que se arrastam. Quantidade, nunca conteúdo.
                        if (demorou > 500) Log.i("DocVoice", "folha $n: $demorou ms")
                        progresso(n, total)
                    }
                    if (maisLenta > 0) Log.i(
                        "DocVoice", "folha mais lenta: $folhaLenta com $maisLenta ms"
                    )
                    // Quantas folhas e quanto tempo. Nunca o que lá está
                    // escrito: o registo do sistema é legível por outras
                    // aplicações, e o documento é de quem o escreveu.
                    Log.i(
                        "DocVoice",
                        "extraccao: $total folhas em ${System.currentTimeMillis() - arranque} ms"
                    )
                    paginas
                }
            }
        }
}
