package app.fouru.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.fouru.data.Drawing
import app.fouru.data.DrawingRenderer
import app.fouru.data.Stroke
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToLong

private val COLORS = listOf("#ffffff", "#e84a6f", "#ff9f43", "#feca57", "#1dd1a1", "#54a0ff", "#a55eea", "#111111")
private val WIDTHS = listOf(0.008 to "Fino", 0.018 to "Medio", 0.04 to "Grueso")
private val BACKGROUNDS = listOf("#121216", "#fdf6ec", "#ffd6e0")

private fun hex(c: String) = Color(android.graphics.Color.parseColor(c))

/** Lienzo a pantalla completa. Guarda los tiempos de cada punto para reproducirlo. */
@Composable
fun DrawScreen(onDone: (Drawing) -> Unit, onCancel: () -> Unit) {
    val strokes = remember { mutableStateListOf<Stroke>() }
    var current by remember { mutableStateOf<Stroke?>(null) }
    var color by remember { mutableStateOf(COLORS[1]) }
    var width by remember { mutableStateOf(WIDTHS[1].first) }
    var bg by remember { mutableStateOf(BACKGROUNDS[0]) }
    // Reloj del dibujo: las pausas entre trazos se recortan a MAX_GAP_MS.
    var lastEnd by remember { mutableLongStateOf(0L) }

    // Capa a pantalla completa dentro de la propia ventana (no un Dialog): así
    // llegan los márgenes del sistema y nada queda debajo de la barra de gestos.
    BackHandler(onBack = onCancel)
    run {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancel) { Text("Cancelar") }
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = strokes.isNotEmpty(), onClick = { strokes.removeAt(strokes.lastIndex) }) { Text("Deshacer") }
                    TextButton(enabled = strokes.isNotEmpty(), onClick = { strokes.clear() }) { Text("Borrar") }
                }

                Canvas(
                    Modifier
                        .weight(1f)
                        .aspectRatio((1 / Drawing.ASPECT).toFloat(), matchHeightConstraintsFirst = true)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(color, width) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val start = SystemClock.uptimeMillis()
                                val prevEnd = strokes.lastOrNull()?.points?.lastOrNull()?.get(2) ?: 0.0
                                val gap = if (strokes.isEmpty()) 0L else min(Drawing.MAX_GAP_MS, start - lastEnd)
                                val offset = if (strokes.isEmpty()) 0.0 else prevEnd + gap
                                fun point(x: Float, y: Float) = listOf(
                                    (x / w).coerceIn(0f, 1f).toDouble().round4(),
                                    (y / h).coerceIn(0f, 1f).toDouble().round4(),
                                    (offset + SystemClock.uptimeMillis() - start).toDouble(),
                                )
                                current = Stroke(color, width, listOf(point(down.position.x, down.position.y)))
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    val s = current ?: break
                                    val p = point(change.position.x, change.position.y)
                                    val last = s.points.last()
                                    if (hypot(p[0] - last[0], p[1] - last[1]) >= 0.002) current = s.copy(points = s.points + listOf(p))
                                }
                                current?.let { strokes.add(it) }
                                current = null
                                lastEnd = SystemClock.uptimeMillis()
                            }
                        },
                ) {
                    val d = Drawing(bg = bg, strokes = strokes + listOfNotNull(current))
                    drawIntoCanvas { DrawingRenderer.render(it.nativeCanvas, d, size.width, size.height) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    COLORS.forEach { c -> Swatch(c, c == color) { color = c } }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WIDTHS.forEach { (w, label) -> FilterChip(selected = w == width, onClick = { width = w }, label = { Text(label) }) }
                    Spacer(Modifier.weight(1f))
                    BACKGROUNDS.forEach { b -> Swatch(b, b == bg) { bg = b } }
                }
                Button(
                    enabled = strokes.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    onClick = { onDone(Drawing(bg = bg, strokes = strokes.toList())) },
                ) { Text("Usar dibujo") }
            }
        }
    }
}

@Composable
private fun Swatch(c: String, selected: Boolean, onClick: () -> Unit) {
    Spacer(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(hex(c))
            .border(2.dp, if (selected) Color.White else Color(0xFF34343D), CircleShape)
            .clickable(onClick = onClick),
    )
}

/** Reproduce el dibujo "dibujándose" (toca para repetir). `still` lo pinta terminado. */
@Composable
fun DrawingReplay(d: Drawing, modifier: Modifier = Modifier, still: Boolean = false) {
    var t by remember { mutableLongStateOf(if (still) Long.MAX_VALUE else 0L) }
    var run by remember { mutableStateOf(0) }
    val total = d.duration

    LaunchedEffect(d, run, still) {
        if (still || total == 0L) {
            t = Long.MAX_VALUE
            return@LaunchedEffect
        }
        val speed = maxOf(1.0, total.toDouble() / Drawing.MAX_PLAY_MS)
        val start = withFrameMillis { it }
        while (t < total) {
            t = withFrameMillis { ((it - start) * speed).roundToLong() }
        }
        t = Long.MAX_VALUE
    }

    Canvas(
        modifier
            .aspectRatio((1 / Drawing.ASPECT).toFloat())
            .clickable {
                t = 0L
                run++
            },
    ) {
        drawIntoCanvas { DrawingRenderer.render(it.nativeCanvas, d, size.width, size.height, t) }
    }
}

private fun Double.round4(): Double = Math.round(this * 10000) / 10000.0
