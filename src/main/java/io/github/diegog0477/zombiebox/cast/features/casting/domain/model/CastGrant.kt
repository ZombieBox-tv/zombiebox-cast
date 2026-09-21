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
        require(maxWidth in 32..1280 && maxHeight in 32..720)
        require(fps in 10..30 && bitrate in 128000..2000000)
    }

    fun dimensions(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val scale = minOf(1.0, maxWidth.toDouble() / width, maxHeight.toDouble() / height)
        return Pair(
            (width * scale).toInt().coerceAtLeast(32) / 16 * 16,
            (height * scale).toInt().coerceAtLeast(32) / 16 * 16,
        )
    }
}
