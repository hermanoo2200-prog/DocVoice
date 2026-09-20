package pt.docvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.docvoice.dados.FormatoDeMarcas

class MarcaTest {

    @Test
    fun `o mesmo gesto poe e tira a marca`() {
        var marcas = emptyList<Marca>()
        marcas = marcas.alternar(12, 3, agora = 100)
        assertEquals(1, marcas.size)
        assertEquals(12, marcas[0].pagina)

        marcas = marcas.alternar(12, 3, agora = 200)
        assertTrue("segundo toque devia tirar a marca", marcas.isEmpty())
    }

    @Test
    fun `marcar dois paragrafos da mesma folha nao se confunde`() {
        val marcas = emptyList<Marca>()
            .alternar(12, 0, 100)
            .alternar(12, 1, 200)
        assertEquals(2, marcas.size)
        assertEquals(listOf(0, 1), marcas.map { it.indiceNaPagina })
    }

    @Test
    fun `a lista le-se pela ordem do documento, nao pela de marcacao`() {
        val marcas = emptyList<Marca>()
            .alternar(300, 2, 100)   // marcada primeiro
            .alternar(12, 5, 200)
            .alternar(12, 1, 300)
        assertEquals(
            listOf(12 to 1, 12 to 5, 300 to 2),
            marcas.porOrdemDoDocumento().map { it.pagina to it.indiceNaPagina }
        )
    }

    @Test
    fun `a nota escreve-se so numa marca que existe`() {
        val marcas = emptyList<Marca>().alternar(5, 0, 1)
        val comNota = marcas.comNota(5, 0, "  contradiz a folha 12  ")
        assertEquals("contradiz a folha 12", comNota[0].nota)

        // Parágrafo sem marca: não inventa nenhuma.
        assertEquals(comNota, comNota.comNota(99, 0, "seja o que for"))
    }

    @Test
    fun `o principio do paragrafo nao corta palavras a meio`() {
        val longo = "Nos termos do disposto no artigo trezentos e quarenta do Código de " +
            "Processo Penal, requer-se a inquirição da testemunha indicada, por se " +
            "mostrar essencial à descoberta da verdade material."
        val principio = principioDe(longo)
        assertTrue(principio.endsWith("…"))
        assertTrue(principio.length <= PRINCIPIO_DA_MARCA + 1)
        assertFalse("cortou a meio de uma palavra", principio.dropLast(1).endsWith(" "))
        assertTrue(longo.startsWith(principio.dropLast(1).trim()))
    }

    @Test
    fun `paragrafo curto sai inteiro e numa linha so`() {
        assertEquals("Olá mundo.", principioDe("  Olá\n  mundo.  "))
    }

    @Test
    fun `a lista copia-se como texto que se le`() {
        val marcas = emptyList<Marca>()
            .alternar(12, 0, 100)
            .alternar(40, 2, 200)
            .comNota(40, 2, "aqui está a contradição")
        val textos = mapOf(
            "12:0" to "A autora alegou que cumpriu pontualmente a sua parte.",
            "40:2" to "A re sustentou que o serviço não foi prestado."
        )
        val saida = listaParaCopiar("contrato.pdf", marcas) { textos[it.id] }

        assertTrue(saida.startsWith("contrato.pdf"))
        assertTrue(saida.contains("2 marcas"))
        assertTrue(saida.contains("1. folha 12"))
        assertTrue(saida.contains("2. folha 40"))
        assertTrue(saida.contains("A autora alegou"))
        assertTrue(saida.contains("— aqui está a contradição"))
        // A marca sem nota não deixa um travessão sozinho.
        assertFalse(saida.contains("— \n"))
    }

    @Test
    fun `uma marca so diz marca, nao marcas`() {
        val saida = listaParaCopiar("x.pdf", listOf(Marca(1, 0))) { "Texto." }
        assertTrue(saida.contains("1 marca\n"))
    }

    @Test
    fun `marcas vao e voltam do ficheiro com a nota intacta`() {
        val marcas = listOf(
            Marca(12, 0, "", 1_700_000_000_000),
            Marca(40, 2, "nota com \"aspas\", vírgulas e\nquebra de linha", 1_700_000_001_000)
        )
        val lido = FormatoDeMarcas.ler(FormatoDeMarcas.escrever(marcas))
        assertEquals(marcas, lido)
    }

    @Test
    fun `lista vazia vai e volta`() {
        assertEquals(emptyList<Marca>(), FormatoDeMarcas.ler(FormatoDeMarcas.escrever(emptyList())))
    }

    @Test
    fun `ficheiro estragado da null em vez de marcas a mentir`() {
        assertNull(FormatoDeMarcas.ler("QUALQUER COISA"))
        assertNull(FormatoDeMarcas.ler("DOCVOICEMARCAS1\n2\n1 0 0 5\nOlá\n"))  // truncado
        assertNull(FormatoDeMarcas.ler(""))
    }

    @Test
    fun `a marca reencontra-se depois de o corte dos paragrafos mudar`() {
        // Antes do OCR: a folha 12 tinha um parágrafo. Depois tem três, e o
        // que estava marcado deixou de existir com aquele índice.
        val marca = Marca(12, 0, quando = 1)
        val depois = listOf(
            Paragraph(12, 0, "Cabeçalho reconhecido."),
            Paragraph(12, 1, "Corpo."),
            Paragraph(12, 2, "Fim.")
        )
        val i = indiceDeParagrafo(depois, marca.pagina, marca.indiceNaPagina)
        assertEquals(0, i)   // cai no princípio da mesma folha
    }
}
