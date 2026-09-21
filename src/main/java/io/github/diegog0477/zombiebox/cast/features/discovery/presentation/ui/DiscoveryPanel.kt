package io.github.diegog0477.zombiebox.cast.features.discovery.presentation.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.github.diegog0477.zombiebox.cast.R
import io.github.diegog0477.zombiebox.cast.features.discovery.presentation.viewmodel.DiscoveryViewModel

/** Gateway hints only. Choosing a row edits the URL; it never sends a pairing token. */
@SuppressLint("ViewConstructor") // Constructed by composition roots, never inflated from XML.
class DiscoveryPanel(
    context: Context,
    private val model: DiscoveryViewModel,
    choose: (String) -> Unit,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        val refresh =
            Button(context).apply {
                setText(R.string.discover_gateways)
                setOnClickListener { model.refresh() }
            }
        val status = TextView(context).apply { setTextColor(Color.LTGRAY) }
        val results = LinearLayout(context).apply { orientation = VERTICAL }
        addView(refresh)
        addView(status)
        addView(results)
        model.observer = { state ->
            refresh.isEnabled = !state.searching
            status.setText(
                when {
                    state.searching -> R.string.discovery_searching
                    state.failed -> R.string.discovery_failed
                    state.gateways.isEmpty() -> R.string.discovery_empty
                    else -> R.string.discovery_choose
                }
            )
            results.removeAllViews()
            for (gateway in state.gateways) results.addView(
                Button(context).apply {
                    text = gateway.address
                    setOnClickListener { choose(gateway.address) }
                }
            )
        }
    }

    override fun onDetachedFromWindow() {
        model.close()
        super.onDetachedFromWindow()
    }
}
