package app.fouru.push

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.fouru.data.InboxSync
import app.fouru.data.Supa
import app.fouru.widget.FourUWidget
import io.github.jan.supabase.auth.auth
import java.util.concurrent.TimeUnit

/** Sincronización en segundo plano (al llegar una push o al abrir la app). Ver [InboxSync]. */
class InboxWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    // Necesario para trabajos expedited en Android < 12.
    override suspend fun getForegroundInfo() = ForegroundInfo(4242, Notifier.syncNotification(applicationContext))

    override suspend fun doWork(): Result {
        Supa.client.auth.awaitInitialization()
        return try {
            if (InboxSync.run(applicationContext) > 0) FourUWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Fallo sincronizando", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "InboxWorker"

        fun enqueue(ctx: Context) {
            val request = OneTimeWorkRequestBuilder<InboxWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            // REPLACE: un trabajo nuevo sustituye al que hubiera. Con APPEND, si uno
            // se quedaba reintentando (p. ej. con una sesión caducada), los nuevos
            // esperaban detrás durante horas y el móvil dejaba de recibir.
            WorkManager.getInstance(ctx).enqueueUniqueWork("inbox-sync", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
