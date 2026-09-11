package pt.docvoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.docvoice.R
import pt.docvoice.dados.Preferencias.Companion.VELOCIDADES
import pt.docvoice.dados.RegistoDocumento
import pt.docvoice.leitura.Documento
import pt.docvoice.leitura.SessaoDeLeitura
import pt.docvoice.text.Paragraph
import pt.docvoice.ui.theme.Acento
import pt.docvoice.ui.theme.Apagado
import pt.docvoice.ui.theme.EstiloEtiqueta
import pt.docvoice.ui.theme.EstiloInterface
import pt.docvoice.ui.theme.EstiloParagrafo
import pt.docvoice.ui.theme.Fundo
import pt.docvoice.ui.theme.Linha
import pt.docvoice.ui.theme.Texto

@Composable
fun EcraVazio(aoAbrir: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text("DocVoice", style = EstiloParagrafo.copy(fontSize = 30.sp, color = Texto))
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.ecra_vazio_titulo), style = EstiloInterface.copy(fontSize = 16.sp))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.ecra_vazio_texto), style = EstiloInterface)
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = aoAbrir,
            colors = ButtonDefaults.buttonColors(containerColor = Acento, contentColor = Fundo)
        ) {
            Text(stringResource(R.string.abrir_pdf), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun EcraExtracao(estado: EstadoLeitura.AExtrair) {
    val fraccao = if (estado.total > 0) estado.feitas.toFloat() / estado.total else 0f
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(estado.nome, style = EstiloParagrafo.copy(fontSize = 20.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(18.dp))
        LinearProgressIndicator(
            progress = { fraccao },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = Acento,
            trackColor = Linha
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.a_extrair) + " — " +
                stringResource(R.string.pagina_de, estado.feitas, estado.total),
            style = EstiloInterface
        )
    }
}

@Composable
fun EcraFalha(mensagem: String, aoAbrir: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(mensagem, style = EstiloParagrafo.copy(fontSize = 18.sp))
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = aoAbrir,
            colors = ButtonDefaults.buttonColors(containerColor = Acento, contentColor = Fundo)
        ) { Text(stringResource(R.string.tentar_de_novo)) }
    }
}

@Composable
fun EcraRecentes(
    recentes: List<RegistoDocumento>,
    aoAbrir: () -> Unit,
    aoAbrirRecente: (RegistoDocumento) -> Unit,
    aoEsquecer: (RegistoDocumento) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            // O topo ficava por baixo da barra de estado do sistema: metade do
            // botão «Abrir PDF» não respondia ao toque, porque quem ficava com
            // esse pedaço de ecrã era o sistema.
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.recentes_titulo),
                style = EstiloParagrafo.copy(fontSize = 22.sp),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = aoAbrir) {
                Text(stringResource(R.string.abrir_pdf), style = EstiloInterface.copy(color = Acento))
            }
        }

        // Quantos são: era a primeira coisa que faltava saber ao olhar para a lista.
        Text(
            pluralStringResource(R.plurals.documentos_conta, recentes.size, recentes.size),
            style = EstiloEtiqueta,
            modifier = Modifier.padding(start = 20.dp, bottom = 14.dp)
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(recentes, key = { it.chave }) { registo ->
                CartaoDeDocumento(
                    registo = registo,
                    aoAbrir = { aoAbrirRecente(registo) },
                    aoEsquecer = { aoEsquecer(registo) }
                )
            }
        }
    }
}

/**
 * Uma prateleira, não uma lista de ficheiros: cada documento é um objecto com
 * cara própria — folha de texto ou digitalização — e mostra de relance quanto
 * já foi lido.
 */
@Composable
private fun CartaoDeDocumento(
    registo: RegistoDocumento,
    aoAbrir: () -> Unit,
    aoEsquecer: () -> Unit
) {
    val digitalizado = registo.provavelDigitalizacao == true
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Linha, RoundedCornerShape(12.dp))
            .clickable(onClick = aoAbrir)
            .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(9.dp)).background(Linha),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    if (digitalizado) R.drawable.ic_digitalizado else R.drawable.ic_documento
                ),
                contentDescription = stringResource(
                    if (digitalizado) R.string.documento_digitalizado else R.string.documento_com_texto
                ),
                tint = if (digitalizado) Apagado else Acento,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                registo.nome,
                style = EstiloInterface.copy(color = Texto, fontSize = 16.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Text(
                stringResource(R.string.folha, registo.pagina) + "  ·  " + quandoLegivel(registo.quando),
                style = EstiloEtiqueta
            )
        }

        Spacer(Modifier.width(10.dp))
        AnelDeProgresso(registo.percentagem)

        IconButton(onClick = aoEsquecer, modifier = Modifier.size(38.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_remover),
                contentDescription = stringResource(R.string.remover_da_lista),
                tint = Texto.copy(alpha = 0.4f),
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

/** Quanto já foi lido, em anel: lê-se de relance, sem contas. */
@Composable
private fun AnelDeProgresso(percentagem: Int) {
    val fraccao = (percentagem / 100f).coerceIn(0f, 1f)
    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val traco = 3.dp.toPx()
            val canto = Offset(traco / 2f, traco / 2f)
            val medida = Size(size.width - traco, size.height - traco)
            drawArc(
                color = Linha, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = canto, size = medida, style = Stroke(traco, cap = StrokeCap.Round)
            )
            if (fraccao > 0f) drawArc(
                color = Acento, startAngle = -90f, sweepAngle = 360f * fraccao, useCenter = false,
                topLeft = canto, size = medida, style = Stroke(traco, cap = StrokeCap.Round)
            )
        }
        Text(
            "$percentagem",
            style = EstiloEtiqueta.copy(color = Texto, fontSize = 12.sp)
        )
    }
}

@Composable
private fun quandoLegivel(quando: Long): String {
    if (quando <= 0L) return ""
    val dia = java.time.Instant.ofEpochMilli(quando).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val dias = java.time.temporal.ChronoUnit.DAYS.between(dia, java.time.LocalDate.now()).toInt()
    return when {
        dias <= 0 -> stringResource(R.string.hoje)
        dias == 1 -> stringResource(R.string.ontem)
        dias < 30 -> stringResource(R.string.ha_dias, dias)
        else -> dia.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }
}

@Composable
fun EcraLeitura(
    documento: Documento,
    sessao: SessaoDeLeitura.Estado,
    aoVoltarALista: () -> Unit,
    aoAlternar: () -> Unit,
    aoSaltar: (Int) -> Unit,
    aoIrPara: (Int) -> Unit,
    aoFecharAvisoVoz: () -> Unit,
    aoMudarVelocidade: (Float) -> Unit,
    aoMudarVoz: (String) -> Unit
) {
    val listaEstado = rememberLazyListState()

    // Quem percorre o documento com o dedo quer olhar para outro sítio sem
    // que a voz pare nem o texto lhe seja puxado de volta a meio da frase.
    // Depois de o dedo largar, o texto volta a seguir a voz — mas só passado
    // este tempo, e sem nunca interromper a leitura.
    var instanteDoDedo by remember { mutableStateOf(0L) }
    LaunchedEffect(listaEstado) {
        // Marca tanto o começo como o fim do deslize: a contagem só arranca
        // quando o dedo já largou.
        snapshotFlow { listaEstado.isScrollInProgress }.collect {
            instanteDoDedo = System.currentTimeMillis()
        }
    }

    // O texto segue a voz — só quando o parágrafo lido sai do ecrã, e só se
    // o leitor não andou agora mesmo a percorrer o documento.
    LaunchedEffect(sessao.indice, sessao.aLer) {
        if (!sessao.aLer) return@LaunchedEffect
        if (System.currentTimeMillis() - instanteDoDedo < ESPERA_DEPOIS_DO_DEDO) return@LaunchedEffect
        val visiveis = listaEstado.layoutInfo.visibleItemsInfo
        val aVista = visiveis.any { it.index == sessao.indice }
        if (!aVista) listaEstado.animateScrollToItem(sessao.indice)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Cabecalho(documento, aoVoltarALista)

        when {
            documento.paragrafos.isEmpty() -> Aviso(stringResource(R.string.sem_texto))
            documento.provavelDigitalizacao -> Aviso(
                stringResource(
                    R.string.aviso_digitalizado,
                    documento.paginasComTexto,
                    documento.totalPaginas
                )
            )
        }

        if (sessao.mostrarAvisoVoz) {
            Aviso(stringResource(R.string.aviso_voz_brasil), aoFechar = aoFecharAvisoVoz)
        } else if (sessao.semVozParaOIdioma || sessao.voz == SessaoDeLeitura.Voz.NENHUMA) {
            Aviso(stringResource(R.string.aviso_voz_nenhuma))
        }

        LazyColumn(
            state = listaEstado,
            modifier = Modifier.fillMaxSize().weight(1f),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 24.dp)
        ) {
            itemsIndexed(documento.paragrafos, key = { _, p -> p.id }) { indice, p ->
                Column {
                    if (p.indiceNaPagina == 0) MarcaDeFolha(p.pagina)
                    BlocoDeTexto(
                        paragrafo = p,
                        actual = indice == sessao.indice,
                        aoTocar = { aoIrPara(indice) }
                    )
                }
            }
        }

        BarraDeBaixo(sessao, aoAlternar, aoSaltar, aoMudarVelocidade, aoMudarVoz)
    }
}

@Composable
private fun Cabecalho(documento: Documento, aoVoltarALista: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 18.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                documento.nome,
                style = EstiloInterface.copy(color = Texto, fontSize = 15.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = aoVoltarALista) {
                Text(stringResource(R.string.voltar_a_lista), style = EstiloInterface.copy(color = Acento))
            }
        }
        Text(
            stringResource(R.string.paragrafos_conta, documento.paragrafos.size, documento.totalPaginas),
            style = EstiloEtiqueta
        )
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Linha))
    }
}

@Composable
private fun Aviso(texto: String, aoFechar: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(2.dp).height(44.dp).background(Acento))
        Spacer(Modifier.width(12.dp))
        Text(texto, style = EstiloInterface.copy(color = Texto), modifier = Modifier.weight(1f))
        if (aoFechar != null) {
            TextButton(onClick = aoFechar) {
                Text(stringResource(R.string.entendido), style = EstiloInterface.copy(color = Acento))
            }
        }
    }
}

@Composable
private fun MarcaDeFolha(pagina: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.folha, pagina), style = EstiloEtiqueta)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Linha))
    }
}

/** Quanto tempo o texto deixa de seguir a voz depois de alguém lhe tocar. */
private const val ESPERA_DEPOIS_DO_DEDO = 15_000L

@Composable
private fun BlocoDeTexto(paragrafo: Paragraph, actual: Boolean, aoTocar: () -> Unit) {
    // A marca do parágrafo em leitura é uma barra fina à esquerda —
    // sem caixa nem fundo, que numa página inteira de texto cansa a vista.
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            // `clickable` dava por toque qualquer dedo que andasse menos do
            // que o limiar de deslize: bastava começar a percorrer o texto
            // devagar para a leitura saltar de sítio. Aqui só conta o dedo
            // que pousa e levanta praticamente sem andar.
            .pointerInput(aoTocar) {
                val limite = viewConfiguration.touchSlop / 3f
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var andou = 0f
                    var roubado = false
                    while (true) {
                        val evento = awaitPointerEvent()
                        evento.changes.forEach { mudanca ->
                            andou += (mudanca.position - mudanca.previousPosition).getDistance()
                            if (mudanca.isConsumed) roubado = true
                        }
                        if (evento.changes.none { it.pressed }) break
                    }
                    if (!roubado && andou <= limite) aoTocar()
                }
            }
    ) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(if (actual) Acento else Fundo)
        )
        Text(
            text = paragrafo.texto,
            style = EstiloParagrafo.copy(color = if (actual) Texto else Texto.copy(alpha = 0.62f)),
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp)
        )
    }
}

@Composable
private fun BarraDeBaixo(
    sessao: SessaoDeLeitura.Estado,
    aoAlternar: () -> Unit,
    aoSaltar: (Int) -> Unit,
    aoMudarVelocidade: (Float) -> Unit,
    aoMudarVoz: (String) -> Unit
) {
    var painelAberto by remember { mutableStateOf(false) }
    val fraccao = if (sessao.total > 0) (sessao.indice + 1f) / sessao.total else 0f

    Column(Modifier.fillMaxWidth().background(Fundo)) {

        if (painelAberto) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.velocidade), style = EstiloEtiqueta)
                Spacer(Modifier.height(6.dp))
                // Menos e mais, com o nome pelo meio. Números com «×» diziam
                // pouco a quem não anda nisto.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val i = indiceDaVelocidade(sessao.velocidade)
                    IconButton(
                        onClick = { aoMudarVelocidade(VELOCIDADES[(i - 1).coerceAtLeast(0)]) },
                        enabled = i > 0
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_menos),
                            contentDescription = stringResource(R.string.mais_devagar),
                            tint = if (i > 0) Texto else Apagado,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        stringResource(nomeDaVelocidade(sessao.velocidade)),
                        style = EstiloInterface.copy(color = Texto, fontSize = 16.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { aoMudarVelocidade(VELOCIDADES[(i + 1).coerceAtMost(VELOCIDADES.lastIndex)]) },
                        enabled = i < VELOCIDADES.lastIndex
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mais),
                            contentDescription = stringResource(R.string.mais_depressa),
                            tint = if (i < VELOCIDADES.lastIndex) Texto else Apagado,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                if (sessao.vozes.size > 1) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_globo),
                            contentDescription = null,
                            tint = Apagado,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.lingua_da_voz), style = EstiloEtiqueta)
                    }
                    Spacer(Modifier.height(4.dp))
                    // Uma linha por voz, com o nome escrito por extenso.
                    // Toca-se e fica escolhida: sem listas de códigos.
                    // Altura travada: há motores com uma dúzia de vozes, e a
                    // lista inteira empurrava os comandos para fora do ecrã.
                    Column(
                        Modifier
                            .heightIn(max = 232.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                    sessao.vozes.forEach { voz ->
                        val escolhida = voz.nome == sessao.vozActual
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { aoMudarVoz(voz.nome) }
                                .padding(horizontal = 4.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                voz.etiqueta,
                                style = EstiloInterface.copy(
                                    color = if (escolhida) Acento else Texto,
                                    fontSize = 15.sp
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            if (escolhida) Text("✓", style = EstiloInterface.copy(color = Acento, fontSize = 15.sp))
                        }
                    }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        LinearProgressIndicator(
            progress = { fraccao },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Acento,
            trackColor = Linha,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { aoSaltar(-1) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_atras),
                    contentDescription = stringResource(R.string.atras),
                    tint = Texto,
                    modifier = Modifier.size(26.dp)
                )
            }
            IconButton(onClick = aoAlternar, modifier = Modifier.size(56.dp)) {
                Icon(
                    painter = painterResource(if (sessao.aLer) R.drawable.ic_pausa else R.drawable.ic_play),
                    contentDescription = stringResource(if (sessao.aLer) R.string.pausa else R.string.tocar),
                    tint = Acento,
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(onClick = { aoSaltar(1) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_frente),
                    contentDescription = stringResource(R.string.frente),
                    tint = Texto,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.posicao_notificacao, sessao.indice + 1, sessao.total),
                style = EstiloEtiqueta
            )
            IconButton(onClick = { painelAberto = !painelAberto }) {
                Icon(
                    painter = painterResource(R.drawable.ic_globo),
                    contentDescription = stringResource(R.string.lingua_da_voz),
                    tint = if (painelAberto) Acento else Texto,
                    modifier = Modifier.size(22.dp)
                )
            }
            TextButton(onClick = { painelAberto = !painelAberto }) {
                Text(
                    stringResource(nomeDaVelocidade(sessao.velocidade)),
                    style = EstiloEtiqueta.copy(color = if (painelAberto) Acento else Texto),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/** Qual dos seis degraus é o actual. */
private fun indiceDaVelocidade(v: Float): Int {
    val i = VELOCIDADES.indexOfFirst { kotlin.math.abs(it - v) < 0.01f }
    return if (i >= 0) i else VELOCIDADES.indexOf(1f)
}

/** A velocidade dita por palavras: «Normal» diz mais do que «1,25×». */
private fun nomeDaVelocidade(v: Float): Int = when (indiceDaVelocidade(v)) {
    0 -> R.string.v_devagar          // 0,75
    1 -> R.string.v_normal           // 1
    2 -> R.string.v_um_pouco_mais    // 1,25
    3 -> R.string.v_rapido           // 1,5
    4 -> R.string.v_muito_rapido     // 1,75
    else -> R.string.v_o_mais_rapido // 2
}

@Composable
private fun Ficha(texto: String, escolhida: Boolean, aoTocar: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, if (escolhida) Acento else Linha, RoundedCornerShape(4.dp))
            .clickable(onClick = aoTocar)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(texto, style = EstiloEtiqueta.copy(color = if (escolhida) Acento else Texto))
    }
}
