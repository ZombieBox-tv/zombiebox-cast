package io.github.diegog0477.zombiebox.cast.features.casting.presentation.viewmodel

import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CaptureMode
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CastGrant
import io.github.diegog0477.zombiebox.cast.features.casting.domain.repository.CastRepository

class CastViewModel(
    private val repository: CastRepository,
    private val execute: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
) {
    var state = CastState()
        private set

    var observer: ((CastState) -> Unit)? = null
    private val gate = Any()
    private var closed = false
    private var generation = 0
    private var pending: CastGrant? = null

    fun clearReceiver() {
        val abandoned =
            synchronized(gate) {
                generation++
                state = CastState()
                pending.also { pending = null }
            }
        if (abandoned != null) execute { discard(abandoned) }
        observer?.invoke(state)
    }

    fun select(id: String) {
        if (!state.busy && !closed) {
            state = state.copy(selected = id)
            observer?.invoke(state)
        }
    }

    fun connect(address: String, code: String) = work { previous ->
        repository.pair(address, code)
        previous.copy(receivers = repository.receivers(), selected = "")
    }

    fun refresh() = work { previous ->
        val receivers = repository.receivers()
        previous.copy(
            receivers = receivers,
            selected = previous.selected.takeIf { id -> receivers.any { it.id == id } } ?: "",
        )
    }

    fun start(mode: CaptureMode = CaptureMode.SCREEN) {
        if (state.selected.isNotEmpty())
            work { previous -> previous.copy(grant = repository.create(previous.selected, mode)) }
    }

    fun consumeGrant() {
        synchronized(gate) {
            pending = null
            state = state.copy(grant = null)
        }
    }

    private fun discard(grant: CastGrant?) {
        grant?.let {
            try {
                repository.stop(it.id)
            } catch (_: Exception) {}
        }
    }

    private fun work(task: (CastState) -> CastState) {
        if (closed || state.busy) return
        val revision = generation
        val previous = state
        state = state.copy(busy = true, failed = false)
        observer?.invoke(state)
        execute {
            val next =
                try {
                    task(previous).copy(busy = false, failed = false)
                } catch (_: Exception) {
                    previous.copy(busy = false, failed = true)
                }
            val abandoned =
                synchronized(gate) {
                    if (closed || revision != generation) true
                    else {
                        pending = next.grant
                        deliver {
                            synchronized(gate) {
                                if (!closed && revision == generation) {
                                    state = next
                                    observer?.invoke(state)
                                }
                            }
                        }
                        false
                    }
                }
            if (abandoned) discard(next.grant)
        }
    }

    fun close() {
        val abandoned =
            synchronized(gate) {
                closed = true
                observer = null
                pending.also { pending = null }
            }
        if (abandoned != null) execute { discard(abandoned) }
    }
}
