package io.github.diegog0477.zombiebox.cast

import io.github.diegog0477.zombiebox.cast.features.dial.data.DialDocuments
import io.github.diegog0477.zombiebox.cast.features.dial.domain.model.DialDevice
import io.github.diegog0477.zombiebox.cast.features.dial.domain.repository.DialRepository
import io.github.diegog0477.zombiebox.cast.features.dial.presentation.viewmodel.DialViewModel
import org.junit.Assert.*
import org.junit.Test

class DialViewModelTest {
    @Test
    fun discoveryDoesNotLaunchAndUnknownDeviceCannotLaunch() {
        val target = DialDevice("one", "TV", "http://192.168.1.2/apps/ZombieBox")
        var launches = 0
        val r =
            object : DialRepository {
                override fun discover() = listOf(target)

                override fun launch(device: DialDevice): Boolean {
                    launches++
                    return false
                }

                override fun close() {}
            }
        val vm = DialViewModel(r, { it() }, { it() })
        vm.refresh()
        assertEquals(0, launches)
        vm.launch(target.copy(id = "other"))
        assertEquals(0, launches)
        vm.launch(target)
        assertEquals("FAILED", vm.state.result)
        assertEquals(1, launches)
    }

    @Test
    fun advertisedEndpointsCannotEscapePeerOrCarryCredentials() {
        for (url in
            listOf(
                "http://127.0.0.1/admin",
                "http://user:pass@192.168.1.2/apps",
                "file:///etc/passwd",
                "http://192.168.1.2/apps#fragment",
            )) {
            try {
                DialDocuments.endpoint(url, "192.168.1.2")
                fail(url)
            } catch (_: IllegalArgumentException) {}
        }
        assertEquals(
            "http://192.168.1.2:8009/apps",
            DialDocuments.endpoint("http://192.168.1.2:8009/apps", "192.168.1.2"),
        )
    }

    @Test
    fun parseNamespacedStateAndRejectEntitiesAndAmbiguity() {
        assertEquals(
            "running",
            DialDocuments.field(
                "<service xmlns=\"urn:dial-multiscreen-org:schemas:dial\"><state>running</state></service>"
                    .toByteArray(),
                "state",
            ),
        )
        for (xml in
            listOf(
                "<!DOCTYPE a [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><a>&e;</a>",
                "<a><state>running</state><state>stopped</state></a>",
            )) {
            try {
                DialDocuments.field(xml.toByteArray(), "state")
                fail("accepted invalid XML")
            } catch (_: IllegalArgumentException) {}
        }
    }
}
