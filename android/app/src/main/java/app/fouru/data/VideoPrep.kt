package app.fouru.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min
import kotlin.math.roundToInt

/** Vídeo listo para enviar: H.264 con el lado corto a 720 px como mucho y ~2 Mbps (DECISIONS §8). */
object VideoPrep {
    const val MAX_BYTES = 15L * 1024 * 1024
    private const val SHORT_SIDE = 720
    private const val BITRATE = 2_000_000

    @OptIn(UnstableApi::class)
    suspend fun compress(ctx: Context, uri: Uri): File {
        val (w, h) = displaySize(ctx, uri)
        val scale = min(1.0, SHORT_SIDE.toDouble() / min(w, h).coerceAtLeast(1))
        val outW = even(w * scale)
        val outH = even(h * scale)
        val out = File(ctx.cacheDir, "video-${System.nanoTime()}.mp4")

        // Transformer necesita un hilo con Looper: se crea y arranca en el principal.
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val transformer = Transformer.Builder(ctx)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(ctx)
                            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(BITRATE).build())
                            .build(),
                    )
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            cont.resume(out)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            out.delete()
                            cont.resumeWithException(exportException)
                        }
                    })
                    .build()
                val item = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                    .setEffects(Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT))))
                    .build()
                transformer.start(item, out.absolutePath)
                cont.invokeOnCancellation {
                    transformer.cancel()
                    out.delete()
                }
            }
        }
    }

    /** Ancho y alto tal como se ve (con la rotación aplicada). */
    private fun displaySize(ctx: Context, uri: Uri): Pair<Int, Int> {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            return if (rot == 90 || rot == 270) h to w else w to h
        } finally {
            r.release()
        }
    }

    private fun even(v: Double): Int = ((v / 2).roundToInt() * 2).coerceAtLeast(2)
}
