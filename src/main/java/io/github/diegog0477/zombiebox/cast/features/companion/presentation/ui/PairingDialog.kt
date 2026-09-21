package io.github.diegog0477.zombiebox.cast.features.companion.presentation.ui

import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.ScrollView
import io.github.diegog0477.zombiebox.cast.R
import io.github.diegog0477.zombiebox.cast.core.ui.PhoneWidgets
import io.github.diegog0477.zombiebox.cast.features.discovery.presentation.ui.DiscoveryPanel
import io.github.diegog0477.zombiebox.cast.features.discovery.presentation.viewmodel.DiscoveryViewModel

class PairingDialog(
    private val activity: Activity,
    private val discovery: DiscoveryViewModel,
    private val scan: () -> Unit,
    private val join: (String, String) -> Unit,
) {
    fun show(base: String) {
        val ui = PhoneWidgets(activity)
        val form = ui.column().apply { setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12)) }
        val address =
            EditText(activity).apply {
                setHint(R.string.gateway)
                setText(base)
                setSingleLine()
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
        val code =
            EditText(activity).apply {
                setHint(R.string.six_digit_code)
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(6))
                isSaveEnabled = false
                setSingleLine()
            }
        val dialog =
            AlertDialog.Builder(activity)
                .setTitle(R.string.pair_phone)
                .setView(ScrollView(activity).apply { addView(form) })
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        form.addView(ui.label(R.string.pair_instructions, 16f))
        form.addView(
            ui.action(activity.getString(R.string.scan_qr), true) {
                dialog.dismiss()
                scan()
            }
        )
        form.addView(ui.label(R.string.manual_pair, 16f, ui.muted))
        form.addView(address)
        form.addView(DiscoveryPanel(activity, discovery) { address.setText(it) })
        form.addView(code)
        form.addView(
            ui.action(activity.getString(R.string.connect)) {
                val value = code.text.toString()
                if (value.matches(Regex("[0-9]{6}")) && address.text.isNotBlank()) {
                    code.setText("")
                    dialog.dismiss()
                    join(address.text.toString(), value)
                } else code.error = activity.getString(R.string.six_digit_code)
            }
        )
        dialog.show()
        discovery.refresh()
    }
}
