package net.olamaelcu.livtet.sync

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QrDecoderTest {
    private val nodeId = "abcdefghijklmnopqrstuvwxyz234567"
    private val payload = "livtet://pair?node=$nodeId"

    private fun qr(
        contents: String,
        size: Int = 320,
    ): ByteArray {
        val matrix = QRCodeWriter().encode(contents, BarcodeFormat.QR_CODE, size, size)
        return ByteArray(size * size) { index ->
            val x = index % size
            val y = index / size
            if (matrix[x, y]) 0 else 255.toByte()
        }
    }

    @Test
    fun `a pairing code decodes to the node it names`() {
        val decoder = QrDecoder()

        val text = decoder.decode(qr(payload), rowStride = 320, width = 320, height = 320)

        assertEquals(payload, text)
        assertEquals(nodeId, PairPayload.nodeId(text!!), "the decode lands on the same node id")
    }

    @Test
    fun `a padded camera row still decodes`() {
        // CameraX pads Y rows to a hardware stride; reading the buffer as if it
        // were exactly `width` wide shears the image and decodes as nothing.
        val size = 320
        val rowStride = 384
        val tight = qr(payload, size)
        val padded = ByteArray(rowStride * size) { 0x7f }
        for (y in 0 until size) {
            tight.copyInto(
                destination = padded,
                destinationOffset = y * rowStride,
                startIndex = y * size,
                endIndex = (y + 1) * size,
            )
        }

        val text = QrDecoder().decode(padded, rowStride, width = size, height = size)

        assertEquals(payload, text)
    }

    @Test
    fun `a frame with no code decodes to nothing`() {
        val blank = ByteArray(320 * 320) { 255.toByte() }

        assertNull(QrDecoder().decode(blank, rowStride = 320, width = 320, height = 320))
    }

    @Test
    fun `a buffer shorter than the stated geometry is refused`() {
        val tooShort = ByteArray(100)

        assertNull(QrDecoder().decode(tooShort, rowStride = 320, width = 320, height = 320))
    }
}
