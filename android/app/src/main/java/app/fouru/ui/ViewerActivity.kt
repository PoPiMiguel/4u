package app.fouru.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.fouru.data.Drawing
import java.io.ByteArrayInputStream
import java.io.File

/** Pantalla completa para vídeo, animación HTML y dibujo. */
class ViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent.getStringExtra(EXTRA_TYPE).orEmpty()
        val location = intent.getStringExtra(EXTRA_LOCATION).orEmpty()
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                when (type) {
                    "video" -> VideoPlayer(Uri.parse(location))
                    "html" -> HtmlPlayer(File(location))
                    "drawing" -> {
                        val d = remember(location) { runCatching { Drawing.parse(File(location).readText()) }.getOrNull() }
                        if (d != null) DrawingReplay(d, Modifier.fillMaxSize()) else Text("No se pudo abrir el dibujo", color = Color.White)
                    }
                    else -> Text("Formato no soportado", color = Color.White)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_LOCATION = "location"

        /** `location`: content:// (vídeo) o ruta de archivo privado (html, dibujo). */
        fun open(ctx: Context, type: String, location: String) {
            ctx.startActivity(
                Intent(ctx, ViewerActivity::class.java)
                    .putExtra(EXTRA_TYPE, type)
                    .putExtra(EXTRA_LOCATION, location),
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(uri: Uri) {
    val ctx = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * Animación HTML de un contacto (DECISIONS §9): JS sí, pero sin puente con la app,
 * sin acceso a archivos y sin red (todo lo que no sea data:/blob: se bloquea).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlPlayer(file: File) {
    val html = remember(file) { runCatching { file.readText() }.getOrDefault("<p>No se pudo abrir</p>") }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.domStorageEnabled = false
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val scheme = request.url.scheme
                        if (scheme == "data" || scheme == "blob") return null
                        return WebResourceResponse("text/plain", "utf-8", 403, "Bloqueado", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                }
                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}
