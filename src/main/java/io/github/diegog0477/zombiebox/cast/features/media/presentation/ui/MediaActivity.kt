package io.github.diegog0477.zombiebox.cast.features.media.presentation.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Toast
import io.github.diegog0477.zombiebox.cast.R
import io.github.diegog0477.zombiebox.cast.core.ui.PhoneWidgets
import io.github.diegog0477.zombiebox.cast.features.media.data.GatewayMediaRepository
import io.github.diegog0477.zombiebox.cast.features.media.presentation.viewmodel.MediaViewModel
import java.util.concurrent.Executors

/** Platform document consent and composition; wire mapping stays in the repository. */
@Suppress("DEPRECATION")
class MediaActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler()
    private lateinit var model: MediaViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cast", MODE_PRIVATE)
        if (!prefs.getBoolean("companion", false)) {
            Toast.makeText(this, R.string.pair_prompt, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val repository =
            GatewayMediaRepository(
                applicationContext.contentResolver,
                prefs.getString("gateway", "").orEmpty(),
                prefs.getString("device", "").orEmpty(),
                prefs.getString("token", "").orEmpty(),
            )
        model =
            MediaViewModel(
                repository,
                { work -> executor.execute { work() } },
                { work -> handler.post { work() } },
            )
        val ui = PhoneWidgets(this)
        val content = ui.column().apply { setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(24)) }
        val header = ui.row()
        header.addView(
            ui.iconAction(R.drawable.ic_back, R.string.media_back) { finish() },
            LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)),
        )
        header.addView(
            ui.label(R.string.media_title, 30f, ui.accent),
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        content.addView(header)
        content.addView(ui.label(R.string.media_description, 16f, ui.muted))
        val tabs = ui.segments()
        for ((index, label) in
            listOf(R.string.mode_screen, R.string.mode_media, R.string.mode_audio).withIndex()) {
            tabs.addView(
                ui.segment(
                    getString(label),
                    listOf(R.drawable.ic_screen, R.drawable.ic_media, R.drawable.ic_audio)[index],
                    label == R.string.mode_media,
                ) {
                    if (!model.busy && label != R.string.mode_media) {
                        setResult(
                            RESULT_OK,
                            Intent()
                                .putExtra(
                                    "mode",
                                    if (label == R.string.mode_audio) "AUDIO" else "SCREEN",
                                ),
                        )
                        finish()
                    }
                },
                LinearLayout.LayoutParams(0, -2, 1f),
            )
        }
        content.addView(tabs)
        val card = ui.card()
        val document = ui.label(R.string.media_choose, 22f)
        val detail = ui.label(R.string.media_empty, 16f, ui.muted)
        val fileRow = ui.row()
        fileRow.addView(
            ImageView(this).apply {
                setImageDrawable(
                    getDrawable(R.drawable.ic_media)?.mutate()?.apply { setTint(ui.accent) }
                )
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
            },
            LinearLayout.LayoutParams(ui.dp(64), ui.dp(72)),
        )
        fileRow.addView(document, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(fileRow)
        card.addView(detail)
        val progress =
            ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progressTintList = android.content.res.ColorStateList.valueOf(ui.accent)
                indeterminateTintList = android.content.res.ColorStateList.valueOf(ui.accent)
                visibility = View.GONE
            }
        card.addView(progress, LinearLayout.LayoutParams(-1, ui.dp(12)))
        content.addView(card)
        val choose = ui.action(getString(R.string.media_choose)) { chooseDocument() }
        val send = ui.action(getString(R.string.media_send), true) { model.send() }
        val stop = ui.action(getString(R.string.media_cancel)) { model.stop() }
        content.addView(
            choose,
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(12) },
        )
        content.addView(send, LinearLayout.LayoutParams(-1, ui.dp(60)))
        content.addView(
            stop,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = ui.dp(12)
                bottomMargin = ui.dp(12)
            },
        )
        content.addView(ui.label(R.string.media_limits, 15f, ui.muted))
        val scroll =
            ScrollView(this).apply {
                isFillViewport = true
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
        setContentView(scroll)
        model.observer = { state ->
            document.text =
                state.document?.title?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.media_choose)
            detail.text =
                when (state.phase) {
                    "READING" -> getString(R.string.media_reading)
                    "SENDING" -> getString(R.string.media_uploading, state.percent)
                    "ACCEPTED" -> getString(R.string.media_accepted)
                    "SIZE" -> getString(R.string.media_size)
                    "FAILED",
                    "STOP_FAILED" -> getString(R.string.media_failed)
                    "STOPPING" -> getString(R.string.media_stopping)
                    "READY" ->
                        getString(R.string.media_selected, (state.document?.bytes ?: 0) / 1048576.0)
                    else -> getString(R.string.media_empty)
                }
            progress.visibility =
                if (state.phase in listOf("READING", "SENDING", "STOPPING")) View.VISIBLE
                else View.GONE
            progress.isIndeterminate = state.phase != "SENDING"
            progress.progress = state.percent.coerceIn(0, 100)
            progress.contentDescription = detail.text
            for (index in 0 until tabs.childCount) tabs.getChildAt(index).isEnabled = !model.busy
            choose.isEnabled = !model.busy && state.phase != "ACCEPTED"
            send.isEnabled =
                !model.busy && state.document?.supportedSize == true && state.phase == "READY"
            stop.isEnabled = state.phase in listOf("SENDING", "ACCEPTED", "FAILED", "STOP_FAILED")
            stop.setText(
                if (state.phase == "ACCEPTED") R.string.media_stop else R.string.media_cancel
            )
        }
        model.restore()
    }

    private fun chooseDocument() {
        try {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*", "audio/*"))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                201,
            )
        } catch (_: Exception) {
            Toast.makeText(this, R.string.media_picker_missing, Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 201 && resultCode == RESULT_OK)
            data?.data?.let { model.select(it.toString()) }
    }

    override fun onDestroy() {
        if (::model.isInitialized) model.close()
        executor.shutdown()
        super.onDestroy()
    }
}
