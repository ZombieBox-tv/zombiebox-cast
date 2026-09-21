package io.github.diegog0477.zombiebox.cast

import android.media.projection.MediaProjection

interface PlaybackAudio {
    fun prepare(projection:MediaProjection)
    fun start(publisher:RtspPublisher,onFailure:()->Unit)
    fun close()
}
object PlaybackAudioFactory {
    fun create(api:Int,requested:Boolean):PlaybackAudio? {
        if(api<29 || !requested)return null
        return try {Class.forName("io.github.diegog0477.zombiebox.cast.Api29PlaybackAudio").getDeclaredConstructor().newInstance() as PlaybackAudio} catch(_:ReflectiveOperationException){null}
    }
}
