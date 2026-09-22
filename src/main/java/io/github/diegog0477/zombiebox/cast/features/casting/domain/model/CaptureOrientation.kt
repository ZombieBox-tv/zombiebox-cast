package io.github.diegog0477.zombiebox.cast.features.casting.domain.model

/** Fixed canvas uses the platform's aspect-preserving projection on Android 12L+. */
enum class CaptureOrientation {
    AUTO,
    PORTRAIT,
    LANDSCAPE;

    fun supported(androidApi: Int) = this == AUTO || androidApi >= 32

    fun effective(androidApi: Int) = if (supported(androidApi)) this else AUTO

    fun source(width: Int, height: Int): Pair<Int, Int> =
        when (this) {
            AUTO -> width to height
            PORTRAIT -> 1080 to 1920
            LANDSCAPE -> 1920 to 1080
        }
}
