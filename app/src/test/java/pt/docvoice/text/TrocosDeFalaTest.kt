package pt.docvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrocosDeFalaTest {

    /** Um parágrafo jurídico comprido a sério: é o caso que calava o motor. */
    private val paragrafoLongo = """
        Nos termos do disposto no artigo 340.º do Código de Processo Penal, o tribunal
        ordena, oficiosamente ou a requerimento, a produção de todos os meios de prova
        cujo conhecimento se afigure necessário à descoberta da verdade e à boa decisão
        da causa, sem prejuízo do disposto no n.º 2 do artigo 97.º, cuja aplicação foi
        requerida a fls. 1240 e não mereceu apreciação na sessão anterior; o valor da
        indemnização, fixado em 15.000 euros, foi considerado adequado à gravidade dos
        factos e à situação económica do lesado, conforme resulta do acórdão do Supremo
        Tribunal de Justiça de 12 de Março, cuja doutrina aqui se acolhe sem reservas,
        não se vislumbrando fundamento para decidir de outro modo.
    """.trimIndent().replace("\n", " ").replace(Regex(" +"), " ")

    @Test
    fun `parágrafo curto fica inteiro`() {
        val curto = "O requerimento foi indeferido."
        assertEquals(listOf(curto), TrocosDeFala.partir(curto))
    }

    @Test
    fun `parágrafo longo é partido`() {
        assertTrue("o caso de ensaio tem de ser mesmo longo", paragrafoLongo.length > 600)
        assertTrue(TrocosDeFala.partir(paragrafoLongo).size > 1)
    }

    @Test
    fun `nenhum troço passa do limite`() {
        TrocosDeFala.partir(paragrafoLongo).forEach {
            assertTrue("troço de ${it.length}: $it", it.length <= TrocosDeFala.LIMITE)
        }
    }

    @Test
    fun `nada se perde pelo caminho`() {
        val junto = TrocosDeFala.partir(paragrafoLongo).joinToString(" ")
        assertEquals(semEspacos(paragrafoLongo), semEspacos(junto))
    }

    @Test
    fun `nenhum troço vem vazio`() {
        TrocosDeFala.partir(paragrafoLongo).forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `não corta dentro de uma palavra`() {
        TrocosDeFala.partir(paragrafoLongo).forEach { troco ->
            val fim = troco.last()
            assertTrue(
                "acabou a meio de uma palavra: ...${troco.takeLast(30)}",
                !fim.isLetter() || paragrafoLongo.endsWith(troco)
            )
        }
    }

    @Test
    fun `não corta em abreviatura nem em número`() {
        TrocosDeFala.partir(paragrafoLongo).dropLast(1).forEach { troco ->
            val ultimaPalavra = troco.trimEnd('.', ' ').takeLastWhile { it.isLetterOrDigit() }
            assertTrue(
                "cortou depois de «$ultimaPalavra.» — é abreviatura",
                ultimaPalavra.lowercase() !in setOf("art", "fls", "n", "cf", "dr", "proc")
            )
            assertTrue("cortou dentro de um número: $troco", !troco.endsWith("15."))
        }
    }

    @Test
    fun `uma palavra maior do que o limite não trava nem se perde`() {
        val monstro = "a".repeat(TrocosDeFala.LIMITE * 2 + 7)
        val trocos = TrocosDeFala.partir(monstro)
        assertTrue(trocos.isNotEmpty())
        trocos.forEach { assertTrue(it.length <= TrocosDeFala.LIMITE) }
        assertEquals(monstro, trocos.joinToString(""))
    }

    @Test
    fun `texto vazio não dá troço nenhum`() {
        assertEquals(emptyList<String>(), TrocosDeFala.partir("   \n  "))
    }

    @Test
    fun `mil caracteres cabem em poucos troços, não num por frase`() {
        val trocos = TrocosDeFala.partir(paragrafoLongo)
        assertTrue("troços a mais: ${trocos.size}", trocos.size <= 6)
    }

    private fun semEspacos(s: String) = s.replace(Regex("\\s+"), "")
}
