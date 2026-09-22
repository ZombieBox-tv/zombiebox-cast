package io.github.diegog0477.zombiebox.cast.features.casting.domain.model

enum class CaptureQuality {
    AUTO,
    SD,
    HD,
    FULL_HD,
}

/** User limits may lower a receiver budget, never raise it. */
data class CapturePreferences(
    val quality: CaptureQuality = CaptureQuality.AUTO,
    val lowLatency: Boolean = false,
    val audio: Boolean = false,
    val orientation: CaptureOrientation = CaptureOrientation.AUTO,
    val mode: CaptureMode = CaptureMode.SCREEN,
) {
    fun video(receiver: CastVideo): CastVideo {
        val width =
            when (quality) {
                CaptureQuality.SD -> 854
                CaptureQuality.HD -> 1280
                else -> 1920
            }
        val height =
            when (quality) {
                CaptureQuality.SD -> 480
                CaptureQuality.HD -> 720
                else -> 1080
            }
        val rate =
            when (quality) {
                CaptureQuality.SD -> 1200000
                CaptureQuality.HD -> 2000000
                else -> 4000000
            }
        return receiver.copy(
            maxWidth = minOf(receiver.maxWidth, width),
            maxHeight = minOf(receiver.maxHeight, height),
            bitrate = minOf(receiver.bitrate, if (lowLatency) 1000000 else rate),
        )
    }

    val keyFrameSeconds: Int
        get() = if (lowLatency) 1 else 2
}
