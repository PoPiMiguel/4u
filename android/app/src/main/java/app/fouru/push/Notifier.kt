package app.fouru.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.fouru.R
import app.fouru.data.HistoryItem
import app.fouru.ui.MainActivity

object Notifier {
    private const val CH_MESSAGES = "messages"
    private const val CH_SYNC = "sync"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MESSAGES, "Mensajes", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_SYNC, "Sincronización", NotificationManager.IMPORTANCE_MIN))
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx,
        0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission")
    private fun show(ctx: Context, id: Int, title: String, body: String) {
        if (!canNotify(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            // La push la muestra al llegar y el worker la actualiza al descargar: suena una vez.
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    private fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun body(type: String, caption: String?): String = caption?.takeIf { it.isNotBlank() } ?: when (type) {
        "ping" -> "Está pensando en ti"
        "photo" -> "Foto nueva"
        "video" -> "Vídeo nuevo"
        "gif" -> "GIF nuevo"
        "html" -> "Animación nueva"
        "drawing" -> "Dibujo nuevo"
        else -> "Mensaje nuevo"
    }

    /** Al llegar la push, antes de descargar nada (funciona con la app cerrada). */
    fun incoming(ctx: Context, messageId: String, sender: String, type: String, caption: String?) =
        show(ctx, messageId.hashCode(), sender.ifEmpty { "4u" }, body(type, caption))

    /** Tras descargar (mismo id que [incoming]: si ya se mostró, no vuelve a sonar). */
    fun message(ctx: Context, item: HistoryItem) =
        show(ctx, item.id.hashCode(), item.senderName, body(item.type, item.caption))

    fun receipt(ctx: Context, name: String, reaction: String, seen: Boolean) {
        val body = when {
            reaction.isNotEmpty() -> "Ha reaccionado $reaction"
            seen -> "Lo ha visto"
            else -> return
        }
        show(ctx, (name + reaction + seen).hashCode(), name.ifEmpty { "4u" }, body)
    }

    fun vibratePing(ctx: Context) {
        val vibrator = ctx.getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120, 80, 300), -1))
    }

    fun syncNotification(ctx: Context): Notification =
        NotificationCompat.Builder(ctx, CH_SYNC)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("4u")
            .setContentText("Recibiendo…")
            .build()
}
