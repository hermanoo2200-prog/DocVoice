package pt.docvoice.dados

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Velocidade e voz escolhidas ficam para a próxima vez — e valem para todos os documentos. */
class Preferencias(contexto: Context) {

    private val ctx = contexto.applicationContext

    val velocidade: Flow<Float> = ctx.arquivoDeDados.data.map { it[CHAVE_VELOCIDADE] ?: 1f }

    suspend fun guardarVelocidade(valor: Float) {
        ctx.arquivoDeDados.edit { it[CHAVE_VELOCIDADE] = valor.coerceIn(VELOCIDADE_MINIMA, VELOCIDADE_MAXIMA) }
    }

    /** Nome técnico da voz do sistema, guardado por língua: pt, ru, en. */
    fun voz(idioma: String): Flow<String?> =
        ctx.arquivoDeDados.data.map { it[stringPreferencesKey("voz_$idioma")] }

    suspend fun guardarVoz(idioma: String, nomeDaVoz: String) {
        ctx.arquivoDeDados.edit { it[stringPreferencesKey("voz_$idioma")] = nomeDaVoz }
    }

    companion object {
        const val VELOCIDADE_MINIMA = 0.75f
        const val VELOCIDADE_MAXIMA = 2.0f
        val VELOCIDADES = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        private val CHAVE_VELOCIDADE = floatPreferencesKey("velocidade")
    }
}
