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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.docvoice.leitura.LeituraService
import pt.docvoice.leitura.SessaoDeLeitura
import pt.docvoice.ui.EcraExtracao
import pt.docvoice.ui.EcraFalha
import pt.docvoice.ui.EcraLeitura
import pt.docvoice.ui.EcraMarcas
import pt.docvoice.ui.PainelDeOcr
import pt.docvoice.ui.EcraDefinicoes
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
    val estadoOcr by vm.estadoOcr.collectAsStateWithLifecycle()
    val planoDeOcr by vm.plano.collectAsStateWithLifecycle()
    val ocrPedido by vm.ocrPedido.collectAsStateWithLifecycle()
    val marcas by vm.marcas.collectAsStateWithLifecycle()
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

    // As definições são um desvio curto, não um estado do documento: vive aqui.
    var nasDefinicoes by remember { mutableStateOf(false) }
    // A lista de marcas é o mesmo tipo de desvio curto: entra-se, lê-se, sai-se.
    var nasMarcas by remember { mutableStateOf(false) }
    var espacoGuardado by remember { mutableStateOf("—") }
    var apagouTudo by remember { mutableStateOf(false) }

    LaunchedEffect(nasDefinicoes, apagouTudo) {
        if (nasDefinicoes) espacoGuardado = emKilobytes(vm.espacoGuardado())
    }

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
        if (nasDefinicoes) {
            EcraDefinicoes(
                espacoOcupado = espacoGuardado,
                aoApagarTudo = { vm.apagarTudoOGuardado { apagouTudo = !apagouTudo } },
                apagado = apagouTudo,
                aoVoltar = { nasDefinicoes = false; apagouTudo = false }
            )
            return@Surface
        }
        val aberto = (estado as? EstadoLeitura.Aberto)?.documento
        if (nasMarcas && aberto != null) {
            val porId = aberto.paragrafos.associateBy { it.id }
            EcraMarcas(
                nomeDoDocumento = aberto.nome,
                marcas = marcas,
                textoDoParagrafo = { porId[it.id]?.texto },
                aoIrPara = { marca ->
                    vm.irParaMarca(marca)
                    nasMarcas = false
                    if (!sessao.aLer) alternar()
                },
                aoTirar = { vm.alternarMarca(it.pagina, it.indiceNaPagina) },
                aoEscreverNota = { marca, nota ->
                    vm.escreverNota(marca.pagina, marca.indiceNaPagina, nota)
                },
                textoParaCopiar = { vm.listaDeMarcasParaCopiar() },
                aoVoltar = { nasMarcas = false }
            )
            return@Surface
        }

        when (val e = estado) {
            is EstadoLeitura.Vazio ->
                if (recentes.isEmpty()) EcraVazio(abrir)
                else EcraRecentes(
                    recentes = recentes,
                    aoAbrir = abrir,
                    aoAbrirRecente = { vm.abrirRecente(it) },
                    aoEsquecer = { vm.esquecer(it) },
                    aoAbrirDefinicoes = { nasDefinicoes = true }
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
                aoMudarVoz = { vm.mudarVoz(it) },
                painelDeOcr = {
                    PainelDeOcr(
                        plano = planoDeOcr,
                        estado = estadoOcr,
                        pedido = ocrPedido,
                        aoComecar = { idioma, alcance -> vm.iniciarOcr(idioma, alcance) },
                        aoParar = { vm.pararOcr() },
                        aoLimpar = { vm.limparEstadoOcr() }
                    )
                },
                folhasPorReconhecer = planoDeOcr?.paginasSemTexto?.size ?: 0,
                aoPedirOcr = { vm.pedirOcr() },
                marcado = sessao.paragrafoActual?.let { p ->
                    marcas.any { it.pagina == p.pagina && it.indiceNaPagina == p.indiceNaPagina }
                } ?: false,
                quantasMarcas = marcas.size,
                aoMarcar = { vm.marcarOndeVai() },
                aoAbrirMarcas = { nasMarcas = true }
            )
        }
    }
}

/** «128 KB», «1,4 MB» — sem casas decimais a mais. */
private fun emKilobytes(bytes: Long): String = when {
    bytes <= 0L -> "0 KB"
    bytes < 1024L * 1024L -> "${(bytes + 1023) / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0).replace('.', ',')
}
