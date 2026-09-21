package io.github.diegog0477.zombiebox.cast.features.casting.platform

import android.media.projection.MediaProjection
import io.github.diegog0477.zombiebox.cast.features.casting.transport.RtspPublisher

interface PlaybackAudio {
    fun prepare(projection: MediaProjection)

    fun start(publisher: RtspPublisher, onFailure: () -> Unit, onState: (String) -> Unit)

    fun close()
}

object PlaybackAudioFactory {
    fun create(api: Int, requested: Boolean): PlaybackAudio? {
        if (api < 29 || !requested) return null
        return try {
            Class.forName(
                    "io.github.diegog0477.zombiebox.cast.features.casting.platform.Api29PlaybackAudio"
                )
                .getDeclaredConstructor()
                .newInstance() as PlaybackAudio
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: LinkageError) {
            null
        }
    }
}
