package io.github.diegog0477.zombiebox.cast.features.mediaqueue.presentation.viewmodel

import io.github.diegog0477.zombiebox.cast.features.mediaqueue.domain.model.MediaQueue
import io.github.diegog0477.zombiebox.cast.features.mediaqueue.domain.model.QueueInput
import io.github.diegog0477.zombiebox.cast.features.mediaqueue.domain.repository.MediaQueueRepository

class MediaQueueViewModel(
    private val repository: MediaQueueRepository,
    private val execute: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
) {
    data class State(
        val queue: MediaQueue = MediaQueue(),
        val busy: Boolean = false,
        val error: Boolean = false,
    )

    var state = State()
        private set

    var observer: ((State) -> Unit)? = null
    @Volatile private var closed = false

    fun refresh() = work { repository.status() }

    fun start(text: String) {
        if (state.queue.active || state.busy || closed) return
        val urls =
            try {
                QueueInput.parse(text)
            } catch (_: Exception) {
                update(state.copy(error = true))
                return
            }
        work { repository.start(urls) }
    }

    fun stop() = work {
        repository.stop()
        repository.status()
    }

    fun close() {
        closed = true
        observer = null
        repository.close()
    }

    private fun work(action: () -> MediaQueue) {
        if (closed || state.busy) return
        update(state.copy(busy = true, error = false))
        execute {
            if (closed) return@execute
            val result =
                try {
                    State(action())
                } catch (_: Exception) {
                    state.copy(busy = false, error = true)
                }
            deliver { if (!closed) update(result) }
        }
    }

    private fun update(value: State) {
        state = value
        observer?.invoke(value)
    }
}
