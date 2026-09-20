package pt.docvoice.ocr

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pt.docvoice.text.PageText
import java.io.File

/**
 * O passo 6 a trabalhar a sério, no aparelho.
 *
 * Os testes da JVM provam a contabilidade — que folhas reconhecer, o que
 * juntar, o que guardar. O que eles não podem provar é que o motor arranca,
 * que lê o que lá está escrito e quanto tempo demora, porque o motor é código
 * nativo. Isso é aqui.
 *
 * O documento de prova (`scan-prova.pdf`) são duas folhas feitas de raiz para
 * isto: uma sentença inventada em português, uma decisão inventada em russo,
 * ambas desenhadas como imagem com ruído e um grau de inclinação, como sai de
 * um digitalizador. Não há nada de real nem de ninguém lá dentro.
 */
@RunWith(AndroidJUnit4::class)
class OcrNoAparelhoTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var pdf: Uri
    private lateinit var ficheiros: FicheirosDeIdioma

    @Before
    fun preparar() {
        val destino = File(ctx.cacheDir, "scan-prova.pdf")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("scan-prova.pdf").use { entrada ->
                destino.outputStream().use { entrada.copyTo(it) }
            }
        pdf = Uri.fromFile(destino)
        ficheiros = FicheirosDeIdioma(ctx)
    }

    @Test
    fun os_ficheiros_de_idioma_vem_dentro_da_aplicacao() = runBlocking {
        val prontos = ficheiros.preparar()
        assertTrue("português não ficou pronto", IdiomaOcr.PORTUGUES in prontos)
        assertTrue("russo não ficou pronto", IdiomaOcr.RUSSO in prontos)
        assertTrue("ficheiros de idioma vazios", ficheiros.tamanho() > 1_000_000)
    }

    @Test
    fun o_pdf_de_prova_nao_traz_camada_de_texto_nenhuma() = runBlocking {
        val paginas = pt.docvoice.pdf.PdfTextExtractor(ctx.contentResolver).extrair(pdf) { _, _ -> }
        assertEquals(2, paginas.size)
        val plano = planearOcr(paginas)
        assertTrue("devia ser digitalização de ponta a ponta", plano.digitalizacaoInteira)
        assertTrue(plano.propor)
    }

    @Test
    fun a_folha_desenha_se_em_imagem() {
        pt.docvoice.pdf.RasterizadorDePaginas(ctx.contentResolver, pdf).use { r ->
            assertEquals(2, r.totalPaginas)
            val imagem = r.desenhar(1)!!
            assertTrue("imagem pequena demais: ${imagem.width}x${imagem.height}",
                imagem.width > 1500 && imagem.height > 2000)
            imagem.recycle()
            assertEquals(null, r.desenhar(99))
        }
    }

    @Test
    fun le_a_folha_portuguesa_e_diz_quanto_demorou() = runBlocking {
        ficheiros.preparar()
        LeitorTesseract(ctx.contentResolver, pdf, ficheiros.caminhoDeDados, IdiomaOcr.PORTUGUES)
            .use { leitor ->
                val antes = System.currentTimeMillis()
                val texto = leitor.ler(1)
                val demorou = System.currentTimeMillis() - antes
                println("OCR português: folha 1 em $demorou ms, ${texto.length} caracteres")

                val achatado = texto.uppercase()
                for (palavra in listOf("TRIBUNAL", "SENTEN", "RELAT", "CONTRATO")) {
                    assertTrue("não leu «$palavra». Saiu: ${texto.take(400)}",
                        achatado.contains(palavra))
                }
                assertTrue("folha reconhecida devia contar como folha com texto",
                    PageText(1, texto).temTexto)
            }
    }

    @Test
    fun le_a_folha_russa() = runBlocking {
        ficheiros.preparar()
        LeitorTesseract(ctx.contentResolver, pdf, ficheiros.caminhoDeDados, IdiomaOcr.RUSSO)
            .use { leitor ->
                val antes = System.currentTimeMillis()
                val texto = leitor.ler(2)
                println("OCR russo: folha 2 em ${System.currentTimeMillis() - antes} ms, " +
                    "${texto.length} caracteres")

                val achatado = texto.uppercase()
                for (palavra in listOf("СУД", "РЕШЕНИЕ", "ДОГОВОР")) {
                    assertTrue("не прочитал «$palavra». Вышло: ${texto.take(400)}",
                        achatado.contains(palavra))
                }
                assertTrue(PageText(2, texto).temTexto)
            }
    }

    @Test
    fun o_documento_inteiro_passa_pelo_reconhecimento_com_progresso() = runBlocking {
        ficheiros.preparar()
        val lido = LinkedHashMap<Int, String>()
        val progressos = mutableListOf<ProgressoDeOcr>()
        val antes = System.currentTimeMillis()
        LeitorTesseract(ctx.contentResolver, pdf, ficheiros.caminhoDeDados, IdiomaOcr.PORTUGUES)
            .use { leitor ->
                reconhecer(listOf(1, 2), leitor) { pagina, texto, progresso ->
                    lido[pagina] = texto
                    progressos += progresso
                }
            }
        val total = System.currentTimeMillis() - antes
        println("OCR: 2 folhas em $total ms (${total / 2} ms por folha)")

        assertEquals(listOf(1, 2), progressos.map { it.paginaActual })
        assertEquals(listOf(1, 2), progressos.map { it.feitas })
        assertEquals(100, progressos.last().percentagem)

        // E o que saiu junta-se ao documento como deve ser.
        val originais = listOf(PageText(1, ""), PageText(2, ""))
        val fundidas = fundirPaginas(originais, lido)
        assertTrue(fundidas.all { it.temTexto })
        assertTrue(planearOcr(fundidas).nadaAFazer)
    }
}
