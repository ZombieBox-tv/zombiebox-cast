package io.github.diegog0477.zombiebox.cast.features.casting.domain.model

data class CastGrant(
    val id: String,
    val host: String,
    val port: Int,
    val path: String,
    val user: String,
    val token: String,
    val video: CastVideo = CastVideo(),
)

/** Provider-neutral, bounded encoder budget negotiated for the receiver. */
data class CastVideo(
    val maxWidth: Int = 640,
    val maxHeight: Int = 360,
    val fps: Int = 24,
    val bitrate: Int = 800000,
) {
    init {
        require(maxWidth in 32..1920 && maxHeight in 32..1080)
        require(fps in 10..30 && bitrate in 128000..4000000)
    }

    fun fallbacks(): List<CastVideo> =
        listOf(
                this,
                copy(
                    maxWidth = minOf(maxWidth, 1280),
                    maxHeight = minOf(maxHeight, 720),
                    bitrate = minOf(bitrate, 2000000),
                ),
                copy(
                    maxWidth = minOf(maxWidth, 854),
                    maxHeight = minOf(maxHeight, 480),
                    fps = minOf(fps, 24),
                    bitrate = minOf(bitrate, 1200000),
                ),
                copy(
                    maxWidth = minOf(maxWidth, 640),
                    maxHeight = minOf(maxHeight, 360),
                    fps = minOf(fps, 24),
                    bitrate = minOf(bitrate, 800000),
                ),
            )
            .distinct()

    fun dimensions(
        width: Int,
        height: Int,
        widthAlignment: Int = 16,
        heightAlignment: Int = 16,
    ): Pair<Int, Int> {
        require(width > 0 && height > 0)
        require(widthAlignment in 1..64 && heightAlignment in 1..64)
        val scale = minOf(1.0, maxWidth.toDouble() / width, maxHeight.toDouble() / height)
        return Pair(
            (width * scale).toInt().coerceAtLeast(32) / widthAlignment * widthAlignment,
            (height * scale).toInt().coerceAtLeast(32) / heightAlignment * heightAlignment,
        )
    }
}
