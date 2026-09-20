package pt.docvoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.docvoice.R
import pt.docvoice.ocr.AlcanceDoOcr
import pt.docvoice.ocr.IdiomaOcr
import pt.docvoice.ocr.PlanoDeOcr
import pt.docvoice.ui.theme.Acento
import pt.docvoice.ui.theme.Apagado
import pt.docvoice.ui.theme.EstiloEtiqueta
import pt.docvoice.ui.theme.EstiloInterface
import pt.docvoice.ui.theme.Linha
import pt.docvoice.ui.theme.Texto

/**
 * O painel do reconhecimento, por cima do texto no ecrã de leitura.
 *
 * Só se mostra quando há alguma coisa a dizer. Um documento escrito nunca vê
 * isto, e um documento com duas fotografias no meio também não — ver
 * [PlanoDeOcr.propor]. Quem quiser reconhecer essas duas vai buscá-lo às
 * definições; aqui só aparece o que se impõe sozinho.
 */
@Composable
fun PainelDeOcr(
    plano: PlanoDeOcr?,
    estado: EstadoDoOcr,
    /** Pediram-no à mão, pelo cabeçalho: mostra-se mesmo acima do limiar. */
    pedido: Boolean = false,
    aoComecar: (IdiomaOcr, AlcanceDoOcr) -> Unit,
    aoParar: () -> Unit,
    aoLimpar: () -> Unit
) {
    var dispensado by remember { mutableStateOf(false) }

    when (estado) {
        is EstadoDoOcr.Parado -> {
            if (plano == null || plano.nadaAFazer) return
            // Propõe-se sozinho abaixo do limiar; acima dele, só a pedido.
            val mostrar = pedido || (plano.propor && !dispensado)
            if (!mostrar) return
            Proposta(plano, aoComecar) { dispensado = true; aoLimpar() }
        }
        is EstadoDoOcr.APreparar -> Moldura {
            Text(stringResource(R.string.ocr_a_preparar), style = EstiloInterface.copy(color = Texto))
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Acento, trackColor = Linha)
        }
        is EstadoDoOcr.AProcessar -> Moldura {
            val p = estado.progresso
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.ocr_a_processar, p.feitas, p.total),
                    style = EstiloInterface.copy(color = Texto),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = aoParar) {
                    Text(stringResource(R.string.ocr_parar), style = EstiloInterface.copy(color = Acento))
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (p.total == 0) 0f else p.feitas.toFloat() / p.total },
                modifier = Modifier.fillMaxWidth(),
                color = Acento,
                trackColor = Linha
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.ocr_demora), style = EstiloEtiqueta.copy(color = Apagado))
        }
        is EstadoDoOcr.Terminado -> Fecho(
            if (estado.folhas == 0) stringResource(R.string.ocr_nada_lido)
            else stringResource(R.string.ocr_terminado, estado.folhas),
            aoLimpar
        )
        is EstadoDoOcr.Interrompido ->
            Fecho(stringResource(R.string.ocr_interrompido, estado.folhas), aoLimpar)
        is EstadoDoOcr.Falhou -> Fecho(estado.mensagem, aoLimpar)
    }
}

@Composable
private fun Proposta(
    plano: PlanoDeOcr,
    aoComecar: (IdiomaOcr, AlcanceDoOcr) -> Unit,
    aoDispensar: () -> Unit
) {
    var idioma by remember { mutableStateOf(IdiomaOcr.PORTUGUES) }
    // Numa mistura pergunta-se; numa digitalização inteira não há nada a escolher.
    var alcance by remember { mutableStateOf(AlcanceDoOcr.SO_AS_QUE_FALTAM) }

    Moldura {
        Text(stringResource(R.string.ocr_titulo), style = EstiloInterface.copy(color = Texto))
        Spacer(Modifier.height(6.dp))
        Text(
            if (plano.digitalizacaoInteira)
                stringResource(R.string.ocr_explica_inteiro, plano.total)
            else
                stringResource(R.string.ocr_explica, plano.total, plano.paginasComTexto.size),
            style = EstiloEtiqueta.copy(color = Apagado)
        )

        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.ocr_perguntar_idioma), style = EstiloEtiqueta.copy(color = Apagado))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Escolha(stringResource(R.string.ocr_idioma_por), idioma == IdiomaOcr.PORTUGUES) {
                idioma = IdiomaOcr.PORTUGUES
            }
            Escolha(stringResource(R.string.ocr_idioma_rus), idioma == IdiomaOcr.RUSSO) {
                idioma = IdiomaOcr.RUSSO
            }
        }

        if (plano.mistura) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Escolha(
                    stringResource(R.string.ocr_so_as_que_faltam, plano.paginasSemTexto.size),
                    alcance == AlcanceDoOcr.SO_AS_QUE_FALTAM
                ) { alcance = AlcanceDoOcr.SO_AS_QUE_FALTAM }
                Escolha(
                    stringResource(R.string.ocr_todas, plano.total),
                    alcance == AlcanceDoOcr.TODAS
                ) { alcance = AlcanceDoOcr.TODAS }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = aoDispensar) {
                Text(stringResource(R.string.ocr_agora_nao), style = EstiloInterface.copy(color = Apagado))
            }
            TextButton(onClick = { aoComecar(idioma, alcance) }) {
                Text(stringResource(R.string.ocr_comecar), style = EstiloInterface.copy(color = Acento))
            }
        }
    }
}

@Composable
private fun Fecho(texto: String, aoLimpar: () -> Unit) = Moldura {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, style = EstiloInterface.copy(color = Texto), modifier = Modifier.weight(1f))
        TextButton(onClick = aoLimpar) {
            Text(stringResource(R.string.entendido), style = EstiloInterface.copy(color = Acento))
        }
    }
}

/** A mesma moldura de todos os avisos: risco de acento à esquerda. */
@Composable
private fun Moldura(conteudo: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) {
        Box(Modifier.width(2.dp).height(IntrinsicoDaColuna).background(Acento))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { conteudo() }
    }
}

private val IntrinsicoDaColuna = 44.dp

@Composable
private fun Escolha(texto: String, escolhida: Boolean, aoTocar: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, if (escolhida) Acento else Linha, RoundedCornerShape(4.dp))
            .clickable(onClick = aoTocar)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(texto, style = EstiloEtiqueta.copy(color = if (escolhida) Acento else Texto))
    }
}
