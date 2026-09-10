package pt.docvoice.leitura

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pt.docvoice.MainActivity
import pt.docvoice.R

/**
 * Mantém a leitura viva com a aplicação minimizada e o ecrã apagado.
 * Sem isto o sistema cala o TTS assim que o ecrã adormece.
 */
class LeituraService : Service() {

    companion object {
        const val CANAL = "leitura"
        const val ID_NOTIFICACAO = 1

        const val ACCAO_ALTERNAR = "pt.docvoice.ALTERNAR"
        const val ACCAO_ATRAS = "pt.docvoice.ATRAS"
        const val ACCAO_FRENTE = "pt.docvoice.FRENTE"
        const val ACCAO_FECHAR = "pt.docvoice.FECHAR"

        const val SALTO = 5

        private const val TECTO_DO_BLOQUEIO = 30 * 60 * 1000L      // meia hora
        private const val INTERVALO_DE_RENOVACAO = 10 * 60 * 1000L // dez minutos

        fun garantirEmMarcha(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, LeituraService::class.java))
        }
    }

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var renovacao: Job? = null

    /** Auscultadores retirados: calar, como faz qualquer leitor. */
    private val receptorDeRuido = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) SessaoDeLeitura.pausar()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        criarCanal()
        ContextCompat.registerReceiver(
            this,
            receptorDeRuido,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        escopo.launch {
            SessaoDeLeitura.estado.collectLatest { estado ->
                if (estado.aLer) segurarCpu() else largarCpu()
                notificar(construirNotificacao())
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Tem de acontecer nos primeiros segundos, antes de tratar a acção.
        arrancarEmPrimeiroPlano()

        when (intent?.action) {
            ACCAO_ALTERNAR -> SessaoDeLeitura.alternar()
            ACCAO_ATRAS -> SessaoDeLeitura.saltar(-SALTO)
            ACCAO_FRENTE -> SessaoDeLeitura.saltar(SALTO)
            ACCAO_FECHAR -> {
                SessaoDeLeitura.pausar()
                SessaoDeLeitura.libertar()
                pararTudo()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        largarCpu()
        runCatching { unregisterReceiver(receptorDeRuido) }
        escopo.cancel()
        super.onDestroy()
    }

    // --- notificação --------------------------------------------------------

    private fun criarCanal() {
        val gestor = getSystemService(NotificationManager::class.java)
        if (gestor.getNotificationChannel(CANAL) == null) {
            val canal = NotificationChannel(
                CANAL,
                getString(R.string.canal_leitura),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            gestor.createNotificationChannel(canal)
        }
    }

    private fun accao(nome: String): PendingIntent = PendingIntent.getService(
        this,
        nome.hashCode(),
        Intent(this, LeituraService::class.java).setAction(nome),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun construirNotificacao(): Notification {
        val estado = SessaoDeLeitura.estado.value
        val documento = estado.documento

        val abrir = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val posicao = if (estado.total > 0) {
            getString(R.string.posicao_notificacao, estado.indice + 1, estado.total)
        } else ""

        return NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(documento?.nome ?: getString(R.string.app_name))
            .setContentText(posicao)
            .setContentIntent(abrir)
            .setDeleteIntent(accao(ACCAO_FECHAR))
            .setOngoing(estado.aLer)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(R.drawable.ic_atras, getString(R.string.atras_5), accao(ACCAO_ATRAS))
            .addAction(
                if (estado.aLer) R.drawable.ic_pausa else R.drawable.ic_play,
                getString(if (estado.aLer) R.string.pausa else R.string.tocar),
                accao(ACCAO_ALTERNAR)
            )
            .addAction(R.drawable.ic_frente, getString(R.string.frente_5), accao(ACCAO_FRENTE))
            .build()
    }

    private fun arrancarEmPrimeiroPlano() {
        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0
        ServiceCompat.startForeground(this, ID_NOTIFICACAO, construirNotificacao(), tipo)
    }

    private fun notificar(notificacao: Notification) {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(ID_NOTIFICACAO, notificacao)
        }
    }

    private fun pararTudo() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- cpu ----------------------------------------------------------------

    /**
     * Serviço em primeiro plano não garante CPU acordado: com o ecrã apagado
     * a síntese pode parar a meio. O bloqueio só vive enquanto se lê.
     *
     * O tecto é de meia hora, mas renova-se de dez em dez minutos enquanto a
     * leitura durar: um documento de quatro horas não pode calar-se a meio
     * sem dizer nada. Se o processo morrer, o tecto solta o bloqueio sozinho.
     */
    private fun segurarCpu() {
        val gestor = getSystemService(PowerManager::class.java)
        val bloqueio = wakeLock ?: gestor
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "docvoice:leitura")
            .also { it.setReferenceCounted(false); wakeLock = it }
        bloqueio.acquire(TECTO_DO_BLOQUEIO)   // voltar a pedir estende o prazo
        if (renovacao?.isActive != true) {
            renovacao = escopo.launch {
                while (SessaoDeLeitura.estado.value.aLer) {
                    delay(INTERVALO_DE_RENOVACAO)
                    if (SessaoDeLeitura.estado.value.aLer) {
                        runCatching { wakeLock?.acquire(TECTO_DO_BLOQUEIO) }
                    }
                }
            }
        }
    }

    private fun largarCpu() {
        renovacao?.cancel()
        renovacao = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
