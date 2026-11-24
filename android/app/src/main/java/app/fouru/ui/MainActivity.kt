package app.fouru.ui

import android.Manifest
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import app.fouru.data.HistoryItem
import app.fouru.data.LocalStore
import app.fouru.data.MediaSaver
import app.fouru.data.Repo
import app.fouru.data.Supa
import app.fouru.push.InboxWorker
import coil3.compose.AsyncImage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val REACTIONS = listOf("❤️", "😂", "😮", "😢", "🔥")

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
        Text("4u 💌", style = MaterialTheme.typography.headlineLarge)
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
    var invite by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun launchSafe(okMessage: String? = null, block: suspend () -> Unit) {
        scope.launch {
            busy = true
            runCatching { block() }
                .onSuccess { okMessage?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() } }
                .onFailure { Toast.makeText(ctx, "⚠️ ${it.message}", Toast.LENGTH_LONG).show() }
            busy = false
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) launchSafe("✅ Enviado") { sendMedia(ctx, uri, selected.toList()) }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        runCatching { Repo.registerDevice(ctx) }
        runCatching { contacts = Repo.contacts() }
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
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("4u 💌", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { launchSafe { Repo.signOut() } }) { Text("Salir") }
            }
        }

        // Contactos
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Contactos", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = { launchSafe { invite = Repo.createInvite() } }) { Text("Generar código") }
                    invite?.let { Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(code, { code = it.take(6) }, label = { Text("Código") }, modifier = Modifier.weight(1f))
                    Button(enabled = !busy && code.length == 6, onClick = {
                        launchSafe("Contacto añadido") {
                            Repo.acceptInvite(code)
                            code = ""
                            contacts = Repo.contacts()
                        }
                    }) { Text("Añadir") }
                }
            }
        }
        items(contacts, key = { it.contactId }) { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = c.contactId in selected,
                    enabled = !c.blocked,
                    onCheckedChange = { selected = if (it) selected + c.contactId else selected - c.contactId },
                )
                Text(c.name + if (c.blocked) " (bloqueado)" else "", modifier = Modifier.weight(1f))
                TextButton(enabled = !busy && !c.blocked, onClick = {
                    launchSafe("💭 Toque enviado") { Repo.send(type = "ping", recipients = listOf(c.contactId)) }
                }) { Text("💭") }
            }
        }

        // Enviar
        // TODO(M3): vídeo (Media3 Transformer para comprimir), HTML (selector de documentos).
        // TODO(M5): lienzo de dibujo. TODO(M6): programar envío.
        item {
            Button(
                enabled = !busy && selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            ) { Text("Enviar foto o GIF a ${selected.size} contacto(s)") }
        }

        // Historial
        item { Text("Historial", style = MaterialTheme.typography.titleMedium) }
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
                Text(if (item.type == "ping") "💭" else "🌸", style = MaterialTheme.typography.headlineMedium)
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
            if (item.type in setOf("photo", "gif", "video") && item.localUri != null) {
                TextButton(onClick = { openExternal(ctx, item) }) { Text("Abrir") }
            }
            // TODO(M3): reproductor HTML a pantalla completa (WebView aislado).
        }
    }
}

private fun openExternal(ctx: Context, item: HistoryItem) {
    val mime = when (item.type) {
        "video" -> "video/*"
        "gif" -> "image/gif"
        else -> "image/*"
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(item.localUri), mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { ctx.startActivity(intent) }
}

/** Prepara y envía una foto (redimensionada) o un GIF (tal cual). */
private suspend fun sendMedia(ctx: Context, uri: Uri, recipients: List<String>) = withContext(Dispatchers.IO) {
    require(recipients.isNotEmpty()) { "Elige al menos un contacto" }
    val resolver = ctx.contentResolver
    val mime = resolver.getType(uri) ?: "image/jpeg"
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("No se pudo leer el archivo")
    val thumb = MediaSaver.decodeScaled(bytes, 512)?.let { MediaSaver.jpeg(it, 80) }

    if (mime == "image/gif") {
        require(bytes.size <= 10 * 1024 * 1024) { "El GIF no puede superar 10 MB" }
        Repo.send(type = "gif", recipients = recipients, file = bytes, ext = "gif", mime = mime, thumb = thumb)
    } else {
        val jpeg = MediaSaver.prepareJpeg(bytes, 2048)
        Repo.send(type = "photo", recipients = recipients, file = jpeg, ext = "jpg", mime = "image/jpeg", thumb = thumb)
    }
}
