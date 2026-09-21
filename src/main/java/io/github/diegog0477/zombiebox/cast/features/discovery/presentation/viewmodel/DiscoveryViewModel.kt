package io.github.diegog0477.zombiebox.cast.features.discovery.presentation.viewmodel

import io.github.diegog0477.zombiebox.shared.DiscoveredGateway

class DiscoveryViewModel(
    private val scan: () -> List<DiscoveredGateway>,
    private val execute: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
) {
    data class State(
        val searching: Boolean = false,
        val failed: Boolean = false,
        val gateways: List<DiscoveredGateway> = emptyList(),
    )

    var state = State()
        private set

    var observer: ((State) -> Unit)? = null
    private var closed = false

    fun refresh() {
        if (closed || state.searching) return
        state = State(searching = true)
        observer?.invoke(state)
        execute {
            val next =
                try {
                    State(gateways = scan())
                } catch (_: Exception) {
                    State(failed = true)
                }
            deliver {
                if (!closed) {
                    state = next
                    observer?.invoke(state)
                }
            }
        }
    }

    fun close() {
        closed = true
        observer = null
    }
}
