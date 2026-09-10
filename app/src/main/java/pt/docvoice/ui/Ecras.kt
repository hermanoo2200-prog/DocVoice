package pt.docvoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
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
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 22.dp, bottom = 14.dp),
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
        Box(Modifier.fillMaxWidth().height(1.dp).background(Linha))

        LazyColumn(Modifier.fillMaxSize()) {
            items(recentes, key = { it.chave }) { registo ->
                LinhaDeRecente(
                    registo = registo,
                    aoAbrir = { aoAbrirRecente(registo) },
                    aoEsquecer = { aoEsquecer(registo) }
                )
                Box(Modifier.fillMaxWidth().padding(start = 22.dp).height(1.dp).background(Linha))
            }
        }
    }
}

@Composable
private fun LinhaDeRecente(
    registo: RegistoDocumento,
    aoAbrir: () -> Unit,
    aoEsquecer: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = aoAbrir).padding(start = 22.dp, end = 6.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                registo.nome,
                style = EstiloInterface.copy(color = Texto, fontSize = 16.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.percentagem_lida, registo.percentagem) +
                    "  ·  " + stringResource(R.string.folha, registo.pagina) +
                    "  ·  " + quandoLegivel(registo.quando),
                style = EstiloEtiqueta
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { registo.percentagem / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Acento,
                trackColor = Linha,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
        IconButton(onClick = aoEsquecer) {
            Icon(
                painter = painterResource(R.drawable.ic_remover),
                contentDescription = stringResource(R.string.remover_da_lista),
                tint = Texto.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp)
            )
        }
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

    // O texto segue a voz — mas só quando o parágrafo lido sai do ecrã,
    // para não lutar contra quem está a percorrer o documento com o dedo.
    LaunchedEffect(sessao.indice, sessao.aLer) {
        if (!sessao.aLer) return@LaunchedEffect
        val visiveis = listaEstado.layoutInfo.visibleItemsInfo
        val aVista = visiveis.any { it.index == sessao.indice }
        if (!aVista) listaEstado.animateScrollToItem(sessao.indice)
    }

    Column(Modifier.fillMaxSize()) {
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

@Composable
private fun BlocoDeTexto(paragrafo: Paragraph, actual: Boolean, aoTocar: () -> Unit) {
    // A marca do parágrafo em leitura é uma barra fina à esquerda —
    // sem caixa nem fundo, que numa página inteira de texto cansa a vista.
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = aoTocar)
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
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VELOCIDADES.forEach { v ->
                        Ficha(
                            texto = etiquetaDeVelocidade(v),
                            escolhida = kotlin.math.abs(v - sessao.velocidade) < 0.01f,
                            aoTocar = { aoMudarVelocidade(v) }
                        )
                    }
                }
                if (sessao.vozes.size > 1) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.voz), style = EstiloEtiqueta)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sessao.vozes.forEach { voz ->
                            Ficha(
                                texto = voz.etiqueta,
                                escolhida = voz.nome == sessao.vozActual,
                                aoTocar = { aoMudarVoz(voz.nome) }
                            )
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
            IconButton(onClick = { aoSaltar(-5) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_atras),
                    contentDescription = stringResource(R.string.atras_5),
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
            IconButton(onClick = { aoSaltar(5) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_frente),
                    contentDescription = stringResource(R.string.frente_5),
                    tint = Texto,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.posicao_notificacao, sessao.indice + 1, sessao.total),
                style = EstiloEtiqueta
            )
            TextButton(onClick = { painelAberto = !painelAberto }) {
                Text(
                    etiquetaDeVelocidade(sessao.velocidade),
                    style = EstiloEtiqueta.copy(color = if (painelAberto) Acento else Texto)
                )
            }
        }
    }
}

/** 1,25× — vírgula decimal, como se escreve em português. */
private fun etiquetaDeVelocidade(v: Float): String {
    val texto = if (v == v.toInt().toFloat()) "${v.toInt()}" else "%.2f".format(v).trimEnd('0').trimEnd('.', ',')
    return texto.replace('.', ',') + "×"
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
