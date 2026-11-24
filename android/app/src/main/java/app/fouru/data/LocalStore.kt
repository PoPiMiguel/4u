package app.fouru.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Historial local en un JSON (filesDir/history.json). Lo leen la app y el widget.
 * TODO(M3): pasar a Room si el historial crece.
 */
object LocalStore {
    private const val MAX_ITEMS = 500
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private fun file(ctx: Context) = File(ctx.filesDir, "history.json")

    fun history(ctx: Context): List<HistoryItem> = synchronized(lock) {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        runCatching { json.decodeFromString<List<HistoryItem>>(f.readText()) }.getOrDefault(emptyList())
    }

    fun contains(ctx: Context, id: String): Boolean = history(ctx).any { it.id == id }

    fun add(ctx: Context, item: HistoryItem) = synchronized(lock) {
        write(ctx, (listOf(item) + history(ctx).filterNot { it.id == item.id }).take(MAX_ITEMS))
    }

    fun update(ctx: Context, id: String, transform: (HistoryItem) -> HistoryItem) = synchronized(lock) {
        write(ctx, history(ctx).map { if (it.id == id) transform(it) else it })
    }

    private fun write(ctx: Context, items: List<HistoryItem>) {
        val target = file(ctx)
        val tmp = File(target.parentFile, "history.json.tmp")
        tmp.writeText(json.encodeToString(items))
        if (!tmp.renameTo(target)) {
            target.writeText(tmp.readText())
            tmp.delete()
        }
    }

    /** Id estable de esta instalación (para la tabla devices). */
    fun deviceId(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("4u", Context.MODE_PRIVATE)
        return prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
    }
}
