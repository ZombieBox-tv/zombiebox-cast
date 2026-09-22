package io.github.diegog0477.zombiebox.cast.features.media.platform

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.diegog0477.zombiebox.cast.R
import io.github.diegog0477.zombiebox.cast.features.media.presentation.ui.MediaActivity
import io.github.diegog0477.zombiebox.cast.features.media.presentation.viewmodel.MediaViewModel

@Suppress("DEPRECATION")
class TransferNotifications(private val service: Service) {
    private val manager =
        service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun notification(state: MediaViewModel.State): Notification {
        val builder =
            if (Build.VERSION.SDK_INT >= 26) TransferApi26.builder(service, manager)
            else Notification.Builder(service)
        val immutable = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val open =
            PendingIntent.getActivity(
                service,
                34,
                Intent(service, MediaActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or immutable,
            )
        val stop =
            PendingIntent.getService(
                service,
                34,
                Intent(service, MediaTransferService::class.java).setAction("STOP"),
                PendingIntent.FLAG_UPDATE_CURRENT or immutable,
            )
        return builder
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle(service.getString(R.string.media_transfer_channel))
            .setContentText(
                service.getString(
                    if (state.phase == "STOPPING") R.string.media_stopping
                    else R.string.media_background
                )
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, state.percent.coerceIn(0, 100), state.phase != "SENDING")
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                service.getString(R.string.media_cancel),
                stop,
            )
            .build()
    }

    fun start(state: MediaViewModel.State) {
        service.startForeground(34, notification(state))
    }

    fun update(state: MediaViewModel.State) {
        if (Build.VERSION.SDK_INT < 33 || TransferApi33.allowed(service))
            manager.notify(34, notification(state))
    }
}
