package app.fouru.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fouru.data.ContactRow
import app.fouru.data.HistoryItem
import app.fouru.data.OutPending
import app.fouru.data.SentItem
import coil3.compose.AsyncImage
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val REACTIONS = listOf("❤️", "😂", "😮", "😢", "🔥")
private val DATE_TIME: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

private val pagePadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), style = SectionLabel, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun Empty(icon: ImageVector, text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = FourUColors.Muted, modifier = Modifier.size(40.dp))
        Text(text, color = FourUColors.Muted, modifier = Modifier.padding(top = 12.dp))
    }
}

fun relativeTime(millis: Long): String {
    val diff = (System.currentTimeMillis() - millis) / 1000
    return when {
        diff < 60 -> "ahora"
        diff < 3600 -> "hace ${diff / 60} min"
        diff < 86400 -> "hace ${diff / 3600} h"
        diff < 7 * 86400 -> "hace ${diff / 86400} d"
        else -> DATE_TIME.format(Instant.ofEpochMilli(millis))
    }
}

// ---------------------------------------------------------------- Recibidos

@Composable
fun InboxTab(history: List<HistoryItem>, onReact: (HistoryItem, String) -> Unit, onSeen: (HistoryItem) -> Unit) {
    if (history.isEmpty()) return Empty(Icons.Outlined.Email, "Cuando te envíen algo, aparecerá aquí.")
    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(history, key = { it.id }) { item ->
            LaunchedEffect(item.id) { if (!item.seen) onSeen(item) }
            if (item.type == "ping") PingCard(item) else PostCard(item, onReact)
        }
    }
}

@Composable
private fun PingCard(item: HistoryItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, FourUColors.Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = FourUColors.Accent, modifier = Modifier.size(20.dp))
        Text(item.senderName, fontWeight = FontWeight.SemiBold)
        Text("está pensando en ti", color = FourUColors.Muted, modifier = Modifier.weight(1f))
        Text(relativeTime(item.receivedAt), color = FourUColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PostCard(item: HistoryItem, onReact: (HistoryItem, String) -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FourUColors.Surface)
            .border(1.dp, FourUColors.Line, RoundedCornerShape(16.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.senderName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(relativeTime(item.receivedAt), color = FourUColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(androidx.compose.ui.graphics.Color.Black)
                .clickable(enabled = item.localUri != null) { openItem(ctx, item) },
            contentAlignment = Alignment.Center,
        ) {
            if (item.thumbPath != null) {
                AsyncImage(model = File(item.thumbPath), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Text(typeLabel(item.type), color = FourUColors.Muted)
            }
            if (item.type in setOf("video", "html", "drawing")) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0x99000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Ver", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
        item.caption?.let { Text(it, modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) }
        Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
            REACTIONS.forEach { r ->
                val on = item.reaction == r
                TextButton(
                    onClick = { onReact(item, r) },
                    modifier = Modifier.alpha(if (on || item.reaction == null) 1f else 0.55f),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        containerColor = if (on) FourUColors.AccentSoft else androidx.compose.ui.graphics.Color.Transparent,
                    ),
                ) { Text(r, fontSize = 18.sp) }
            }
        }
    }
}

fun openItem(ctx: Context, item: HistoryItem) {
    val location = item.localUri ?: return
    when (item.type) {
        "video", "html", "drawing" -> ViewerActivity.open(ctx, item.type, location)
        else -> {
            val mime = if (item.type == "gif") "image/gif" else "image/*"
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(location), mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { ctx.startActivity(intent) }
        }
    }
}

// ---------------------------------------------------------------- Enviar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposeTab(
    contacts: List<ContactRow>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    caption: String,
    onCaption: (String) -> Unit,
    deliverAt: Instant?,
    onPickDate: () -> Unit,
    onClearDate: () -> Unit,
    busy: Boolean,
    onMedia: () -> Unit,
    onDraw: () -> Unit,
    onHtml: () -> Unit,
    onPing: () -> Unit,
) {
    val available = contacts.filter { !it.blocked }
    val canSend = !busy && selected.isNotEmpty()
    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Section("Para") }
        item {
            if (available.isEmpty()) {
                Text("Añade a alguien en Contactos.", color = FourUColors.Muted)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    available.forEach { c ->
                        val on = c.contactId in selected
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (on) FourUColors.AccentSoft else FourUColors.Surface2)
                                .border(1.dp, if (on) FourUColors.Accent else FourUColors.Line, RoundedCornerShape(50))
                                .clickable { onToggle(c.contactId) }
                                .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Avatar(c.avatarPath, c.name, 28.dp)
                            Text(c.name)
                        }
                    }
                }
            }
        }
        item { Section("Texto y hora") }
        item {
            OutlinedTextField(caption, { onCaption(it.take(280)) }, label = { Text("Texto (opcional)") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = onPickDate) {
                    Icon(Icons.Outlined.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(deliverAt?.let { "  " + DATE_TIME.format(it) } ?: "  Programar")
                }
                if (deliverAt != null) TextButton(onClick = onClearDate) { Text("Quitar") }
            }
        }
        item { Section(if (deliverAt != null) "Programar" else "Enviar") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Choice(Icons.Outlined.Add, "Foto o vídeo", canSend, Modifier.weight(1f), onMedia)
                Choice(Icons.Outlined.Create, "Dibujo", canSend, Modifier.weight(1f), onDraw)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Choice(Icons.Outlined.Star, "Animación HTML", canSend, Modifier.weight(1f), onHtml)
                Choice(Icons.Outlined.FavoriteBorder, "Toque", canSend, Modifier.weight(1f), onPing)
            }
        }
        if (selected.isEmpty() && available.isNotEmpty()) {
            item { Text("Elige primero a quién.", color = FourUColors.Muted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun Choice(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .heightIn(min = 92.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(FourUColors.Surface)
            .border(1.dp, FourUColors.Line, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = FourUColors.Accent, modifier = Modifier.size(22.dp))
        Text(label, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- Enviados

@Composable
fun OutboxTab(pending: List<OutPending>, sent: List<SentItem>, busy: Boolean, onCancel: (OutPending) -> Unit) {
    if (pending.isEmpty() && sent.isEmpty()) return Empty(Icons.Outlined.DateRange, "Aquí verás lo que envíes y si ya lo han visto.")
    LazyColumn(contentPadding = pagePadding) {
        if (pending.isNotEmpty()) {
            item { Section("En espera") }
            items(pending, key = { "p-" + it.id }) { p ->
                Box(Modifier.padding(vertical = 8.dp)) { PendingRow(p, enabled = !busy, onCancel = { onCancel(p) }) }
                HorizontalDivider(color = FourUColors.Line)
            }
        }
        if (sent.isNotEmpty()) {
            item { Section("Enviados") }
            items(sent, key = { "s-" + it.id }) { s ->
                Box(Modifier.padding(vertical = 8.dp)) { SentRow(s) }
                HorizontalDivider(color = FourUColors.Line)
            }
        }
    }
}

// ---------------------------------------------------------------- Contactos

@Composable
fun ContactsTab(
    contacts: List<ContactRow>,
    busy: Boolean,
    launchSafe: (String?, suspend () -> Unit) -> Unit,
    onChanged: suspend () -> Unit,
    onPing: (ContactRow) -> Unit,
) {
    LazyColumn(contentPadding = pagePadding) {
        item { Section("Añadir a alguien") }
        item { Box(Modifier.padding(vertical = 8.dp)) { InviteBlock(busy, launchSafe, onChanged) } }
        item { Section("Tus contactos") }
        if (contacts.isEmpty()) item { Text("Aún no tienes contactos.", color = FourUColors.Muted) }
        items(contacts, key = { "c-" + it.contactId }) { c ->
            ContactItem(c = c, busy = busy, onPing = { onPing(c) }, launchSafe = launchSafe, onChanged = onChanged)
            HorizontalDivider(color = FourUColors.Line)
        }
    }
}

// ---------------------------------------------------------------- Cuenta

@Composable
fun AccountTab(busy: Boolean, launchSafe: (String?, suspend () -> Unit) -> Unit) {
    LazyColumn(contentPadding = pagePadding) {
        item { AccountSection(busy, launchSafe) }
    }
}
