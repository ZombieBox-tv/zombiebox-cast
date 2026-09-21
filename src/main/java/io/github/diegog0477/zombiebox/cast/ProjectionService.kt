package io.github.diegog0477.zombiebox.cast

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Base64
import io.github.diegog0477.zombiebox.shared.GatewayFailure
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("DEPRECATION")
class ProjectionService : Service() {
    companion object {
        @Volatile
        var active = false
            private set
    }

    private var encoderStarted = false
    private var shareAudio = false
    @Volatile private var audio: PlaybackAudio? = null
    private val main = Handler(Looper.getMainLooper())
    private val heartbeat = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var running = false
    @Volatile private var publisher: RtspPublisher? = null
    private var projection: MediaProjection? = null
    private lateinit var repository: GatewayCastRepository
    private var castId = ""

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
        shareAudio = intent.getBooleanExtra("audio", false)
        try {
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
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        main.post { stopSelf() }
                    }
                },
                main,
            )
            running = true
            active = true
            prefs.edit().putString("status", "BUFFERING").apply()
            publisher =
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
            val activeProjection = projection!!
            Thread({ encode(activeProjection) }, "zombie-cast-encoder").start()
            encoderStarted = true
        } catch (_: Exception) {
            prefs.edit().putString("status", "FAILED").apply()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun encode(projection: MediaProjection) {
        var codec: MediaCodec? = null
        var display: android.hardware.display.VirtualDisplay? = null
        var surface: android.view.Surface? = null
        val prefs = getSharedPreferences("cast", MODE_PRIVATE)
        val ready = AtomicBoolean(false)
        try {
            audio = PlaybackAudioFactory.create(Build.VERSION.SDK_INT, shareAudio)
            audio?.prepare(projection)
            val metrics = resources.displayMetrics
            val scale = minOf(1.0, 1280.0 / metrics.widthPixels, 720.0 / metrics.heightPixels)
            val width = (metrics.widthPixels * scale).toInt().coerceAtLeast(32) / 16 * 16
            val height = (metrics.heightPixels * scale).toInt().coerceAtLeast(32) / 16 * 16
            val format =
                MediaFormat.createVideoFormat("video/avc", width, height).apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, 2000000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                    setInteger(
                        MediaFormat.KEY_PROFILE,
                        MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline,
                    )
                }
            codec = MediaCodec.createEncoderByType("video/avc")
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = codec.createInputSurface()
            codec.start()
            display =
                projection.createVirtualDisplay(
                    "Zombiebox Cast",
                    width,
                    height,
                    metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface,
                    null,
                    null,
                )
            var connected = false
            val started = SystemClock.elapsedRealtime()
            heartbeat.scheduleWithFixedDelay(
                {
                    if (running)
                        try {
                            repository.api.request("PUT", "/v1/cast/$castId")
                            if (!ready.get()) {
                                repository.api.request("POST", "/v1/cast/$castId/ready")
                                ready.set(true)
                                prefs.edit().putString("status", "SHARING").apply()
                            }
                        } catch (e: Exception) {
                            if (
                                (e is GatewayFailure && e.status != 503) ||
                                    SystemClock.elapsedRealtime() - started > 30000
                            )
                                main.post { stopSelf() }
                        }
                },
                2,
                5,
                TimeUnit.SECONDS,
            )
            val info = MediaCodec.BufferInfo()
            while (running) {
                val index = codec.dequeueOutputBuffer(info, 10000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !connected) {
                    val output = codec.outputFormat
                    fun data(key: String): ByteArray {
                        val bytes = output.getByteBuffer(key)!!.duplicate()
                        return ByteArray(bytes.remaining()).also { bytes.get(it) }
                    }
                    val parameters =
                        RtpH264.split(data("csd-0")) +
                            (if (output.containsKey("csd-1")) RtpH264.split(data("csd-1"))
                            else emptyList())
                    val sps = parameters.first { it.isNotEmpty() && it[0].toInt() and 31 == 7 }
                    val pps = parameters.first { it.isNotEmpty() && it[0].toInt() and 31 == 8 }
                    publisher!!.connect(sps, pps, audio != null) {
                        Base64.encodeToString(it, Base64.NO_WRAP)
                    }
                    connected = true
                    audio?.start(publisher!!) { main.post { stopSelf() } }
                } else if (index >= 0) {
                    try {
                        if (
                            connected &&
                                info.size > 0 &&
                                info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            require(info.size <= 4 * 1024 * 1024)
                            val buffer = codec.getOutputBuffer(index)!!
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            buffer.get(bytes)
                            publisher!!.frame(RtpH264.split(bytes), info.presentationTimeUs)
                        }
                    } finally {
                        codec.releaseOutputBuffer(index, false)
                    }
                }
                if (!connected && SystemClock.elapsedRealtime() - started > 20000)
                    error("encoder startup timeout")
            }
        } catch (_: Exception) {
            if (running) prefs.edit().putString("status", "FAILED").apply()
        } finally {
            running = false
            active = false
            publisher?.close()
            audio?.close()
            heartbeat.shutdownNow()
            try {
                display?.release()
            } catch (_: Exception) {}
            try {
                codec?.stop()
            } catch (_: Exception) {}
            try {
                codec?.release()
            } catch (_: Exception) {}
            try {
                surface?.release()
            } catch (_: Exception) {}
            try {
                projection.stop()
            } catch (_: Exception) {}
            try {
                repository.stop(castId)
            } catch (_: Exception) {}
            repository.api.close()
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
            .setContentText(getString(R.string.sharing))
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.stop),
                stop,
            )
            .build()
    }

    override fun onDestroy() {
        if (!encoderStarted && ::repository.isInitialized)
            Thread(
                    {
                        try {
                            repository.stop(castId)
                        } catch (_: Exception) {}
                        repository.api.close()
                    },
                    "zombie-cast-cleanup",
                )
                .start()
        running = false
        active = false
        publisher?.close()
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
