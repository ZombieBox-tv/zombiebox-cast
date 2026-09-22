package io.github.diegog0477.zombiebox.cast.features.casting.domain.model

/** Fail closed if an OEM ignores the requested AVC Baseline profile/level. */
object AvcEnvelope {
    fun accepts(sps: ByteArray, width: Int, height: Int): Boolean {
        if (sps.size < 4 || (sps[0].toInt() and 31) != 7 || (sps[1].toInt() and 255) != 66)
            return false
        val level = sps[3].toInt() and 255
        val ceiling =
            if (width > 1280 || height > 720) 40 else if (width > 640 || height > 480) 31 else 30
        return level in listOf(10, 11, 12, 13, 20, 21, 22, 30, 31, 32, 40) && level <= ceiling
    }
}
