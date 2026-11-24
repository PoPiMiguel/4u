package app.fouru.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

object MediaSaver {

    /** Guarda en la galería (Imágenes/4u o Películas/4u). Sin permisos en Android 10+. */
    fun saveToGallery(ctx: Context, bytes: ByteArray, fileName: String, mime: String, video: Boolean): Uri {
        val collection = if (video) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val folder = if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/4u")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(collection, values) ?: error("No se pudo crear el archivo en la galería")
        resolver.openOutputStream(uri).use { out ->
            requireNotNull(out) { "No se pudo escribir en la galería" }.write(bytes)
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    /** Archivos que no van a la galería (HTML, dibujos). */
    fun savePrivate(ctx: Context, bytes: ByteArray, fileName: String): File {
        val dir = File(ctx.filesDir, "media").apply { mkdirs() }
        return File(dir, fileName).apply { writeBytes(bytes) }
    }

    /** Miniatura para el widget. Devuelve la ruta o null. */
    fun saveThumb(ctx: Context, id: String, bitmap: Bitmap?): String? {
        if (bitmap == null) return null
        val dir = File(ctx.filesDir, "thumbs").apply { mkdirs() }
        val scaled = scaleDown(bitmap, 512)
        val file = File(dir, "$id.jpg")
        file.writeBytes(jpeg(scaled, 80))
        return file.absolutePath
    }

    fun decodeScaled(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return scaleDown(bmp, maxSide)
    }

    /** Foto lista para enviar: rotada según EXIF, lado máximo [maxSide], JPEG. */
    fun prepareJpeg(bytes: ByteArray, maxSide: Int): ByteArray {
        val bmp = decodeScaled(bytes, maxSide) ?: error("Imagen no válida")
        val orientation = ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val rotated = if (degrees == 0f) bmp else {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
        }
        return jpeg(rotated, 85)
    }

    fun videoFrame(ctx: Context, uri: Uri): Bitmap? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(ctx, uri)
            retriever.getFrameAtTime(500_000)
        } finally {
            retriever.release()
        }
    }.getOrNull()

    fun jpeg(bmp: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    private fun scaleDown(bmp: Bitmap, maxSide: Int): Bitmap {
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        if (scale >= 1f) return bmp
        return Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * scale).toInt().coerceAtLeast(1),
            (bmp.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }
}
