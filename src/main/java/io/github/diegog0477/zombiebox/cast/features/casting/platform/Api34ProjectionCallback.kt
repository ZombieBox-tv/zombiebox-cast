package io.github.diegog0477.zombiebox.cast.features.casting.platform

import android.annotation.TargetApi
import android.media.projection.MediaProjection

@TargetApi(34)
class Api34ProjectionCallback(
    private val stopped: () -> Unit,
    private val resized: (Int, Int) -> Unit,
) : MediaProjection.Callback() {
    override fun onStop() {
        stopped()
    }

    override fun onCapturedContentResize(width: Int, height: Int) {
        resized(width, height)
    }
}
