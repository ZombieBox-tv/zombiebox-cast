package io.github.diegog0477.zombiebox.cast.features.companion.platform

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.github.diegog0477.zombiebox.cast.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Bounded camera-only QR capture, requested only when the user taps Scan. */
@Suppress("DEPRECATION")
class QrScanActivity : Activity(), SurfaceHolder.Callback {
    private val decoder = Executors.newSingleThreadExecutor()
    private val decoding = AtomicBoolean(false)
    private var camera: Camera? = null
    private lateinit var preview: SurfaceView
    private lateinit var message: TextView
    private var surfaceReady = false
    @Volatile private var active = false
    private var lastFrame = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.rgb(10, 15, 16))
                fitsSystemWindows = true
            }
        message =
            TextView(this).apply {
                setText(R.string.qr_scan_hint)
                setTextColor(Color.WHITE)
                textSize = 18f
                setPadding(24, 24, 24, 24)
            }
        root.addView(message)
        preview = SurfaceView(this)
        preview.holder.addCallback(this)
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        if (
            Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(android.Manifest.permission.CAMERA) !=
                    PackageManager.PERMISSION_GRANTED
        )
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 201)
    }

    override fun onResume() {
        super.onResume()
        active = true
        open()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        open()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        release()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 201 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            open()
        else message.setText(R.string.qr_unavailable)
    }

    private fun open() {
        if (!active || !surfaceReady || camera != null) return
        if (
            Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(android.Manifest.permission.CAMERA) !=
                    PackageManager.PERMISSION_GRANTED
        )
            return
        try {
            val cameraId =
                (0 until Camera.getNumberOfCameras()).firstOrNull {
                    val info = Camera.CameraInfo()
                    Camera.getCameraInfo(it, info)
                    info.facing == Camera.CameraInfo.CAMERA_FACING_BACK
                } ?: 0
            val source = Camera.open(cameraId)
            camera = source
            val parameters = source.parameters
            val size =
                parameters.supportedPreviewSizes
                    .filter { it.width in 320..1280 && it.height in 240..720 }
                    .minByOrNull { kotlin.math.abs(it.width * it.height - 640 * 480) }
                    ?: throw IllegalStateException("No bounded preview")
            parameters.setPreviewSize(size.width, size.height)
            parameters.previewFormat = android.graphics.ImageFormat.NV21
            if (
                parameters.supportedFocusModes?.contains(
                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                ) == true
            )
                parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            source.parameters = parameters
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(cameraId, info)
            val rotation = windowManager.defaultDisplay.rotation * 90
            val orientation =
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
                    (360 - (info.orientation + rotation) % 360) % 360
                else (info.orientation - rotation + 360) % 360
            source.setDisplayOrientation(orientation)
            source.setPreviewDisplay(preview.holder)
            source.setPreviewCallback { bytes, _ ->
                if (
                    !active ||
                        SystemClock.elapsedRealtime() - lastFrame < 200 ||
                        !decoding.compareAndSet(false, true)
                )
                    return@setPreviewCallback
                lastFrame = SystemClock.elapsedRealtime()
                val length = size.width * size.height
                if (bytes == null || bytes.size < length) {
                    decoding.set(false)
                    return@setPreviewCallback
                }
                val luminance = bytes.copyOf(length)
                try {
                    decoder.execute {
                        try {
                            val input =
                                PlanarYUVLuminanceSource(
                                    luminance,
                                    size.width,
                                    size.height,
                                    0,
                                    0,
                                    size.width,
                                    size.height,
                                    false,
                                )
                            val result =
                                QRCodeReader().decode(BinaryBitmap(HybridBinarizer(input))).text
                            if (result.length <= 1024)
                                runOnUiThread {
                                    if (active) {
                                        setResult(RESULT_OK, Intent().putExtra("pairingQr", result))
                                        finish()
                                    }
                                }
                        } catch (_: Exception) {} finally {
                            decoding.set(false)
                        }
                    }
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    decoding.set(false)
                }
            }
            source.startPreview()
        } catch (_: Exception) {
            release()
            message.setText(R.string.qr_unavailable)
        }
    }

    private fun release() {
        try {
            camera?.setPreviewCallback(null)
            camera?.stopPreview()
            camera?.release()
        } catch (_: Exception) {}
        camera = null
    }

    override fun onPause() {
        active = false
        release()
        super.onPause()
    }

    override fun onDestroy() {
        decoder.shutdownNow()
        super.onDestroy()
    }
}
