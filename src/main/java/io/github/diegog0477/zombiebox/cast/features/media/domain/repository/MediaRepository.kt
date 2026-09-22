package io.github.diegog0477.zombiebox.cast.features.media.domain.repository

import io.github.diegog0477.zombiebox.cast.features.media.domain.model.MediaDocument

interface MediaRepository {
    fun restore(): String?

    fun inspect(locator: String): MediaDocument

    fun send(document: MediaDocument, progress: (Int) -> Unit)

    fun cancel()

    fun stop()
}
