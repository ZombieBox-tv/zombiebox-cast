package io.github.diegog0477.zombiebox.cast

import io.github.diegog0477.zombiebox.cast.features.mediaqueue.domain.model.*
import io.github.diegog0477.zombiebox.cast.features.mediaqueue.domain.repository.MediaQueueRepository
import io.github.diegog0477.zombiebox.cast.features.mediaqueue.presentation.viewmodel.MediaQueueViewModel
import org.junit.Assert.*
import org.junit.Test

class MediaQueueViewModelTest {
    @Test
    fun invalidLinksNeverReachGatewayAndActiveQueueCannotBeReplaced() {
        val r = Repository()
        val vm = MediaQueueViewModel(r, { it() }, { it() })
        vm.start("file:///secret")
        assertTrue(vm.state.error)
        assertEquals(0, r.starts)
        vm.start("https://example.com/a.mp4\nhttps://example.com/b.mp4")
        assertEquals(1, r.starts)
        vm.start("https://example.com/c.mp4")
        assertEquals(1, r.starts)
        vm.close()
        assertFalse(r.stopped)
    }

    @Test
    fun closeFencesQueuedSubmissionAndPollingRestoresGatewayOwnership() {
        val tasks = ArrayList<() -> Unit>()
        val r = Repository()
        val vm = MediaQueueViewModel(r, { tasks.add(it) }, { it() })
        vm.start("https://example.com/a.mp4")
        vm.close()
        tasks.removeAt(0)()
        assertEquals(0, r.starts)
        val restored = MediaQueueViewModel(r, { it() }, { it() })
        restored.refresh()
        assertTrue(restored.state.queue.active)
        restored.stop()
        assertTrue(r.stopped)
    }

    private class Repository : MediaQueueRepository {
        var starts = 0
        var stopped = false

        override fun status() = MediaQueue(if (stopped) "STOPPED" else "PLAYING", 0, 2)

        override fun start(urls: List<String>): MediaQueue {
            starts++
            return MediaQueue("PREPARING", 0, urls.size)
        }

        override fun stop() {
            stopped = true
        }

        override fun close() {}
    }
}
