package pt.docvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import pt.docvoice.dados.RegistoDocumento

class PosicaoGuardadaTest {

    /** Três folhas: 2 + 3 + 2 parágrafos. */
    private val paragrafos = listOf(
        Paragraph(1, 0, "a"), Paragraph(1, 1, "b"),
        Paragraph(2, 0, "c"), Paragraph(2, 1, "d"), Paragraph(2, 2, "e"),
        Paragraph(3, 0, "f"), Paragraph(3, 1, "g")
    )

    @Test
    fun `encontra a posicao exacta`() {
        assertEquals(3, indiceDeParagrafo(paragrafos, pagina = 2, indiceNaPagina = 1))
        assertEquals(0, indiceDeParagrafo(paragrafos, pagina = 1, indiceNaPagina = 0))
        assertEquals(6, indiceDeParagrafo(paragrafos, pagina = 3, indiceNaPagina = 1))
    }

    @Test
    fun `parágrafo desapareceu — cai no inicio da mesma folha`() {
        // o corte mudou e a folha 2 passou a ter menos parágrafos
        assertEquals(2, indiceDeParagrafo(paragrafos, pagina = 2, indiceNaPagina = 9))
    }

    @Test
    fun `folha inexistente — volta ao principio`() {
        assertEquals(0, indiceDeParagrafo(paragrafos, pagina = 40, indiceNaPagina = 0))
    }

    @Test
    fun `lista vazia nao rebenta`() {
        assertEquals(0, indiceDeParagrafo(emptyList(), pagina = 2, indiceNaPagina = 1))
    }

    @Test
    fun `o mesmo ficheiro de dois sitios tem a mesma chave`() {
        fun registo(uri: String) = RegistoDocumento(
            uri = uri, nome = "sentenca.pdf", tamanho = 482_331L, totalPaginas = 40,
            totalParagrafos = 300, pagina = 1, indiceNaPagina = 0,
            indiceCorrido = 0, quando = 0L
        )
        val doFicheiros = registo("content://com.android.externalstorage/documento/9")
        val doDrive = registo("content://com.google.android.apps.docs/documento/abc")
        assertEquals(doFicheiros.chave, doDrive.chave)

        val outroTamanho = doDrive.copy(tamanho = 999L)
        assertNotEquals(doFicheiros.chave, outroTamanho.chave)
    }

    @Test
    fun `percentagem lida`() {
        fun registo(indice: Int, total: Int) = RegistoDocumento(
            uri = "content://x", nome = "d.pdf", tamanho = 1234L, totalPaginas = 40,
            totalParagrafos = total, pagina = 1, indiceNaPagina = 0,
            indiceCorrido = indice, quando = 0L
        )
        assertEquals(0, registo(0, 0).percentagem)      // documento sem parágrafos
        assertEquals(1, registo(0, 100).percentagem)    // primeiro parágrafo de cem
        assertEquals(50, registo(49, 100).percentagem)
        assertEquals(100, registo(99, 100).percentagem)
    }
}
