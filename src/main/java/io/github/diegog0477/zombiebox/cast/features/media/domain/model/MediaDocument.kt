package io.github.diegog0477.zombiebox.cast.features.media.domain.model

data class MediaDocument(val locator: String, val title: String, val bytes: Long) {
    val supportedSize
        get() = bytes in 1..268435456L
}
