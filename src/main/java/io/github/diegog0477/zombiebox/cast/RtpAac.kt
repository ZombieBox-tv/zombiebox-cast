package io.github.diegog0477.zombiebox.cast

/** RFC 3640 AAC-hbr: one access unit, 13-bit size and 3-bit index. */
class RtpAac(private val ssrc:Int) {
    private var sequence=0
    fun packet(frame:ByteArray,timestamp:Long):ByteArray {
        require(frame.isNotEmpty() && frame.size<=8191)
        val packet=ByteArray(16+frame.size);packet[0]=0x80.toByte();packet[1]=(128 or 97).toByte()
        packet[2]=(sequence shr 8).toByte();packet[3]=sequence++.toByte()
        for(i in 0..3){packet[4+i]=(timestamp shr (24-i*8)).toByte();packet[8+i]=(ssrc shr (24-i*8)).toByte()}
        packet[12]=0;packet[13]=16;packet[14]=(frame.size shr 5).toByte();packet[15]=(frame.size shl 3).toByte()
        frame.copyInto(packet,16);return packet
    }
}
