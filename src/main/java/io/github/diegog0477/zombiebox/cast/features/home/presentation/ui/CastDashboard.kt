package io.github.diegog0477.zombiebox.cast.features.home.presentation.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import io.github.diegog0477.zombiebox.cast.R
import io.github.diegog0477.zombiebox.cast.core.ui.PhoneWidgets
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CapturePreferences
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.Receiver
import io.github.diegog0477.zombiebox.cast.features.casting.presentation.ui.CaptureOptions
import io.github.diegog0477.zombiebox.cast.features.companion.presentation.ui.RemotePanel
import io.github.diegog0477.zombiebox.cast.features.companion.presentation.viewmodel.CompanionViewModel
import io.github.diegog0477.zombiebox.cast.features.history.domain.model.CaptureSession
import io.github.diegog0477.zombiebox.cast.features.history.presentation.ui.HistoryPanel

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
    preferences: CapturePreferences,
    changePreferences: (CapturePreferences) -> Unit,
    clearHistory: () -> Unit,
) : LinearLayout(context) {
    private val ui = PhoneWidgets(context)
    private val pages = ui.column()
    private val home = ui.column()
    private val devices = ui.column()
    private val remote = RemotePanel(context, send)
    private val activity = ui.column()
    private val history = HistoryPanel(context, clearHistory)
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
    private val videoDetail = ui.label("", 13f, ui.muted).apply { visibility = GONE }
    private val options = CaptureOptions(context, changePreferences).apply { render(preferences) }
    val audioStatus
        get() = options.audioStatus

    val audio
        get() = options.audio

    private val scroll = ScrollView(context).apply { isFillViewport = true }
    private val scrollPositions = IntArray(5)
    val start = ui.action(context.getString(R.string.start), true, startCapture)
    private val stop = ui.action(context.getString(R.string.stop), click = stopCapture)
    private val forgetButton = ui.action(context.getString(R.string.forget_phone), click = forget)
    private var page = 0
    private var sharing = false
    private var trustedKey = ""

    init {
        orientation = VERTICAL
        setBackgroundColor(ui.background)
        val content = ui.column().apply { setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12)) }
        val title = context.getString(R.string.cast_title)
        val header = ui.row()
        header.addView(
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
            },
            LayoutParams(0, -2, 1f),
        )
        header.addView(
            ui.iconAction(R.drawable.ic_cast, R.string.pair_phone) { if (!sharing) pair() },
            LayoutParams(ui.dp(48), ui.dp(48)),
        )
        header.addView(
            ui.iconAction(R.drawable.ic_settings, R.string.nav_settings) { showPage(4) },
            LayoutParams(ui.dp(48), ui.dp(48)),
        )
        content.addView(header)
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
        val sourceRow = ui.row()
        sourceRow.addView(
            PhoneIllustration(context),
            LayoutParams(ui.dp(88), ui.dp(156)).apply { marginEnd = ui.dp(16) },
        )
        val sourceText = ui.column()
        sourceText.addView(ui.label(R.string.this_phone, 23f))
        sourceText.addView(status)
        sourceText.addView(videoDetail)
        sourceText.addView(ui.label(R.string.screen_detail, 16f, ui.muted))
        sourceRow.addView(sourceText, LayoutParams(0, -2, 1f))
        source.addView(sourceRow)
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
        home.addView(
            options,
            LayoutParams(-1, -2).apply {
                topMargin = ui.dp(16)
                bottomMargin = ui.dp(16)
            },
        )
        home.addView(ui.label(R.string.consent, 14f, ui.muted))
        devices.addView(ui.label(R.string.nav_devices, 24f))
        devices.addView(trustedStatus)
        devices.addView(trustedCards)
        devices.addView(ui.action(context.getString(R.string.pair_phone)) { if (!sharing) pair() })
        remote.addView(remoteStatus, 1)
        activity.addView(ui.label(R.string.nav_activity, 24f))
        activity.addView(history)
        activity.addView(ui.label(R.string.history_remote_title, 20f))
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
        scrollPositions[page] = scroll.scrollY
        page = index.coerceIn(0, 4)
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
                ui.navigation(
                    context.getString(name),
                    i == page,
                    listOf(
                        R.drawable.ic_home,
                        R.drawable.ic_devices,
                        R.drawable.ic_remote,
                        R.drawable.ic_activity,
                        R.drawable.ic_settings,
                    )[i],
                ) {
                    showPage(i)
                },
                LayoutParams(0, -2, 1f),
            )
        }
        scroll.post { scroll.scrollTo(0, scrollPositions[page]) }
    }

    fun renderHistory(values: List<CaptureSession>) = history.render(values)

    fun videoProfile(width: Int, height: Int, fps: Int) {
        videoDetail.visibility = if (width > 0 && height > 0 && fps > 0) VISIBLE else GONE
        videoDetail.text = context.getString(R.string.video_profile, width, height, fps)
    }

    fun renderPreferences(value: CapturePreferences) = options.render(value)

    fun saveNavigation(out: Bundle) {
        scrollPositions[page] = scroll.scrollY
        out.putInt("dashboardPage", page)
        out.putIntArray("dashboardScroll", scrollPositions)
    }

    fun restoreNavigation(saved: Bundle?) {
        val restored = saved?.getIntArray("dashboardScroll") ?: return
        if (restored.size != scrollPositions.size) return
        showPage(saved.getInt("dashboardPage", 0))
        restored.copyInto(scrollPositions)
        scroll.post { scroll.scrollTo(0, scrollPositions[page]) }
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

    fun sharing(active: Boolean, locked: Boolean = active) {
        sharing = locked
        stop.visibility = if (active) View.VISIBLE else View.GONE
        options.lock(locked)
        pairButton.isEnabled = !locked
        forgetButton.isEnabled = !locked
    }
}
