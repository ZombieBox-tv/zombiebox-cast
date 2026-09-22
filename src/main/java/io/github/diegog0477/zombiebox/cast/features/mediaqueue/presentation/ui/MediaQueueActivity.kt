package io.github.diegog0477.zombiebox.cast.features.mediaqueue.presentation.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.diegog0477.zombiebox.cast.R
import io.github.diegog0477.zombiebox.cast.core.ui.PhoneWidgets
import io.github.diegog0477.zombiebox.cast.features.mediaqueue.data.GatewayMediaQueueRepository
import io.github.diegog0477.zombiebox.cast.features.mediaqueue.presentation.viewmodel.MediaQueueViewModel
import java.util.concurrent.Executors

/** Screen composition only; the gateway owns the list after explicit submission. */
@Suppress("DEPRECATION")
class MediaQueueActivity : Activity() {
    private val handler = Handler()
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var model: MediaQueueViewModel
    private val refresh =
        object : Runnable {
            override fun run() {
                if (::model.isInitialized) model.refresh()
                handler.postDelayed(this, 2000)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cast", MODE_PRIVATE)
        if (!prefs.getBoolean("companion", false)) {
            finish()
            return
        }
        model =
            MediaQueueViewModel(
                GatewayMediaQueueRepository(
                    prefs.getString("gateway", "").orEmpty(),
                    prefs.getString("device", "").orEmpty(),
                    prefs.getString("token", "").orEmpty(),
                ),
                { work -> worker.execute { work() } },
                { work -> handler.post { work() } },
            )
        val ui = PhoneWidgets(this)
        val content = ui.column().apply { setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(24)) }
        val header = ui.row()
        header.addView(
            ui.iconAction(R.drawable.ic_back, R.string.media_back) { finish() },
            LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)),
        )
        header.addView(ui.label(R.string.queue_title, 26f, ui.accent))
        content.addView(header)
        content.addView(ui.label(R.string.queue_description, 16f, ui.muted))
        content.addView(
            ui.label(R.string.media_target, 17f, ui.accent).apply {
                text = getString(R.string.media_target, prefs.getString("targetName", "").orEmpty())
            }
        )
        val input =
            EditText(this).apply {
                setTextColor(ui.foreground)
                setHintTextColor(ui.muted)
                setHint(R.string.queue_hint)
                inputType =
                    InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_URI or
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE
                filters = arrayOf(InputFilter.LengthFilter(65536))
                minLines = 3
                maxLines = 8
                if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain")
                    setText(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(65536))
            }
        content.addView(input, LinearLayout.LayoutParams(-1, -2))
        val status = ui.label(R.string.queue_idle, 18f)
        content.addView(status)
        val send =
            ui.action(getString(R.string.queue_start), true) { model.start(input.text.toString()) }
        val stop = ui.action(getString(R.string.queue_stop)) { model.stop() }
        content.addView(send, LinearLayout.LayoutParams(-1, ui.dp(60)))
        content.addView(stop, LinearLayout.LayoutParams(-1, -2))
        content.addView(ui.label(R.string.queue_limits, 15f, ui.muted))
        model.observer = { state ->
            val q = state.queue
            send.isEnabled = !state.busy && !q.active
            stop.isEnabled = !state.busy && q.active
            status.text =
                when {
                    state.error || q.phase == "FAILED" -> getString(R.string.queue_failed)
                    q.active ->
                        getString(
                            if (q.phase == "PREPARING") R.string.queue_preparing
                            else R.string.queue_playing,
                            q.index + 1,
                            q.count,
                        )
                    q.phase == "FINISHED" -> getString(R.string.queue_finished)
                    else -> getString(R.string.queue_idle)
                }
        }
        send.isEnabled = false
        stop.isEnabled = false
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(ui.background)
                addView(content)
                setOnApplyWindowInsetsListener { view, insets ->
                    view.setPadding(
                        insets.systemWindowInsetLeft,
                        insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight,
                        insets.systemWindowInsetBottom,
                    )
                    insets
                }
            }
        )
    }

    override fun onStart() {
        super.onStart()
        if (::model.isInitialized) handler.post(refresh)
    }

    override fun onStop() {
        handler.removeCallbacks(refresh)
        super.onStop()
    }

    override fun onDestroy() {
        if (::model.isInitialized) model.close()
        worker.shutdown()
        super.onDestroy()
    }
}
