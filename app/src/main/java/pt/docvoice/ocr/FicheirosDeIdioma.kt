package pt.docvoice.ocr

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Põe os ficheiros de idioma do Tesseract onde ele os sabe ir buscar.
 *
 * Os ficheiros viajam **dentro da aplicação**, em `assets/tessdata/`. Não há
 * transferência nenhuma, nem na primeira vez: o manifesto não pede autorização
 * de rede, e isso é regra da casa — os documentos de quem usa isto não saem do
 * telemóvel, e um OCR que fosse buscar o idioma à Internet seria a primeira
 * porta a abrir-se.
 *
 * O Tesseract quer uma pasta que **contenha** uma pasta `tessdata`, e é à pasta
 * de fora que se chama caminho de dados. Daí a arrumação:
 *
 *     filesDir/tesseract/tessdata/por.traineddata
 *     filesDir/tesseract/tessdata/rus.traineddata
 *                └──────┬──────┘
 *                  caminhoDeDados
 */
class FicheirosDeIdioma(private val ctx: Context) {

    val caminhoDeDados: String get() = File(ctx.filesDir, PASTA).absolutePath

    private val tessdata: File get() = File(File(ctx.filesDir, PASTA), "tessdata")

    /**
     * Copia de `assets` o que ainda não estiver copiado e devolve os idiomas
     * que ficaram prontos a usar.
     *
     * Volta a copiar se o tamanho não bater certo: um ficheiro cortado a meio
     * faz o Tesseract arrancar e devolver disparates, que é pior do que não
     * arrancar de todo.
     */
    suspend fun preparar(): Set<IdiomaOcr> = withContext(Dispatchers.IO) {
        tessdata.mkdirs()
        val prontos = LinkedHashSet<IdiomaOcr>()
        for (idioma in IdiomaOcr.entries) {
            val nome = "${idioma.codigo}.traineddata"
            val destino = File(tessdata, nome)
            val ok = runCatching {
                val origem = "tessdata/$nome"
                val tamanhoNaAplicacao = ctx.assets.openFd(origem).use { it.length }
                if (!destino.exists() || destino.length() != tamanhoNaAplicacao) {
                    val meio = File(tessdata, "$nome.parte")
                    ctx.assets.open(origem).use { entrada ->
                        meio.outputStream().buffered().use { entrada.copyTo(it) }
                    }
                    // Só depois de copiado por inteiro é que passa a valer.
                    meio.renameTo(destino)
                }
                destino.exists() && destino.length() > 0
            }.getOrElse { false }
            if (ok) prontos += idioma
        }
        // Quantos idiomas, nunca que documento. O registo do sistema lê-se de fora.
        Log.i("DocVoice", "idiomas de OCR prontos: ${prontos.size}")
        prontos
    }

    /** Já está copiado? Serve para não mandar preparar de cada vez. */
    fun jaPreparado(idioma: IdiomaOcr): Boolean =
        File(tessdata, "${idioma.codigo}.traineddata").let { it.exists() && it.length() > 0 }

    /** Quanto ocupam os ficheiros de idioma já copiados. */
    fun tamanho(): Long = tessdata.listFiles()?.sumOf { it.length() } ?: 0L

    private companion object {
        const val PASTA = "tesseract"
    }
}
