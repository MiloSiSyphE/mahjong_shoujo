package dev.mahjong.shoujo.cv.api.model

import android.graphics.Bitmap
import android.net.Uri

/**
 * Sealed input type so the recognition engine can handle different image sources
 * without exposing Bitmap-specific logic to callers.
 *
 * Adapters that need a Bitmap should convert internally.
 */
sealed class RecognitionInput {
    abstract val captureType: CaptureType

    data class BitmapInput(
        val bitmap: Bitmap,
        override val captureType: CaptureType,
    ) : RecognitionInput()

    data class UriInput(
        val uri: Uri,
        override val captureType: CaptureType,
    ) : RecognitionInput()
}

enum class CaptureType {
    /** Image originated from a digital screenshot (Majsoul, Tenhou, etc.). */
    SCREENSHOT,
    /** Image captured from the device camera in real time. */
    CAMERA_PHOTO,
    /** Image loaded from the device gallery / file picker. */
    GALLERY_PHOTO,
}
