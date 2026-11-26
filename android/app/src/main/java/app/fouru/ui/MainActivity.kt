package app.fouru.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fouru.data.ContactRow
import app.fouru.data.Drawing
import app.fouru.data.DrawingRenderer
import app.fouru.data.HistoryItem
import app.fouru.data.LocalStore
import app.fouru.data.MediaSaver
import app.fouru.data.OutPending
import app.fouru.data.Repo
import app.fouru.data.SentItem
import app.fouru.data.Supa
import app.fouru.data.VideoPrep
import app.fouru.push.InboxWorker
import coil3.compose.AsyncImage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val REACTIONS = listOf("❤️", "😂", "😮", "😢", "🔥")

private val DATE_TIME: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

private const val HTML_MAX_BYTES = 2 * 1024 * 1024
private const val GIF_MAX_BYTES = 10 * 1024 * 1024

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        InboxWorker.enqueue(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Root() }
            }
        }
    }
}

@Composable
private fun Root() {
    val status by Supa.client.auth.sessionStatus.collectAsState()
    when (status) {
        is SessionStatus.Authenticated -> HomeScreen()
        is SessionStatus.Initializing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> LoginScreen()
    }
}

// ---------------------------------------------------------------- Login

@Composable
private fun LoginScreen() {
    val scope = rememberCoroutineScope()
    var signup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("4u", style = MaterialTheme.typography.headlineLarge)
        if (signup) OutlinedTextField(name, { name = it }, label = { Text("Tu nombre") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, modifier = Modifier.fillMaxWidth())
        Button(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    runCatching {
                        if (signup) Repo.signUp(email.trim(), password, name.trim()) else Repo.signIn(email.trim(), password)
                    }.onFailure { error = it.message }
                    busy = false
                }
            },
        ) { Text(if (signup) "Crear cuenta" else "Entrar") }
        TextButton(onClick = { signup = !signup }) {
            Text(if (signup) "Ya tengo cuenta" else "¿No tienes cuenta? Regístrate")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

// ---------------------------------------------------------------- Home

@Composable
private fun HomeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<ContactRow>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var history by remember { mutableStateOf(LocalStore.history(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf("") }
    var deliverAt by remember { mutableStateOf<Instant?>(null) }
    var outPending by remember { mutableStateOf<List<OutPending>>(emptyList()) }
    var sent by remember { mutableStateOf<List<SentItem>>(emptyList()) }
    var toCancel by remember { mutableStateOf<OutPending?>(null) }
    var drawing by remember { mutableStateOf(false) }

    suspend fun refreshOutgoing() {
        runCatching { Repo.outPending() }.onSuccess { outPending = it }
        runCatching { Repo.sent() }.onSuccess { sent = it }
    }

    suspend fun refreshContacts() {
        runCatching { Repo.contacts() }.onSuccess { list ->
            contacts = list
            // Quien ya no está o está bloqueado deja de estar seleccionado.
            selected = selected.filter { id -> list.any { it.contactId == id && !it.blocked } }.toSet()
        }
    }

    fun launchSafe(okMessage: String? = null, block: suspend () -> Unit) {
        scope.launch {
            busy = true
            runCatching { block() }
                .onSuccess { okMessage?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() } }
                .onFailure { Toast.makeText(ctx, "Error: ${it.message}", Toast.LENGTH_LONG).show() }
            busy = false
        }
    }

    /** Envía con el texto y la hora elegidos y deja el formulario limpio. */
    fun sendPrepared(label: String, prepare: suspend () -> Prepared) {
        val at = deliverAt
        val to = selected.toList()
        launchSafe(if (at != null) "Programado" else "Enviado") {
            require(to.isNotEmpty()) { "Elige al menos un contacto" }
            if (at != null) require(at.isAfter(Instant.now())) { "Esa hora ya ha pasado" }
            Toast.makeText(ctx, label, Toast.LENGTH_SHORT).show()
            val p = prepare()
            val id = Repo.send(
                type = p.type, recipients = to, file = p.file, ext = p.ext, mime = p.mime,
                thumb = p.thumb, caption = caption.trim().ifEmpty { null }, deliverAt = at,
            )
            p.thumb?.let { thumb ->
                withContext(Dispatchers.IO) {
                    MediaSaver.decodeScaled(thumb, 160)?.let { LocalStore.saveSentThumb(ctx, id, MediaSaver.jpeg(it, 80)) }
                }
            }
            caption = ""
            deliverAt = null
            refreshOutgoing()
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) sendPrepared("Preparando…") { prepareMedia(ctx, uri) }
    }
    val htmlPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) sendPrepared("Enviando animación…") { prepareHtml(ctx, uri) }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        runCatching { Repo.registerDevice(ctx) }
    }
    // Programados que salen, entregas, vistos y contactos que cambian desde el otro lado.
    LaunchedEffect(Unit) {
        while (true) {
            refreshContacts()
            refreshOutgoing()
            delay(15_000)
        }
    }
    // El worker escribe el historial en disco; refresco simple mientras la app está abierta.
    LaunchedEffect(Unit) {
        while (true) {
            history = withContext(Dispatchers.IO) { LocalStore.history(ctx) }
            delay(3_000)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("4u", style = MaterialTheme.typography.headlineMedium) }

        // Contactos
        item {
            Text("Contactos", style = MaterialTheme.typography.titleMedium)
        }
        item { InviteBlock(busy, ::launchSafe) { refreshContacts() } }
        items(contacts, key = { "c-" + it.contactId }) { c ->
            ContactItem(
                c = c,
                checked = c.contactId in selected,
                busy = busy,
                onCheck = { selected = if (it) selected + c.contactId else selected - c.contactId },
                onPing = {
                    launchSafe("Toque enviado") {
                        Repo.send(type = "ping", recipients = listOf(c.contactId))
                        refreshOutgoing()
                    }
                },
                launchSafe = ::launchSafe,
                onChanged = { refreshContacts() },
            )
        }
        if (contacts.isEmpty()) item { Text("Aún no tienes contactos", style = MaterialTheme.typography.bodySmall) }

        // Enviar
        item {
            HorizontalDivider()
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enviar", style = MaterialTheme.typography.titleMedium)
                val names = contacts.filter { it.contactId in selected }.joinToString { it.name }
                Text(if (names.isEmpty()) "Marca a quién en Contactos" else "Para: $names", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    caption,
                    { caption = it.take(280) },
                    label = { Text("Texto (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = { pickDateTime(ctx, deliverAt) { deliverAt = it } }) {
                        Text(deliverAt?.let { "Programado: " + DATE_TIME.format(it) } ?: "Programar")
                    }
                    if (deliverAt != null) TextButton(onClick = { deliverAt = null }) { Text("Quitar") }
                }
                val canSend = !busy && selected.isNotEmpty()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = canSend, modifier = Modifier.weight(1f), onClick = {
                        mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }) { Text("Foto o vídeo") }
                    Button(enabled = canSend, modifier = Modifier.weight(1f), onClick = { drawing = true }) { Text("Dibujo") }
                }
                OutlinedButton(enabled = canSend, modifier = Modifier.fillMaxWidth(), onClick = {
                    htmlPicker.launch(arrayOf("text/html"))
                }) { Text("Animación HTML") }
            }
        }

        // En espera
        if (outPending.isNotEmpty()) {
            item { Text("En espera", style = MaterialTheme.typography.titleMedium) }
            items(outPending, key = { "out-" + it.id }) { p ->
                PendingRow(p, enabled = !busy, onCancel = { toCancel = p })
            }
        }

        // Enviados
        if (sent.isNotEmpty()) {
            item { Text("Enviados", style = MaterialTheme.typography.titleMedium) }
            items(sent.take(15), key = { "sent-" + it.id }) { SentRow(it) }
        }

        // Recibidos
        item { Text("Recibidos", style = MaterialTheme.typography.titleMedium) }
        items(history, key = { it.id }) { item ->
            HistoryRow(item, onReact = { emoji ->
                launchSafe {
                    val value = if (item.reaction == emoji) null else emoji
                    Repo.react(item.id, value)
                    LocalStore.update(ctx, item.id) { it.copy(reaction = value) }
                    history = LocalStore.history(ctx)
                }
            }, onSeen = {
                scope.launch {
                    runCatching { Repo.markSeen(item.id) }.onSuccess {
                        LocalStore.update(ctx, item.id) { it.copy(seen = true) }
                    }
                }
            })
        }

        item {
            HorizontalDivider()
            Box(Modifier.padding(top = 12.dp)) { AccountSection(busy, ::launchSafe) }
        }
    }

    if (drawing) {
        DrawScreen(
            onCancel = { drawing = false },
            onDone = { d ->
                drawing = false
                sendPrepared("Enviando dibujo…") { prepareDrawing(d) }
            },
        )
    }

    toCancel?.let { p ->
        AlertDialog(
            onDismissRequest = { toCancel = null },
            title = { Text("¿Cancelar este envío?") },
            text = {
                Text(
                    if (p.scheduled) "No llegará a nadie."
                    else "Se quitará a quien todavía no lo haya recibido.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    toCancel = null
                    launchSafe("Envío cancelado") {
                        Repo.cancelOutPending(p)
                        refreshOutgoing()
                    }
                }) { Text("Sí, cancelar") }
            },
            dismissButton = { TextButton(onClick = { toCancel = null }) { Text("No") } },
        )
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, onReact: (String) -> Unit, onSeen: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(item.id) { if (!item.seen) onSeen() }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (item.thumbPath != null) {
                AsyncImage(model = File(item.thumbPath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(typeLabel(item.type), style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(item.senderName, fontWeight = FontWeight.Bold)
            item.caption?.let { Text(it) }
            Row {
                REACTIONS.forEach { r ->
                    TextButton(onClick = { onReact(r) }) {
                        Text(if (item.reaction == r) "[$r]" else r)
                    }
                }
            }
            if (item.type != "ping" && item.localUri != null) {
                TextButton(onClick = { openItem(ctx, item) }) { Text("Abrir") }
            }
        }
    }
}

private fun openItem(ctx: Context, item: HistoryItem) {
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

// ---------------------------------------------------------------- Preparar envíos

private class Prepared(
    val type: String,
    val file: ByteArray,
    val ext: String,
    val mime: String,
    val thumb: ByteArray?,
)

/** Foto (redimensionada), GIF (tal cual) o vídeo (comprimido a 720p). */
private suspend fun prepareMedia(ctx: Context, uri: Uri): Prepared {
    val resolver = ctx.contentResolver
    val mime = resolver.getType(uri) ?: "image/jpeg"

    if (mime.startsWith("video/")) {
        val out = VideoPrep.compress(ctx, uri)
        return withContext(Dispatchers.IO) {
            try {
                require(out.length() <= VideoPrep.MAX_BYTES) { "El vídeo es demasiado largo (máximo 15 MB, aprox. 1 minuto)" }
                val thumb = MediaSaver.videoFrame(ctx, Uri.fromFile(out))?.let { MediaSaver.jpeg(it, 80) }
                Prepared("video", out.readBytes(), "mp4", "video/mp4", thumb)
            } finally {
                out.delete()
            }
        }
    }

    return withContext(Dispatchers.IO) {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("No se pudo leer el archivo")
        val thumb = MediaSaver.decodeScaled(bytes, 512)?.let { MediaSaver.jpeg(it, 80) }
        if (mime == "image/gif") {
            require(bytes.size <= GIF_MAX_BYTES) { "El GIF no puede superar 10 MB" }
            Prepared("gif", bytes, "gif", mime, thumb)
        } else {
            Prepared("photo", MediaSaver.prepareJpeg(bytes, 2048), "jpg", "image/jpeg", thumb)
        }
    }
}

private suspend fun prepareHtml(ctx: Context, uri: Uri): Prepared = withContext(Dispatchers.IO) {
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("No se pudo leer el archivo")
    require(bytes.size <= HTML_MAX_BYTES) { "La animación HTML no puede superar 2 MB" }
    Prepared("html", bytes, "html", "text/html", null)
}

private suspend fun prepareDrawing(d: Drawing): Prepared = withContext(Dispatchers.Default) {
    Prepared("drawing", d.toJson().toByteArray(), "json", "application/json", MediaSaver.jpeg(DrawingRenderer.bitmap(d), 90))
}

/** Diálogos del sistema: primero fecha y luego hora. */
private fun pickDateTime(ctx: Context, current: Instant?, onPicked: (Instant) -> Unit) {
    val zone = ZoneId.systemDefault()
    val start = LocalDateTime.ofInstant(current ?: Instant.now().plusSeconds(3600), zone)
    DatePickerDialog(ctx, { _, y, m, d ->
        TimePickerDialog(ctx, { _, h, min ->
            onPicked(LocalDateTime.of(y, m + 1, d, h, min).atZone(zone).toInstant())
        }, start.hour, start.minute, true).show()
    }, start.year, start.monthValue - 1, start.dayOfMonth).apply {
        datePicker.minDate = System.currentTimeMillis() - 1000
    }.show()
}
