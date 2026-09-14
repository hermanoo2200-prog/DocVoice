package pt.docvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Texto que existe na folha mas não é fala: pontinhos de índice, filetes,
 * traços de separação, numeração solta.
 *
 * Num documento a sério isto aparece nas bordas das folhas — cabeçalhos,
 * rodapés, índices — e foi o que fez a leitura calar-se meio minuto
 * na passagem de página.
 */
class TextoSemFalaTest {

    @Test
    fun `linha de pontinhos de indice perde os pontos e guarda as palavras`() {
        val indice = "Artigo 1.º " + ".".repeat(60) + " 3"
        val trocos = TrocosDeFala.partir(indice)
        assertEquals(1, trocos.size)
        assertTrue("os pontos ficaram: ${trocos[0]}", !trocos[0].contains("...."))
        assertTrue("perdeu-se o artigo", trocos[0].contains("Artigo"))
        assertTrue("perdeu-se o número da página", trocos[0].contains("3"))
    }

    @Test
    fun `filete de sublinhados nao vai ao motor`() {
        assertEquals(emptyList<String>(), TrocosDeFala.partir("_".repeat(200)))
    }

    @Test
    fun `linha so de travessoes nao vai ao motor`() {
        assertEquals(emptyList<String>(), TrocosDeFala.partir("— — — — — — — — — — — —"))
    }

    @Test
    fun `indice inteiro encolhe em vez de crescer`() {
        val indice = (1..6).joinToString(" ") { "Artigo $it.º " + ".".repeat(40) + " $it" }
        val trocos = TrocosDeFala.partir(indice)
        val letras = trocos.sumOf { t -> t.count { it.isLetterOrDigit() } }
        val total = trocos.sumOf { it.length }
        assertTrue("ainda vai demasiado lixo: $letras úteis em $total", letras * 2 > total)
    }

    @Test
    fun `numeros com ponto de milhar nao sao tocados`() {
        val t = TrocosDeFala.partir("O valor foi fixado em 15.000 euros.")
        assertEquals(1, t.size)
        assertTrue(t[0].contains("15.000"))
    }

    @Test
    fun `abreviaturas nao sao tocadas`() {
        val t = TrocosDeFala.partir("Nos termos do art. 340.º e do n.º 2, a fls. 1240.")
        assertTrue(t[0].contains("art."))
        assertTrue(t[0].contains("n.º"))
        assertTrue(t[0].contains("fls."))
    }

    @Test
    fun `data com hifens fica intacta`() {
        val t = TrocosDeFala.partir("Acórdão de 12-03-2026, confirmado.")
        assertTrue(t[0].contains("12-03-2026"))
    }

    @Test
    fun `paragrafo vazio nao da troco nenhum`() {
        assertEquals(emptyList<String>(), TrocosDeFala.partir("   \n  "))
    }
}
