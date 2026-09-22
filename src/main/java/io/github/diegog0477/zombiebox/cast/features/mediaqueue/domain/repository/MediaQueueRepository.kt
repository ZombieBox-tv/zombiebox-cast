package io.github.diegog0477.zombiebox.cast.features.mediaqueue.domain.repository

import io.github.diegog0477.zombiebox.cast.features.mediaqueue.domain.model.MediaQueue

interface MediaQueueRepository {
    fun status(): MediaQueue

    fun start(urls: List<String>): MediaQueue

    fun stop()

    fun close()
}
