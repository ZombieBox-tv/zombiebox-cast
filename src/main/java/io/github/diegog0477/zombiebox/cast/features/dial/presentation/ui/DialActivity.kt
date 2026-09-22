package io.github.diegog0477.zombiebox.cast.features.dial.presentation.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.diegog0477.zombiebox.cast.R
import io.github.diegog0477.zombiebox.cast.core.ui.PhoneWidgets
import io.github.diegog0477.zombiebox.cast.features.dial.data.LanDialRepository
import io.github.diegog0477.zombiebox.cast.features.dial.presentation.viewmodel.DialViewModel
import java.util.concurrent.Executors

@Suppress("DEPRECATION")
class DialActivity : Activity() {
    private val handler = Handler()
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var model: DialViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model =
            DialViewModel(
                LanDialRepository(),
                { task -> worker.execute { task() } },
                { task -> handler.post { task() } },
            )
        val ui = PhoneWidgets(this)
        val content = ui.column().apply { setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(24)) }
        content.addView(
            ui.iconAction(R.drawable.ic_back, R.string.media_back) { finish() },
            LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)),
        )
        content.addView(ui.label(R.string.dial_title, 26f, ui.accent))
        content.addView(ui.label(R.string.dial_description, 16f, ui.muted))
        val refresh = ui.action(getString(R.string.refresh)) { model.refresh() }
        content.addView(refresh)
        val status = ui.label(R.string.dial_empty, 17f)
        content.addView(status)
        val list = ui.column()
        content.addView(list)
        model.observer = { state ->
            refresh.isEnabled = !state.busy
            status.setText(
                when {
                    state.busy -> R.string.dial_searching
                    state.result == "RUNNING" -> R.string.dial_running
                    state.result == "FAILED" -> R.string.dial_failed
                    state.devices.isEmpty() -> R.string.dial_empty
                    else -> R.string.dial_choose
                }
            )
            list.removeAllViews()
            for (device in state.devices) list.addView(
                ui.action(device.name) { model.launch(device) }.apply { isEnabled = !state.busy }
            )
        }
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
        model.refresh()
    }

    override fun onDestroy() {
        model.close()
        worker.shutdown()
        super.onDestroy()
    }
}
