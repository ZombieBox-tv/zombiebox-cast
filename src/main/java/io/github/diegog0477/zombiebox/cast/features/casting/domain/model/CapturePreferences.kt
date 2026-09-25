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
                CaptureQuality.FULL_HD -> 1920
                else -> receiver.maxWidth
            }
        val height =
            when (quality) {
                CaptureQuality.SD -> 480
                CaptureQuality.HD -> 720
                CaptureQuality.FULL_HD -> 1080
                else -> receiver.maxHeight
            }
        val rate =
            when (quality) {
                CaptureQuality.SD -> 1200000
                CaptureQuality.HD -> 2000000
                CaptureQuality.FULL_HD -> 4000000
                else -> receiver.bitrate
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
