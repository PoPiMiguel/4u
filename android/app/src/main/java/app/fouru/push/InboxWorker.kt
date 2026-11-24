package app.fouru.push

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.fouru.data.HistoryItem
import app.fouru.data.LocalStore
import app.fouru.data.MediaSaver
import app.fouru.data.MessageRow
import app.fouru.data.Repo
import app.fouru.data.Supa
import app.fouru.widget.FourUWidget
import io.github.jan.supabase.auth.auth

/** Descarga lo pendiente, lo guarda en local, confirma la descarga y refresca el widget. */
class InboxWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    // Necesario para trabajos expedited en Android < 12.
    override suspend fun getForegroundInfo() = ForegroundInfo(4242, Notifier.syncNotification(applicationContext))

    override suspend fun doWork(): Result {
        Supa.client.auth.awaitInitialization()
        if (Repo.uidOrNull() == null) return Result.success()

        return try {
            Repo.pending().forEach { process(it) }
            FourUWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Fallo sincronizando", e)
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    private suspend fun process(m: MessageRow) {
        val ctx = applicationContext

        // Ya guardado pero sin confirmar (falló la red): solo confirmar.
        if (LocalStore.contains(ctx, m.id)) {
            Repo.markDownloaded(m.id)
            return
        }

        var localUri: String? = null
        var thumb: String? = null

        m.storagePath?.let { path ->
            val bytes = Repo.download(path)
            val ext = path.substringAfterLast('.', "bin")
            val name = "4u_${m.id}.$ext"
            when (m.type) {
                "photo", "gif" -> {
                    val mime = if (m.type == "gif") "image/gif" else "image/jpeg"
                    localUri = MediaSaver.saveToGallery(ctx, bytes, name, mime, video = false).toString()
                    thumb = MediaSaver.saveThumb(ctx, m.id, MediaSaver.decodeScaled(bytes, 512))
                }
                "video" -> {
                    val mime = if (ext == "mov") "video/quicktime" else "video/$ext"
                    val uri = MediaSaver.saveToGallery(ctx, bytes, name, mime, video = true)
                    localUri = uri.toString()
                    thumb = MediaSaver.saveThumb(ctx, m.id, MediaSaver.videoFrame(ctx, uri))
                }
                else -> {
                    // html / drawing. TODO(M3): fotogramas del HTML para el widget (WebView offscreen).
                    localUri = MediaSaver.savePrivate(ctx, bytes, name).absolutePath
                }
            }
        }

        if (thumb == null) {
            m.thumbPath?.let { thumb = MediaSaver.saveThumb(ctx, m.id, MediaSaver.decodeScaled(Repo.download(it), 512)) }
        }

        val item = HistoryItem(
            id = m.id,
            type = m.type,
            senderId = m.senderId,
            senderName = m.sender?.displayName ?: "Alguien",
            caption = m.caption,
            localUri = localUri,
            thumbPath = thumb,
            receivedAt = System.currentTimeMillis(),
        )
        LocalStore.add(ctx, item)
        Repo.markDownloaded(m.id)
        Notifier.message(ctx, item)
    }

    companion object {
        private const val TAG = "InboxWorker"

        fun enqueue(ctx: Context) {
            val request = OneTimeWorkRequestBuilder<InboxWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("inbox-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
