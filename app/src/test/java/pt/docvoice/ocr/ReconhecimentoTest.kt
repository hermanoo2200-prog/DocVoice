package pt.docvoice.ocr

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconhecimentoTest {

    @Test
    fun `percorre as folhas pela ordem pedida e avisa a cada uma`() = runBlocking {
        val saiu = LinkedHashMap<Int, String>()
        val progressos = mutableListOf<ProgressoDeOcr>()
        reconhecer(listOf(3, 1, 2), { p -> "folha $p" }) { pagina, texto, prog ->
            saiu[pagina] = texto
            progressos += prog
        }
        assertEquals(listOf(3, 1, 2), saiu.keys.toList())
        assertEquals("folha 3", saiu[3])
        assertEquals(listOf(1, 2, 3), progressos.map { it.feitas })
        assertEquals(listOf(3, 1, 2), progressos.map { it.paginaActual })
        assertTrue(progressos.all { it.total == 3 })
        assertEquals(100, progressos.last().percentagem)
    }

    @Test
    fun `folha que rebenta fica vazia e o resto continua`() = runBlocking {
        val saiu = LinkedHashMap<Int, String>()
        reconhecer(listOf(1, 2, 3), { p ->
            if (p == 2) error("esta folha saiu torta") else "folha $p"
        }) { pagina, texto, _ -> saiu[pagina] = texto }

        assertEquals(3, saiu.size)
        assertEquals("", saiu[2])
        assertEquals("folha 3", saiu[3])
    }

    @Test
    fun `parar a meio guarda o que ja foi lido`() {
        val saiu = LinkedHashMap<Int, String>()
        val erro = runCatching {
            runBlocking {
                reconhecer((1..10).toList(), { p ->
                    if (p == 4) throw CancellationException("o botão de parar")
                    "folha $p"
                }) { pagina, texto, _ -> saiu[pagina] = texto }
            }
        }.exceptionOrNull()

        assertTrue(erro is CancellationException)
        assertEquals(listOf(1, 2, 3), saiu.keys.toList())   // o que já saiu não se perde
    }

    @Test
    fun `lista vazia nao faz nada nem rebenta`() = runBlocking {
        var chamadas = 0
        reconhecer(emptyList(), { "nunca" }) { _, _, _ -> chamadas++ }
        assertEquals(0, chamadas)
    }

    @Test
    fun `a percentagem acompanha as folhas feitas`() {
        assertEquals(0, ProgressoDeOcr(0, 40, 0).percentagem)
        assertEquals(50, ProgressoDeOcr(20, 40, 20).percentagem)
        assertEquals(0, ProgressoDeOcr(0, 0, 0).percentagem)   // sem folhas, sem divisão por zero
    }
}
