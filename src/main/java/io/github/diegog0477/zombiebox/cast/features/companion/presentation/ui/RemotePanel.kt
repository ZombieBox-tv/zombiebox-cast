package io.github.diegog0477.zombiebox.cast.features.companion.presentation.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import io.github.diegog0477.zombiebox.cast.R
import io.github.diegog0477.zombiebox.cast.core.ui.PhoneWidgets

class RemotePanel(context: Context, send: (String, String) -> Unit) : LinearLayout(context) {
    private val widgets = PhoneWidgets(context)
    private val buttons = mutableListOf<View>()

    init {
        orientation = VERTICAL
        addView(widgets.label(R.string.remote_title, 24f))
        addView(widgets.label(R.string.remote_detail, 14f, widgets.muted))
        val pad = widgets.card()
        for (row in
            listOf(listOf("", "UP", ""), listOf("LEFT", "OK", "RIGHT"), listOf("", "DOWN", ""))) {
            val line = widgets.row()
            for (command in row) {
                val label =
                    when (command) {
                        "UP" -> R.string.remote_up
                        "LEFT" -> R.string.remote_left
                        "RIGHT" -> R.string.remote_right
                        "DOWN" -> R.string.remote_down
                        else -> R.string.remote_ok
                    }
                val button =
                    widgets.action(
                        if (command.isEmpty()) "" else context.getString(label),
                        command == "OK",
                    ) {
                        send(command, "")
                    }
                if (command.isEmpty()) button.visibility = INVISIBLE else buttons.add(button)
                line.addView(
                    button,
                    LayoutParams(0, widgets.dp(64), 1f).apply {
                        setMargins(widgets.dp(3), widgets.dp(3), widgets.dp(3), widgets.dp(3))
                    },
                )
            }
            pad.addView(line)
        }
        addView(pad)
        fun actions(items: List<Pair<Int, String>>) {
            val row = widgets.row()
            for ((label, command) in items) {
                val button = widgets.action(context.getString(label)) { send(command, "") }
                buttons.add(button)
                row.addView(
                    button,
                    LayoutParams(0, -2, 1f).apply {
                        setMargins(widgets.dp(3), widgets.dp(4), widgets.dp(3), widgets.dp(4))
                    },
                )
            }
            addView(row)
        }
        actions(listOf(R.string.remote_back to "BACK", R.string.nav_home to "HOME"))
        actions(
            listOf(
                R.string.remote_rewind to "SEEK_BACK",
                R.string.remote_play to "PLAY_PAUSE",
                R.string.remote_forward to "SEEK_FORWARD",
            )
        )
        actions(listOf(R.string.remote_stop to "STOP", R.string.remote_next to "NEXT"))
        actions(
            listOf(R.string.remote_quieter to "VOLUME_DOWN", R.string.remote_louder to "VOLUME_UP")
        )
        addView(widgets.label(R.string.remote_apps, 20f))
        for ((label, provider) in
            listOf(
                R.string.provider_youtube to "youtube",
                R.string.provider_plex to "plex",
                R.string.provider_stremio to "stremio",
                R.string.provider_jellyfin to "jellyfin",
                R.string.provider_iptv to "iptv",
                R.string.provider_spotify to "spotify",
                R.string.provider_airplay to "airplay",
            )) {
            val button = widgets.action(context.getString(label)) { send("PROVIDER", provider) }
            buttons.add(button)
            addView(button, LayoutParams(-1, -2).apply { bottomMargin = widgets.dp(6) })
        }
        available(false)
    }

    fun available(value: Boolean) {
        buttons.forEach {
            it.isEnabled = value
            it.alpha = if (value) 1f else 0.45f
        }
    }
}
