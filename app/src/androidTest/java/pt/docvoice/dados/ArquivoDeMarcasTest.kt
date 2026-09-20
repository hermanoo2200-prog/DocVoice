package pt.docvoice.dados

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pt.docvoice.text.Marca
import java.io.File

/** As marcas ficam guardadas, e obedecem às mesmas regras do texto. */
@RunWith(AndroidJUnit4::class)
class ArquivoDeMarcasTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var arquivo: ArquivoDeMarcas

    @Before
    fun limpar() = runBlocking {
        arquivo = ArquivoDeMarcas(ctx)
        arquivo.apagarTudo()
    }

    @Test
    fun marcas_vao_e_voltam_do_telemovel() = runBlocking {
        val marcas = listOf(
            Marca(12, 0, "", 1_700_000_000_000),
            Marca(40, 2, "contradiz a folha doze", 1_700_000_001_000)
        )
        arquivo.guardar("contrato.pdf:12345", marcas)
        assertEquals(marcas, arquivo.ler("contrato.pdf:12345"))
    }

    @Test
    fun documento_sem_marcas_nao_deixa_ficheiro_para_tras() = runBlocking {
        arquivo.guardar("x:1", listOf(Marca(1, 0)))
        assertTrue(arquivo.tamanho() > 0)
        // Tirar a última marca apaga o ficheiro, não deixa um vazio.
        arquivo.guardar("x:1", emptyList())
        assertEquals(0L, arquivo.tamanho())
        assertTrue(arquivo.ler("x:1").isEmpty())
    }

    @Test
    fun tirar_o_documento_da_lista_leva_as_marcas() = runBlocking {
        arquivo.guardar("a:1", listOf(Marca(1, 0)))
        arquivo.guardar("b:2", listOf(Marca(9, 0)))
        arquivo.apagar("a:1")
        assertTrue(arquivo.ler("a:1").isEmpty())
        assertEquals(1, arquivo.ler("b:2").size)
    }

    @Test
    fun a_limpeza_da_lista_deita_fora_o_que_ja_nao_esta_nela() = runBlocking {
        arquivo.guardar("fica:1", listOf(Marca(1, 0)))
        arquivo.guardar("sai:2", listOf(Marca(2, 0)))
        arquivo.limparOsQueSaíramDaLista(listOf("fica:1"))
        assertEquals(1, arquivo.ler("fica:1").size)
        assertTrue(arquivo.ler("sai:2").isEmpty())
    }

    @Test
    fun o_botao_de_apagar_tudo_apaga_mesmo_tudo() = runBlocking {
        arquivo.guardar("a:1", listOf(Marca(1, 0)))
        arquivo.guardar("b:2", listOf(Marca(2, 0)))
        arquivo.apagarTudo()
        assertEquals(0L, arquivo.tamanho())
    }

    @Test
    fun o_nome_do_ficheiro_nao_diz_que_documento_e() = runBlocking {
        arquivo.guardar("processo-768-do-tribunal.pdf:999", listOf(Marca(1, 0)))
        val nomes = File(ctx.filesDir, "marcas").listFiles()?.map { it.name }.orEmpty()
        assertEquals(1, nomes.size)
        assertFalse("o nome do documento está à vista na pasta",
            nomes.single().contains("processo") || nomes.single().contains("768"))
    }

    @Test
    fun ficheiro_estragado_nao_deita_a_aplicacao_abaixo() = runBlocking {
        arquivo.guardar("z:1", listOf(Marca(1, 0)))
        File(ctx.filesDir, "marcas").listFiles()!!.single().writeText("lixo")
        assertTrue(arquivo.ler("z:1").isEmpty())
    }
}
