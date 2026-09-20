package pt.docvoice.ocr

import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Lê UMA folha e devolve o texto que lá está.
 *
 * Quem implementa é que sabe desenhar a página em imagem e passá-la ao
 * Tesseract — ver [pt.docvoice.ocr.LeitorTesseract]. Aqui em cima não entra
 * nada do Android nem do Tesseract, e por isso esta parte testa-se na JVM,
 * sem telemóvel.
 */
fun interface LeitorDeFolha {
    /** Devolve o texto da folha, ou cadeia vazia se não deu para ler nada. */
    suspend fun ler(pagina: Int): String
}

/** Onde é que o reconhecimento vai, para quem está a olhar para o ecrã. */
data class ProgressoDeOcr(
    val feitas: Int,
    val total: Int,
    /** A folha que acabou de sair. Zero antes da primeira. */
    val paginaActual: Int
) {
    val percentagem: Int get() = if (total == 0) 0 else feitas * 100 / total
}

/**
 * Percorre as folhas pedidas, uma a uma, e entrega cada uma assim que sai.
 *
 * Entrega folha a folha de propósito: quem chama vai guardando, e se o
 * reconhecimento for parado a meio — pelo botão, ou porque a aplicação foi
 * fechada — o que já foi lido não se perde. Quarenta folhas de um processo
 * demoram, e obrigar a recomeçar do zero por causa de uma interrupção era
 * castigar quem lê.
 *
 * A paragem é a do próprio Kotlin: [ensureActive] antes de cada folha. Não há
 * bandeira nenhuma a inventar, e cancelar o trabalho pára-o mesmo.
 *
 * Uma folha que rebente não deita o resto abaixo: fica vazia, conta como
 * feita, e segue para a seguinte. Numa digitalização de quarenta folhas há
 * sempre uma que sai torta.
 */
suspend fun reconhecer(
    paginas: List<Int>,
    leitor: LeitorDeFolha,
    aoLer: (pagina: Int, texto: String, progresso: ProgressoDeOcr) -> Unit
) {
    val total = paginas.size
    var feitas = 0
    for (pagina in paginas) {
        coroutineContext.ensureActive()
        val texto = try {
            leitor.ler(pagina)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e                     // parar é parar: não se engole
        } catch (_: Throwable) {
            ""                          // folha torta não trava o resto
        }
        feitas++
        aoLer(pagina, texto, ProgressoDeOcr(feitas, total, pagina))
    }
}
