package pt.docvoice

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import pt.docvoice.dados.GuardaDePosicao
import pt.docvoice.dados.GuardaDePreferencias

class DocVoiceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // O PdfBox-Android precisa disto antes de tocar em qualquer PDF.
        PDFBoxResourceLoader.init(applicationContext)
        // A posição guarda-se sozinha, parágrafo a parágrafo, venha o comando de onde vier.
        GuardaDePosicao.ligar(applicationContext)
        GuardaDePreferencias.ligar(applicationContext)
    }
}
