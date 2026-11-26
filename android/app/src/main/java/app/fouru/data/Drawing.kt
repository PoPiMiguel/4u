package app.fouru.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Formato de los dibujos (DECISIONS §12), igual que desktop/src/lib/drawing.ts.
 * x/y normalizadas a 0..1 sobre el ancho y el alto; `width` relativo al ancho;
 * cada punto es [x, y, tMs] con las pausas entre trazos recortadas.
 */
@Serializable
data class Drawing(
    val w: Double = 1.0,
    val h: Double = ASPECT,
    val bg: String = "#121216",
    val strokes: List<Stroke> = emptyList(),
) {
    companion object {
        const val ASPECT = 1.5
        const val MAX_GAP_MS = 350L
        const val MAX_PLAY_MS = 6000L
        // encodeDefaults: sin él, Kotlin omite bg/w/h cuando valen lo de por
        // defecto y el escritorio no sabe qué fondo pintar.
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun parse(text: String): Drawing = json.decodeFromString(text)
    }

    fun toJson(): String = json.encodeToString(this)

    val duration: Long get() = strokes.maxOfOrNull { s -> s.points.lastOrNull()?.getOrNull(2)?.toLong() ?: 0L } ?: 0L
}

@Serializable
data class Stroke(
    val color: String,
    val width: Double,
    val points: List<List<Double>>,
)

object DrawingRenderer {
    private fun color(hex: String): Int = runCatching { android.graphics.Color.parseColor(hex) }.getOrDefault(android.graphics.Color.WHITE)

    /** Pinta el dibujo hasta el instante [t] (ms del dibujo). */
    fun render(canvas: Canvas, d: Drawing, w: Float, h: Float, t: Long = Long.MAX_VALUE) {
        canvas.drawColor(color(d.bg))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (s in d.strokes) {
            val pts = s.points.filter { it.size >= 3 && it[2] <= t }
            if (pts.isEmpty()) break
            paint.color = color(s.color)
            paint.strokeWidth = max(1f, (s.width * w).toFloat())
            if (pts.size == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle((pts[0][0] * w).toFloat(), (pts[0][1] * h).toFloat(), paint.strokeWidth / 2, paint)
                paint.style = Paint.Style.STROKE
                continue
            }
            val path = Path().apply {
                moveTo((pts[0][0] * w).toFloat(), (pts[0][1] * h).toFloat())
                for (i in 1 until pts.size) lineTo((pts[i][0] * w).toFloat(), (pts[i][1] * h).toFloat())
            }
            canvas.drawPath(path, paint)
        }
    }

    /** Imagen final (miniatura). */
    fun bitmap(d: Drawing, maxSide: Int = 512): Bitmap {
        val h = maxSide
        val w = (maxSide / Drawing.ASPECT).roundToInt()
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            render(Canvas(it), d, w.toFloat(), h.toFloat())
        }
    }
}
