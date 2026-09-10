package pt.docvoice

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.docvoice.leitura.LeituraService
import pt.docvoice.leitura.SessaoDeLeitura
import pt.docvoice.ui.EcraExtracao
import pt.docvoice.ui.EcraFalha
import pt.docvoice.ui.EcraLeitura
import pt.docvoice.ui.EcraRecentes
import pt.docvoice.ui.EcraVazio
import pt.docvoice.ui.EstadoLeitura
import pt.docvoice.ui.LeitorViewModel
import pt.docvoice.ui.theme.DocVoiceTheme
import pt.docvoice.ui.theme.Fundo

class MainActivity : ComponentActivity() {

    private val vm: LeitorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // O motor de voz demora a arrancar: preparar já, para o play ser imediato.
        SessaoDeLeitura.preparar(applicationContext)
        setContent {
            DocVoiceTheme {
                Aplicacao(vm)
            }
        }
    }

    // Nada de libertar o TTS aqui: minimizar não é fechar, e a leitura continua no bolso.
}

@Composable
private fun Aplicacao(vm: LeitorViewModel) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val sessao by SessaoDeLeitura.estado.collectAsStateWithLifecycle()
    val recentes by vm.recentes.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.abrir(uri)
    }
    val abrir: () -> Unit = { escolher.launch(arrayOf("application/pdf")) }

    // Android 13+: sem esta permissão a notificação não aparece.
    // A leitura funciona na mesma — por isso pede-se e segue-se, sem bloquear nada.
    val pedirNotificacoes = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val alternar: () -> Unit = {
        if (!sessao.aLer) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pedirNotificacoes.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            LeituraService.garantirEmMarcha(ctx)
        }
        SessaoDeLeitura.alternar()
    }

    Surface(color = Fundo, modifier = Modifier.fillMaxSize()) {
        when (val e = estado) {
            is EstadoLeitura.Vazio ->
                if (recentes.isEmpty()) EcraVazio(abrir)
                else EcraRecentes(
                    recentes = recentes,
                    aoAbrir = abrir,
                    aoAbrirRecente = { vm.abrirRecente(it) },
                    aoEsquecer = { vm.esquecer(it) }
                )
            is EstadoLeitura.AExtrair -> EcraExtracao(e)
            is EstadoLeitura.Falhou -> EcraFalha(e.mensagem, abrir)
            is EstadoLeitura.Aberto -> EcraLeitura(
                documento = e.documento,
                sessao = sessao,
                aoVoltarALista = { vm.voltarALista() },
                aoAlternar = alternar,
                aoSaltar = { passos -> SessaoDeLeitura.saltar(passos) },
                aoIrPara = { indice ->
                    SessaoDeLeitura.irPara(indice)
                    if (!sessao.aLer) alternar()
                },
                aoFecharAvisoVoz = { SessaoDeLeitura.avisoVozVisto() },
                aoMudarVelocidade = { vm.mudarVelocidade(it) },
                aoMudarVoz = { vm.mudarVoz(it) }
            )
        }
    }
}
