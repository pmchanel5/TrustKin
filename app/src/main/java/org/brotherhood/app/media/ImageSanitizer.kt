package org.brotherhood.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.max

data class SanitizedImage(
    val base64: String,
    val mimeType: String,
    val byteSize: Int,
    val width: Int,
    val height: Int,
)

object ImageSanitizer {
    private const val MAX_DIMENSION = 2048
    private const val MAX_OUTPUT_BYTES = 2_300_000

    fun sanitize(context: Context, uri: Uri): SanitizedImage {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Immagine non leggibile" }
            BitmapFactory.decodeStream(input, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Formato immagine non valido" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_DIMENSION * 2) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri).use { input ->
            requireNotNull(BitmapFactory.decodeStream(input, null, options)) { "Immagine non leggibile" }
        }
        val orientation = resolver.openInputStream(uri).use { input ->
            runCatching { ExifInterface(requireNotNull(input)).rotationDegrees }.getOrDefault(0)
        }
        val oriented = if (orientation == 0) {
            decoded
        } else {
            Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                Matrix().apply { postRotate(orientation.toFloat()) },
                true,
            ).also { if (it !== decoded) decoded.recycle() }
        }
        val scale = minOf(1f, MAX_DIMENSION.toFloat() / max(oriented.width, oriented.height))
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                oriented,
                (oriented.width * scale).toInt().coerceAtLeast(1),
                (oriented.height * scale).toInt().coerceAtLeast(1),
                true,
            ).also { if (it !== oriented) oriented.recycle() }
        } else oriented
        try {
            var quality = 88
            var bytes: ByteArray
            do {
                val output = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, quality, output)
                bytes = output.toByteArray()
                quality -= 8
            } while (bytes.size > MAX_OUTPUT_BYTES && quality >= 52)
            require(bytes.size <= MAX_OUTPUT_BYTES) { "Immagine troppo grande dopo la compressione" }
            return SanitizedImage(
                base64 = Base64.getEncoder().encodeToString(bytes),
                mimeType = "image/jpeg",
                byteSize = bytes.size,
                width = resized.width,
                height = resized.height,
            )
        } finally {
            resized.recycle()
        }
    }
}
