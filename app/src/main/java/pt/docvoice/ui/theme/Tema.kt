package pt.docvoice.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Fundo = Color(0xFF15181C)
val Texto = Color(0xFFE9E4DA)
val Acento = Color(0xFFD9A441)
val Apagado = Color(0xFF7F7A6E)
val Linha = Color(0x407F7A6E)

/**
 * Por resolver com texto real: serifas em cirílico
 * costumam sair tortas no Android. Se ficar mau, trocar por FontFamily.Default —
 * é esta linha e mais nenhuma.
 */
val FonteLeitura = FontFamily.Serif

/** 19sp, entrelinha 1,7. */
val EstiloParagrafo = TextStyle(
    fontFamily = FonteLeitura,
    fontSize = 19.sp,
    lineHeight = 32.sp,
    color = Texto
)

val EstiloInterface = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    color = Apagado
)

val EstiloEtiqueta = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.8.sp,
    fontWeight = FontWeight.Medium,
    color = Apagado
)

@Composable
fun DocVoiceTheme(conteudo: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Acento,
            onPrimary = Fundo,
            background = Fundo,
            onBackground = Texto,
            surface = Fundo,
            onSurface = Texto,
            outline = Apagado,
            error = Acento
        ),
        content = conteudo
    )
}
