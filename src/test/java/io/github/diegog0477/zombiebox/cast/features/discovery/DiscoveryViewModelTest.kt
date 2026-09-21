package io.github.diegog0477.zombiebox.cast.features.discovery

import io.github.diegog0477.zombiebox.cast.features.discovery.presentation.viewmodel.DiscoveryViewModel
import io.github.diegog0477.zombiebox.shared.DiscoveredGateway
import org.junit.Assert.*
import org.junit.Test

class DiscoveryViewModelTest {
    @Test
    fun duplicateRefreshAndLateDeliveryAreSuppressed() {
        val work = mutableListOf<() -> Unit>()
        val deliveries = mutableListOf<() -> Unit>()
        val model =
            DiscoveryViewModel(
                { listOf(DiscoveredGateway("http://192.168.1.7:8090")) },
                { work.add(it) },
                { deliveries.add(it) },
            )
        var updates = 0
        model.observer = { updates++ }
        model.refresh()
        model.refresh()
        assertEquals(1, work.size)
        work.single()()
        model.close()
        deliveries.single()()
        assertEquals(1, updates)
        assertTrue(model.state.gateways.isEmpty())
    }

    @Test
    fun failedScanCanBeRetriedWithoutPairingSideEffects() {
        var fail = true
        val model =
            DiscoveryViewModel(
                { if (fail) throw IllegalStateException("offline") else emptyList() },
                { it() },
                { it() },
            )
        model.refresh()
        assertTrue(model.state.failed)
        assertFalse(model.state.searching)
        fail = false
        model.refresh()
        assertFalse(model.state.failed)
        assertTrue(model.state.gateways.isEmpty())
    }
}
