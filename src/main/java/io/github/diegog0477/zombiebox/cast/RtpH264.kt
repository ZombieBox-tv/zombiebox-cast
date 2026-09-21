package io.github.diegog0477.zombiebox.cast

/** RFC 6184 single-NAL / FU-A packetization. Sequence and timestamp wrap by design. */
class RtpH264(private val ssrc: Int, initialSequence: Int = 0) {
    private var sequence = initialSequence

    fun packets(nal: ByteArray, timestamp: Long, last: Boolean): List<ByteArray> {
        require(nal.isNotEmpty() && nal.size <= 4 * 1024 * 1024)
        val result = ArrayList<ByteArray>()
        fun packet(payload: ByteArray, marker: Boolean) {
            val bytes = ByteArray(12 + payload.size)
            bytes[0] = 0x80.toByte()
            bytes[1] = (96 or if (marker) 128 else 0).toByte()
            bytes[2] = (sequence shr 8).toByte()
            bytes[3] = sequence++.toByte()
            for (i in 0..3) {
                bytes[4 + i] = (timestamp shr (24 - i * 8)).toByte()
                bytes[8 + i] = (ssrc shr (24 - i * 8)).toByte()
            }
            payload.copyInto(bytes, 12)
            result.add(bytes)
        }
        if (nal.size <= 1200) packet(nal, last)
        else {
            var offset = 1
            while (offset < nal.size) {
                val count = minOf(1198, nal.size - offset)
                val payload = ByteArray(count + 2)
                payload[0] = ((nal[0].toInt() and 0xe0) or 28).toByte()
                payload[1] =
                    ((nal[0].toInt() and 31) or
                            (if (offset == 1) 128 else 0) or
                            (if (offset + count == nal.size) 64 else 0))
                        .toByte()
                nal.copyInto(payload, 2, offset, offset + count)
                packet(payload, last && offset + count == nal.size)
                offset += count
            }
        }
        return result
    }

    companion object {
        fun split(data: ByteArray): List<ByteArray> {
            fun prefix(i: Int): Int =
                when {
                    i + 3 < data.size &&
                        data[i] == 0.toByte() &&
                        data[i + 1] == 0.toByte() &&
                        data[i + 2] == 0.toByte() &&
                        data[i + 3] == 1.toByte() -> 4
                    i + 2 < data.size &&
                        data[i] == 0.toByte() &&
                        data[i + 1] == 0.toByte() &&
                        data[i + 2] == 1.toByte() -> 3
                    else -> 0
                }
            val out = ArrayList<ByteArray>()
            var start = -1
            var i = 0
            while (i < data.size) {
                val length = prefix(i)
                if (length > 0) {
                    if (start >= 0 && i > start) out.add(data.copyOfRange(start, i))
                    start = i + length
                    i += length
                } else i++
            }
            if (start >= 0) {
                if (start < data.size) out.add(data.copyOfRange(start, data.size))
                return out
            }
            // Some encoders emit four-byte AVCC lengths rather than Annex B.
            i = 0
            while (i + 4 <= data.size) {
                var length = 0
                for (j in 0..3) length = (length shl 8) or (data[i + j].toInt() and 255)
                if (length <= 0 || length > data.size - i - 4) {
                    out.clear()
                    break
                }
                out.add(data.copyOfRange(i + 4, i + 4 + length))
                i += 4 + length
            }
            return if (out.isNotEmpty() && i == data.size) out else listOf(data)
        }
    }
}
