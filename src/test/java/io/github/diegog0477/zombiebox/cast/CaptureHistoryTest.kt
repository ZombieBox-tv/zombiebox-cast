package io.github.diegog0477.zombiebox.cast

import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CaptureMode
import io.github.diegog0477.zombiebox.cast.features.history.data.HistoryCodec
import io.github.diegog0477.zombiebox.cast.features.history.domain.CaptureHistory
import io.github.diegog0477.zombiebox.cast.features.history.domain.model.*
import io.github.diegog0477.zombiebox.cast.features.history.domain.repository.HistoryStore
import io.github.diegog0477.zombiebox.cast.features.history.presentation.viewmodel.HistoryViewModel
import org.junit.Assert.*
import org.junit.Test

class CaptureHistoryTest {
    private class Store : HistoryStore {
        var bytes = HistoryCodec.encode(emptyList())

        @Synchronized override fun read() = HistoryCodec.decode(bytes)

        @Synchronized
        override fun update(change: (List<CaptureSession>) -> List<CaptureSession>) {
            bytes = HistoryCodec.encode(change(read()))
        }
    }

    private val store = Store()
    private var time = 1000L
    private var sequence = 0

    private fun history(process: String = "process-one") =
        CaptureHistory(store, process, { time }, { "local-${++sequence}" })

    @Test
    fun modeRoundTripsAndVersionOneRecordsKeepTheirScreenMeaning() {
        val history = history()
        history.begin("TV", true, CaptureMode.AUDIO)
        assertEquals(CaptureMode.AUDIO, history.sessions().single().mode)
        history.begin("TV", false)
        val current = HistoryCodec.encode(listOf(history.sessions().first()))
        // Version 1 had the same record prefix, without the trailing mode string.
        val old = current.copyOf(current.size - 8)
        old[3] = 1
        assertEquals(CaptureMode.SCREEN, HistoryCodec.decode(old).single().mode)
    }

    @Test
    fun recoveryAndFailurePreserveObservedFormatAndCannotBeResurrected() {
        val history = history()
        val id = history.begin("Living room", true)
        assertEquals(SessionAudio.WAITING, history.sessions().single().audio)
        history.video(id, 1280, 720, 30)
        history.phase(id, SessionPhase.SHARING)
        history.audio(id, SessionAudio.CAPTURING)
        history.phase(id, SessionPhase.RECOVERING)
        history.video(id, 640, 360, 24)
        history.audio(id, SessionAudio.UNAVAILABLE)
        time = 2000
        history.phase(id, SessionPhase.FAILED)
        val failed = history.sessions().single()
        history.phase(id, SessionPhase.STOPPED)
        history.phase(id, SessionPhase.SHARING)
        history.video(id, 1920, 1080, 30)
        history.audio(id, SessionAudio.CAPTURING)
        assertEquals(failed, history.sessions().single())
        assertEquals(2000L, failed.endedAt)
        assertEquals(640, failed.width)
        assertEquals(SessionAudio.UNAVAILABLE, failed.audio)
        assertEquals(failed, history().sessions().single())
    }

    @Test
    fun processDeathDoesNotInventAnEndTimeOrResumeCapture() {
        val original = history()
        val id = original.begin("TV", false)
        original.phase(id, SessionPhase.SHARING)
        val reopened = history("process-two")
        val interrupted = reopened.sessions().single()
        assertEquals(SessionPhase.INTERRUPTED, interrupted.phase)
        assertNull(interrupted.endedAt)
        reopened.phase(id, SessionPhase.SHARING)
        assertEquals(interrupted, reopened.sessions().single())
        reopened.begin("Other TV", false)
        assertEquals(interrupted, reopened.sessions().last())
    }

    @Test
    fun clearingFinishedKeepsLiveSessionAndLateCallbacksCannotRestoreDeletedRecords() {
        val history = history()
        val finished = history.begin("TV", false)
        history.phase(finished, SessionPhase.STOPPED)
        val live = history.begin("TV", false)
        val ui = HistoryViewModel(history())
        var observations = 0
        ui.observer = { observations++ }
        ui.refresh()
        ui.refresh()
        assertEquals(1, observations)
        ui.clearFinished()
        history.phase(finished, SessionPhase.SHARING)
        assertEquals(listOf(live), ui.state.map { it.id })
        assertEquals(ui.state, history.sessions())
        assertEquals(2, observations)
    }

    @Test
    fun boundedRetentionUsesInsertionOrderEvenIfClockMovesBackwards() {
        val history = history()
        repeat(40) {
            time = 1000L - it
            history.phase(history.begin("TV", false), SessionPhase.STOPPED)
        }
        assertEquals(30, history.sessions().size)
        assertEquals("local-40", history.sessions().first().id)
        assertEquals("local-11", history.sessions().last().id)
        val live = history.begin("TV", false)
        time = 1L
        history.phase(live, SessionPhase.STOPPED)
        val ended = history.sessions().first()
        assertEquals(ended.startedAt, ended.endedAt)
    }

    @Test
    fun invalidFormatIsIgnoredAndUtfNamesSurviveBoundedStorage() {
        val history = history()
        val name = "电视 🍀".repeat(50)
        val id = history.begin(name, false)
        history.video(id, 4000, 2000, 60)
        val value = history.sessions().single()
        assertEquals(0, value.width)
        assertEquals(name.take(120), value.receiver)
        assertEquals(SessionAudio.DISABLED, value.audio)
        assertTrue(store.bytes.size < HistoryCodec.MAX_BYTES)
    }

    @Test
    fun corruptedUnknownAndOversizedStorageDoesNotBreakStartup() {
        val history = history()
        history.begin("TV", false)
        val valid = store.bytes
        for (bytes in
            listOf(
                valid.copyOf(valid.size - 1),
                valid + byteArrayOf(0),
                ByteArray(40000),
                byteArrayOf(0, 0, 0, 2),
            )) {
            assertTrue(HistoryCodec.decode(bytes).isEmpty())
        }
        store.bytes = byteArrayOf(42)
        assertTrue(history.sessions().isEmpty())
        history.begin("New TV", false)
        assertEquals("New TV", history.sessions().single().receiver)
    }
}
