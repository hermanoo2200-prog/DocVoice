package pt.docvoice.dados

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.docvoice.text.Paragraph

class FormatoDeTextoTest {

    private val exemplo = FormatoDeTexto.TextoGuardado(
        totalPaginas = 3,
        paginasComTexto = 2,
        paragrafos = listOf(
            Paragraph(1, 0, "Nos termos do artigo 340.º do Código de Processo Penal, o tribunal ordena."),
            Paragraph(1, 1, "O valor foi fixado em 15.000 euros — «adequado», lê-se a fls. 1240."),
            Paragraph(2, 0, "Linha com \"aspas\", barra \\ e chaveta } no meio."),
            Paragraph(3, 0, "Texto com\nquebra de linha lá dentro.")
        )
    )

    @Test
    fun `o que se escreve volta igual`() {
        val lido = FormatoDeTexto.ler(FormatoDeTexto.escrever(exemplo))
        assertEquals(exemplo, lido)
    }

    @Test
    fun `aspas, barras e quebras de linha sobrevivem`() {
        val lido = FormatoDeTexto.ler(FormatoDeTexto.escrever(exemplo))!!
        assertEquals("Texto com\nquebra de linha lá dentro.", lido.paragrafos[3].texto)
        assertEquals("Linha com \"aspas\", barra \\ e chaveta } no meio.", lido.paragrafos[2].texto)
    }

    @Test
    fun `página e índice não se perdem`() {
        val lido = FormatoDeTexto.ler(FormatoDeTexto.escrever(exemplo))!!
        assertEquals(listOf(1 to 0, 1 to 1, 2 to 0, 3 to 0),
            lido.paragrafos.map { it.pagina to it.indiceNaPagina })
    }

    @Test
    fun `documento sem parágrafos nenhuns`() {
        val vazio = FormatoDeTexto.TextoGuardado(0, 0, emptyList())
        assertEquals(vazio, FormatoDeTexto.ler(FormatoDeTexto.escrever(vazio)))
    }

    @Test
    fun `ficheiro truncado não devolve lixo`() {
        val inteiro = FormatoDeTexto.escrever(exemplo)
        assertNull(FormatoDeTexto.ler(inteiro.substring(0, inteiro.length / 2)))
    }

    @Test
    fun `ficheiro de outra coisa qualquer não passa`() {
        assertNull(FormatoDeTexto.ler("{\"isto\":\"é JSON\"}"))
        assertNull(FormatoDeTexto.ler(""))
    }

    @Test
    fun `comprimento mentiroso não faz rebentar`() {
        // Troca o comprimento anunciado do primeiro parágrafo por um disparate,
        // sem depender de quantas letras ele tem.
        val inteiro = FormatoDeTexto.escrever(exemplo)
        val real = exemplo.paragrafos[0].texto.length
        val estragado = inteiro.replaceFirst("1 0 $real\n", "1 0 99999\n")
        assertNull(FormatoDeTexto.ler(estragado))
    }

    @Test
    fun `comprimento a menos também não passa`() {
        val inteiro = FormatoDeTexto.escrever(exemplo)
        val real = exemplo.paragrafos[0].texto.length
        assertNull(FormatoDeTexto.ler(inteiro.replaceFirst("1 0 $real\n", "1 0 3\n")))
    }

    @Test
    fun `um parágrafo muito grande aguenta`() {
        val grande = FormatoDeTexto.TextoGuardado(
            1, 1, listOf(Paragraph(1, 0, "á".repeat(200_000)))
        )
        assertEquals(grande, FormatoDeTexto.ler(FormatoDeTexto.escrever(grande)))
    }
}

// ── passo 6: OCR ────────────────────────────────────────────────────────────

class FormatoDeTextoOcrTest {

    private val paragrafos = listOf(
        Paragraph(1, 0, "Primeira folha, com vírgulas, «aspas» e\nquebra de linha."),
        Paragraph(2, 0, "Segunda folha.")
    )

    @Test
    fun `idioma e folhas reconhecidas sobrevivem a ida e volta`() {
        val original = FormatoDeTexto.TextoGuardado(
            totalPaginas = 2,
            paginasComTexto = 2,
            paragrafos = paragrafos,
            idiomaOcr = "rus",
            paginasReconhecidas = setOf(2, 1)
        )
        val lido = FormatoDeTexto.ler(FormatoDeTexto.escrever(original))
        assertEquals("rus", lido!!.idiomaOcr)
        assertEquals(setOf(1, 2), lido.paginasReconhecidas)
        assertEquals(paragrafos, lido.paragrafos)
        assertEquals(2, lido.totalPaginas)
    }

    @Test
    fun `documento sem ocr guarda idioma nenhum`() {
        val original = FormatoDeTexto.TextoGuardado(2, 2, paragrafos)
        val lido = FormatoDeTexto.ler(FormatoDeTexto.escrever(original))
        assertNull(lido!!.idiomaOcr)
        assertTrue(lido.paginasReconhecidas.isEmpty())
    }

    @Test
    fun `ficheiro antigo do passo 5 continua a ler-se`() {
        // Escrito à mão no formato DOCVOICE1, como está nos telemóveis que já têm a aplicação.
        val texto = "Olá."
        val antigo = "DOCVOICE1\n3\n3\n1\n1 0 ${texto.length}\n$texto\n"
        val lido = FormatoDeTexto.ler(antigo)
        assertNotNull(lido)
        assertEquals(3, lido!!.totalPaginas)
        assertNull(lido.idiomaOcr)
        assertTrue(lido.paginasReconhecidas.isEmpty())
        assertEquals(1, lido.paragrafos.size)
    }

    @Test
    fun `cabecalho desconhecido continua a dar null`() {
        assertNull(FormatoDeTexto.ler("DOCVOICE9\n1\n1\n-\n\n0\n"))
    }
}
