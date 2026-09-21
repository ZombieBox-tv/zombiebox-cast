package io.github.diegog0477.zombiebox.cast.features.companion

import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class PairingQrTest {
    @Test
    fun decodesPinnedGatewayEncoderFixture() {
        val pixels =
            GZIPInputStream(javaClass.getResourceAsStream("/pairing-qr.gray.gz")!!).use {
                it.readBytes()
            }
        assertEquals(384 * 384, pixels.size)
        val source = PlanarYUVLuminanceSource(pixels, 384, 384, 0, 0, 384, 384, false)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
        val expected =
            javaClass.getResourceAsStream("/pairing-qr.json")!!.bufferedReader().use {
                it.readText().trim()
            }
        assertEquals(expected.filterNot { it.isWhitespace() }, decoded)
    }
}
