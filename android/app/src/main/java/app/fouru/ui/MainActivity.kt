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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
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
import app.fouru.data.InboxSync
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import app.fouru.widget.FourUWidget
import androidx.core.content.FileProvider
import androidx.glance.appwidget.updateAll
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

private data class Tab(val label: String, val icon: ImageVector)

private val DATE_TIME: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

private const val GIF_MAX_BYTES = 10 * 1024 * 1024

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        InboxWorker.enqueue(this)
        setContent {
            FourUTheme {
                Surface(Modifier.fillMaxSize(), color = FourUColors.Bg) { Root() }
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
        Text("4u", color = FourUColors.Accent, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, letterSpacing = (-2).sp)
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    // "Hacer foto": la app de cámara guarda en un archivo nuestro (FileProvider).
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) sendPrepared("Preparando…") { prepareMedia(ctx, uri) }
    }
    var pinned by remember { mutableStateOf(LocalStore.pinnedId(ctx)) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        runCatching { Repo.registerDevice(ctx) }
    }
    // Con la app abierta, sincroniza ella misma (no depende de que Android deje
    // correr el worker): mensajes nuevos, programados que salen, entregas,
    // vistos y contactos que cambian desde el otro lado.
    suspend fun syncAll() {
        runCatching { InboxSync.run(ctx) }.onSuccess { added ->
            if (added > 0) runCatching { FourUWidget().updateAll(ctx) }
        }
        history = withContext(Dispatchers.IO) { LocalStore.history(ctx) }
        refreshContacts()
        refreshOutgoing()
    }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            syncAll()
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

    val tabs = listOf(
        Tab("Recibidos", Icons.Outlined.Email),
        Tab("Enviar", Icons.Outlined.Send),
        Tab("Enviados", Icons.Outlined.DateRange),
        Tab("Contactos", Icons.Outlined.Person),
        Tab("Cuenta", Icons.Outlined.AccountCircle),
    )
    val pager = rememberPagerState { tabs.size }
    val unseen = history.count { !it.seen }

    Scaffold(
        containerColor = FourUColors.Bg,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("4u", color = FourUColors.Accent, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = (-1).sp)
                Text(tabs[pager.currentPage].label, style = MaterialTheme.typography.headlineSmall)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = FourUColors.Bg, tonalElevation = 0.dp) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = pager.currentPage == i,
                        onClick = { scope.launch { pager.animateScrollToPage(i) } },
                        icon = {
                            BadgedBox(badge = {
                                when {
                                    i == 0 && unseen > 0 -> Badge(containerColor = FourUColors.Accent)
                                    i == 2 && outPending.isNotEmpty() -> Badge(containerColor = FourUColors.Accent) { Text("${outPending.size}") }
                                }
                            }) { Icon(t.icon, contentDescription = t.label) }
                        },
                        label = { Text(t.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = FourUColors.Accent,
                            selectedTextColor = FourUColors.Text,
                            indicatorColor = FourUColors.AccentSoft,
                            unselectedIconColor = FourUColors.Muted,
                            unselectedTextColor = FourUColors.Muted,
                        ),
                    )
                }
            }
        },
    ) { inner ->
      // Deslizar hacia abajo para refrescar, en cualquier pestaña.
      PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                syncAll()
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize().padding(inner),
      ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            when (page) {
                0 -> InboxTab(
                    history = history,
                    pinned = pinned,
                    onPin = { item ->
                        launchSafe(if (item != null) "Fijado en el widget" else "Quitado del widget") {
                            LocalStore.setPinned(ctx, item?.id)
                            pinned = item?.id
                            FourUWidget().updateAll(ctx)
                        }
                    },
                    onReact = { item, emoji ->
                        launchSafe {
                            val value = if (item.reaction == emoji) null else emoji
                            Repo.react(item.id, value)
                            LocalStore.update(ctx, item.id) { it.copy(reaction = value) }
                            history = LocalStore.history(ctx)
                        }
                    },
                    onSeen = { item ->
                        // Visto solo cuando de verdad estás en Recibidos.
                        if (pager.currentPage == 0) scope.launch {
                            runCatching { Repo.markSeen(item.id) }.onSuccess {
                                LocalStore.update(ctx, item.id) { it.copy(seen = true) }
                            }
                        }
                    },
                )
                1 -> ComposeTab(
                    contacts = contacts,
                    selected = selected,
                    onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
                    caption = caption,
                    onCaption = { caption = it },
                    deliverAt = deliverAt,
                    onPickDate = { pickDateTime(ctx, deliverAt) { deliverAt = it } },
                    onClearDate = { deliverAt = null },
                    busy = busy,
                    onMedia = { mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    onCamera = {
                        val uri = newCameraUri(ctx)
                        cameraUri = uri
                        camera.launch(uri)
                    },
                    onDraw = { drawing = true },
                    onPing = {
                        val to = selected.toList()
                        launchSafe("Toque enviado") {
                            Repo.send(type = "ping", recipients = to)
                            refreshOutgoing()
                        }
                    },
                )
                2 -> OutboxTab(outPending, sent, busy, onCancel = { toCancel = it })
                3 -> ContactsTab(
                    contacts = contacts,
                    busy = busy,
                    launchSafe = ::launchSafe,
                    onChanged = { refreshContacts() },
                    onPing = { c ->
                        launchSafe("Toque enviado") {
                            Repo.send(type = "ping", recipients = listOf(c.contactId))
                            refreshOutgoing()
                        }
                    },
                )
                else -> AccountTab(busy, ::launchSafe)
            }
        }
      }
    }

    // Al volver a Recibidos, lo que haya sin ver cuenta como visto.
    LaunchedEffect(pager.currentPage, history) {
        if (pager.currentPage != 0) return@LaunchedEffect
        history.filter { !it.seen }.forEach { item ->
            runCatching { Repo.markSeen(item.id) }.onSuccess {
                LocalStore.update(ctx, item.id) { it.copy(seen = true) }
            }
        }
        history = withContext(Dispatchers.IO) { LocalStore.history(ctx) }
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
        if (mime == "image/gif") {
            require(bytes.size <= GIF_MAX_BYTES) { "El GIF no puede superar 10 MB" }
            val thumb = MediaSaver.decodeScaled(bytes, 512)?.let { MediaSaver.jpeg(it, 80) }
            Prepared("gif", bytes, "gif", mime, thumb)
        } else {
            // La miniatura sale de la foto ya girada según su EXIF.
            val jpeg = MediaSaver.prepareJpeg(bytes, 2048)
            val thumb = MediaSaver.decodeScaled(jpeg, 512)?.let { MediaSaver.jpeg(it, 80) }
            Prepared("photo", jpeg, "jpg", "image/jpeg", thumb)
        }
    }
}

/** Archivo temporal para la cámara (cache/camera, compartido por FileProvider). */
private fun newCameraUri(ctx: Context): Uri {
    val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "foto-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
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
