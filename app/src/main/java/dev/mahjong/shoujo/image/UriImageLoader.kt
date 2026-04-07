package dev.mahjong.shoujo.image

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mahjong.shoujo.cv.api.model.CaptureType
import dev.mahjong.shoujo.cv.api.model.ImageFormat
import dev.mahjong.shoujo.cv.api.model.RecognitionInput
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts an Android content [Uri] into a platform-neutral [RecognitionInput.BytesInput].
 *
 * This is the single place in :app where Android-specific URI loading happens.
 * Keeping it here means :cv:api stays free of Android SDK types (DESIGN.md §5.3).
 *
 * Call from an IO dispatcher — this does blocking I/O.
 */
@Singleton
class UriImageLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Reads all bytes from [uri] and wraps them as [RecognitionInput.BytesInput].
     *
     * @throws IOException if the URI cannot be opened, is empty, or dimensions cannot be decoded.
     */
    fun load(uri: Uri, captureType: CaptureType): RecognitionInput.BytesInput {
        val bytes = context.contentResolver.openInputStream(uri)
            ?.use { it.readBytes() }
            ?: throw IOException("Cannot open input stream for URI: $uri")

        if (bytes.isEmpty()) throw IOException("Empty content at URI: $uri")

        val format = detectImageFormat(bytes)
        val (w, h) = decodeDimensions(bytes)

        return RecognitionInput.BytesInput(
            bytes = bytes,
            widthPx = w,
            heightPx = h,
            format = format,
            captureType = captureType,
        )
    }
}

/**
 * Detects [ImageFormat] from leading magic bytes.
 *
 * Checks PNG signature first (8 bytes), then JPEG (3 bytes).
 * Defaults to JPEG for anything unrecognised — screenshots are typically
 * JPEG or PNG, so the JPEG fallback is safe for Phase 1.
 *
 * Exposed as a package-internal function so it can be covered by JVM unit tests
 * without needing Android instrumentation.
 */
internal fun detectImageFormat(bytes: ByteArray): ImageFormat {
    // PNG: 89 50 4E 47 (‰PNG)
    if (bytes.size >= 4 &&
        bytes[0] == 0x89.toByte() &&
        bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() &&
        bytes[3] == 0x47.toByte()
    ) return ImageFormat.PNG

    // JPEG: FF D8 FF
    if (bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() &&
        bytes[1] == 0xD8.toByte() &&
        bytes[2] == 0xFF.toByte()
    ) return ImageFormat.JPEG

    // Fallback: screenshots are almost always JPEG or PNG; treat unknown as JPEG.
    return ImageFormat.JPEG
}

/**
 * Decodes image dimensions without allocating the full bitmap.
 * Returns (0, 0) if dimensions cannot be determined.
 */
private fun decodeDimensions(bytes: ByteArray): Pair<Int, Int> {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    return Pair(maxOf(opts.outWidth, 0), maxOf(opts.outHeight, 0))
}
