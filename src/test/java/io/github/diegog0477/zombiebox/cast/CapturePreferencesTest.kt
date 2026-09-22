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
        val selected =
            CapturePreferences(CaptureQuality.SD, true, true, CaptureOrientation.LANDSCAPE)
        model.update(selected)
        model.lock(true)
        model.update(CapturePreferences())
        assertEquals(selected, model.state)
        assertEquals(selected, CapturePreferencesViewModel(repository).state)
        model.lock(false)
        model.update(CapturePreferences())
        assertEquals(CapturePreferences(), repository.saved)
    }

    @Test
    fun fullHdIsBoundedAndRecoveryOnlyLowersBudgets() {
        val receiver = CastVideo(1920, 1080, 30, 4000000)
        val video = CapturePreferences(CaptureQuality.FULL_HD).video(receiver)
        assertEquals(1920 to 1080, video.dimensions(1920, 1080, 2, 2))
        assertEquals(1280, CapturePreferences(CaptureQuality.HD).video(receiver).maxWidth)
        assertEquals(640, CapturePreferences(CaptureQuality.FULL_HD).video(CastVideo()).maxWidth)
        val tiers = video.fallbacks()
        assertEquals(360, tiers.last().maxHeight)
        for ((higher, lower) in tiers.zipWithNext()) {
            assertTrue(lower.maxWidth <= higher.maxWidth && lower.maxHeight <= higher.maxHeight)
            assertTrue(lower.fps <= higher.fps && lower.bitrate <= higher.bitrate)
        }
    }

    @Test
    fun fixedFrameIgnoresSourceRotationButKeepsReceiverLimits() {
        val receiver = CastVideo(1920, 1080, 30, 4000000)
        for (orientation in listOf(CaptureOrientation.PORTRAIT, CaptureOrientation.LANDSCAPE)) {
            assertFalse(orientation.supported(31))
            assertEquals(CaptureOrientation.AUTO, orientation.effective(21))
            assertTrue(orientation.supported(32))
            val source = orientation.source(1080, 1920)
            assertEquals(source, orientation.source(1920, 1080))
            val (w, h) = receiver.dimensions(source.first, source.second, 2, 2)
            assertTrue(w <= receiver.maxWidth && h <= receiver.maxHeight)
            assertTrue(
                kotlin.math.abs(w.toDouble() / h - source.first.toDouble() / source.second) < 0.01
            )
        }
        assertEquals(1080 to 1920, CaptureOrientation.AUTO.source(1080, 1920))
        assertTrue(CaptureOrientation.AUTO.supported(21))
    }

    @Test
    fun oemOutputCannotSilentlyRaiseProfileOrLevel() {
        assertTrue(AvcEnvelope.accepts(byteArrayOf(0x67, 66, 0, 40), 1920, 1080))
        assertFalse(AvcEnvelope.accepts(byteArrayOf(0x67, 66, 0, 40), 1280, 720))
        assertFalse(AvcEnvelope.accepts(byteArrayOf(0x67, 100, 0, 40), 1920, 1080))
        assertFalse(AvcEnvelope.accepts(byteArrayOf(0x67, 66, 0, 51), 1920, 1080))
        assertFalse(AvcEnvelope.accepts(byteArrayOf(0x67), 1920, 1080))
    }
}
