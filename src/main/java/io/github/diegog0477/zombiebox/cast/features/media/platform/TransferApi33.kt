package io.github.diegog0477.zombiebox.cast.features.media.platform

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager

@TargetApi(33)
object TransferApi33 {
    fun allowed(context: Context) =
        context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") ==
            PackageManager.PERMISSION_GRANTED

    fun request(activity: Activity) {
        activity.requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 134)
    }
}
