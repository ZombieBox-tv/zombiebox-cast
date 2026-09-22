package io.github.diegog0477.zombiebox.cast.features.casting.platform

import android.media.projection.MediaProjection
import android.os.SystemClock
import io.github.diegog0477.zombiebox.cast.features.casting.transport.RtspPublisher
import java.util.concurrent.atomic.AtomicBoolean

/** Audio-only capture never creates a Surface, virtual display or video encoder. */
class AudioProjectionEncoder(
    private val projection: MediaProjection,
    private val audioFactory: () -> PlaybackAudio?,
    private val publisherFactory: () -> RtspPublisher,
    private val alive: () -> Boolean,
    private val state: (String) -> Unit,
    private val audioState: (String) -> Unit,
) : CaptureEncoder {
    @Volatile private var publisher: RtspPublisher? = null

    override fun checkProgress() {
        publisher?.let { if (System.nanoTime() - it.lastMediaNanos > 20000000000L) it.close() }
    }

    override fun interrupt() {
        publisher?.close()
    }

    override fun run() {
        var failures = 0
        while (alive()) {
            val started = SystemClock.elapsedRealtime()
            try {
                stream()
            } catch (error: Exception) {
                if (!alive()) break
                if (SystemClock.elapsedRealtime() - started > 60000) failures = 0
                failures++
                if (failures > 3) {
                    audioState("UNAVAILABLE")
                    throw IllegalStateException("Audio capture recovery exhausted", error)
                }
                state("RECOVERING")
                val deadline = SystemClock.elapsedRealtime() + failures * 500L
                while (alive() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            }
        }
    }

    private fun stream() {
        state("BUFFERING")
        audioState("WAITING")
        val connection = publisherFactory()
        publisher = connection
        var audio: PlaybackAudio? = null
        val failed = AtomicBoolean(false)
        try {
            audio = audioFactory() ?: error("Playback capture unavailable")
            audio.prepare(projection)
            if (!alive()) return
            connection.connectAudio()
            audio.start(connection, { failed.set(true) }, audioState)
            while (alive()) {
                check(!failed.get()) { "Audio encoder or transport failed" }
                check(System.nanoTime() - connection.lastMediaNanos < 20000000000L) {
                    "Audio stopped producing packets"
                }
                SystemClock.sleep(50)
            }
        } finally {
            connection.close()
            audio?.close()
            publisher = null
        }
    }
}
