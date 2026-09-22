package io.github.diegog0477.zombiebox.cast.features.companion.presentation.ui

import android.content.Context
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import io.github.diegog0477.zombiebox.cast.R
import io.github.diegog0477.zombiebox.cast.core.ui.PhoneWidgets

class RemotePanel(context: Context, send: (String, String) -> Unit, sendText: (String) -> Unit) :
    LinearLayout(context) {
    private val ui = PhoneWidgets(context)
    private val buttons = mutableListOf<View>()
    private val input =
        EditText(context).apply {
            setHint(R.string.remote_text_hint)
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(512))
            isSaveEnabled = false
        }
    private val paste =
        ui.action(context.getString(R.string.remote_text_send), true) {
            val value = input.text.toString()
            if (value.isNotEmpty() && value.none { it.isISOControl() }) sendText(value)
        }
    private val textStatus = ui.label(R.string.remote_text_focus, 14f, ui.muted)

    init {
        orientation = VERTICAL
        addView(ui.label(R.string.remote_title, 24f))
        addView(ui.label(R.string.remote_detail, 14f, ui.muted))
        val pad =
            ui.column().apply {
                background = ui.shape(ui.surface)
                setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
            }
        val directions =
            listOf(
                listOf(
                    Triple(0, 0, ""),
                    Triple(R.drawable.ic_remote_up, R.string.remote_up, "UP"),
                    Triple(0, 0, ""),
                ),
                listOf(
                    Triple(R.drawable.ic_remote_left, R.string.remote_left, "LEFT"),
                    Triple(R.drawable.ic_remote_confirm, R.string.remote_ok, "OK"),
                    Triple(R.drawable.ic_remote_right, R.string.remote_right, "RIGHT"),
                ),
                listOf(
                    Triple(0, 0, ""),
                    Triple(R.drawable.ic_remote_down, R.string.remote_down, "DOWN"),
                    Triple(0, 0, ""),
                ),
            )
        for (items in directions) {
            val row = ui.row()
            for ((icon, label, action) in items) {
                val control =
                    if (action.isEmpty()) View(context)
                    else icon(icon, label, action == "OK") { send(action, "") }
                row.addView(
                    control,
                    LayoutParams(0, ui.dp(70), 1f).apply {
                        setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
                    },
                )
            }
            pad.addView(row)
        }
        addView(
            pad,
            LayoutParams(ui.dp(254), -2).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = ui.dp(12)
                bottomMargin = ui.dp(12)
            },
        )
        fun row(vararg actions: Triple<Int, Int, String>) {
            val line = ui.row()
            for ((image, label, action) in actions) line.addView(
                icon(image, label) { send(action, "") },
                LayoutParams(0, ui.dp(54), 1f).apply {
                    setMargins(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
                },
            )
            addView(line)
        }
        row(
            Triple(R.drawable.ic_back, R.string.remote_back, "BACK"),
            Triple(R.drawable.ic_home, R.string.nav_home, "HOME"),
        )
        row(
            Triple(R.drawable.ic_remote_rewind, R.string.remote_rewind, "SEEK_BACK"),
            Triple(R.drawable.ic_remote_play_pause, R.string.remote_play, "PLAY_PAUSE"),
            Triple(R.drawable.ic_remote_forward, R.string.remote_forward, "SEEK_FORWARD"),
        )
        row(
            Triple(R.drawable.ic_remote_stop, R.string.remote_stop, "STOP"),
            Triple(R.drawable.ic_remote_next, R.string.remote_next, "NEXT"),
        )
        row(
            Triple(R.drawable.ic_remote_volume_down, R.string.remote_quieter, "VOLUME_DOWN"),
            Triple(R.drawable.ic_remote_volume_up, R.string.remote_louder, "VOLUME_UP"),
        )
        val typing =
            ui.column().apply {
                visibility = GONE
                addView(textStatus)
                addView(input)
                addView(paste)
            }
        val keyboard =
            ui.iconAction(R.drawable.ic_remote_keyboard, R.string.remote_keyboard) {
                typing.visibility = if (typing.visibility == GONE) VISIBLE else GONE
                if (typing.visibility == VISIBLE) input.requestFocus()
            }
        addView(keyboard, LayoutParams(-1, ui.dp(52)))
        addView(typing)
        addView(ui.label(R.string.remote_apps, 20f))
        val services =
            listOf(
                Triple(R.drawable.ic_provider_youtube, R.string.provider_youtube, "youtube"),
                Triple(R.drawable.ic_provider_plex, R.string.provider_plex, "plex"),
                Triple(R.drawable.ic_provider_stremio, R.string.provider_stremio, "stremio"),
                Triple(R.drawable.ic_provider_jellyfin, R.string.provider_jellyfin, "jellyfin"),
                Triple(R.drawable.ic_provider_iptv, R.string.provider_iptv, "iptv"),
                Triple(R.drawable.ic_provider_spotify, R.string.provider_spotify, "spotify"),
                Triple(R.drawable.ic_provider_airplay, R.string.provider_airplay, "airplay"),
            )
        for (group in services.chunked(3)) {
            val line = ui.row()
            for ((image, label, id) in group) {
                val mark = icon(image, label) { send("PROVIDER", id) }
                mark.setImageResource(
                    image
                ) // Keep the service mark colors, not monochrome control tint.
                line.addView(
                    mark,
                    LayoutParams(0, ui.dp(64), 1f).apply {
                        setMargins(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
                    },
                )
            }
            repeat(3 - group.size) { line.addView(View(context), LayoutParams(0, 1, 1f)) }
            addView(line)
        }
        available(false)
    }

    private fun icon(
        image: Int,
        label: Int,
        primary: Boolean = false,
        click: () -> Unit,
    ): ImageButton =
        ui.iconAction(image, label, click).apply {
            background = ui.buttonBackground(primary)
            if (primary) imageTintList = android.content.res.ColorStateList.valueOf(ui.background)
            buttons.add(this)
            setOnLongClickListener {
                android.widget.Toast.makeText(context, label, android.widget.Toast.LENGTH_SHORT)
                    .show()
                true
            }
        }

    fun available(value: Boolean, textReady: Boolean = false) {
        buttons.forEach {
            it.isEnabled = value
            it.alpha = if (value) 1f else 0.45f
        }
        input.isEnabled = value && textReady
        paste.isEnabled = value && textReady
        textStatus.setText(
            if (value && textReady) R.string.remote_text_ready else R.string.remote_text_focus
        )
    }
}
