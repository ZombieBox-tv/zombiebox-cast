package io.github.diegog0477.zombiebox.cast.features.casting.presentation.ui

import android.content.Context
import android.os.Build
import android.widget.LinearLayout
import android.widget.Switch
import io.github.diegog0477.zombiebox.cast.R
import io.github.diegog0477.zombiebox.cast.core.ui.PhoneWidgets
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CaptureOrientation
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CapturePreferences
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CaptureQuality

class CaptureOptions(context: Context, private val change: (CapturePreferences) -> Unit) :
    LinearLayout(context) {
    private val ui = PhoneWidgets(context)
    private var current = CapturePreferences()
    private var rendering = false
    val audio =
        Switch(context).apply {
            setText(R.string.share_audio)
            setTextColor(ui.foreground)
            isEnabled = Build.VERSION.SDK_INT >= 29
            setOnCheckedChangeListener { _, checked ->
                if (!rendering) change(current.copy(audio = checked))
            }
        }
    val audioStatus = ui.label(R.string.audio_disabled, 14f, ui.muted)
    private val quality = ui.row()
    private val framing = ui.row()
    private val latency =
        Switch(context).apply {
            setText(R.string.low_latency)
            setTextColor(ui.foreground)
            setOnCheckedChangeListener { _, checked ->
                if (!rendering) change(current.copy(lowLatency = checked))
            }
        }
    private var locked = false

    init {
        orientation = VERTICAL
        background = ui.shape(ui.surface)
        setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
        addView(audio, LayoutParams(-1, -2))
        addView(audioStatus)
        addView(ui.label(R.string.video_quality))
        addView(quality)
        addView(ui.label(R.string.quality_limit, 14f, ui.muted))
        addView(ui.label(R.string.orientation_title))
        addView(framing)
        addView(
            ui.label(
                if (Build.VERSION.SDK_INT >= 32) R.string.orientation_detail
                else R.string.orientation_legacy,
                14f,
                ui.muted,
            )
        )
        addView(latency, LayoutParams(-1, -2))
        addView(ui.label(R.string.low_latency_detail, 14f, ui.muted))
        render(current)
    }

    fun render(value: CapturePreferences) {
        current = value
        rendering = true
        audio.isChecked = value.audio
        latency.isChecked = value.lowLatency
        rendering = false
        quality.removeAllViews()
        for ((choice, label) in
            listOf(
                CaptureQuality.AUTO to R.string.quality_auto,
                CaptureQuality.SD to R.string.quality_480,
                CaptureQuality.HD to R.string.quality_720,
                CaptureQuality.FULL_HD to R.string.quality_1080,
            )) {
            quality.addView(
                ui.action(context.getString(label), value.quality == choice) {
                        if (!locked) change(current.copy(quality = choice))
                    }
                    .apply { isEnabled = !locked },
                LayoutParams(0, -2, 1f),
            )
        }
        framing.removeAllViews()
        for ((choice, label) in
            listOf(
                CaptureOrientation.AUTO to R.string.quality_auto,
                CaptureOrientation.PORTRAIT to R.string.orientation_portrait,
                CaptureOrientation.LANDSCAPE to R.string.orientation_landscape,
            )) {
            framing.addView(
                ui.action(
                        context.getString(label),
                        value.orientation.effective(Build.VERSION.SDK_INT) == choice,
                    ) {
                        if (!locked && choice.supported(Build.VERSION.SDK_INT))
                            change(current.copy(orientation = choice))
                    }
                    .apply { isEnabled = !locked && choice.supported(Build.VERSION.SDK_INT) },
                LayoutParams(0, -2, 1f),
            )
        }
    }

    fun lock(value: Boolean) {
        locked = value
        audio.isEnabled = !value && Build.VERSION.SDK_INT >= 29
        latency.isEnabled = !value
        for (i in 0 until quality.childCount) quality.getChildAt(i).isEnabled = !value
        for (i in 0 until framing.childCount) framing.getChildAt(i).isEnabled =
            !value && CaptureOrientation.values()[i].supported(Build.VERSION.SDK_INT)
    }
}
