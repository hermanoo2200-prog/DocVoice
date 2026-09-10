package pt.docvoice.text

import org.junit.Assert.assertEquals
import org.junit.Test

class DetectorDeIdiomaTest {

    private val portugues = """
        Nos termos do disposto no artigo 340.º do Código de Processo Penal, o tribunal
        ordena, oficiosamente ou a requerimento, a produção de todos os meios de prova
        cujo conhecimento se afigure necessário à descoberta da verdade e à boa decisão
        da causa. O requerimento apresentado pelo arguido não foi apreciado na sessão
        anterior, pelo que se determina a sua remessa ao juiz de instrução.
    """.trimIndent()

    private val russo = """
        Настоящим уведомляем, что производство по делу приостановлено до получения
        ответа на международный запрос. Копия постановления направлена сторонам
        по указанным адресам, а также приобщена к материалам дела в двух экземплярах.
    """.trimIndent()

    private val ingles = """
        This agreement is made between the parties and shall be governed by the laws
        of the jurisdiction in which the property is located. The tenant agrees to pay
        the rent on the first day of each month, and the landlord shall keep the
        premises in good repair for the duration of this contract.
    """.trimIndent()

    @Test fun `reconhece portugues`() = assertEquals(Idioma.PORTUGUES, DetectorDeIdioma.detectar(portugues))

    @Test fun `reconhece russo`() = assertEquals(Idioma.RUSSO, DetectorDeIdioma.detectar(russo))

    @Test fun `reconhece ingles`() = assertEquals(Idioma.INGLES, DetectorDeIdioma.detectar(ingles))

    @Test fun `texto curto de mais fica desconhecido`() =
        assertEquals(Idioma.DESCONHECIDO, DetectorDeIdioma.detectar("fl. 12"))

    @Test fun `pagina so com numeros fica desconhecida`() =
        assertEquals(Idioma.DESCONHECIDO, DetectorDeIdioma.detectar("1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20"))

    @Test fun `russo com cabecalho latino continua russo`() =
        assertEquals(Idioma.RUSSO, DetectorDeIdioma.detectar("Anexo B-12 Proc. 1234/25\n$russo"))
}
