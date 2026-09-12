package pt.docvoice.dados

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Guarda no telemóvel o texto já extraído dos documentos, para não voltar a
 * abrir o PDF de cada vez. Quarenta folhas custavam quatro segundos e a voz
 * calava-se.
 *
 * Regras, decididas com quem usa isto:
 *
 *  - guarda-se **só** o texto de documentos que estão na lista de recentes;
 *  - tirar um documento da lista apaga o texto dele no mesmo gesto;
 *  - há um botão que apaga tudo de uma vez, sem desinstalar nada.
 *
 * Onde fica: na pasta privada da aplicação, que só ela lê. Não sai do
 * telemóvel — a aplicação não tem autorização de rede, e `allowBackup=false`
 * com as regras de extracção impedem que vá parar a uma cópia de segurança
 * na nuvem ou a outro aparelho.
 *
 * O nome do ficheiro é o resumo criptográfico da chave, não o nome do
 * documento: quem espreitar a pasta não fica a saber que processos é que
 * alguém anda a ouvir.
 */
class ArquivoDeTextos(private val ctx: Context) {

    private val pasta: File get() = File(ctx.filesDir, "textos")

    private fun ficheiro(chave: String) = File(pasta, "${resumo(chave)}.gz")

    /** SHA-256 da chave, em hexadecimal. */
    private fun resumo(chave: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(chave.toByteArray())
            .joinToString("") { "%02x".format(it) }

    suspend fun guardar(chave: String, texto: FormatoDeTexto.TextoGuardado) =
        withContext(Dispatchers.IO) {
            runCatching {
                pasta.mkdirs()
                val destino = ficheiro(chave)
                val meio = File(destino.parentFile, destino.name + ".parte")
                GZIPOutputStream(meio.outputStream().buffered()).use { saida ->
                    saida.write(FormatoDeTexto.escrever(texto).toByteArray())
                }
                // Só depois de escrito por inteiro é que passa a valer: um
                // corte a meio deixaria um ficheiro que se lê como lixo.
                meio.renameTo(destino)
            }
            Unit
        }

    suspend fun ler(chave: String): FormatoDeTexto.TextoGuardado? =
        withContext(Dispatchers.IO) {
            val f = ficheiro(chave)
            if (!f.exists()) return@withContext null
            val cru = runCatching {
                GZIPInputStream(f.inputStream().buffered()).use { it.readBytes().decodeToString() }
            }.getOrNull() ?: return@withContext null
            FormatoDeTexto.ler(cru).also { if (it == null) f.delete() }
        }

    suspend fun apagar(chave: String) = withContext(Dispatchers.IO) {
        ficheiro(chave).delete()
        Unit
    }

    /** O botão «apagar tudo o que está guardado». */
    suspend fun apagarTudo() = withContext(Dispatchers.IO) {
        pasta.listFiles()?.forEach { it.delete() }
        Unit
    }

    /**
     * Deita fora o texto de documentos que já não estão na lista — por
     * exemplo, os que caíram para lá dos trinta que a lista guarda.
     */
    suspend fun limparOsQueSaíramDaLista(chaves: Collection<String>) =
        withContext(Dispatchers.IO) {
            val validos = chaves.map { "${resumo(it)}.gz" }.toHashSet()
            pasta.listFiles()?.forEach { if (it.name !in validos) it.delete() }
            Unit
        }

    /** Quantos bytes ocupa tudo o que está guardado. */
    suspend fun tamanho(): Long = withContext(Dispatchers.IO) {
        pasta.listFiles()?.sumOf { it.length() } ?: 0L
    }
}
