package io.github.diegog0477.zombiebox.cast.features.casting.presentation.viewmodel

import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CapturePreferences
import io.github.diegog0477.zombiebox.cast.features.casting.domain.repository.CapturePreferencesRepository

class CapturePreferencesViewModel(private val repository: CapturePreferencesRepository) {
    var state = repository.load()
        private set

    var observer: ((CapturePreferences) -> Unit)? = null
    private var locked = false

    fun lock(value: Boolean) {
        locked = value
    }

    fun update(value: CapturePreferences) {
        if (locked) return
        repository.save(value)
        state = value
        observer?.invoke(value)
    }
}
