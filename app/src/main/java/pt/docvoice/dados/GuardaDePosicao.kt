package pt.docvoice.dados

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pt.docvoice.leitura.SessaoDeLeitura

/**
 * Guarda o sítio onde vai a leitura a cada parágrafo — não só ao pausar.
 * Assim, se o sistema matar a aplicação no bolso, perde-se um parágrafo, não uma hora.
 */
object GuardaDePosicao {

    private var ligado = false

    fun ligar(contexto: Context) {
        if (ligado) return
        ligado = true
        val arquivo = ArquivoDeRecentes(contexto)
        val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        escopo.launch {
            SessaoDeLeitura.estado
                .map { estado ->
                    val documento = estado.documento ?: return@map null
                    val paragrafo = estado.paragrafoActual ?: return@map null
                    RegistoDocumento(
                        uri = documento.uri.toString(),
                        nome = documento.nome,
                        tamanho = documento.tamanho,
                        totalPaginas = documento.totalPaginas,
                        totalParagrafos = documento.paragrafos.size,
                        pagina = paragrafo.pagina,
                        indiceNaPagina = paragrafo.indiceNaPagina,
                        indiceCorrido = estado.indice,
                        quando = System.currentTimeMillis(),
                        paginasComTexto = documento.paginasComTexto
                    )
                }
                .filterNotNull()
                .distinctUntilChanged { antigo, novo ->
                    antigo.uri == novo.uri &&
                        antigo.pagina == novo.pagina &&
                        antigo.indiceNaPagina == novo.indiceNaPagina
                }
                .collect { arquivo.guardar(it) }
        }
    }
}
