package io.github.diegog0477.zombiebox.cast.features.media.platform

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent

/** Channel and background-service APIs are isolated from the API21 baseline. */
@TargetApi(26)
object TransferApi26 {
    fun start(context: Context, intent: Intent) {
        context.startForegroundService(intent)
    }

    fun builder(context: Context, manager: NotificationManager): Notification.Builder {
        manager.createNotificationChannel(
            NotificationChannel(
                "media-transfer",
                context.getString(
                    io.github.diegog0477.zombiebox.cast.R.string.media_transfer_channel
                ),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        return Notification.Builder(context, "media-transfer")
    }
}
