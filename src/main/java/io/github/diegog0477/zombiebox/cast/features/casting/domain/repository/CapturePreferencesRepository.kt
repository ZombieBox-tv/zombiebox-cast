package io.github.diegog0477.zombiebox.cast.features.casting.domain.repository

import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CapturePreferences

interface CapturePreferencesRepository {
    fun load(): CapturePreferences

    fun save(value: CapturePreferences)
}
