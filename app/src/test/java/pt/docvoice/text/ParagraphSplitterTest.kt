package pt.docvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParagraphSplitterTest {

    private val paginaJuridica = """
        Nos termos do disposto no art. 340.º do Código de Processo Penal, o tribunal
        ordena, oficiosamente ou a requerimento, a produção de todos os meios de prova
        cujo conhecimento se afigure necessário à descoberta da verdade e à boa decisão
        da causa. O requerimento apresentado pelo arguido a fls. 1240 não foi apreciado
        na sessão anterior. Cf. o acórdão do STJ de 15.000 processos analisados pela
        Rel. do Porto, cuja doutrina se acolhe.

        O valor da indemnização foi fixado em 15.000 euros, montante que o Dr. Fulano
        considerou adequado.
    """.trimIndent()

    @Test
    fun `abreviaturas nao fecham frase`() {
        val frases = ParagraphSplitter.frases(ParagraphSplitter.blocos(paginaJuridica).first())
        assertTrue("art. 340.º não pode ficar sozinho", frases.none { it.trim() == "art." })
        assertTrue("fls. não pode fechar frase", frases.none { it.endsWith("fls.") })
        assertTrue("Cf. não pode fechar frase", frases.none { it.trim() == "Cf." })
    }

    @Test
    fun `numero com ponto de milhar nao parte`() {
        val paragrafos = ParagraphSplitter.splitPagina(1, paginaJuridica)
        assertTrue(paragrafos.none { it.texto.trim().startsWith("000") })
    }

    @Test
    fun `linha em branco separa blocos`() {
        assertEquals(2, ParagraphSplitter.blocos(paginaJuridica).size)
    }

    @Test
    fun `blocos ficam dentro do maximo salvo frase longa`() {
        val frase = "Palavra ".repeat(60).trim() + "." // ~480 caracteres, sem pontuação interna
        val paragrafos = ParagraphSplitter.splitPagina(3, frase)
        assertEquals(1, paragrafos.size)
        assertTrue("frase longa passa inteira", paragrafos[0].texto.length > ParagraphSplitter.ALVO_MAX)
    }

    @Test
    fun `frase longa parte no ponto e virgula`() {
        val frase = ("a".repeat(200) + "; " + "b".repeat(200) + "; " + "c".repeat(120) + ".")
        val paragrafos = ParagraphSplitter.splitPagina(4, frase)
        assertTrue("devia partir em mais do que um bloco", paragrafos.size > 1)
        assertTrue(paragrafos.all { it.texto.length <= 400 })
    }

    @Test
    fun `numeracao e por pagina`() {
        val texto = "Uma frase. ".repeat(40)
        val p7 = ParagraphSplitter.splitPagina(7, texto)
        val p8 = ParagraphSplitter.splitPagina(8, texto)
        assertEquals(0, p7.first().indiceNaPagina)
        assertEquals(0, p8.first().indiceNaPagina)
        assertEquals("7:0", p7.first().id)
        assertEquals("8:0", p8.first().id)
    }

    @Test
    fun `hifen de fim de linha junta-se`() {
        val blocos = ParagraphSplitter.blocos("indemniza-\nção devida ao lesado.")
        assertTrue(blocos.first().startsWith("indemnização"))
    }

    @Test
    fun `nunca corta a meio de uma frase`() {
        val texto = "Frase curta uma. Frase curta duas. ".repeat(20)
        val paragrafos = ParagraphSplitter.splitPagina(1, texto)
        assertTrue(
            "todo o bloco tem de acabar em pontuação",
            paragrafos.all { it.texto.trimEnd().last() in listOf('.', ';', ':', '!', '?', '…') }
        )
    }
}
