package io.github.diegog0477.zombiebox.cast.features.media.presentation.viewmodel

import io.github.diegog0477.zombiebox.cast.features.media.domain.model.MediaDocument
import io.github.diegog0477.zombiebox.cast.features.media.domain.repository.MediaRepository

/** UI-thread state; generation fencing prevents cancelled work from restarting playback. */
class MediaViewModel(
    private val repository: MediaRepository,
    private val execute: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
) {
    data class State(
        val document: MediaDocument? = null,
        val phase: String = "EMPTY",
        val percent: Int = 0,
    )

    var state = State()
        private set

    var observer: ((State) -> Unit)? = null
    private var generation = 0
    private var closed = false
    private var ownsTransfer = false
    val busy
        get() = state.phase in listOf("READING", "SENDING", "STOPPING")

    fun restore() {
        if (closed || busy) return
        val request = ++generation
        update(State(phase = "READING"))
        execute {
            val result =
                try {
                    val title = repository.restore()
                    if (title == null) State() else State(MediaDocument("", title, 0), "ACCEPTED")
                } catch (_: Exception) {
                    State(phase = "FAILED")
                }
            deliver { if (!closed && request == generation) update(result) }
        }
    }

    fun select(locator: String) {
        if (closed || busy || state.phase == "ACCEPTED") return
        val request = ++generation
        update(State(phase = "READING"))
        execute {
            val result =
                try {
                    val document = repository.inspect(locator)
                    State(document, if (document.supportedSize) "READY" else "SIZE")
                } catch (_: Exception) {
                    State(phase = "FAILED")
                }
            deliver { if (!closed && request == generation) update(result) }
        }
    }

    fun send() {
        val document = state.document ?: return
        if (closed || busy || !document.supportedSize || state.phase == "ACCEPTED") return
        val request = ++generation
        ownsTransfer = true
        update(state.copy(phase = "SENDING", percent = 0))
        execute {
            val phase =
                try {
                    repository.send(document) { percent ->
                        deliver {
                            if (!closed && request == generation)
                                update(state.copy(percent = percent))
                        }
                    }
                    "ACCEPTED"
                } catch (_: Exception) {
                    "FAILED"
                }
            deliver { if (!closed && request == generation) update(state.copy(phase = phase)) }
        }
    }

    fun stop() {
        if (closed || state.phase == "STOPPING") return
        ++generation
        repository.cancel()
        update(state.copy(phase = "STOPPING"))
        execute {
            val phase =
                try {
                    repository.stop()
                    "READY"
                } catch (_: Exception) {
                    "STOP_FAILED"
                }
            deliver { if (!closed) update(state.copy(phase = phase, percent = 0)) }
        }
    }

    fun close() {
        closed = true
        ++generation
        observer = null
        // Once accepted the TV owns playback. Rotation/exit cancels an unfinished
        // transfer, never an already handed-off file.
        if (ownsTransfer && state.phase != "ACCEPTED") {
            repository.cancel()
            execute {
                try {
                    repository.stop()
                } catch (_: Exception) {}
            }
        }
    }

    private fun update(value: State) {
        state = value
        observer?.invoke(value)
    }
}
