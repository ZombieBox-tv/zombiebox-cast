package io.github.diegog0477.zombiebox.cast

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.widget.*
import io.github.diegog0477.zombiebox.cast.features.casting.data.GatewayCastRepository
import io.github.diegog0477.zombiebox.cast.features.casting.data.LocalCapturePreferences
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CaptureMode
import io.github.diegog0477.zombiebox.cast.features.casting.platform.ProjectionService
import io.github.diegog0477.zombiebox.cast.features.casting.presentation.viewmodel.CapturePreferencesViewModel
import io.github.diegog0477.zombiebox.cast.features.casting.presentation.viewmodel.CastViewModel
import io.github.diegog0477.zombiebox.cast.features.companion.data.GatewayCompanionRepository
import io.github.diegog0477.zombiebox.cast.features.companion.platform.QrScanActivity
import io.github.diegog0477.zombiebox.cast.features.companion.presentation.ui.PairingDialog
import io.github.diegog0477.zombiebox.cast.features.companion.presentation.viewmodel.CompanionViewModel
import io.github.diegog0477.zombiebox.cast.features.discovery.presentation.viewmodel.DiscoveryViewModel
import io.github.diegog0477.zombiebox.cast.features.history.data.LocalHistoryStore
import io.github.diegog0477.zombiebox.cast.features.history.presentation.viewmodel.HistoryViewModel
import io.github.diegog0477.zombiebox.cast.features.home.presentation.ui.CastDashboard
import io.github.diegog0477.zombiebox.shared.GatewayDiscovery
import java.util.concurrent.Executors

@Suppress("DEPRECATION")
class CastActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler()
    private lateinit var history: HistoryViewModel
    private lateinit var repository: GatewayCastRepository
    private lateinit var capturePreferences: CapturePreferencesViewModel
    private lateinit var model: CastViewModel
    private lateinit var discoveryModel: DiscoveryViewModel
    private lateinit var dashboard: CastDashboard
    private lateinit var companion: CompanionViewModel
    private var resumed = false
    private lateinit var status: TextView
    private lateinit var audioStatus: TextView
    private lateinit var start: Button
    private var consent: Intent? = null
    private lateinit var audio: CompoundButton
    private var shareAudio = false
    private var capturePending = false
    private val serviceStatus =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            if (key == LocalHistoryStore.KEY && ::history.isInitialized) history.refresh()
            if (
                key in listOf("videoWidth", "videoHeight", "videoFps", "status") &&
                    ::dashboard.isInitialized
            ) {
                dashboard.videoProfile(
                    if (ProjectionService.active) preferences.getInt("videoWidth", 0) else 0,
                    preferences.getInt("videoHeight", 0),
                    preferences.getInt("videoFps", 0),
                )
            }
            if (key == "audioStatus" && ::audioStatus.isInitialized) renderAudioStatus()
            if (key == "status" && ::status.isInitialized) {
                status.setText(
                    when (preferences.getString("status", "")) {
                        "SHARING" ->
                            if (capturePreferences.state.mode == CaptureMode.AUDIO)
                                R.string.audio_sharing
                            else R.string.sharing
                        "BUFFERING" -> R.string.buffering
                        "RECOVERING" -> R.string.recovering
                        "FAILED" -> R.string.failed
                        else -> R.string.stopped
                    }
                )
                dashboard.sharing(
                    ProjectionService.active,
                    capturePending || model.state.busy || ProjectionService.active,
                )
                capturePreferences.lock(
                    ProjectionService.active || capturePending || model.state.busy
                )
                start.isEnabled =
                    !model.state.busy &&
                        model.state.selected.isNotEmpty() &&
                        !ProjectionService.active &&
                        !capturePending &&
                        repository.paired &&
                        capturePreferences.state.mode.supported(Build.VERSION.SDK_INT)
            }
        }
    private var receiverKey = ""
    private var companionKey = ""
    private val companionTick =
        object : Runnable {
            override fun run() {
                if (!resumed) return
                companion.refresh()
                handler.postDelayed(this, 1500)
            }
        }

    private fun pairPhone() {
        if (ProjectionService.active || capturePending) return
        PairingDialog(
                this,
                discoveryModel,
                { startActivityForResult(Intent(this, QrScanActivity::class.java), 103) },
                { base, code -> companion.join(base, code, "") },
            )
            .show(repository.address)
    }

    private fun beginCapture() {
        if (
            ProjectionService.active ||
                capturePending ||
                model.state.busy ||
                !repository.paired ||
                model.state.selected.isEmpty() ||
                !capturePreferences.state.mode.supported(Build.VERSION.SDK_INT)
        )
            return
        capturePending = true
        capturePreferences.lock(true)
        dashboard.sharing(false, true)
        shareAudio =
            (capturePreferences.state.mode == CaptureMode.AUDIO || audio.isChecked) &&
                Build.VERSION.SDK_INT >= 29
        if (
            Build.VERSION.SDK_INT >= 29 &&
                shareAudio &&
                checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        )
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 102)
        else captureConsent()
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        capturePending = saved?.getBoolean("capturePending", false) ?: false
        shareAudio = saved?.getBoolean("shareAudio", false) ?: false
        capturePreferences =
            CapturePreferencesViewModel(
                LocalCapturePreferences(getSharedPreferences("cast", MODE_PRIVATE))
            )
        history =
            HistoryViewModel(LocalHistoryStore.create(getSharedPreferences("cast", MODE_PRIVATE)))
        repository = GatewayCastRepository(getSharedPreferences("cast", MODE_PRIVATE))
        val background = executor
        val ui = handler
        model =
            CastViewModel(
                repository,
                { work -> background.execute { work() } },
                { done -> ui.post { done() } },
            )
        saved?.getString("captureReceiver")?.let(model::select)
        discoveryModel =
            DiscoveryViewModel(
                GatewayDiscovery()::scan,
                { work -> background.execute { work() } },
                { work -> ui.post { work() } },
            )
        companion =
            CompanionViewModel(
                GatewayCompanionRepository(getSharedPreferences("cast", MODE_PRIVATE)),
                { work -> background.execute { work() } },
                { work -> ui.post { work() } },
            )
        dashboard =
            CastDashboard(
                this,
                ::pairPhone,
                {
                    companion.refresh(reconnect = !ProjectionService.active && !capturePending)
                    if (repository.paired) model.refresh()
                },
                ::beginCapture,
                {
                    stopService(Intent(this, ProjectionService::class.java))
                    status.setText(R.string.stopped)
                },
                companion::send,
                { id -> if (!ProjectionService.active && !capturePending) companion.select(id) },
                {
                    if (!ProjectionService.active && !capturePending)
                        android.app.AlertDialog.Builder(this)
                            .setMessage(R.string.forget_confirm)
                            .setPositiveButton(R.string.forget_phone) { _, _ -> companion.forget() }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                },
                capturePreferences.state,
                capturePreferences::update,
                history::clearFinished,
            )
        history.observer = dashboard::renderHistory
        history.refresh()
        capturePreferences.observer = { value ->
            dashboard.renderPreferences(value)
            if (::audioStatus.isInitialized) renderAudioStatus()
            if (::start.isInitialized)
                start.isEnabled =
                    !model.state.busy &&
                        !capturePending &&
                        !ProjectionService.active &&
                        repository.paired &&
                        model.state.selected.isNotEmpty() &&
                        value.mode.supported(Build.VERSION.SDK_INT)
        }
        dashboard.restoreNavigation(saved)
        status = dashboard.status
        audioStatus = dashboard.audioStatus
        audio = dashboard.audio
        start = dashboard.start
        dashboard.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom,
            )
            insets
        }
        setContentView(dashboard)
        companion.observer = { state ->
            dashboard.companion(state)
            if (!state.busy && !state.failed && !ProjectionService.active && !capturePending) {
                repository.reload()
                val key =
                    state.target?.grant?.id.orEmpty() +
                        state.target?.castAvailable +
                        repository.address
                if (key != companionKey && state.target != null) {
                    companionKey = key
                    model.refresh()
                }
            }
        }
        companion.paired = {
            repository.reload()
            if (repository.paired) model.refresh()
            else {
                receiverKey = ""
                model.clearReceiver()
                dashboard.receivers(emptyList(), "", model::select)
                start.isEnabled = false
            }
        }
        dashboard.companion(companion.state)
        dashboard.sharing(
            ProjectionService.active,
            capturePending || model.state.busy || ProjectionService.active,
        )
        capturePreferences.lock(ProjectionService.active || capturePending || model.state.busy)
        model.observer = { state ->
            capturePreferences.lock(state.busy || capturePending || ProjectionService.active)
            dashboard.sharing(
                ProjectionService.active,
                state.busy || capturePending || ProjectionService.active,
            )
            start.isEnabled =
                !state.busy &&
                    state.selected.isNotEmpty() &&
                    !ProjectionService.active &&
                    !capturePending &&
                    repository.paired &&
                    capturePreferences.state.mode.supported(Build.VERSION.SDK_INT)
            status.setText(
                if (ProjectionService.active) {
                    if (capturePreferences.state.mode == CaptureMode.AUDIO) R.string.audio_sharing
                    else R.string.sharing
                } else if (state.failed) R.string.failed
                else if (state.busy) R.string.connecting
                else if (state.receivers.isEmpty()) R.string.no_receivers else R.string.ready
            )
            val key = state.receivers.toString() + state.selected
            if (receiverKey != key) {
                receiverKey = key
                dashboard.receivers(state.receivers, state.selected, model::select)
            }
            state.grant?.let { grant ->
                val permission = consent
                consent = null
                model.consumeGrant()
                if (permission != null && !ProjectionService.active) {
                    val video = capturePreferences.state.video(grant.video)
                    val intent =
                        Intent(this, ProjectionService::class.java)
                            .putExtra("audio", shareAudio)
                            .putExtra("captureMode", grant.mode.name)
                            .putExtra("consent", permission)
                            .putExtra("castId", grant.id)
                            .putExtra(
                                "receiverName",
                                state.receivers
                                    .firstOrNull { it.id == state.selected }
                                    ?.name
                                    .orEmpty(),
                            )
                            .putExtra("maxWidth", video.maxWidth)
                            .putExtra("maxHeight", video.maxHeight)
                            .putExtra("fps", video.fps)
                            .putExtra("keyFrameSeconds", capturePreferences.state.keyFrameSeconds)
                            .putExtra("orientation", capturePreferences.state.orientation.name)
                            .putExtra("bitrate", video.bitrate)
                            .putExtra("host", grant.host)
                            .putExtra("port", grant.port)
                            .putExtra("path", grant.path)
                            .putExtra("user", grant.user)
                            .putExtra("publishToken", grant.token)
                    try {
                        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent)
                        else startService(intent)
                        status.setText(R.string.buffering)
                        start.isEnabled = false
                    } catch (_: Exception) {
                        executor.execute {
                            try {
                                repository.stop(grant.id)
                            } catch (_: Exception) {}
                        }
                        status.setText(R.string.failed)
                    }
                } else
                    executor.execute {
                        try {
                            repository.stop(grant.id)
                        } catch (_: Exception) {}
                    }
            }
            if (state.failed || (!state.busy && state.selected.isEmpty())) consent = null
            if (consent != null && !state.busy && state.grant == null)
                model.start(capturePreferences.state.mode)
        }
        if (repository.paired && !capturePending) model.refresh()
        else if (!repository.paired) discoveryModel.refresh()
    }

    private fun renderAudioStatus() {
        audioStatus.setText(
            when (getSharedPreferences("cast", MODE_PRIVATE).getString("audioStatus", "DISABLED")) {
                "CAPTURING" -> R.string.audio_capturing
                "SILENT" -> R.string.audio_silent
                "UNAVAILABLE" ->
                    if (capturePreferences.state.mode == CaptureMode.AUDIO)
                        R.string.audio_only_unavailable
                    else R.string.audio_unavailable
                "WAITING" -> R.string.audio_waiting
                else -> R.string.audio_disabled
            }
        )
    }

    private fun captureConsent() {
        startActivityForResult(
            (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                .createScreenCaptureIntent(),
            101,
        )
    }

    override fun onRequestPermissionsResult(
        request: Int,
        permissions: Array<out String>,
        results: IntArray,
    ) {
        super.onRequestPermissionsResult(request, permissions, results)
        if (request == 102) {
            shareAudio =
                results.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!shareAudio && capturePreferences.state.mode == CaptureMode.AUDIO) {
                capturePending = false
                dashboard.sharing(false)
                capturePreferences.lock(false)
                start.isEnabled =
                    !model.state.busy &&
                        repository.paired &&
                        model.state.selected.isNotEmpty() &&
                        !ProjectionService.active
                Toast.makeText(this, R.string.audio_permission_required, Toast.LENGTH_LONG).show()
            } else captureConsent()
        }
    }

    override fun onResume() {
        super.onResume()
        getSharedPreferences("cast", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(serviceStatus)
        resumed = true
        history.refresh()
        if (::companion.isInitialized) {
            companion.refresh(reconnect = !capturePending && !ProjectionService.active)
            handler.removeCallbacks(companionTick)
            handler.postDelayed(companionTick, 1500)
        }
        if (::audioStatus.isInitialized) renderAudioStatus()
        serviceStatus.onSharedPreferenceChanged(
            getSharedPreferences("cast", MODE_PRIVATE),
            "status",
        )
        if (::model.isInitialized && repository.paired && !capturePending) model.refresh()
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(companionTick)
        getSharedPreferences("cast", MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(serviceStatus)
        super.onPause()
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == 103 && result == RESULT_OK && data != null && !ProjectionService.active) {
            companion.join("", "", data.getStringExtra("pairingQr") ?: "")
        }
        if (request == 101) {
            capturePending = false
            capturePreferences.lock(ProjectionService.active)
            dashboard.sharing(ProjectionService.active)
            if (result == RESULT_OK && data != null) {
                consent = data
                if (!model.state.busy) model.start(capturePreferences.state.mode)
            }
        }
    }

    override fun onSaveInstanceState(out: Bundle) {
        dashboard.saveNavigation(out)
        out.putBoolean("capturePending", capturePending)
        out.putBoolean("shareAudio", shareAudio)
        out.putString("captureReceiver", model.state.selected)
        super.onSaveInstanceState(out)
    }

    override fun onDestroy() {
        history.observer = null
        capturePreferences.observer = null
        companion.close()
        handler.removeCallbacks(companionTick)
        discoveryModel.close()
        model.close()
        consent = null
        executor.shutdown()
        super.onDestroy()
    }
}
