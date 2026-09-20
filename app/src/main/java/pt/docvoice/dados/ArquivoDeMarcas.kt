package pt.docvoice.dados

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pt.docvoice.text.Marca
import java.io.File
import java.security.MessageDigest

/**
 * Guarda no telemóvel as marcas de cada documento.
 *
 * Mesmas regras do [ArquivoDeTextos], e de propósito as mesmas:
 *
 *  - guardam-se só marcas de documentos que estão na lista de recentes;
 *  - tirar um documento da lista apaga as marcas dele no mesmo gesto;
 *  - o botão «apagar tudo o que está guardado» apaga-as também.
 *
 * Fica na pasta privada da aplicação, que só ela lê, e não sai do telemóvel:
 * não há autorização de rede, e `allowBackup=false` com as regras de
 * extracção impedem que vá parar a uma cópia na nuvem.
 *
 * O nome do ficheiro é o resumo criptográfico da chave, não o nome do
 * documento — e aqui isso pesa mais do que no texto: quem espreitasse a pasta
 * ficava a saber não só que processos alguém ouve, mas o que nele lhe
 * interessou.
 *
 * Pasta à parte da do texto porque [ArquivoDeTextos.limparOsQueSaíramDaLista]
 * deita fora tudo o que não reconhece na sua própria pasta.
 */
class ArquivoDeMarcas(private val ctx: Context) {

    private val pasta: File get() = File(ctx.filesDir, "marcas")

    private fun ficheiro(chave: String) = File(pasta, "${resumo(chave)}.txt")

    private fun resumo(chave: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(chave.toByteArray())
            .joinToString("") { "%02x".format(it) }

    suspend fun guardar(chave: String, marcas: List<Marca>) = withContext(Dispatchers.IO) {
        runCatching {
            pasta.mkdirs()
            val destino = ficheiro(chave)
            if (marcas.isEmpty()) {
                destino.delete()
                return@runCatching
            }
            val meio = File(destino.parentFile, destino.name + ".parte")
            meio.writeText(FormatoDeMarcas.escrever(marcas))
            // Só depois de escrito por inteiro é que passa a valer.
            meio.renameTo(destino)
        }
        Unit
    }

    suspend fun ler(chave: String): List<Marca> = withContext(Dispatchers.IO) {
        val f = ficheiro(chave)
        if (!f.exists()) return@withContext emptyList()
        val cru = runCatching { f.readText() }.getOrNull() ?: return@withContext emptyList()
        FormatoDeMarcas.ler(cru) ?: emptyList<Marca>().also { f.delete() }
    }

    suspend fun apagar(chave: String) = withContext(Dispatchers.IO) {
        ficheiro(chave).delete()
        Unit
    }

    suspend fun apagarTudo() = withContext(Dispatchers.IO) {
        pasta.listFiles()?.forEach { it.delete() }
        Unit
    }

    suspend fun limparOsQueSaíramDaLista(chaves: Collection<String>) =
        withContext(Dispatchers.IO) {
            val validos = chaves.map { "${resumo(it)}.txt" }.toHashSet()
            pasta.listFiles()?.forEach { if (it.name !in validos) it.delete() }
            Unit
        }

    suspend fun tamanho(): Long = withContext(Dispatchers.IO) {
        pasta.listFiles()?.sumOf { it.length() } ?: 0L
    }
}
