package net.olamaelcu.livtet.sync

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Turns one camera frame into the text of a QR code, if it holds one.
 *
 * Kept out of [QrScannerActivity] so the decode rules — QR only, and how a padded camera row is
 * read — are testable without a device.
 *
 * The luminance source is built with the frame's row stride rather than its width: CameraX pads Y
 * rows to a hardware-friendly stride, and describing the buffer as `width` wide skews every row
 * after the first, which decodes as nothing at all.
 */
internal class QrDecoder {
    private val reader =
        MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }

    /**
     * The QR text in this luminance plane, or `null` when the frame holds none or the buffer is too
     * short for the stated geometry.
     */
    fun decode(
        y: ByteArray,
        rowStride: Int,
        width: Int,
        height: Int,
    ): String? {
        if (rowStride <= 0 || width <= 0 || height <= 0) return null
        if (y.size < rowStride * (height - 1) + width) return null

        val source =
            PlanarYUVLuminanceSource(
                y,
                rowStride,
                height,
                // left =
                0,
                // top =
                0,
                width.coerceAtMost(rowStride),
                height,
                // reverseHorizontal =
                false,
            )
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: com.google.zxing.NotFoundException) {
            null
        } catch (_: com.google.zxing.FormatException) {
            null
        } catch (_: com.google.zxing.ChecksumException) {
            null
        } finally {
            reader.reset()
        }
    }
}
