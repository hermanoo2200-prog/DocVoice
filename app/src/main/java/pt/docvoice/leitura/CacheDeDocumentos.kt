package pt.docvoice.leitura

import android.net.Uri
import pt.docvoice.dados.RegistoDocumento

/**
 * Guarda em memória os últimos documentos já extraídos, para se poder andar
 * entre eles sem esperar.
 *
 * Porquê: abrir um PDF é ler o ficheiro inteiro e cortá-lo em parágrafos.
 * Quem tem quatro processos abertos e salta de um para outro estava a pagar
 * essa extracção de cada vez — e a leitura calava-se no meio.
 *
 * Só memória, nada de disco: o texto de documentos alheios não fica gravado
 * em lado nenhum sem ser pedido. Fechada a aplicação, isto desaparece.
 *
 * A chave é a mesma da lista de recentes — nome + tamanho — porque o mesmo
 * ficheiro escolhido outra vez no selector do sistema traz outro endereço.
 */
object CacheDeDocumentos {

    /**
     * Quantos documentos ficam à mão. Quatro chega para andar entre processos
     * de um mesmo caso sem encher a memória: um documento de mil folhas anda
     * pelo megabyte de texto.
     */
    private const val QUANTOS = 4

    private val guardados = LinkedHashMap<String, Documento>(QUANTOS, 0.75f, true)

    @Synchronized
    fun guardar(documento: Documento) {
        guardados[RegistoDocumento.chaveDe(documento.nome, documento.tamanho)] = documento
        while (guardados.size > QUANTOS) {
            val maisAntigo = guardados.keys.firstOrNull() ?: break
            guardados.remove(maisAntigo)
        }
    }

    /**
     * Devolve o documento já extraído, com o endereço actualizado: o ficheiro
     * é o mesmo, mas o endereço com que o sistema o entregou desta vez pode
     * não ser o de antes.
     */
    @Synchronized
    fun procurar(nome: String, tamanho: Long, uri: Uri): Documento? =
        guardados[RegistoDocumento.chaveDe(nome, tamanho)]
            ?.let { if (it.uri == uri) it else it.copy(uri = uri) }

    @Synchronized
    fun esquecer(chave: String) {
        guardados.remove(chave)
    }

    @Synchronized
    fun limpar() = guardados.clear()
}
