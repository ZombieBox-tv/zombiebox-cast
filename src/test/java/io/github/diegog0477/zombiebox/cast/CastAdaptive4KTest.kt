package io.github.diegog0477.zombiebox.cast

import android.content.SharedPreferences
import io.github.diegog0477.zombiebox.cast.features.casting.data.GatewayCastRepository
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.*
import io.github.diegog0477.zombiebox.cast.features.casting.platform.SurfaceEncoderFactory
import org.junit.Assert.*
import org.junit.Test

class CastAdaptive4KTest {

    private class FakeSharedPreferences(private val map: Map<String, Any?> = emptyMap()) :
        SharedPreferences {
        override fun getAll(): MutableMap<String, *> = map.toMutableMap()

        override fun getString(key: String?, defValue: String?): String? =
            (map[key] as? String) ?: defValue

        override fun getStringSet(
            key: String?,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? = defValues

        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue

        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            (map[key] as? Float) ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            (map[key] as? Boolean) ?: defValue

        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {}

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {}
    }

    private fun testRepo(
        canEncode4K: () -> Boolean = { false },
        stopped: MutableList<String> = mutableListOf(),
    ): GatewayCastRepository {
        val prefs =
            FakeSharedPreferences(
                mapOf(
                    "gateway" to "http://127.0.0.1:8080",
                    "device" to "test-device",
                    "token" to "test-token",
                )
            )
        return object : GatewayCastRepository(prefs, canEncode4K) {
            override fun stop(id: String) {
                stopped.add(id)
            }
        }
    }

    @Test
    fun verified4KGrantNegotiationAndAvcEnvelope() {
        val video4K = CastVideo(3840, 2160, 30, 12000000)
        assertEquals(3840, video4K.maxWidth)
        assertEquals(2160, video4K.maxHeight)
        assertEquals(30, video4K.fps)
        assertEquals(12000000, video4K.bitrate)

        val (w, h) = video4K.dimensions(3840, 2160)
        assertEquals(3840, w)
        assertEquals(2160, h)

        // AVC Baseline Level 5.1 SPS accepted for 4K
        assertTrue(AvcEnvelope.accepts(byteArrayOf(0x67, 66, 0, 51), 3840, 2160))

        // When encoder supports 4K, repository negotiates 2160 for screen
        val repo = testRepo(canEncode4K = { true })
        assertEquals(2160, repo.targetMaxVideoHeight(CaptureMode.SCREEN))

        // Successfully parses 4K grant
        val grant =
            repo.parseGrantData(
                id = "cast-4k-1",
                host = "127.0.0.1",
                port = 8554,
                path = "zombie/cast-4k-1",
                user = "zombie",
                token = "token123",
                mode = CaptureMode.SCREEN,
                grantMode = "SCREEN",
                videoWidth = 3840,
                videoHeight = 2160,
                videoFps = 30,
                videoBitrate = 12000000,
            )
        assertEquals("cast-4k-1", grant.id)
        assertEquals(3840, grant.video.maxWidth)
        assertEquals(2160, grant.video.maxHeight)
        assertEquals(30, grant.video.fps)
        assertEquals(12000000, grant.video.bitrate)

        // CapturePreferences AUTO preserves receiver 4K budget
        val autoPref = CapturePreferences(CaptureQuality.AUTO).video(grant.video)
        assertEquals(3840, autoPref.maxWidth)
        assertEquals(2160, autoPref.maxHeight)
        assertEquals(12000000, autoPref.bitrate)
    }

    @Test
    fun missingOrFailedProbeKeepsSafe1080Ceiling() {
        // When encoder cannot do 4K, requests 1080p safe default
        val repo = testRepo(canEncode4K = { false })
        assertEquals(1080, repo.targetMaxVideoHeight(CaptureMode.SCREEN))

        // Audio mode avoids changing audio-only capture even if 4K encoder is available
        val audioRepo = testRepo(canEncode4K = { true })
        assertEquals(1080, audioRepo.targetMaxVideoHeight(CaptureMode.AUDIO))

        // SurfaceEncoderFactory probe4K safely handles unmocked environments
        SurfaceEncoderFactory.setProbeOverride(null)
        assertFalse(SurfaceEncoderFactory.probe4K())

        // Test probe override works as expected
        SurfaceEncoderFactory.setProbeOverride(true)
        assertTrue(SurfaceEncoderFactory.probe4K())
        SurfaceEncoderFactory.setProbeOverride(null)
    }

    @Test
    fun vizio1080PathSafelyParsedAndBounded() {
        val repo = testRepo(canEncode4K = { true })
        // Gateway negotiated down to 1080p for 1080p Vizio TV
        val grant =
            repo.parseGrantData(
                id = "cast-vizio-1080",
                host = "127.0.0.1",
                port = 8554,
                path = "zombie/cast-vizio-1080",
                user = "zombie",
                token = "token1080",
                mode = CaptureMode.SCREEN,
                grantMode = "SCREEN",
                videoWidth = 1920,
                videoHeight = 1080,
                videoFps = 30,
                videoBitrate = 4000000,
            )
        assertEquals(1920, grant.video.maxWidth)
        assertEquals(1080, grant.video.maxHeight)
        assertEquals(4000000, grant.video.bitrate)

        // AVC Baseline level 40 accepted for 1080p, level 51 rejected
        assertTrue(AvcEnvelope.accepts(byteArrayOf(0x67, 66, 0, 40), 1920, 1080))
        assertFalse(AvcEnvelope.accepts(byteArrayOf(0x67, 66, 0, 51), 1920, 1080))

        // FULL_HD user preference bounds 4K receiver to 1080p
        val pref =
            CapturePreferences(CaptureQuality.FULL_HD).video(CastVideo(3840, 2160, 30, 12000000))
        assertEquals(1920, pref.maxWidth)
        assertEquals(1080, pref.maxHeight)
        assertEquals(4000000, pref.bitrate)
    }

    @Test
    fun senderEncoderFailureDowngrading() {
        val budget4K = CastVideo(3840, 2160, 30, 12000000)
        val fallbacks = budget4K.fallbacks()
        assertEquals(5, fallbacks.size)

        // Verify tier hierarchy
        assertEquals(3840 to 2160, fallbacks[0].maxWidth to fallbacks[0].maxHeight)
        assertEquals(1920 to 1080, fallbacks[1].maxWidth to fallbacks[1].maxHeight)
        assertEquals(1280 to 720, fallbacks[2].maxWidth to fallbacks[2].maxHeight)
        assertEquals(854 to 480, fallbacks[3].maxWidth to fallbacks[3].maxHeight)
        assertEquals(640 to 360, fallbacks[4].maxWidth to fallbacks[4].maxHeight)

        // Verify recovery index drops previous failed tiers
        val recovery0 = fallbacks.drop(0.coerceIn(0, fallbacks.lastIndex))
        assertEquals(2160, recovery0.first().maxHeight)

        // After 4K encoder failure (recovery = 1), next candidate begins at 1080p
        val recovery1 = fallbacks.drop(1.coerceIn(0, fallbacks.lastIndex))
        assertEquals(1080, recovery1.first().maxHeight)
        assertEquals(4000000, recovery1.first().bitrate)

        // After 1080p encoder failure (recovery = 2), next candidate begins at 720p
        val recovery2 = fallbacks.drop(2.coerceIn(0, fallbacks.lastIndex))
        assertEquals(720, recovery2.first().maxHeight)
        assertEquals(2000000, recovery2.first().bitrate)

        // After 720p failure (recovery = 3), next candidate is 480p
        val recovery3 = fallbacks.drop(3.coerceIn(0, fallbacks.lastIndex))
        assertEquals(480, recovery3.first().maxHeight)
        assertEquals(1200000, recovery3.first().bitrate)

        // After 480p failure (recovery = 4), final candidate is 360p
        val recovery4 = fallbacks.drop(4.coerceIn(0, fallbacks.lastIndex))
        assertEquals(360, recovery4.first().maxHeight)
        assertEquals(800000, recovery4.first().bitrate)

        for ((higher, lower) in fallbacks.zipWithNext()) {
            assertTrue(lower.maxWidth <= higher.maxWidth)
            assertTrue(lower.maxHeight <= higher.maxHeight)
            assertTrue(lower.fps <= higher.fps)
            assertTrue(lower.bitrate <= higher.bitrate)
        }
    }

    @Test
    fun malformedGrantsFailClosedAndCleanUpLease() {
        // Unbounded width
        try {
            CastVideo(4096, 2160)
            fail("unbounded width accepted")
        } catch (_: IllegalArgumentException) {}

        // Unbounded height
        try {
            CastVideo(3840, 2161)
            fail("unbounded height accepted")
        } catch (_: IllegalArgumentException) {}

        // Unbounded fps
        try {
            CastVideo(3840, 2160, fps = 60)
            fail("unbounded fps accepted")
        } catch (_: IllegalArgumentException) {}

        // Unbounded bitrate
        try {
            CastVideo(3840, 2160, bitrate = 16000000)
            fail("unbounded bitrate accepted")
        } catch (_: IllegalArgumentException) {}

        // Too small dimensions
        try {
            CastVideo(16, 2160)
            fail("dimension below minimum accepted")
        } catch (_: IllegalArgumentException) {}

        // AvcEnvelope rejects invalid levels/profiles
        assertFalse(
            "level 52 rejected for 4K",
            AvcEnvelope.accepts(byteArrayOf(0x67, 66, 0, 52), 3840, 2160),
        )
        assertFalse(
            "level 51 rejected for 1080p",
            AvcEnvelope.accepts(byteArrayOf(0x67, 66, 0, 51), 1920, 1080),
        )
        assertFalse(
            "profile 100 rejected",
            AvcEnvelope.accepts(byteArrayOf(0x67, 100, 0, 51), 3840, 2160),
        )
        assertFalse(
            "truncated SPS rejected",
            AvcEnvelope.accepts(byteArrayOf(0x67, 66, 0), 3840, 2160),
        )
        assertFalse(
            "PPS rejected as SPS",
            AvcEnvelope.accepts(byteArrayOf(0x68, 66, 0, 51), 3840, 2160),
        )

        // Malformed grant triggers stop(castId) on repository
        val stopped = mutableListOf<String>()
        val repo = testRepo(canEncode4K = { true }, stopped = stopped)
        try {
            repo.parseGrantData(
                id = "bad-grant-1",
                host = "127.0.0.1",
                port = 8554,
                path = "zombie/bad-grant-1",
                user = "zombie",
                token = "tok",
                mode = CaptureMode.SCREEN,
                grantMode = "SCREEN",
                videoWidth = 5000,
                videoHeight = 3000,
                videoFps = 60,
                videoBitrate = 50000000,
            )
            fail("malformed grant parsed")
        } catch (_: IllegalArgumentException) {
            assertEquals(listOf("bad-grant-1"), stopped)
        }
    }
}
