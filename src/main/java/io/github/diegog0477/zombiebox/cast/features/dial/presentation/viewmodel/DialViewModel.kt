package io.github.diegog0477.zombiebox.cast.features.dial.presentation.viewmodel

import io.github.diegog0477.zombiebox.cast.features.dial.domain.model.DialDevice
import io.github.diegog0477.zombiebox.cast.features.dial.domain.repository.DialRepository

class DialViewModel(
    private val repository: DialRepository,
    private val execute: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
) {
    data class State(
        val devices: List<DialDevice> = emptyList(),
        val busy: Boolean = false,
        val result: String = "IDLE",
    )

    var state = State()
        private set

    var observer: ((State) -> Unit)? = null
    @Volatile private var closed = false

    fun refresh() = work { State(repository.discover()) }

    fun launch(device: DialDevice) {
        if (!state.devices.contains(device)) return
        val before = state
        work {
            before.copy(
                busy = false,
                result = if (repository.launch(device)) "RUNNING" else "FAILED",
            )
        }
    }

    fun close() {
        closed = true
        observer = null
        repository.close()
    }

    private fun work(action: () -> State) {
        if (closed || state.busy) return
        val before = state
        update(state.copy(busy = true))
        execute {
            if (closed) return@execute
            val result =
                try {
                    action()
                } catch (_: Exception) {
                    before.copy(busy = false, result = "FAILED")
                }
            deliver { if (!closed) update(result) }
        }
    }

    private fun update(value: State) {
        state = value
        observer?.invoke(value)
    }
}
