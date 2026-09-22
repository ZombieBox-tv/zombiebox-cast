package io.github.diegog0477.zombiebox.cast.features.casting.domain.model

enum class CaptureQuality {
    AUTO,
    SD,
    HD,
}

/** User limits may lower a receiver budget, never raise it. */
data class CapturePreferences(
    val quality: CaptureQuality = CaptureQuality.AUTO,
    val lowLatency: Boolean = false,
    val audio: Boolean = false,
) {
    fun video(receiver: CastVideo): CastVideo {
        val width = if (quality == CaptureQuality.SD) 854 else 1280
        val height = if (quality == CaptureQuality.SD) 480 else 720
        return receiver.copy(
            maxWidth = minOf(receiver.maxWidth, width),
            maxHeight = minOf(receiver.maxHeight, height),
            bitrate = minOf(receiver.bitrate, if (lowLatency) 1000000 else 2000000),
        )
    }

    val keyFrameSeconds: Int
        get() = if (lowLatency) 1 else 2
}
