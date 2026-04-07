package dev.mahjong.shoujo.image

import dev.mahjong.shoujo.cv.api.model.ImageFormat
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [detectImageFormat].
 *
 * detectImageFormat is a pure function that inspects magic bytes — no Android SDK
 * needed, so these run on the JVM without instrumentation.
 */
class ImageFormatDetectorTest {

    @Test
    fun `detects JPEG from canonical magic bytes`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        assertEquals(ImageFormat.JPEG, detectImageFormat(jpeg))
    }

    @Test
    fun `detects JPEG from JFIF header`() {
        val jfif = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte())
        assertEquals(ImageFormat.JPEG, detectImageFormat(jfif))
    }

    @Test
    fun `detects PNG from canonical magic bytes`() {
        // 89 50 4E 47 0D 0A 1A 0A  (‰PNG\r\n\u001a\n)
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A.toByte(), 0x0A
        )
        assertEquals(ImageFormat.PNG, detectImageFormat(png))
    }

    @Test
    fun `PNG is detected even with only 4 leading bytes available`() {
        val shortPng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        assertEquals(ImageFormat.PNG, detectImageFormat(shortPng))
    }

    @Test
    fun `PNG takes precedence check — first byte differs from JPEG`() {
        // Sanity: PNG magic starts with 0x89, not 0xFF — no ambiguity
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        assertEquals(ImageFormat.PNG, detectImageFormat(png))
        assertEquals(ImageFormat.JPEG, detectImageFormat(jpeg))
    }

    @Test
    fun `falls back to JPEG for unknown magic bytes`() {
        val unknown = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        assertEquals(ImageFormat.JPEG, detectImageFormat(unknown))
    }

    @Test
    fun `falls back to JPEG for empty array`() {
        assertEquals(ImageFormat.JPEG, detectImageFormat(ByteArray(0)))
    }

    @Test
    fun `falls back to JPEG for single byte`() {
        assertEquals(ImageFormat.JPEG, detectImageFormat(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun `falls back to JPEG for two bytes that start like JPEG but are truncated`() {
        // Only 2 bytes — not enough for a 3-byte JPEG check
        val truncated = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        assertEquals(ImageFormat.JPEG, detectImageFormat(truncated))
    }
}
