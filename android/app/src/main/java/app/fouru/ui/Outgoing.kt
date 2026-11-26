package app.fouru.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fouru.data.LocalStore
import app.fouru.data.OutPending
import app.fouru.data.Repo
import app.fouru.data.SentItem
import coil3.compose.AsyncImage
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DATE_TIME: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

fun formatInstant(iso: String?): String =
    iso?.let { runCatching { DATE_TIME.format(OffsetDateTime.parse(it).toInstant()) }.getOrNull() } ?: ""

fun typeLabel(type: String) = when (type) {
    "ping" -> "Toque"
    "html" -> "HTML"
    "drawing" -> "Dibujo"
    "video" -> "Vídeo"
    "gif" -> "GIF"
    else -> "Foto"
}

/** Miniatura: copia local si la hay; si no, la del servidor mientras exista. */
@Composable
private fun OutThumb(id: String, thumbPath: String?, type: String) {
    val ctx = LocalContext.current
    val model by produceState<Any?>(null, id, thumbPath) {
        value = LocalStore.sentThumb(ctx, id)
            ?: thumbPath?.let { path -> runCatching { Repo.download(path) }.getOrNull() }
    }
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF26262E)),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(typeLabel(type), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun PendingRow(p: OutPending, enabled: Boolean, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutThumb(p.id, p.thumbPath, p.type)
        Column(Modifier.weight(1f)) {
            Text("Para " + p.recipients.joinToString { it.name }, fontWeight = FontWeight.Bold)
            p.caption?.let { Text(it, maxLines = 1) }
            Text(
                if (p.scheduled) "Programado: " + formatInstant(p.deliverAt)
                else "Sin entregar a " + p.recipients.filter { !it.delivered }.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(enabled = enabled, onClick = onCancel) { Text("Cancelar") }
    }
}

@Composable
fun SentRow(s: SentItem) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutThumb(s.id, s.thumbPath, s.type)
        Column(Modifier.weight(1f)) {
            s.caption?.let { Text(it, maxLines = 1) }
            Text(formatInstant(s.sentAt), style = MaterialTheme.typography.bodySmall)
            s.recipients.forEach { r ->
                val state = when {
                    r.seen -> "visto"
                    r.delivered -> "entregado"
                    else -> "enviado"
                }
                Text(
                    "${r.name}: $state" + (r.reaction?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (r.seen) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
