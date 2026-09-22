package io.github.diegog0477.zombiebox.cast.features.casting.data

import android.content.SharedPreferences
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CaptureOrientation
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CapturePreferences
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CaptureQuality
import io.github.diegog0477.zombiebox.cast.features.casting.domain.repository.CapturePreferencesRepository

class LocalCapturePreferences(private val preferences: SharedPreferences) :
    CapturePreferencesRepository {
    override fun load() =
        CapturePreferences(
            quality =
                CaptureQuality.values().firstOrNull {
                    it.name == preferences.getString("captureQuality", "AUTO")
                } ?: CaptureQuality.AUTO,
            lowLatency = preferences.getBoolean("captureLowLatency", false),
            audio = preferences.getBoolean("captureAudio", false),
            orientation =
                CaptureOrientation.values().firstOrNull {
                    it.name == preferences.getString("captureOrientation", "AUTO")
                } ?: CaptureOrientation.AUTO,
        )

    override fun save(value: CapturePreferences) {
        preferences
            .edit()
            .putString("captureQuality", value.quality.name)
            .putString("captureOrientation", value.orientation.name)
            .putBoolean("captureLowLatency", value.lowLatency)
            .putBoolean("captureAudio", value.audio)
            .apply()
    }
}
