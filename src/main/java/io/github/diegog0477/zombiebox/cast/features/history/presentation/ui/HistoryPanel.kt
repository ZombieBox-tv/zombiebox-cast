package io.github.diegog0477.zombiebox.cast.features.history.presentation.ui

import android.app.AlertDialog
import android.content.Context
import android.widget.LinearLayout
import io.github.diegog0477.zombiebox.cast.R
import io.github.diegog0477.zombiebox.cast.core.ui.PhoneWidgets
import io.github.diegog0477.zombiebox.cast.features.history.domain.model.*
import java.text.DateFormat
import java.util.Date

class HistoryPanel(context: Context, clear: () -> Unit) : LinearLayout(context) {
    private val ui = PhoneWidgets(context)
    private val rows = ui.column()
    private val clearButton =
        ui.action(context.getString(R.string.history_clear)) {
            AlertDialog.Builder(context)
                .setMessage(R.string.history_clear_confirm)
                .setPositiveButton(R.string.history_clear) { _, _ -> clear() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

    init {
        orientation = VERTICAL
        addView(ui.label(R.string.history_title, 20f))
        addView(ui.label(R.string.history_detail, 14f, ui.muted))
        addView(clearButton)
        addView(rows)
        render(emptyList())
    }

    fun render(sessions: List<CaptureSession>) {
        rows.removeAllViews()
        clearButton.isEnabled = sessions.any { it.phase.terminal }
        if (sessions.isEmpty()) rows.addView(ui.label(R.string.history_empty, 16f, ui.muted))
        val dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        for (session in sessions) {
            val card = ui.card()
            card.addView(
                ui.label(
                    session.receiver.ifBlank { context.getString(R.string.history_receiver) },
                    18f,
                )
            )
            card.addView(ui.label(dates.format(Date(session.startedAt)), 14f, ui.muted))
            card.addView(
                ui.label(
                    when (session.phase) {
                        SessionPhase.STARTING -> R.string.buffering
                        SessionPhase.SHARING -> R.string.sharing
                        SessionPhase.RECOVERING -> R.string.recovering
                        SessionPhase.STOPPED -> R.string.stopped
                        SessionPhase.FAILED -> R.string.failed
                        SessionPhase.INTERRUPTED -> R.string.history_interrupted
                    },
                    16f,
                    if (session.phase.terminal) ui.muted else ui.accent,
                )
            )
            session.endedAt?.let {
                card.addView(
                    ui.label(
                        context.getString(R.string.history_ended, dates.format(Date(it))),
                        14f,
                        ui.muted,
                    )
                )
            }
            if (session.width > 0 && session.height > 0 && session.fps > 0)
                card.addView(
                    ui.label(
                        context.getString(
                            R.string.video_profile,
                            session.width,
                            session.height,
                            session.fps,
                        ),
                        14f,
                        ui.muted,
                    )
                )
            val audioLabel =
                when (session.audio) {
                    SessionAudio.DISABLED -> R.string.audio_disabled
                    SessionAudio.WAITING -> R.string.audio_waiting
                    SessionAudio.UNAVAILABLE -> R.string.audio_unavailable
                    SessionAudio.CAPTURING -> R.string.audio_capturing
                    SessionAudio.SILENT -> R.string.audio_silent
                }
            card.addView(
                ui.label(
                    context.getString(R.string.history_audio, context.getString(audioLabel)),
                    14f,
                    ui.muted,
                )
            )
            rows.addView(card, LayoutParams(-1, -2).apply { bottomMargin = ui.dp(10) })
        }
    }
}
