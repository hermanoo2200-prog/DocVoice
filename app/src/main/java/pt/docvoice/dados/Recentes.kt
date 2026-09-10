package pt.docvoice.dados

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * Um documento visto antes, com o sítio onde ficou.
 *
 * A posição é guardada por [pagina] + [indiceNaPagina] — a contagem corrida
 * desvia-se assim que o OCR acrescenta texto a uma folha.
 * [indiceCorrido] só serve para a percentagem na lista, sem reabrir o PDF.
 */
data class RegistoDocumento(
    val uri: String,
    val nome: String,
    val tamanho: Long,
    val totalPaginas: Int,
    val totalParagrafos: Int,
    val pagina: Int,
    val indiceNaPagina: Int,
    val indiceCorrido: Int,
    val quando: Long
) {
    /**
     * O mesmo ficheiro aberto de dois sítios (Ficheiros, Drive, WhatsApp) traz
     * dois URI diferentes e apareceria duas vezes na lista. Nome e tamanho
     * identificam o documento; o URI só serve para o voltar a abrir.
     */
    val chave: String get() = "$nome:$tamanho"

    val percentagem: Int
        get() = if (totalParagrafos <= 0) 0
        else (((indiceCorrido + 1).toFloat() / totalParagrafos) * 100).toInt().coerceIn(0, 100)
}

internal val Context.arquivoDeDados: DataStore<Preferences> by preferencesDataStore("docvoice")
private val CHAVE_RECENTES = stringPreferencesKey("recentes")
private const val QUANTOS_GUARDAR = 30

class ArquivoDeRecentes(contexto: Context) {

    private val ctx = contexto.applicationContext

    val lista: Flow<List<RegistoDocumento>> =
        ctx.arquivoDeDados.data.map { ler(it[CHAVE_RECENTES]) }

    suspend fun guardar(registo: RegistoDocumento) {
        ctx.arquivoDeDados.edit { prefs ->
            val actuais = ler(prefs[CHAVE_RECENTES]).filter { it.chave != registo.chave }
            val nova = (listOf(registo) + actuais)
                .sortedByDescending { it.quando }
                .take(QUANTOS_GUARDAR)
            prefs[CHAVE_RECENTES] = escrever(nova)
        }
    }

    suspend fun esquecer(chave: String) {
        ctx.arquivoDeDados.edit { prefs ->
            prefs[CHAVE_RECENTES] = escrever(ler(prefs[CHAVE_RECENTES]).filter { it.chave != chave })
        }
    }

    // --- JSON à mão: sem processador de anotações, sem base de dados por três campos ---

    private fun ler(cru: String?): List<RegistoDocumento> {
        if (cru.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(cru)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                RegistoDocumento(
                    uri = o.getString("uri"),
                    nome = o.optString("nome", "documento.pdf"),
                    tamanho = o.optLong("tamanho"),
                    totalPaginas = o.optInt("totalPaginas"),
                    totalParagrafos = o.optInt("totalParagrafos"),
                    pagina = o.optInt("pagina", 1),
                    indiceNaPagina = o.optInt("indiceNaPagina"),
                    indiceCorrido = o.optInt("indiceCorrido"),
                    quando = o.optLong("quando")
                )
            }.sortedByDescending { it.quando }
        }.getOrDefault(emptyList())
    }

    private fun escrever(lista: List<RegistoDocumento>): String {
        val array = JSONArray()
        lista.forEach { r ->
            array.put(
                JSONObject()
                    .put("uri", r.uri)
                    .put("nome", r.nome)
                    .put("tamanho", r.tamanho)
                    .put("totalPaginas", r.totalPaginas)
                    .put("totalParagrafos", r.totalParagrafos)
                    .put("pagina", r.pagina)
                    .put("indiceNaPagina", r.indiceNaPagina)
                    .put("indiceCorrido", r.indiceCorrido)
                    .put("quando", r.quando)
            )
        }
        return array.toString()
    }
}
