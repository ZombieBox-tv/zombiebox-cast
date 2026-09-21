package io.github.diegog0477.zombiebox.cast.features.casting.platform

import android.media.projection.MediaProjection

object ProjectionCallbacks {
    fun create(
        api: Int,
        stopped: () -> Unit,
        resized: (Int, Int) -> Unit,
    ): MediaProjection.Callback {
        if (api >= 34)
            return Class.forName(
                    "io.github.diegog0477.zombiebox.cast.features.casting.platform.Api34ProjectionCallback"
                )
                .getConstructor(
                    kotlin.jvm.functions.Function0::class.java,
                    kotlin.jvm.functions.Function2::class.java,
                )
                .newInstance(stopped, resized) as MediaProjection.Callback
        return object : MediaProjection.Callback() {
            override fun onStop() {
                stopped()
            }
        }
    }
}
