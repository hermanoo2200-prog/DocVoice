package pt.docvoice.dados

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pt.docvoice.leitura.SessaoDeLeitura

/** Velocidade e voz guardadas voltam a valer no arranque seguinte. */
object GuardaDePreferencias {

    private var ligado = false

    fun ligar(contexto: Context) {
        if (ligado) return
        ligado = true
        val preferencias = Preferencias(contexto)
        val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        escopo.launch {
            preferencias.velocidade.collect { SessaoDeLeitura.definirVelocidade(it) }
        }
        for (lingua in listOf("pt", "ru", "en")) {
            escopo.launch {
                preferencias.voz(lingua).collect { nome ->
                    if (!nome.isNullOrBlank()) SessaoDeLeitura.lembrarVozPreferida(lingua, nome)
                }
            }
        }
    }
}
