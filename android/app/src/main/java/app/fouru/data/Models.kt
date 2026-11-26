package app.fouru.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileName(
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_path") val avatarPath: String? = null,
)

@Serializable
data class ContactRow(
    @SerialName("contact_id") val contactId: String,
    val status: String,
    val nickname: String? = null,
    val contact: ProfileName? = null,
) {
    val name: String get() = nickname ?: contact?.displayName ?: "?"
    val displayName: String get() = contact?.displayName ?: "?"
    val avatarPath: String? get() = contact?.avatarPath
    val blocked: Boolean get() = status == "blocked"
}

@Serializable
data class MessageRow(
    val id: String,
    val type: String,
    @SerialName("storage_path") val storagePath: String? = null,
    @SerialName("thumb_path") val thumbPath: String? = null,
    val caption: String? = null,
    @SerialName("sender_id") val senderId: String,
    @SerialName("created_at") val createdAt: String,
    val sender: ProfileName? = null,
)

@Serializable
data class PendingRow(
    @SerialName("message_id") val messageId: String,
    val message: MessageRow? = null,
)

/** Elemento recibido guardado en local (historial + widget). */
@Serializable
data class HistoryItem(
    val id: String,
    val type: String,
    val senderId: String,
    val senderName: String,
    val caption: String? = null,
    /** content:// de MediaStore (foto/vídeo/gif) o ruta absoluta (html/dibujo). */
    val localUri: String? = null,
    /** Miniatura JPEG para el widget (filesDir/thumbs). */
    val thumbPath: String? = null,
    val receivedAt: Long,
    val seen: Boolean = false,
    val reaction: String? = null,
)

@Serializable
data class OutRecipient(
    val id: String,
    val name: String,
    val delivered: Boolean,
)

/** Envío propio programado o que algún destinatario aún no ha recibido (RPC my_pending). */
@Serializable
data class OutPending(
    val id: String,
    val type: String,
    val caption: String? = null,
    @SerialName("storage_path") val storagePath: String? = null,
    @SerialName("thumb_path") val thumbPath: String? = null,
    val status: String,
    @SerialName("deliver_at") val deliverAt: String,
    @SerialName("created_at") val createdAt: String,
    val recipients: List<OutRecipient> = emptyList(),
) {
    val scheduled: Boolean get() = status == "scheduled"
}

@Serializable
data class SentRecipient(
    val id: String,
    val name: String,
    val delivered: Boolean,
    val seen: Boolean,
    val reaction: String? = null,
)

/** Envío propio ya salido (RPC my_sent). */
@Serializable
data class SentItem(
    val id: String,
    val type: String,
    val caption: String? = null,
    @SerialName("thumb_path") val thumbPath: String? = null,
    @SerialName("sent_at") val sentAt: String? = null,
    val recipients: List<SentRecipient> = emptyList(),
)

/** Lo que le falta a este dispositivo (RPC device_inbox). */
@Serializable
data class InboxMessage(
    val id: String,
    val type: String,
    @SerialName("storage_path") val storagePath: String? = null,
    @SerialName("thumb_path") val thumbPath: String? = null,
    val caption: String? = null,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_name") val senderName: String? = null,
    @SerialName("sent_at") val sentAt: String,
    val seen: Boolean = false,
    val reaction: String? = null,
)
