package dev.mahjong.shoujo.cv.api.model

/**
 * Platform-neutral input to the recognition engine.
 *
 * Android-specific conversions (Bitmap → ByteArray, Uri → ByteArray) belong in
 * :cv:baseline or :app, not here. This keeps :cv:api free of the Android SDK and
 * fully testable on the JVM.
 */
sealed class RecognitionInput {
    abstract val captureType: CaptureType

    /**
     * Raw image bytes (e.g. JPEG or PNG) with declared dimensions.
     * The adapter is responsible for decoding.
     */
    @Suppress("ArrayInDataClass")
    data class BytesInput(
        val bytes: ByteArray,
        val widthPx: Int,
        val heightPx: Int,
        val format: ImageFormat,
        override val captureType: CaptureType,
    ) : RecognitionInput()
}

enum class ImageFormat { JPEG, PNG, RGB_888 }

enum class CaptureType {
    /** Image originated from a digital screenshot (Majsoul, Tenhou, etc.). */
    SCREENSHOT,
    /** Image captured from the device camera in real time. */
    CAMERA_PHOTO,
    /** Image loaded from the device gallery / file picker. */
    GALLERY_PHOTO,
}
