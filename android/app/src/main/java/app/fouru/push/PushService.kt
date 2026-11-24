package app.fouru.push

import android.util.Log
import app.fouru.data.Repo
import app.fouru.data.Supa
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Recibe los mensajes de datos de FCM (Edge Function `notify`):
 *  - kind=message → descarga en segundo plano (InboxWorker) y actualiza el widget
 *  - kind=receipt → notificación de reacción / visto
 */
class PushService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            runCatching {
                Supa.client.auth.awaitInitialization()
                if (Repo.uidOrNull() != null) Repo.registerDevice(applicationContext)
            }.onFailure { Log.e(TAG, "No se pudo registrar el token", it) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["kind"]) {
            "message" -> {
                if (data["type"] == "ping") Notifier.vibratePing(this)
                InboxWorker.enqueue(this)
            }
            "receipt" -> Notifier.receipt(
                this,
                data["recipient_name"].orEmpty(),
                data["reaction"].orEmpty(),
                data["seen"] == "1",
            )
        }
    }

    private companion object {
        const val TAG = "PushService"
    }
}
