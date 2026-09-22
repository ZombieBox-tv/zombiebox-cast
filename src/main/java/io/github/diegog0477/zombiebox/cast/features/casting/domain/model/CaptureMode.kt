package io.github.diegog0477.zombiebox.cast.features.casting.domain.model

enum class CaptureMode {
    SCREEN,
    AUDIO;

    fun supported(api: Int) = api >= if (this == AUDIO) 29 else 21

    fun acceptsGrant(mode: String) =
        if (this == SCREEN) mode == "SCREEN" || mode.isEmpty() else mode == "AUDIO"
}
