package pt.docvoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Intent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.docvoice.R
import pt.docvoice.text.Marca
import pt.docvoice.text.principioDe
import pt.docvoice.ui.theme.Acento
import pt.docvoice.ui.theme.Apagado
import pt.docvoice.ui.theme.EstiloEtiqueta
import pt.docvoice.ui.theme.EstiloInterface
import pt.docvoice.ui.theme.EstiloParagrafo
import pt.docvoice.ui.theme.Fundo
import pt.docvoice.ui.theme.Linha
import pt.docvoice.ui.theme.Texto

/**
 * A lista do que se apanhou a ouvir.
 *
 * Mostra o princípio de cada parágrafo marcado para se reconhecer o sítio sem
 * lá ir — que é o ponto todo: de trezentas folhas fica uma folha de argumentos
 * que se lê de uma assentada, e só se salta para o documento quando é preciso
 * ver o resto.
 */
@Composable
fun EcraMarcas(
    nomeDoDocumento: String,
    marcas: List<Marca>,
    textoDoParagrafo: (Marca) -> String?,
    aoIrPara: (Marca) -> Unit,
    aoTirar: (Marca) -> Unit,
    aoEscreverNota: (Marca, String) -> Unit,
    textoParaCopiar: () -> String,
    aoVoltar: () -> Unit
) {
    val prancheta = LocalClipboardManager.current
    val ctx = LocalContext.current
    var copiada by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Fundo).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.marcas_titulo), style = EstiloParagrafo.copy(fontSize = 22.sp))
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.marcas_conta, marcas.size), style = EstiloEtiqueta)
            }
            TextButton(onClick = aoVoltar) {
                Text(stringResource(R.string.voltar), style = EstiloInterface.copy(color = Acento))
            }
        }

        if (marcas.isEmpty()) {
            Text(
                stringResource(R.string.marcas_nenhuma),
                style = EstiloEtiqueta.copy(lineHeight = 22.sp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
            return@Column
        }

        // Copiar a lista toda: uma só vez, e vai tudo — número, folha,
        // princípio do parágrafo e a nota de quem ouviu.
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .border(1.dp, Acento, RoundedCornerShape(8.dp))
                    .clickable {
                        prancheta.setText(AnnotatedString(textoParaCopiar()))
                        copiada = true
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    stringResource(R.string.marcas_copiar),
                    style = EstiloInterface.copy(color = Acento, fontSize = 14.sp)
                )
            }
            Spacer(Modifier.width(10.dp))
            // Enviar: quem escolhe para onde é quem carrega. A aplicação entrega
            // o texto ao sistema e o sistema mostra o que está instalado —
            // WhatsApp, Telegram, correio. Não há aqui nenhuma ligação à rede,
            // nem destinatário escrito em lado nenhum, e continua a não haver
            // autorização de Internet no manifesto.
            Box(
                Modifier
                    .border(1.dp, Acento, RoundedCornerShape(8.dp))
                    .clickable {
                        val texto = textoParaCopiar()
                        val envio = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, nomeDoDocumento)
                            putExtra(Intent.EXTRA_TEXT, texto)
                        }
                        val escolha = Intent.createChooser(
                            envio, ctx.getString(R.string.marcas_enviar)
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        runCatching { ctx.startActivity(escolha) }
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    stringResource(R.string.marcas_enviar),
                    style = EstiloInterface.copy(color = Acento, fontSize = 14.sp)
                )
            }
            if (copiada) {
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.marcas_copiada), style = EstiloEtiqueta.copy(color = Acento))
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp)
        ) {
            itemsIndexed(marcas, key = { _, m -> m.id }) { i, marca ->
                ItemDeMarca(
                    numero = i + 1,
                    marca = marca,
                    principio = textoDoParagrafo(marca)?.let { principioDe(it) },
                    aoIrPara = { aoIrPara(marca) },
                    aoTirar = { aoTirar(marca) },
                    aoEscreverNota = { aoEscreverNota(marca, it) }
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(Linha))
            }
        }
    }
}

@Composable
private fun ItemDeMarca(
    numero: Int,
    marca: Marca,
    principio: String?,
    aoIrPara: () -> Unit,
    aoTirar: () -> Unit,
    aoEscreverNota: (String) -> Unit
) {
    var aEscrever by remember(marca.id) { mutableStateOf(false) }
    var rascunho by remember(marca.id) { mutableStateOf(marca.nota) }
    val foco = remember(marca.id) { FocusRequester() }
    val teclado = LocalSoftwareKeyboardController.current

    // Abrir o campo tem de bastar. Antes era preciso um segundo toque dentro
    // dele para o teclado aparecer — e quem carregava em «escrever uma nota»
    // e começava a escrever ficava a ver o que escrevia ir para o vazio.
    LaunchedEffect(aEscrever) {
        if (aEscrever) { foco.requestFocus(); teclado?.show() }
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = aoIrPara),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                "$numero.",
                style = EstiloEtiqueta.copy(color = Acento),
                modifier = Modifier.width(30.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.marcas_folha, marca.pagina),
                    style = EstiloEtiqueta.copy(color = Apagado)
                )
                if (principio != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(principio, style = EstiloParagrafo.copy(fontSize = 16.sp, lineHeight = 24.sp))
                }
                if (marca.nota.isNotBlank() && !aEscrever) {
                    Spacer(Modifier.height(6.dp))
                    Text("— ${marca.nota}", style = EstiloEtiqueta.copy(color = Acento))
                }
            }
        }

        if (aEscrever) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(start = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = rascunho,
                    onValueChange = { rascunho = it },
                    // Não é só uma linha: aqui cola-se um bocado do texto que
                    // se seleccionou lá atrás, e um pedaço de uma sentença não
                    // cabe numa linha. Cresce até cinco e depois rola.
                    singleLine = false,
                    maxLines = 5,
                    textStyle = EstiloInterface.copy(color = Texto, fontSize = 15.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Acento),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { aoEscreverNota(rascunho); aEscrever = false; teclado?.hide() }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(foco)
                        .border(1.dp, Linha, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                )
                TextButton(onClick = {
                    aoEscreverNota(rascunho); aEscrever = false; teclado?.hide()
                }) {
                    Text(
                        stringResource(R.string.marcas_nota_guardar),
                        style = EstiloEtiqueta.copy(color = Acento)
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            if (!aEscrever) {
                TextButton(onClick = { aEscrever = true }) {
                    Text(
                        stringResource(R.string.marcas_nota_por),
                        style = EstiloEtiqueta.copy(color = Apagado)
                    )
                }
            }
            TextButton(onClick = aoTirar) {
                Text(stringResource(R.string.marcas_apagar), style = EstiloEtiqueta.copy(color = Apagado))
            }
        }
    }
}
