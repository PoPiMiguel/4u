package app.fouru.data

import android.content.Context
import app.fouru.push.Notifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.OffsetDateTime

/**
 * Descarga lo que le falta a este dispositivo (device_inbox), lo guarda en local
 * y lo confirma (mark_device_delivered). Lo usan el worker (push, segundo plano)
 * y la app abierta (al abrir, cada 15 s y al deslizar para refrescar).
 * Cada dispositivo de la misma cuenta recibe lo mismo; al entrar en uno nuevo
 * se carga el historial (DECISIONS §17).
 */
object InboxSync {
    private val mutex = Mutex()
    private const val NOTIFY_WINDOW_MS = 60 * 60 * 1000L

    /** Devuelve cuántos mensajes nuevos han entrado. */
    suspend fun run(ctx: Context): Int = mutex.withLock {
        if (Repo.uidOrNull() == null) return 0
        // device_inbox exige que el dispositivo esté registrado.
        runCatching { Repo.registerDevice(ctx) }
        var added = 0
        for (m in Repo.deviceInbox(ctx)) {
            if (process(ctx, m)) added++
        }
        added
    }

    private suspend fun process(ctx: Context, m: InboxMessage): Boolean {
        // Ya guardado pero sin confirmar (falló la red): solo confirmar.
        if (LocalStore.contains(ctx, m.id)) {
            Repo.markDeviceDelivered(ctx, m.id)
            return false
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
                // html / dibujo: almacenamiento privado.
                else -> localUri = MediaSaver.savePrivate(ctx, bytes, name).absolutePath
            }
        }

        // Miniatura del remitente: si no se pudo sacar del original, o si el
        // original ya caducó (30 días) y solo queda ella.
        if (thumb == null) {
            m.thumbPath?.let { thumb = MediaSaver.saveThumb(ctx, m.id, MediaSaver.decodeScaled(Repo.download(it), 512)) }
        }

        val sentAt = runCatching { OffsetDateTime.parse(m.sentAt).toInstant().toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
        val item = HistoryItem(
            id = m.id,
            type = m.type,
            senderId = m.senderId,
            senderName = m.senderName ?: "Alguien",
            caption = m.caption,
            localUri = localUri,
            thumbPath = thumb,
            receivedAt = sentAt,
            seen = m.seen,
            reaction = m.reaction,
        )
        LocalStore.add(ctx, item)
        Repo.markDeviceDelivered(ctx, m.id)
        // Solo se avisa de lo reciente y sin ver (no del historial que se carga al entrar).
        if (!m.seen && System.currentTimeMillis() - sentAt < NOTIFY_WINDOW_MS) Notifier.message(ctx, item)
        return true
    }
}
