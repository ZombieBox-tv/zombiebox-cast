package io.github.diegog0477.zombiebox.cast

import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CaptureMode
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CastGrant
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.Receiver
import io.github.diegog0477.zombiebox.cast.features.casting.domain.repository.CastRepository
import io.github.diegog0477.zombiebox.cast.features.casting.presentation.viewmodel.CastViewModel
import io.github.diegog0477.zombiebox.cast.features.casting.transport.RtpH264
import org.junit.Assert.*
import org.junit.Test

class CastTest {
    @Test
    fun audioModeIsExplicitAndNeverAcceptsAnOldScreenGrant() {
        assertFalse(CaptureMode.AUDIO.supported(28))
        assertTrue(CaptureMode.AUDIO.supported(29))
        assertTrue(CaptureMode.SCREEN.supported(21))
        assertFalse(CaptureMode.AUDIO.acceptsGrant(""))
        assertFalse(CaptureMode.AUDIO.acceptsGrant("SCREEN"))
        assertTrue(CaptureMode.SCREEN.acceptsGrant(""))
        assertTrue(CaptureMode.AUDIO.acceptsGrant("AUDIO"))
        val model = CastViewModel(Repo(), { it() }, { it() })
        model.select("tv")
        model.start(CaptureMode.AUDIO)
        assertEquals(CaptureMode.AUDIO, model.state.grant!!.mode)
    }

    @Test
    fun fragmentsReassembleWithSequenceWrapAndSingleMarker() {
        val nal = ByteArray(5000) { (it % 251).toByte() }
        nal[0] = 0x65
        val packets = RtpH264(123, 65535).packets(nal, 0x100000007L, true)
        assertEquals(5, packets.size)
        assertEquals(255, packets[0][2].toInt() and 255)
        assertEquals(0, packets[1][2].toInt())
        assertEquals(7, packets[0][7].toInt())
        assertEquals(1, packets.count { it[1].toInt() and 128 != 0 })
        assertTrue(packets.last()[1].toInt() and 128 != 0)
        val rebuilt =
            byteArrayOf(
                ((packets[0][12].toInt() and 0xe0) or (packets[0][13].toInt() and 31)).toByte()
            ) + packets.flatMap { it.drop(14) }.toByteArray()
        assertArrayEquals(nal, rebuilt)
    }

    @Test
    fun splitsAnnexBAndAvcc() {
        val expected = listOf(listOf<Byte>(0x67, 2), listOf<Byte>(0x68, 3))
        assertEquals(
            expected,
            RtpH264.split(byteArrayOf(0, 0, 0, 1, 0x67, 2, 0, 0, 1, 0x68, 3)).map { it.toList() },
        )
        assertEquals(
            expected,
            RtpH264.split(byteArrayOf(0, 0, 0, 2, 0x67, 2, 0, 0, 0, 2, 0x68, 3)).map { it.toList() },
        )
    }

    private class Repo : CastRepository {
        val stopped = mutableListOf<String>()

        override fun pair(address: String, code: String) {}

        override fun receivers() = listOf(Receiver("tv", "TV"))

        override fun create(receiver: String, mode: CaptureMode) =
            CastGrant("grant", "host", 8554, "path", "user", "token", mode = mode)

        override fun stop(id: String) {
            stopped.add(id)
        }
    }

    @Test
    fun closedViewModelRevokesGrantQueuedForDelivery() {
        val work = mutableListOf<() -> Unit>()
        val ui = mutableListOf<() -> Unit>()
        val repo = Repo()
        val model = CastViewModel(repo, { work.add(it) }, { ui.add(it) })
        model.select("tv")
        model.start()
        work.removeAt(0)()
        model.close()
        ui.removeAt(0)()
        work.removeAt(0)()
        assertEquals(listOf("grant"), repo.stopped)
    }

    @Test
    fun closedViewModelRevokesGrantCreatedAfterClose() {
        val work = mutableListOf<() -> Unit>()
        val repo = Repo()
        val model = CastViewModel(repo, { work.add(it) }, { it() })
        model.select("tv")
        model.start()
        model.close()
        work.removeAt(0)()
        assertEquals(listOf("grant"), repo.stopped)
    }

    @Test
    fun revokedReceiverCannotReturnFromQueuedGrant() {
        val work = mutableListOf<() -> Unit>()
        val ui = mutableListOf<() -> Unit>()
        val repo = Repo()
        val model = CastViewModel(repo, { work.add(it) }, { ui.add(it) })
        model.select("tv")
        model.start()
        work.removeAt(0)()
        model.clearReceiver()
        ui.removeAt(0)()
        work.removeAt(0)()
        assertEquals("", model.state.selected)
        assertNull(model.state.grant)
        assertEquals(listOf("grant"), repo.stopped)
    }

    @Test
    fun revokedReceiverDiscardsInFlightWork() {
        val work = mutableListOf<() -> Unit>()
        val repo = Repo()
        val model = CastViewModel(repo, { work.add(it) }, { it() })
        model.select("tv")
        model.start()
        model.clearReceiver()
        work.removeAt(0)()
        assertEquals("", model.state.selected)
        assertEquals(listOf("grant"), repo.stopped)
    }

    @Test
    fun negotiatedVideoBudgetBoundsBothOrientations() {
        val profile = io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CastVideo()
        for ((width, height) in listOf(Pair(1920, 1080), Pair(1080, 1920))) {
            val (w, h) = profile.dimensions(width, height)
            assertTrue(w <= 640 && h <= 360)
            assertEquals(0, w % 16)
            assertEquals(0, h % 16)
        }
        try {
            io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CastVideo(4096, 2160)
            fail("unbounded grant")
        } catch (_: IllegalArgumentException) {}
    }
}
