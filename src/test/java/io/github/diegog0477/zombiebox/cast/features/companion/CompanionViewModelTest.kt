package io.github.diegog0477.zombiebox.cast.features.companion

import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.CompanionRepository
import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.TrustedTarget
import io.github.diegog0477.zombiebox.cast.features.companion.presentation.viewmodel.CompanionViewModel
import io.github.diegog0477.zombiebox.shared.companion.*
import org.junit.Assert.*
import org.junit.Test

class CompanionViewModelTest {
    private class Fake : CompanionRepository {
        override var paired = false
        var phase = "PENDING"
        var online = false
        var sent = 0
        var selected = ""
        var joinPhase = "PENDING"
        var inputId = ""

        override fun join(address: String, targetId: String, qr: String) =
            PairingAttempt(
                address,
                PairingRequest("grant", "tv", "Phone", "123456", joinPhase),
                "secret",
            )

        override fun nearbyTargets(address: String) = listOf(PairingTarget("tv", "Living room"))

        override fun await(attempt: PairingAttempt) = attempt.request.copy(state = phase)

        override fun activate(attempt: PairingAttempt) {
            check(phase == "APPROVED")
            paired = true
        }

        override fun status() =
            CompanionStatus(
                CompanionGrant("grant", "tv", "TV", "Phone"),
                online,
                false,
                "",
                inputId,
            )

        override fun reconnect() = status()

        override fun send(action: String, provider: String) {
            sent++
        }

        override fun sendText(text: String, inputId: String) {
            sent++
        }

        override fun forget() {
            paired = false
        }

        override fun targets() = if (paired) listOf(TrustedTarget("grant", "TV")) else emptyList()

        override fun select(id: String) {
            selected = id
        }
    }

    @Test
    fun qrApprovalActivatesImmediatelyWithoutPendingScreen() {
        val repo =
            Fake().apply {
                joinPhase = "APPROVED"
                phase = "APPROVED"
            }
        val model = CompanionViewModel(repo, { it() }, { it() })
        model.join("", "", "scanned QR")
        assertTrue(repo.paired)
        assertEquals("APPROVED", model.state.phase)
        assertEquals("", model.state.comparison)
    }

    @Test
    fun listingTargetsDoesNotRequestConsentUntilOneIsSelected() {
        val model = CompanionViewModel(Fake(), { it() }, { it() })
        model.discoverTargets("http://gateway")
        assertEquals("SELECT_TARGET", model.state.phase)
        assertEquals("Living room", model.state.nearby.first().name)
        assertEquals("", model.state.comparison)
    }

    @Test
    fun typingRequiresAnAvailableTargetField() {
        val repo =
            Fake().apply {
                paired = true
                online = true
            }
        val model = CompanionViewModel(repo, { it() }, { it() })
        model.refresh()
        model.sendText("Hello")
        assertEquals(0, repo.sent)
        repo.inputId = "current-field"
        model.refresh()
        model.sendText("Hello")
        assertEquals(1, repo.sent)
    }

    @Test
    fun pendingAndDeniedRequestsCannotActivateOrSend() {
        val repo = Fake()
        val model = CompanionViewModel(repo, { it() }, { it() })
        model.join("http://gateway", "123456", "")
        assertEquals("123456", model.state.comparison)
        model.send("OK")
        model.refresh()
        assertFalse(repo.paired)
        assertEquals(0, repo.sent)
        repo.phase = "DENIED"
        model.refresh()
        assertEquals("DENIED", model.state.phase)
        assertFalse(repo.paired)
    }

    @Test
    fun approvalAndTargetReadinessAreSeparateFromCapture() {
        val repo = Fake()
        val model = CompanionViewModel(repo, { it() }, { it() })
        model.join("http://gateway", "123456", "")
        repo.phase = "APPROVED"
        model.refresh()
        assertTrue(repo.paired)
        assertFalse(model.state.target!!.castAvailable)
        model.send("OK")
        assertEquals(0, repo.sent)
        repo.online = true
        model.refresh()
        model.send("PROVIDER", "youtube")
        assertEquals(1, repo.sent)
        model.forget()
        assertNull(model.state.target)
        assertFalse(repo.paired)
    }

    @Test
    fun closedScreenIgnoresLateDiscoveryAndDoesNotNotifyActivity() {
        val repo = Fake().apply { paired = true }
        val pending = mutableListOf<() -> Unit>()
        val model = CompanionViewModel(repo, { pending.add(it) }, { it() })
        model.refresh(true)
        model.close()
        model.observer = { fail("late screen callback") }
        model.paired = { fail("late pairing callback") }
        pending.removeAt(0)()
        assertNull(model.state.target)
    }

    @Test
    fun scanResultIsNotLostWhileStatusRequestFinishes() {
        val pending = mutableListOf<() -> Unit>()
        val model = CompanionViewModel(Fake(), { pending.add(it) }, { it() })
        model.refresh()
        model.join("http://gateway", "123456", "")
        pending.removeAt(0)()
        assertEquals(1, pending.size)
        pending.removeAt(0)()
        assertEquals("PENDING", model.state.phase)
        assertEquals("123456", model.state.comparison)
    }
}
