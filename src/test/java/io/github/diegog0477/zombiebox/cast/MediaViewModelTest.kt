package io.github.diegog0477.zombiebox.cast

import io.github.diegog0477.zombiebox.cast.features.media.domain.model.MediaDocument
import io.github.diegog0477.zombiebox.cast.features.media.domain.repository.MediaRepository
import io.github.diegog0477.zombiebox.cast.features.media.presentation.viewmodel.MediaViewModel
import org.junit.Assert.*
import org.junit.Test

class MediaViewModelTest {
    private class Repo : MediaRepository {
        var sends = 0
        var stops = 0
        var cancels = 0
        var size = 42L
        var restored: String? = null

        override fun restore() = restored

        override fun inspect(locator: String) = MediaDocument(locator, "Song", size)

        override fun send(document: MediaDocument, progress: (Int) -> Unit) {
            sends++
            progress(50)
        }

        override fun cancel() {
            cancels++
        }

        override fun stop() {
            stops++
        }
    }

    @Test
    fun sizeIsBoundedBeforeSending() {
        val repo = Repo()
        val model = MediaViewModel(repo, { it() }, { it() })
        for (size in listOf(-1L, 0L, 268435457L)) {
            repo.size = size
            model.select("document")
            model.send()
            assertEquals("SIZE", model.state.phase)
        }
        assertEquals(0, repo.sends)
    }

    @Test
    fun acceptedMediaSurvivesScreenClosureAndCanBeRestored() {
        val repo = Repo()
        val model = MediaViewModel(repo, { it() }, { it() })
        model.select("document")
        model.send()
        assertEquals("ACCEPTED", model.state.phase)
        model.close()
        assertEquals(0, repo.stops)
        assertEquals(0, repo.cancels)
        repo.restored = "Song"
        val restored = MediaViewModel(repo, { it() }, { it() })
        restored.restore()
        assertEquals("ACCEPTED", restored.state.phase)
        restored.stop()
        assertEquals(1, repo.stops)
    }

    @Test
    fun lateUploadCompletionCannotOverrideCancellation() {
        val work = mutableListOf<() -> Unit>()
        val ui = mutableListOf<() -> Unit>()
        val repo = Repo()
        val model = MediaViewModel(repo, { work.add(it) }, { ui.add(it) })
        model.select("document")
        work.removeAt(0)()
        ui.removeAt(0)()
        model.send()
        work.removeAt(0)()
        model.stop()
        while (ui.isNotEmpty()) ui.removeAt(0)()
        assertEquals("STOPPING", model.state.phase)
        work.removeAt(0)()
        ui.removeAt(0)()
        assertEquals("READY", model.state.phase)
        assertEquals(1, repo.cancels)
    }

    @Test
    fun closingDuringRestoreDoesNotStopExistingTvPlayback() {
        val work = mutableListOf<() -> Unit>()
        val repo = Repo().apply { restored = "Song" }
        val model = MediaViewModel(repo, { work.add(it) }, { it() })
        model.restore()
        model.close()
        while (work.isNotEmpty()) work.removeAt(0)()
        assertEquals(0, repo.stops)
    }
}
