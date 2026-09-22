package io.github.diegog0477.zombiebox.cast.features.companion.presentation.ui

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.ScrollView
import io.github.diegog0477.zombiebox.cast.R
import io.github.diegog0477.zombiebox.cast.core.ui.PhoneWidgets
import io.github.diegog0477.zombiebox.cast.features.companion.presentation.viewmodel.CompanionViewModel
import io.github.diegog0477.zombiebox.cast.features.discovery.presentation.ui.DiscoveryPanel
import io.github.diegog0477.zombiebox.cast.features.discovery.presentation.viewmodel.DiscoveryViewModel

class PairingDialog(
    private val activity: Activity,
    private val discovery: DiscoveryViewModel,
    private val model: CompanionViewModel,
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
        val screens = ui.column()
        val status = ui.label(R.string.pair_select_screen, 16f, ui.muted)
        var selectedAddress = ""
        val dialog =
            AlertDialog.Builder(activity)
                .setTitle(R.string.pair_phone)
                .setView(ScrollView(activity).apply { addView(form) })
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        fun loadScreens() {
            if (address.text.isBlank() || model.state.busy) return
            selectedAddress = address.text.toString().trim()
            screens.removeAllViews()
            model.discoverTargets(selectedAddress)
        }
        form.addView(ui.label(R.string.pair_instructions, 16f))
        form.addView(
            ui.action(activity.getString(R.string.scan_qr), true) {
                dialog.dismiss()
                scan()
            }
        )
        form.addView(ui.label(R.string.manual_pair, 16f, ui.muted))
        form.addView(address)
        form.addView(
            DiscoveryPanel(activity, discovery) {
                address.setText(it)
                loadScreens()
            }
        )
        val find = ui.action(activity.getString(R.string.pair_find_screens)) { loadScreens() }
        form.addView(find)
        form.addView(status)
        form.addView(screens)
        val observer: (CompanionViewModel.State) -> Unit = { state ->
            if (dialog.isShowing) {
                find.isEnabled = !state.busy
                if (state.busy) status.setText(R.string.pair_searching)
                else if (state.failed) status.setText(R.string.pair_target_failed)
                else if (state.phase == "SELECT_TARGET" && selectedAddress.isNotEmpty()) {
                    status.setText(
                        if (state.nearby.isEmpty()) R.string.pair_no_screens
                        else R.string.pair_select_screen
                    )
                    screens.removeAllViews()
                    val gateway = selectedAddress
                    for (target in state.nearby) screens.addView(
                        ui.action(target.name) {
                            dialog.dismiss()
                            join(gateway, target.id)
                        }
                    )
                }
            }
        }
        model.pairingObserver = observer
        dialog.setOnDismissListener {
            if (model.pairingObserver === observer) model.pairingObserver = null
        }
        dialog.show()
        discovery.refresh()
    }
}
