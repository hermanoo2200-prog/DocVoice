package pt.docvoice.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.docvoice.text.PageText

class PlanoDeOcrTest {

    /** Uma folha escrita a sério. */
    private fun escrita(n: Int) =
        PageText(n, "Nos termos do disposto no artigo 340.º do Código de Processo Penal, ".repeat(4))

    /** O que um scan traz por cima da fotografia: cabeçalho e número de folha. */
    private fun digitalizada(n: Int) =
        PageText(n, "Proc. 1234/25.7T8LSB   fls. $n")

    @Test
    fun `documento todo escrito nao tem nada a fazer`() {
        val plano = planearOcr((1..10).map { escrita(it) })
        assertTrue(plano.nadaAFazer)
        assertFalse(plano.propor)
        assertFalse(plano.digitalizacaoInteira)
        assertFalse(plano.mistura)
        assertEquals(100, plano.percentagemComTexto)
    }

    @Test
    fun `scan de ponta a ponta e digitalizacao inteira`() {
        val plano = planearOcr((1..10).map { digitalizada(it) })
        assertTrue(plano.digitalizacaoInteira)
        assertTrue(plano.propor)
        assertFalse(plano.mistura)
        assertEquals(0, plano.percentagemComTexto)
        assertEquals((1..10).toList(), plano.paginasSemTexto)
    }

    @Test
    fun `parte das folhas com texto da uma mistura`() {
        val paginas = (1..10).map { if (it <= 4) escrita(it) else digitalizada(it) }
        val plano = planearOcr(paginas)
        assertTrue(plano.mistura)
        assertEquals(40, plano.percentagemComTexto)
        assertEquals(listOf(1, 2, 3, 4), plano.paginasComTexto)
        assertEquals(listOf(5, 6, 7, 8, 9, 10), plano.paginasSemTexto)
    }

    @Test
    fun `duas fotografias num documento escrito nao se propoem sozinhas`() {
        // 8 em 10 = 80%, acima do limiar: há o que reconhecer, mas não se pergunta.
        val paginas = (1..10).map { if (it <= 8) escrita(it) else digitalizada(it) }
        val plano = planearOcr(paginas)
        assertTrue(plano.mistura)
        assertFalse(plano.propor)
        assertEquals(listOf(9, 10), plano.paginasSemTexto)
    }

    @Test
    fun `mesmo no limiar de setenta por cento ainda se propoe`() {
        // 69% propõe, 70% já não: o limiar é «abaixo de», não «até».
        val a69 = planearOcr((1..100).map { if (it <= 69) escrita(it) else digitalizada(it) })
        assertTrue(a69.propor)
        val a70 = planearOcr((1..100).map { if (it <= 70) escrita(it) else digitalizada(it) })
        assertFalse(a70.propor)
    }

    @Test
    fun `o alcance escolhe que folhas percorrer`() {
        val paginas = (1..6).map { if (it % 2 == 0) escrita(it) else digitalizada(it) }
        val plano = planearOcr(paginas)
        assertEquals(listOf(1, 3, 5), plano.paginasA(AlcanceDoOcr.SO_AS_QUE_FALTAM))
        assertEquals(listOf(1, 2, 3, 4, 5, 6), plano.paginasA(AlcanceDoOcr.TODAS))
    }

    @Test
    fun `documento vazio nao rebenta nem propoe nada`() {
        val plano = planearOcr(emptyList())
        assertEquals(0, plano.total)
        assertEquals(0, plano.percentagemComTexto)
        assertTrue(plano.nadaAFazer)
        assertFalse(plano.propor)
        assertFalse(plano.digitalizacaoInteira)
    }

    @Test
    fun `fundir substitui a folha inteira e guarda a ordem do documento`() {
        val originais = listOf(digitalizada(1), escrita(2), digitalizada(3))
        // Chega fora de ordem, como pode sair de um reconhecimento cancelado e retomado.
        val lido = mapOf(3 to "folha três reconhecida", 1 to "folha um reconhecida")
        val fundido = fundirPaginas(originais, lido)

        assertEquals(listOf(1, 2, 3), fundido.map { it.numero })
        assertEquals("folha um reconhecida", fundido[0].texto)
        assertEquals(originais[1].texto, fundido[1].texto)   // não foi tocada
        assertEquals("folha três reconhecida", fundido[2].texto)
    }

    @Test
    fun `reconhecimento em branco nao apaga o que ja la estava`() {
        val originais = listOf(digitalizada(1))
        val fundido = fundirPaginas(originais, mapOf(1 to "   \n "))
        assertEquals(originais[0].texto, fundido[0].texto)
    }

    @Test
    fun `folhas reconhecidas que nao existem no documento sao ignoradas`() {
        val originais = listOf(digitalizada(1), digitalizada(2))
        val fundido = fundirPaginas(originais, mapOf(2 to "dois", 99 to "folha que não existe"))
        assertEquals(2, fundido.size)
        assertEquals("dois", fundido[1].texto)
    }

    @Test
    fun `os codigos de idioma sao os do tesseract`() {
        assertEquals("por", IdiomaOcr.PORTUGUES.codigo)
        assertEquals("rus", IdiomaOcr.RUSSO.codigo)
        assertEquals(IdiomaOcr.RUSSO, IdiomaOcr.porCodigo("rus"))
        assertEquals(null, IdiomaOcr.porCodigo("eng"))
    }
}

class PaginasDeTest {

    private fun p(pagina: Int, indice: Int, texto: String) =
        pt.docvoice.text.Paragraph(pagina, indice, texto)

    @Test
    fun `folha sem paragrafos nenhuns aparece na mesma, vazia`() {
        // A folha 2 é uma fotografia: não deixou parágrafo nenhum.
        val paragrafos = listOf(p(1, 0, "Primeira."), p(3, 0, "Terceira."))
        val folhas = paginasDe(3, paragrafos)

        assertEquals(listOf(1, 2, 3), folhas.map { it.numero })
        assertEquals("", folhas[1].texto)
        assertFalse(folhas[1].temTexto)
    }

    @Test
    fun `os paragrafos de uma folha voltam a juntar-se pela ordem`() {
        val paragrafos = listOf(p(1, 0, "Um."), p(1, 1, "Dois."), p(1, 2, "Três."))
        assertEquals("Um.\nDois.\nTrês.", paginasDe(1, paragrafos).single().texto)
    }

    @Test
    fun `plano feito a partir de paragrafos ve as folhas em falta`() {
        val corpo = "Nos termos do disposto no artigo 340.º do Código de Processo Penal, ".repeat(4)
        val paragrafos = listOf(p(1, 0, corpo), p(4, 0, corpo))
        val plano = planearOcr(paginasDe(5, paragrafos))

        assertEquals(listOf(1, 4), plano.paginasComTexto)
        assertEquals(listOf(2, 3, 5), plano.paginasSemTexto)
        assertTrue(plano.propor)
    }

    @Test
    fun `documento sem folhas nenhumas da lista vazia`() {
        assertTrue(paginasDe(0, emptyList()).isEmpty())
    }
}
