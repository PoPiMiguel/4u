package app.fouru.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.wrapContentSize
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.fouru.data.HistoryItem
import app.fouru.data.LocalStore
import app.fouru.ui.MainActivity
import java.text.DateFormat
import java.util.Date

/**
 * Widget con 3 tamaños:
 *  - pequeño: el mensaje fijado (si lo hay) o el último
 *  - mediano: último + remitente y hora
 *  - grande: último + remitente y hora + tira con los 3 anteriores
 * El texto del envío va pequeño en la esquina inferior izquierda de la imagen.
 * Android no puede ejecutar HTML/GIF en widgets: se muestra la miniatura.
 * TODO(M3): rotar fotogramas con AndroidRemoteViews + ViewFlipper.
 * TODO(M2+): configurar widget por contacto (actividad de configuración).
 */
class FourUWidget : GlanceAppWidget() {

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Lo fijado manda (Recibidos → fijar); si no, lo último.
        val all = LocalStore.history(context)
        val pinned = LocalStore.pinnedId(context)?.let { id -> all.firstOrNull { it.id == id } }
        val items = (listOfNotNull(pinned) + all.filterNot { it.id == pinned?.id }).take(4)
        val thumbs = items.map { item -> item.thumbPath?.let { BitmapFactory.decodeFile(it) } }
        provideContent { WidgetContent(items, thumbs) }
    }
}

class FourUWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FourUWidget()
}

private val White = ColorProvider(Color.White)
private val Muted = ColorProvider(Color(0xFF9A9AA6))
private val CardBg = Color(0xFF1C1C22)
private val CaptionBg = Color(0x99000000)

@Composable
private fun WidgetContent(items: List<HistoryItem>, thumbs: List<Bitmap?>) {
    val size = LocalSize.current
    val root = GlanceModifier
        .fillMaxSize()
        .cornerRadius(20.dp)
        .background(CardBg)
        .clickable(actionStartActivity<MainActivity>())

    val latest = items.firstOrNull()
    if (latest == null) {
        Box(modifier = root, contentAlignment = Alignment.Center) {
            Text("4u 💌", style = TextStyle(color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold))
        }
        return
    }

    when {
        size.height >= FourUWidget.LARGE.height -> Column(modifier = root) {
            Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) { Thumb(latest, thumbs[0], showCaption = true) }
            Meta(latest, GlanceModifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp))
            Row(modifier = GlanceModifier.fillMaxWidth().height(64.dp).padding(4.dp)) {
                items.drop(1).take(3).forEachIndexed { i, item ->
                    Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(2.dp)) {
                        Thumb(item, thumbs[i + 1])
                    }
                }
            }
        }

        size.width >= FourUWidget.MEDIUM.width -> Row(modifier = root) {
            Box(modifier = GlanceModifier.width(size.height).fillMaxHeight()) { Thumb(latest, thumbs[0], showCaption = true) }
            Meta(latest, GlanceModifier.defaultWeight().fillMaxHeight().padding(10.dp))
        }

        else -> Box(modifier = root) { Thumb(latest, thumbs[0], showCaption = true) }
    }
}

/** Miniatura; con `showCaption`, el texto del envío pequeño en la esquina inferior izquierda. */
@Composable
private fun Thumb(item: HistoryItem, bitmap: Bitmap?, showCaption: Boolean = false) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        ThumbImage(item, bitmap)
        val caption = item.caption?.takeIf { showCaption && it.isNotBlank() }
        if (caption != null) {
            Box(modifier = GlanceModifier.wrapContentSize().padding(6.dp)) {
                Text(
                    caption,
                    maxLines = 1,
                    style = TextStyle(color = White, fontSize = 10.sp),
                    modifier = GlanceModifier
                        .cornerRadius(8.dp)
                        .background(CaptionBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ThumbImage(item: HistoryItem, bitmap: Bitmap?) {
    if (bitmap != null) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = item.caption ?: item.type,
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier.fillMaxSize().cornerRadius(14.dp),
        )
    } else {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emojiFor(item.type), style = TextStyle(fontSize = 36.sp))
        }
    }
}

@Composable
private fun Meta(item: HistoryItem, modifier: GlanceModifier) {
    Column(modifier = modifier) {
        Text(item.senderName, style = TextStyle(color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp))
        Text(
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(item.receivedAt)),
            style = TextStyle(color = Muted, fontSize = 12.sp),
        )
    }
}

private fun emojiFor(type: String) = when (type) {
    "ping" -> "💭"
    "html" -> "🌸"
    "drawing" -> "🎨"
    "video" -> "🎬"
    "gif" -> "✨"
    else -> "📷"
}
