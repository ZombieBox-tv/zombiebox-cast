package io.github.diegog0477.zombiebox.cast.features.history.domain

import io.github.diegog0477.zombiebox.cast.features.history.domain.model.*
import io.github.diegog0477.zombiebox.cast.features.history.domain.repository.*

class CaptureHistory(
    private val store: HistoryStore,
    private val processId: String,
    private val now: () -> Long,
    private val newId: () -> String,
) : HistoryRepository {
    companion object {
        const val LIMIT = 30
    }

    private fun normalize(values: List<CaptureSession>) =
        values.map {
            if (!it.phase.terminal && it.processId != processId)
                it.copy(phase = SessionPhase.INTERRUPTED, endedAt = null)
            else it
        }

    override fun sessions() = normalize(store.read()).take(LIMIT)

    fun begin(receiver: String, audioRequested: Boolean): String {
        val id = newId()
        val session =
            CaptureSession(
                id,
                processId,
                receiver.take(120),
                now(),
                audio = if (audioRequested) SessionAudio.WAITING else SessionAudio.DISABLED,
            )
        store.update { (listOf(session) + normalize(it)).take(LIMIT) }
        return id
    }

    fun phase(id: String, phase: SessionPhase) {
        if (phase == SessionPhase.INTERRUPTED) return
        change(id) {
            it.copy(
                phase = phase,
                endedAt = if (phase.terminal) now().coerceAtLeast(it.startedAt) else null,
            )
        }
    }

    fun video(id: String, width: Int, height: Int, fps: Int) {
        if (width !in 32..1920 || height !in 32..1920 || fps !in 1..30) return
        change(id) { it.copy(width = width, height = height, fps = fps) }
    }

    fun audio(id: String, audio: SessionAudio) = change(id) { it.copy(audio = audio) }

    private fun change(id: String, transform: (CaptureSession) -> CaptureSession) {
        store.update { values ->
            normalize(values)
                .map {
                    if (it.id == id && it.processId == processId && !it.phase.terminal)
                        transform(it)
                    else it
                }
                .take(LIMIT)
        }
    }

    override fun clearFinished() {
        store.update { normalize(it).filterNot { session -> session.phase.terminal } }
    }
}
