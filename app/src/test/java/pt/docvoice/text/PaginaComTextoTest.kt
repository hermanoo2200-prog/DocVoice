package pt.docvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginaComTextoTest {

    @Test
    fun `camada fina de um scan nao conta como pagina com texto`() {
        // o que um scan traz mesmo quando a página é uma fotografia:
        val cabecalho = PageText(3, "Proc. 1234/25.7T8LSB   fls. 128   Tribunal Judicial da Comarca de Lisboa")
        assertFalse(cabecalho.temTexto)
        assertTrue(cabecalho.caracteresUteis > 0)  // pela regra antiga, contava
    }

    @Test
    fun `numero de folha sozinho nao conta`() {
        assertFalse(PageText(1, "128").temTexto)
        assertFalse(PageText(1, "  \n \n ").temTexto)
    }

    @Test
    fun `pagina escrita conta`() {
        val pagina = PageText(1, "Nos termos do disposto no artigo 340.º do Código de Processo Penal, ".repeat(4))
        assertTrue(pagina.temTexto)
    }

    @Test
    fun `o limiar e contado em letras e algarismos`() {
        val soPontuacao = PageText(1, ".".repeat(400))
        assertEquals(0, soPontuacao.caracteresUteis)
        assertFalse(soPontuacao.temTexto)

        val mesmoNoLimiar = PageText(1, "a".repeat(MINIMO_DE_TEXTO_POR_PAGINA))
        assertTrue(mesmoNoLimiar.temTexto)
        assertFalse(PageText(1, "a".repeat(MINIMO_DE_TEXTO_POR_PAGINA - 1)).temTexto)
    }
}
