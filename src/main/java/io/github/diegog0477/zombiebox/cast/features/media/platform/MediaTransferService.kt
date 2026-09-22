package io.github.diegog0477.zombiebox.cast.features.media.platform

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import io.github.diegog0477.zombiebox.cast.features.casting.platform.ProjectionService
import io.github.diegog0477.zombiebox.cast.features.media.data.GatewayMediaRepository
import io.github.diegog0477.zombiebox.cast.features.media.presentation.viewmodel.MediaViewModel
import java.util.concurrent.Executors

/** Owns one target snapshot and transfer; Activities only attach rendering callbacks. */
@Suppress("DEPRECATION")
class MediaTransferService : Service() {
    inner class LocalBinder : Binder() {
        val service
            get() = this@MediaTransferService
    }

    private val binder = LocalBinder()
    private val handler = Handler()
    private val worker = Executors.newSingleThreadExecutor()
    lateinit var model: MediaViewModel
        private set

    var targetName = ""
        private set

    var render: ((MediaViewModel.State) -> Unit)? = null
    private var initialized = false
    private var foreground = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val deadline = Runnable { model.stop() }
    private lateinit var notifications: TransferNotifications

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("cast", MODE_PRIVATE)
        targetName =
            prefs.getString("targetName", "").orEmpty().take(120).ifBlank {
                prefs.getString("device", "").orEmpty().take(80)
            }
        model =
            MediaViewModel(
                GatewayMediaRepository(
                    contentResolver,
                    prefs.getString("gateway", "").orEmpty(),
                    prefs.getString("device", "").orEmpty(),
                    prefs.getString("token", "").orEmpty(),
                ),
                { work -> worker.execute { work() } },
                { work -> handler.post { work() } },
            )
        notifications = TransferNotifications(this)
        model.observer = { state ->
            if (foreground) {
                if (state.phase in listOf("SENDING", "STOPPING")) notifications.update(state)
                else finishForeground()
            }
            render?.invoke(state)
        }
    }

    fun attach(shared: String?) {
        if (!initialized) {
            initialized = true
            model.restore(shared)
        } else if (shared != null) model.select(shared)
        render?.invoke(model.state)
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "SEND") {
            // Always enter foreground promptly after startForegroundService, even
            // when a stale/double tap no longer has a sendable document.
            notifications.start(model.state)
            foreground = true
            if (model.state.phase == "READY" && !ProjectionService.active) {
                wakeLock =
                    (getSystemService(POWER_SERVICE) as PowerManager)
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zombiebox:media-transfer")
                        .apply { acquire(20 * 60 * 1000L) }
                handler.postDelayed(deadline, 20 * 60 * 1000L)
                model.send()
            } else if (model.state.phase !in listOf("SENDING", "STOPPING")) finishForeground()
        } else if (intent?.action == "STOP") model.stop() else if (!foreground) stopSelf()
        // A killed process must never restart a stale URI or resume without consent.
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        model.stop()
        finishForeground()
    }

    private fun finishForeground() {
        handler.removeCallbacks(deadline)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        foreground = false
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        render = null
        model.close()
        handler.removeCallbacks(deadline)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        worker.shutdown()
        super.onDestroy()
    }
}
