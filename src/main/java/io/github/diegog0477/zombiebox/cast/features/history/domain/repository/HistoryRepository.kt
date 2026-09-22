package io.github.diegog0477.zombiebox.cast.features.history.domain.repository

import io.github.diegog0477.zombiebox.cast.features.history.domain.model.CaptureSession

interface HistoryRepository {
    fun sessions(): List<CaptureSession>

    fun clearFinished()
}

/** The adapter must serialize each read/modify/write across repository instances. */
interface HistoryStore {
    fun read(): List<CaptureSession>

    fun update(change: (List<CaptureSession>) -> List<CaptureSession>)
}
