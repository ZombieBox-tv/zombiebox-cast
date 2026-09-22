package io.github.diegog0477.zombiebox.cast.features.history.presentation.viewmodel

import io.github.diegog0477.zombiebox.cast.features.history.domain.model.CaptureSession
import io.github.diegog0477.zombiebox.cast.features.history.domain.repository.HistoryRepository

class HistoryViewModel(private val repository: HistoryRepository) {
    var state: List<CaptureSession> = emptyList()
        private set

    var observer: ((List<CaptureSession>) -> Unit)? = null

    fun refresh() {
        val next = repository.sessions()
        if (next != state) {
            state = next
            observer?.invoke(state)
        }
    }

    fun clearFinished() {
        repository.clearFinished()
        refresh()
    }
}
