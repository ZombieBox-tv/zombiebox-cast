package io.github.diegog0477.zombiebox.cast.features.casting.platform

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Base64
import io.github.diegog0477.zombiebox.cast.R
import io.github.diegog0477.zombiebox.cast.features.casting.data.GatewayCastRepository
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CaptureMode
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CaptureOrientation
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CastVideo
import io.github.diegog0477.zombiebox.cast.features.casting.transport.RtspPublisher
import io.github.diegog0477.zombiebox.cast.features.history.data.LocalHistoryStore
import io.github.diegog0477.zombiebox.cast.features.history.domain.CaptureHistory
import io.github.diegog0477.zombiebox.cast.features.history.domain.model.SessionAudio
import io.github.diegog0477.zombiebox.cast.features.history.domain.model.SessionPhase
import io.github.diegog0477.zombiebox.shared.GatewayFailure
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Suppress("DEPRECATION")
class ProjectionService : Service() {
    private var videoProfile = CastVideo()
    private var keyFrameSeconds = 2
    private var orientation = CaptureOrientation.AUTO

    companion object {
        @Volatile
        var active = false
            private set
    }

    private var encoderStarted = false
    private var shareAudio = false
    private var mode = CaptureMode.SCREEN
    @Volatile private var encoder: CaptureEncoder? = null
    @Volatile private var captureSize = Pair(1, 1)
    private var displayListener: DisplayManager.DisplayListener? = null
    private lateinit var publisherFactory: () -> RtspPublisher
    private val main = Handler(Looper.getMainLooper())
    private val heartbeat = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var running = false
    private var projection: MediaProjection? = null
    private lateinit var repository: GatewayCastRepository
    private var castId = ""
    private lateinit var history: CaptureHistory
    private var historyId = ""

    private fun recordPhase(phase: SessionPhase) {
        if (::history.isInitialized && historyId.isNotEmpty()) history.phase(historyId, phase)
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent == null || running) return START_NOT_STICKY
        val consent =
            intent.getParcelableExtra<Intent>("consent")
                ?: run {
                    stopSelf()
                    return START_NOT_STICKY
                }
        val prefs = getSharedPreferences("cast", MODE_PRIVATE)
        repository = GatewayCastRepository(prefs)
        castId =
            intent.getStringExtra("castId")
                ?: run {
                    stopSelf()
                    return START_NOT_STICKY
                }
        orientation =
            (CaptureOrientation.values().firstOrNull {
                    it.name == intent.getStringExtra("orientation")
                } ?: CaptureOrientation.AUTO)
                .effective(Build.VERSION.SDK_INT)
        mode =
            CaptureMode.values().firstOrNull { it.name == intent.getStringExtra("captureMode") }
                ?: CaptureMode.SCREEN
        shareAudio = mode == CaptureMode.AUDIO || intent.getBooleanExtra("audio", false)
        keyFrameSeconds = intent.getIntExtra("keyFrameSeconds", 2).coerceIn(1, 2)
        history = LocalHistoryStore.create(prefs)
        historyId = history.begin(intent.getStringExtra("receiverName").orEmpty(), shareAudio, mode)
        try {
            check(mode.supported(Build.VERSION.SDK_INT))
            videoProfile =
                CastVideo(
                    intent.getIntExtra("maxWidth", 640),
                    intent.getIntExtra("maxHeight", 360),
                    intent.getIntExtra("fps", 24),
                    intent.getIntExtra("bitrate", 800000),
                )
            val notification = notification()
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(
                    1,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                )
            else startForeground(1, notification)
            projection =
                (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                    .getMediaProjection(Activity.RESULT_OK, consent)
            projection!!.registerCallback(
                ProjectionCallbacks.create(
                    Build.VERSION.SDK_INT,
                    { main.post { stopSelf() } },
                    { width, height ->
                        if (width > 0 && height > 0) captureSize = Pair(width, height)
                    },
                ),
                main,
            )
            running = true
            active = true
            prefs
                .edit()
                .remove("videoWidth")
                .remove("videoHeight")
                .remove("videoFps")
                .putString("status", "BUFFERING")
                .apply()
            publisherFactory = {
                RtspPublisher(
                    intent.getStringExtra("host")!!,
                    intent.getIntExtra("port", 8554),
                    intent.getStringExtra("path")!!,
                    "Basic " +
                        Base64.encodeToString(
                            (intent.getStringExtra("user") +
                                    ":" +
                                    intent.getStringExtra("publishToken"))
                                .toByteArray(Charsets.UTF_8),
                            Base64.NO_WRAP,
                        ),
                )
            }
            updateDisplaySize()
            if (mode == CaptureMode.SCREEN && Build.VERSION.SDK_INT < 34) {
                displayListener =
                    object : DisplayManager.DisplayListener {
                        override fun onDisplayAdded(id: Int) {}

                        override fun onDisplayRemoved(id: Int) {}

                        override fun onDisplayChanged(id: Int) {
                            if (id == android.view.Display.DEFAULT_DISPLAY) updateDisplaySize()
                        }
                    }
                (getSystemService(DISPLAY_SERVICE) as DisplayManager).registerDisplayListener(
                    displayListener,
                    main,
                )
            }
            val activeProjection = projection!!
            Thread({ encode(activeProjection) }, "zombie-cast-encoder").start()
            encoderStarted = true
        } catch (_: Exception) {
            recordPhase(SessionPhase.FAILED)
            prefs.edit().putString("status", "FAILED").apply()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun updateDisplaySize() {
        val metrics = android.util.DisplayMetrics()
        (getSystemService(DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(android.view.Display.DEFAULT_DISPLAY)
            ?.getRealMetrics(metrics)
        if (metrics.widthPixels > 0 && metrics.heightPixels > 0)
            captureSize = Pair(metrics.widthPixels, metrics.heightPixels)
    }

    private fun encode(projection: MediaProjection) {
        val prefs = getSharedPreferences("cast", MODE_PRIVATE)
        val ready = AtomicBoolean(false)
        val waitingSince = AtomicLong(SystemClock.elapsedRealtime())
        var lastLease = SystemClock.elapsedRealtime()
        try {
            val captureState: (String) -> Unit = { value ->
                ready.set(false)
                waitingSince.set(SystemClock.elapsedRealtime())
                SessionPhase.values().firstOrNull { it.name == value }?.let(::recordPhase)
                prefs.edit().putString("status", value).apply()
            }
            val captureAudioState: (String) -> Unit = { value ->
                SessionAudio.values()
                    .firstOrNull { it.name == value }
                    ?.let { history.audio(historyId, it) }
                prefs.edit().putString("audioStatus", value).apply()
            }
            val capture: CaptureEncoder =
                if (mode == CaptureMode.AUDIO)
                    AudioProjectionEncoder(
                        projection,
                        { PlaybackAudioFactory.create(Build.VERSION.SDK_INT, true) },
                        publisherFactory,
                        { running },
                        captureState,
                        captureAudioState,
                    )
                else
                    ProjectionEncoder(
                        projection,
                        videoProfile,
                        keyFrameSeconds,
                        orientation,
                        SurfaceEncoderFactory(),
                        { width, height, fps ->
                            history.video(historyId, width, height, fps)
                            prefs
                                .edit()
                                .putInt("videoWidth", width)
                                .putInt("videoHeight", height)
                                .putInt("videoFps", fps)
                                .apply()
                        },
                        resources.displayMetrics.densityDpi,
                        { captureSize },
                        publisherFactory,
                        shareAudio,
                        { running },
                        captureState,
                        captureAudioState,
                    )
            encoder = capture
            heartbeat.scheduleWithFixedDelay(
                {
                    if (running)
                        try {
                            capture.checkProgress()
                            repository.renew(castId)
                            lastLease = SystemClock.elapsedRealtime()
                            if (!ready.get()) {
                                repository.ready(castId)
                                ready.set(true)
                                recordPhase(SessionPhase.SHARING)
                                prefs.edit().putString("status", "SHARING").apply()
                            }
                        } catch (e: Exception) {
                            if (
                                (e is GatewayFailure && e.status in listOf(401, 403, 404, 410)) ||
                                    SystemClock.elapsedRealtime() - lastLease > 30000 ||
                                    (!ready.get() &&
                                        SystemClock.elapsedRealtime() - waitingSince.get() > 30000)
                            )
                                main.post {
                                    recordPhase(SessionPhase.FAILED)
                                    prefs.edit().putString("status", "FAILED").apply()
                                    stopSelf()
                                }
                        }
                },
                2,
                5,
                TimeUnit.SECONDS,
            )
            capture.run()
        } catch (_: Exception) {
            if (running) {
                recordPhase(SessionPhase.FAILED)
                prefs.edit().putString("status", "FAILED").apply()
            }
        } finally {
            recordPhase(SessionPhase.STOPPED)
            running = false
            active = false
            heartbeat.shutdownNow()
            encoder?.interrupt()
            try {
                projection.stop()
            } catch (_: Exception) {}
            try {
                repository.stop(castId)
            } catch (_: Exception) {}
            repository.close()
            main.post { stopSelf() }
        }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26)
            manager.createNotificationChannel(
                NotificationChannel(
                    "projection",
                    getString(R.string.channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        val stop =
            PendingIntent.getService(
                this,
                0,
                Intent(this, ProjectionService::class.java).setAction("STOP"),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0,
            )
        val builder =
            if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "projection")
            else Notification.Builder(this)
        return builder
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(
                getString(
                    if (mode == CaptureMode.AUDIO) R.string.audio_sharing else R.string.sharing
                )
            )
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.stop),
                stop,
            )
            .build()
    }

    override fun onDestroy() {
        recordPhase(SessionPhase.STOPPED)
        if (!encoderStarted && ::repository.isInitialized)
            Thread(
                    {
                        try {
                            repository.stop(castId)
                        } catch (_: Exception) {}
                        repository.close()
                    },
                    "zombie-cast-cleanup",
                )
                .start()
        running = false
        active = false
        encoder?.interrupt()
        displayListener?.let {
            (getSystemService(DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(it)
        }
        displayListener = null
        heartbeat.shutdownNow()
        try {
            projection?.stop()
        } catch (_: Exception) {}
        val prefs = getSharedPreferences("cast", MODE_PRIVATE)
        if (prefs.getString("status", "") != "FAILED")
            prefs.edit().putString("status", "STOPPED").apply()
        super.onDestroy()
    }
}
