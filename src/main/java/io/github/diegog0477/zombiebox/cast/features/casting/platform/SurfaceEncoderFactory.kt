package io.github.diegog0477.zombiebox.cast.features.casting.platform

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CastVideo

class StartedEncoder(
    val codec: MediaCodec,
    val surface: Surface,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int,
)

interface SurfaceEncoders {
    fun open(
        budget: CastVideo,
        source: Pair<Int, Int>,
        recovery: Int,
        keyFrameSeconds: Int,
    ): StartedEncoder
}

/** Declared support is a candidate; configured, advancing output establishes health. */
class SurfaceEncoderFactory : SurfaceEncoders {
    internal data class Candidate(
        val name: String,
        val video: CastVideo,
        val width: Int,
        val height: Int,
        val profile: Int,
        val level: Int,
    )

    companion object {
        @Volatile private var cachedProbe: Boolean? = null

        fun probe4K(): Boolean {
            cachedProbe?.let {
                return it
            }
            val result =
                try {
                    tryProbe4K()
                } catch (_: Throwable) {
                    false
                }
            cachedProbe = result
            return result
        }

        internal fun setProbeOverride(value: Boolean?) {
            cachedProbe = value
        }

        private fun tryProbe4K(): Boolean {
            val encoders =
                MediaCodecList(MediaCodecList.REGULAR_CODECS)
                    .codecInfos
                    .filter {
                        it.isEncoder &&
                            it.supportedTypes.any { type -> type.equals("video/avc", true) }
                    }
                    .take(16)
            if (encoders.isEmpty()) return false

            val budget = CastVideo(3840, 2160, 30, 12000000)
            val source = Pair(3840, 2160)
            val factory = SurfaceEncoderFactory()
            for (info in encoders) {
                val candidate = factory.candidate(info, budget, source) ?: continue
                var started: StartedEncoder? = null
                try {
                    started = factory.start(candidate, 2)
                    return true
                } catch (_: Throwable) {
                    // Encoder rejected or failed to configure/start 4K
                } finally {
                    try {
                        started?.codec?.stop()
                    } catch (_: Throwable) {}
                    try {
                        started?.codec?.release()
                    } catch (_: Throwable) {}
                    try {
                        started?.surface?.release()
                    } catch (_: Throwable) {}
                }
            }
            return false
        }
    }

    override fun open(
        budget: CastVideo,
        source: Pair<Int, Int>,
        recovery: Int,
        keyFrameSeconds: Int,
    ): StartedEncoder {
        val encoders =
            MediaCodecList(MediaCodecList.REGULAR_CODECS)
                .codecInfos
                .filter {
                    it.isEncoder && it.supportedTypes.any { type -> type.equals("video/avc", true) }
                }
                .take(16)
        val profiles = budget.fallbacks()
        var attempts = 0
        for (profile in profiles.drop(recovery.coerceIn(0, profiles.lastIndex))) {
            for (info in encoders) {
                if (attempts >= 12) break
                try {
                    val candidate = candidate(info, profile, source) ?: continue
                    attempts++
                    return start(candidate, keyFrameSeconds)
                } catch (_: Exception) {
                    // A rejected candidate is not evidence against every encoder on the phone.
                }
            }
        }
        throw IllegalStateException("No bounded surface encoder accepted this capture")
    }

    internal fun candidate(
        info: MediaCodecInfo,
        budget: CastVideo,
        source: Pair<Int, Int>,
    ): Candidate? {
        val caps = info.getCapabilitiesForType("video/avc")
        if (!caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface))
            return null
        val video = caps.videoCapabilities ?: return null
        val (width, height) =
            budget.dimensions(
                source.first,
                source.second,
                maxOf(2, video.widthAlignment),
                maxOf(2, video.heightAlignment),
            )
        if (!video.areSizeAndRateSupported(width, height, budget.fps.toDouble())) return null
        val level =
            when {
                width > 1920 || height > 1080 -> MediaCodecInfo.CodecProfileLevel.AVCLevel51
                width > 1280 || height > 720 -> MediaCodecInfo.CodecProfileLevel.AVCLevel4
                width > 640 || height > 480 -> MediaCodecInfo.CodecProfileLevel.AVCLevel31
                else -> MediaCodecInfo.CodecProfileLevel.AVCLevel3
            }
        val profile =
            caps.profileLevels
                .firstOrNull {
                    val baseline =
                        it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline ||
                            (Build.VERSION.SDK_INT >= 27 &&
                                it.profile ==
                                    MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline)
                    baseline && it.level >= level
                }
                ?.profile ?: return null
        val bitrate = minOf(budget.bitrate, video.bitrateRange.upper)
        if (bitrate < video.bitrateRange.lower || bitrate < 128000) return null
        return Candidate(info.name, budget.copy(bitrate = bitrate), width, height, profile, level)
    }

    /** Own partially-created resources until the caller receives a running encoder. */
    internal fun start(candidate: Candidate, keyFrameSeconds: Int): StartedEncoder {
        val format =
            MediaFormat.createVideoFormat("video/avc", candidate.width, candidate.height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, candidate.video.bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, candidate.video.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyFrameSeconds)
                setInteger(MediaFormat.KEY_PROFILE, candidate.profile)
                if (Build.VERSION.SDK_INT >= 23) setInteger(MediaFormat.KEY_LEVEL, candidate.level)
            }
        val encoder = MediaCodec.createByCodecName(candidate.name)
        var surface: Surface? = null
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val input = encoder.createInputSurface()
            surface = input
            encoder.start()
            return StartedEncoder(
                encoder,
                input,
                candidate.width,
                candidate.height,
                candidate.video.fps,
                candidate.video.bitrate,
            )
        } catch (failure: Exception) {
            try {
                encoder.stop()
            } catch (_: Exception) {}
            try {
                encoder.release()
            } catch (_: Exception) {}
            try {
                surface?.release()
            } catch (_: Exception) {}
            throw failure
        }
    }
}
