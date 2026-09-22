package io.github.diegog0477.zombiebox.cast

import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.*
import io.github.diegog0477.zombiebox.cast.features.casting.domain.repository.CapturePreferencesRepository
import io.github.diegog0477.zombiebox.cast.features.casting.presentation.viewmodel.CapturePreferencesViewModel
import org.junit.Assert.*
import org.junit.Test

class CapturePreferencesTest {
    @Test
    fun userQualityNeverExceedsReceiverEvidence() {
        for (receiver in listOf(CastVideo(), CastVideo(1280, 720, 30, 2000000))) {
            for (quality in CaptureQuality.values()) {
                val profile = CapturePreferences(quality, true).video(receiver)
                assertTrue(profile.maxWidth <= receiver.maxWidth)
                assertTrue(profile.maxHeight <= receiver.maxHeight)
                assertTrue(profile.bitrate <= receiver.bitrate)
                assertEquals(receiver.fps, profile.fps)
                for (source in listOf(1920 to 1080, 1080 to 1920)) {
                    val size = profile.dimensions(source.first, source.second)
                    assertTrue(size.first <= profile.maxWidth && size.second <= profile.maxHeight)
                    assertEquals(0, size.first % 16)
                    assertEquals(0, size.second % 16)
                }
            }
        }
    }

    @Test
    fun sdAndLatencyReduceRealEncoderBudget() {
        val receiver = CastVideo(1280, 720, 30, 2000000)
        val normal = CapturePreferences().video(receiver)
        val low = CapturePreferences(CaptureQuality.SD, true)
        val budget = low.video(receiver)
        assertTrue(budget.maxWidth * budget.maxHeight < normal.maxWidth * normal.maxHeight)
        assertEquals(1000000, budget.bitrate)
        assertTrue(low.keyFrameSeconds < CapturePreferences().keyFrameSeconds)
        assertEquals(800000, low.video(CastVideo()).bitrate)
    }

    @Test
    fun settingsSurviveRecreationAndStayFrozenDuringConsentAndCapture() {
        val repository =
            object : CapturePreferencesRepository {
                var saved = CapturePreferences()

                override fun load() = saved

                override fun save(value: CapturePreferences) {
                    saved = value
                }
            }
        val model = CapturePreferencesViewModel(repository)
        val selected = CapturePreferences(CaptureQuality.SD, true, true)
        model.update(selected)
        model.lock(true)
        model.update(CapturePreferences())
        assertEquals(selected, model.state)
        assertEquals(selected, CapturePreferencesViewModel(repository).state)
        model.lock(false)
        model.update(CapturePreferences())
        assertEquals(CapturePreferences(), repository.saved)
    }
}
