package io.github.diegog0477.zombiebox.cast.features.casting.platform

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.projection.MediaProjection
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import android.view.Surface
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.AvcEnvelope
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CaptureOrientation
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CastVideo
import io.github.diegog0477.zombiebox.cast.features.casting.transport.RtpH264
import io.github.diegog0477.zombiebox.cast.features.casting.transport.RtspPublisher
import java.util.concurrent.atomic.AtomicBoolean

/** One virtual display per consent. Encoder replacement never reuses a projection grant. */
class ProjectionEncoder(
    private val projection: MediaProjection,
    private val profile: CastVideo,
    private val keyFrameSeconds: Int,
    private val orientation: CaptureOrientation,
    private val encoders: SurfaceEncoders,
    private val videoState: (Int, Int, Int) -> Unit,
    private val density: Int,
    private val dimensions: () -> Pair<Int, Int>,
    private val publisherFactory: () -> RtspPublisher,
    private val requestedAudio: Boolean,
    private val alive: () -> Boolean,
    private val state: (String) -> Unit,
    private val audioState: (String) -> Unit,
) {
    @Volatile private var publisher: RtspPublisher? = null
    private var display: VirtualDisplay? = null
    private var audioDisabled = false
    @Volatile private var lastProgress = SystemClock.elapsedRealtime()

    fun checkProgress() {
        if (SystemClock.elapsedRealtime() - lastProgress > 20000) interrupt()
    }

    fun run() {
        var failures = 0
        try {
            while (alive()) {
                val started = SystemClock.elapsedRealtime()
                try {
                    stream(dimensions(), failures)
                    // A changed capture size is a normal reconfiguration, not a failure.
                } catch (_: Exception) {
                    if (!alive()) break
                    if (SystemClock.elapsedRealtime() - started > 60000) failures = 0
                    failures++
                    if (failures > 3) throw IllegalStateException("Capture recovery exhausted")
                    state("RECOVERING")
                    val deadline = SystemClock.elapsedRealtime() + failures * 500L
                    while (alive() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(
                        50
                    )
                }
            }
        } finally {
            try {
                display?.release()
            } catch (_: Exception) {}
            display = null
            interrupt()
        }
    }

    fun interrupt() {
        publisher?.close()
    }

    private fun stream(sourceSize: Pair<Int, Int>, recovery: Int) {
        var codec: MediaCodec? = null
        var surface: Surface? = null
        var audio: PlaybackAudio? = null
        val audioFailed = AtomicBoolean(false)
        state("BUFFERING")
        lastProgress = SystemClock.elapsedRealtime()
        val connection = publisherFactory()
        publisher = connection
        try {
            audio =
                PlaybackAudioFactory.create(Build.VERSION.SDK_INT, requestedAudio && !audioDisabled)
            audioState(
                if (!requestedAudio) "DISABLED" else if (audio == null) "UNAVAILABLE" else "WAITING"
            )
            try {
                audio?.prepare(projection)
            } catch (_: Exception) {
                audio?.close()
                audio = null
                audioState("UNAVAILABLE")
            }
            val startedEncoder =
                encoders.open(
                    profile,
                    orientation.source(sourceSize.first, sourceSize.second),
                    recovery,
                    keyFrameSeconds,
                )
            val encoder = startedEncoder.codec
            val width = startedEncoder.width
            val height = startedEncoder.height
            codec = encoder
            surface = startedEncoder.surface
            videoState(width, height, startedEncoder.fps)
            if (display == null) {
                display =
                    projection.createVirtualDisplay(
                        "Zombiebox Cast",
                        width,
                        height,
                        density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        surface,
                        null,
                        null,
                    )
            } else {
                display!!.resize(width, height, density)
                display!!.surface = surface
            }
            var connected = false
            val started = SystemClock.elapsedRealtime()
            var lastFrame = started
            val info = MediaCodec.BufferInfo()
            while (alive() && dimensions() == sourceSize) {
                if (audioFailed.getAndSet(false)) {
                    audioDisabled = true
                    audioState("UNAVAILABLE")
                    // Republish video-only SDP so the relay cannot wait for a dead audio track.
                    return
                }
                val index = encoder.dequeueOutputBuffer(info, 10000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !connected) {
                    val output = encoder.outputFormat
                    fun data(key: String): ByteArray {
                        val bytes = output.getByteBuffer(key)!!.duplicate()
                        return ByteArray(bytes.remaining()).also { bytes.get(it) }
                    }
                    val parameters =
                        RtpH264.split(data("csd-0")) +
                            if (output.containsKey("csd-1")) RtpH264.split(data("csd-1"))
                            else emptyList()
                    val sps = parameters.first { it.isNotEmpty() && it[0].toInt() and 31 == 7 }
                    val pps = parameters.first { it.isNotEmpty() && it[0].toInt() and 31 == 8 }
                    check(AvcEnvelope.accepts(sps, width, height)) {
                        "Encoder exceeded the negotiated AVC profile/level"
                    }
                    connection.connect(sps, pps, audio != null) {
                        Base64.encodeToString(it, Base64.NO_WRAP)
                    }
                    connected = true
                    audio?.start(connection, { audioFailed.set(true) }, audioState)
                } else if (index >= 0) {
                    try {
                        if (
                            connected &&
                                info.size > 0 &&
                                info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            require(info.size <= 4 * 1024 * 1024)
                            val buffer = encoder.getOutputBuffer(index)!!
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            buffer.get(bytes)
                            connection.frame(RtpH264.split(bytes), info.presentationTimeUs)
                            lastFrame = SystemClock.elapsedRealtime()
                            lastProgress = lastFrame
                        }
                    } finally {
                        encoder.releaseOutputBuffer(index, false)
                    }
                }
                check(SystemClock.elapsedRealtime() - lastFrame < 20000) {
                    "Encoder stopped producing frames"
                }
            }
        } finally {
            // Closing the transport first unblocks an audio writer before joining it.
            connection.close()
            audio?.close()
            try {
                display?.surface = null
            } catch (_: Exception) {}
            try {
                codec?.stop()
            } catch (_: Exception) {}
            try {
                codec?.release()
            } catch (_: Exception) {}
            try {
                surface?.release()
            } catch (_: Exception) {}
        }
    }
}
