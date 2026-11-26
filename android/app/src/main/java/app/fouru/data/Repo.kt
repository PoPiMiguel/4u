package app.fouru.data

import android.content.Context
import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant
import java.util.UUID

/** Acceso a Supabase. Mismas operaciones que desktop/src/lib/api.ts. */
object Repo {
    private val supabase get() = Supa.client

    fun uidOrNull(): String? = supabase.auth.currentUserOrNull()?.id
    fun uid(): String = uidOrNull() ?: error("No hay sesión iniciada")

    // ---------- Auth ----------
    suspend fun signIn(email: String, password: String) {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String, name: String) {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject { put("display_name", name) }
        }
    }

    suspend fun signOut() = supabase.auth.signOut()

    suspend fun registerDevice(ctx: Context) {
        val token = FirebaseMessaging.getInstance().token.await()
        supabase.postgrest.rpc("register_device", buildJsonObject {
            put("p_id", LocalStore.deviceId(ctx))
            put("p_platform", "android")
            put("p_name", Build.MODEL)
            put("p_fcm_token", token)
        })
    }

    // ---------- Contactos ----------
    suspend fun createInvite(): String =
        supabase.postgrest.rpc("create_invite").decodeAs<String>()

    suspend fun acceptInvite(code: String) {
        supabase.postgrest.rpc("accept_invite", buildJsonObject { put("p_code", code.trim().uppercase()) })
    }

    suspend fun contacts(): List<ContactRow> =
        supabase.from("contacts")
            .select(Columns.raw("contact_id, status, nickname, contact:profiles!contacts_contact_id_fkey(display_name, avatar_path)")) {
                filter { eq("user_id", uid()) }
            }
            .decodeList<ContactRow>()
            .sortedBy { it.name.lowercase() }

    /** Apodo solo para mí; null = volver a su nombre. */
    suspend fun renameContact(contactId: String, nickname: String?) {
        supabase.from("contacts").update({ set("nickname", nickname) }) {
            filter {
                eq("user_id", uid())
                eq("contact_id", contactId)
            }
        }
    }

    /** Bloquear corta el envío en los dos sentidos (DECISIONS §3). */
    suspend fun setBlocked(contactId: String, blocked: Boolean) {
        supabase.from("contacts").update({ set("status", if (blocked) "blocked" else "accepted") }) {
            filter {
                eq("user_id", uid())
                eq("contact_id", contactId)
            }
        }
    }

    /** Elimina el contacto en los dos sentidos. */
    suspend fun removeContact(contactId: String) {
        supabase.postgrest.rpc("remove_contact", buildJsonObject { put("p_contact", contactId) })
    }

    // ---------- Perfil ----------
    suspend fun profile(): ProfileName =
        supabase.from("profiles").select(Columns.raw("display_name, avatar_path")) {
            filter { eq("id", uid()) }
        }.decodeSingle<ProfileName>()

    suspend fun setDisplayName(name: String) {
        supabase.from("profiles").update({ set("display_name", name) }) { filter { eq("id", uid()) } }
    }

    /** Sube un avatar nuevo (JPEG cuadrado) y borra el anterior. */
    suspend fun setAvatar(jpeg: ByteArray, previous: String?) {
        val path = "${uid()}/${UUID.randomUUID()}.jpg"
        val bucket = supabase.storage.from("avatars")
        bucket.upload(path, jpeg)
        supabase.from("profiles").update({ set("avatar_path", path) }) { filter { eq("id", uid()) } }
        if (previous != null) runCatching { bucket.delete(previous) }
    }

    private val avatarCache = mutableMapOf<String, ByteArray>()

    /** Bytes de un avatar (cambia de nombre al cambiar, así que se puede cachear). */
    suspend fun avatar(path: String): ByteArray =
        avatarCache[path] ?: supabase.storage.from("avatars").downloadAuthenticated(path).also { avatarCache[path] = it }

    // ---------- Enviados ----------
    suspend fun sent(limit: Int = 30): List<SentItem> =
        supabase.postgrest.rpc("my_sent", buildJsonObject { put("p_limit", limit) }).decodeList<SentItem>()

    // ---------- Envío ----------
    suspend fun send(
        type: String,
        recipients: List<String>,
        file: ByteArray? = null,
        ext: String? = null,
        mime: String? = null,
        thumb: ByteArray? = null,
        caption: String? = null,
        deliverAt: Instant? = null,
    ): String {
        val me = uid()
        val id = UUID.randomUUID().toString()
        val bucket = supabase.storage.from("media")

        val storagePath = file?.let { bytes -> "$me/$id/original.${ext ?: "bin"}".also { bucket.upload(it, bytes) } }
        val thumbPath = thumb?.let { bytes -> "$me/$id/thumb.jpg".also { bucket.upload(it, bytes) } }

        supabase.postgrest.rpc("send_message", buildJsonObject {
            put("p_id", id)
            put("p_type", type)
            putJsonArray("p_recipients") { recipients.forEach { add(it) } }
            put("p_storage_path", storagePath)
            put("p_thumb_path", thumbPath)
            put("p_mime", mime)
            put("p_size", file?.size)
            put("p_caption", caption)
            put("p_deliver_at", deliverAt?.toString())
        })
        return id
    }

    // ---------- Envíos en espera ----------
    suspend fun outPending(): List<OutPending> =
        supabase.postgrest.rpc("my_pending").decodeList<OutPending>()

    /** Cancela un envío en espera. Si se borra entero, borra también sus archivos. */
    suspend fun cancelOutPending(item: OutPending) {
        val deleted = supabase.postgrest.rpc("cancel_pending", buildJsonObject { put("p_id", item.id) })
            .decodeAs<Boolean>()
        val paths = listOfNotNull(item.storagePath, item.thumbPath)
        if (deleted && paths.isNotEmpty()) {
            runCatching { supabase.storage.from("media").delete(paths) }
        }
    }

    // ---------- Recepción ----------
    suspend fun pending(): List<MessageRow> =
        supabase.from("message_recipients")
            .select(
                Columns.raw(
                    "message_id, message:messages!message_recipients_message_id_fkey(" +
                        "id, type, storage_path, thumb_path, caption, sender_id, created_at, " +
                        "sender:profiles!messages_sender_id_fkey(display_name))",
                ),
            ) {
                filter {
                    eq("recipient_id", uid())
                    exact("downloaded_at", null)
                }
            }
            .decodeList<PendingRow>()
            .mapNotNull { it.message }
            .sortedBy { it.createdAt }

    suspend fun download(path: String): ByteArray =
        supabase.storage.from("media").downloadAuthenticated(path)

    suspend fun markDownloaded(messageId: String) {
        val now = Instant.now().toString()
        supabase.from("message_recipients").update({
            set("delivered_at", now)
            set("downloaded_at", now)
        }) {
            filter {
                eq("message_id", messageId)
                eq("recipient_id", uid())
            }
        }
    }

    suspend fun markSeen(messageId: String) {
        supabase.from("message_recipients").update({ set("seen_at", Instant.now().toString()) }) {
            filter {
                eq("message_id", messageId)
                eq("recipient_id", uid())
            }
        }
    }

    suspend fun react(messageId: String, emoji: String?) {
        supabase.from("message_recipients").update({ set("reaction", emoji) }) {
            filter {
                eq("message_id", messageId)
                eq("recipient_id", uid())
            }
        }
    }
}
