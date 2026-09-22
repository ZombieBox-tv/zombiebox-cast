package io.github.diegog0477.zombiebox.cast.features.casting.platform

interface CaptureEncoder {
    fun run()

    fun checkProgress()

    fun interrupt()
}
