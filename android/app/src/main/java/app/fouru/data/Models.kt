package app.fouru.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileName(
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class ContactRow(
    @SerialName("contact_id") val contactId: String,
    val status: String,
    val nickname: String? = null,
    val contact: ProfileName? = null,
) {
    val name: String get() = nickname ?: contact?.displayName ?: "?"
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
