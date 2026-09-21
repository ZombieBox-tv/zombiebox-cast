package io.github.diegog0477.zombiebox.cast.features.home.presentation.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import io.github.diegog0477.zombiebox.cast.R
import io.github.diegog0477.zombiebox.cast.core.ui.PhoneWidgets
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.Receiver
import io.github.diegog0477.zombiebox.cast.features.companion.presentation.ui.RemotePanel
import io.github.diegog0477.zombiebox.cast.features.companion.presentation.viewmodel.CompanionViewModel

/** Renders phone surfaces; networking, credentials and capture remain outside this view. */
class CastDashboard(
    context: Context,
    private val pair: () -> Unit,
    refresh: () -> Unit,
    startCapture: () -> Unit,
    stopCapture: () -> Unit,
    send: (String, String) -> Unit,
    private val selectTrusted: (String) -> Unit,
    forget: () -> Unit,
) : LinearLayout(context) {
    private val ui = PhoneWidgets(context)
    private val pages = ui.column()
    private val home = ui.column()
    private val devices = ui.column()
    private val remote = RemotePanel(context, send)
    private val activity = ui.column()
    private val settings = ui.column()
    private val navigation = ui.row()
    private val deviceCards = ui.column()
    private val trustedCards = ui.column()
    private val pairingStatus = ui.label(R.string.pair_prompt, 16f, ui.muted)
    private val trustedStatus = ui.label(R.string.pair_prompt, 16f, ui.muted)
    private val remoteStatus = ui.label(R.string.remote_offline, 15f, ui.muted)
    private val resultStatus = ui.label(R.string.remote_no_commands, 15f, ui.muted)
    private val pairButton = ui.action(context.getString(R.string.pair_phone), click = pair)
    val status = ui.label(R.string.ready, 16f, ui.accent)
    val audioStatus = ui.label(R.string.audio_disabled, 14f, ui.muted)
    val audio =
        Switch(context).apply {
            setText(R.string.share_audio)
            setTextColor(ui.foreground)
            isEnabled = Build.VERSION.SDK_INT >= 29
        }
    val start = ui.action(context.getString(R.string.start), true, startCapture)
    private val stop = ui.action(context.getString(R.string.stop), click = stopCapture)
    private val forgetButton = ui.action(context.getString(R.string.forget_phone), click = forget)
    private var page = 0
    private var sharing = false
    private var trustedKey = ""

    init {
        orientation = VERTICAL
        setBackgroundColor(ui.background)
        val scroll = ScrollView(context).apply { isFillViewport = true }
        val content = ui.column().apply { setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12)) }
        val title = context.getString(R.string.cast_title)
        content.addView(
            ui.label(title, 30f).apply {
                setTypeface(null, Typeface.BOLD)
                text =
                    SpannableString(title).apply {
                        val offset = title.lastIndexOf("Cast")
                        if (offset >= 0)
                            setSpan(
                                ForegroundColorSpan(ui.accent),
                                offset,
                                title.length,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                            )
                    }
            }
        )
        content.addView(ui.label(R.string.cast_subtitle, 16f, ui.muted))
        val modes = ui.row()
        for ((index, label) in
            listOf(R.string.mode_screen, R.string.mode_media, R.string.mode_audio).withIndex()) {
            modes.addView(
                ui.action(context.getString(label), index == 0) {
                    if (index > 0)
                        AlertDialog.Builder(context)
                            .setMessage(R.string.mode_pending)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                },
                LayoutParams(0, -2, 1f).apply { setMargins(0, ui.dp(12), ui.dp(4), ui.dp(16)) },
            )
        }
        home.addView(modes)
        val source = ui.card()
        source.addView(ui.label(R.string.this_phone, 23f))
        source.addView(status)
        source.addView(ui.label(R.string.screen_detail, 16f, ui.muted))
        home.addView(source)
        val heading = ui.row()
        heading.addView(ui.label(R.string.available_devices, 20f), LayoutParams(0, -2, 1f))
        heading.addView(ui.action(context.getString(R.string.refresh_short), click = refresh))
        home.addView(heading)
        home.addView(deviceCards)
        home.addView(pairingStatus)
        home.addView(pairButton)
        home.addView(
            start,
            LayoutParams(-1, ui.dp(60)).apply {
                topMargin = ui.dp(16)
                bottomMargin = ui.dp(8)
            },
        )
        home.addView(stop)
        val options = ui.card()
        options.addView(audio, LayoutParams(-1, ui.dp(56)))
        options.addView(audioStatus)
        options.addView(ui.label(R.string.quality_adaptive, 15f, ui.muted))
        options.addView(ui.label(R.string.orientation_adaptive, 15f, ui.muted))
        home.addView(options)
        home.addView(ui.label(R.string.consent, 14f, ui.muted))
        devices.addView(ui.label(R.string.nav_devices, 24f))
        devices.addView(trustedStatus)
        devices.addView(trustedCards)
        devices.addView(ui.action(context.getString(R.string.pair_phone)) { if (!sharing) pair() })
        remote.addView(remoteStatus, 1)
        activity.addView(ui.label(R.string.nav_activity, 24f))
        activity.addView(resultStatus)
        settings.addView(ui.label(R.string.nav_settings, 24f))
        settings.addView(ui.label(R.string.audio_detail, 16f, ui.muted))
        settings.addView(ui.label(R.string.settings_pairing, 16f, ui.muted))
        settings.addView(forgetButton)
        for (view in listOf(home, devices, remote, activity, settings)) pages.addView(view)
        content.addView(pages)
        scroll.addView(content)
        addView(scroll, LayoutParams(-1, 0, 1f))
        addView(navigation)
        showPage(0)
    }

    private fun showPage(index: Int) {
        page = index
        for (i in 0 until pages.childCount) pages.getChildAt(i).visibility =
            if (i == page) View.VISIBLE else View.GONE
        navigation.removeAllViews()
        for ((i, name) in
            listOf(
                    R.string.nav_home,
                    R.string.nav_devices,
                    R.string.nav_remote,
                    R.string.nav_activity,
                    R.string.nav_settings,
                )
                .withIndex()) {
            navigation.addView(
                ui.action(context.getString(name), i == page) { showPage(i) }
                    .apply { textSize = 12f },
                LayoutParams(0, ui.dp(56), 1f),
            )
        }
    }

    fun receivers(values: List<Receiver>, selected: String, select: (String) -> Unit) {
        deviceCards.removeAllViews()
        if (values.isEmpty()) deviceCards.addView(ui.label(R.string.no_receivers, 14f, ui.muted))
        for (receiver in values) deviceCards.addView(
            ui.device(receiver.name, selected == receiver.id) { if (!sharing) select(receiver.id) },
            LayoutParams(-1, ui.dp(60)).apply { bottomMargin = ui.dp(8) },
        )
    }

    fun companion(state: CompanionViewModel.State) {
        val target = state.target
        val detail =
            when {
                state.failed -> context.getString(R.string.pair_failed)
                state.phase == "PENDING" ->
                    context.getString(R.string.pair_compare, state.comparison)
                state.phase == "DENIED" -> context.getString(R.string.pair_denied)
                target != null ->
                    context.getString(R.string.pair_connected, target.grant.targetName)
                else -> context.getString(R.string.pair_prompt)
            }
        pairingStatus.text = detail
        trustedStatus.text = detail
        remote.available(target?.remoteOnline == true && !state.busy && !state.failed)
        remoteStatus.text =
            if (target?.remoteOnline == true && !state.failed)
                context.getString(R.string.remote_target, target.grant.targetName)
            else context.getString(R.string.remote_offline)
        resultStatus.setText(
            when (target?.lastCommand) {
                "EXECUTED" -> R.string.command_executed
                "QUEUED",
                "DELIVERED" -> R.string.command_waiting
                "EXPIRED",
                "BUSY",
                "UNSUPPORTED" -> R.string.command_unavailable
                else -> R.string.remote_no_commands
            }
        )
        pairButton.isEnabled = !state.busy && !sharing
        forgetButton.isEnabled = target != null && !state.busy && !sharing
        val key = state.targets.toString() + target?.grant?.id + sharing
        if (key != trustedKey) {
            trustedKey = key
            trustedCards.removeAllViews()
            for (profile in state.targets) trustedCards.addView(
                ui.device(profile.name, profile.id == target?.grant?.id) {
                        if (!sharing) selectTrusted(profile.id)
                    }
                    .apply { isEnabled = !sharing },
                LayoutParams(-1, ui.dp(60)).apply { bottomMargin = ui.dp(8) },
            )
        }
    }

    fun sharing(value: Boolean) {
        sharing = value
        stop.visibility = if (value) View.VISIBLE else View.GONE
        audio.isEnabled = !value && Build.VERSION.SDK_INT >= 29
        pairButton.isEnabled = !value
        forgetButton.isEnabled = !value
    }
}
