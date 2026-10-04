package io.github.diegog0477.zombiebox.cast.features.companion

import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.CompanionRepository
import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.PairingGrantUnavailableException
import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.PairingJoinNetworkException
import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.PairingRequestExpiredException
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
        var pending: PairingAttempt? = null
        var statusExpired = false
        var activationFails = false
        var grantValid = false
        var awaitFailure: Exception? = null
        var activationFailure: Exception? = null
        var joinFailure: Exception? = null

        override fun join(address: String, targetId: String, qr: String): PairingAttempt {
            joinFailure?.let { throw it }
            val attempt =
                PairingAttempt(
                    address,
                    PairingRequest("grant", "tv", "Phone", "123456", joinPhase),
                    "secret",
                )
            pending = attempt
            return attempt
        }

        override fun nearbyTargets(address: String) = listOf(PairingTarget("tv", "Living room"))

        override fun await(attempt: PairingAttempt): PairingRequest {
            awaitFailure?.let { throw it }
            if (statusExpired) throw IllegalStateException("request expired")
            return attempt.request.copy(state = phase)
        }

        override fun pendingAttempt() = pending

        override fun discardPending() {
            pending = null
        }

        override fun activate(attempt: PairingAttempt): CompanionStatus {
            activationFailure?.let { throw it }
            if (activationFails) throw IllegalStateException("proof unavailable")
            if (!grantValid && phase != "APPROVED")
                throw PairingGrantUnavailableException(IllegalStateException("grant missing"))
            paired = true
            pending = null
            return status()
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
            pending = null
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
    fun approvedAttemptSurvivesViewModelRecreationWithoutBecomingActive() {
        val repo = Fake()
        CompanionViewModel(repo, { it() }, { it() }).join("http://gateway", "tv", "")
        assertNotNull(repo.pending)
        assertFalse(repo.paired)

        repo.phase = "APPROVED"
        val resumed = CompanionViewModel(repo, { it() }, { it() })
        assertEquals("PENDING", resumed.state.phase)
        resumed.refresh()
        assertTrue(repo.paired)
        assertNull(repo.pending)
        assertEquals("APPROVED", resumed.state.phase)
    }

    @Test
    fun expiredRequestWithMissingGrantIsTerminal() {
        val repo =
            Fake().apply {
                statusExpired = true
                awaitFailure =
                    PairingRequestExpiredException(IllegalStateException("request expired"))
            }
        val model = CompanionViewModel(repo, { it() }, { it() })
        model.join("http://gateway", "tv", "")
        model.refresh()
        assertFalse(repo.paired)
        assertNull(repo.pending)
        assertEquals("EXPIRED", model.state.phase)
        assertEquals(CompanionViewModel.Failure.EXPIRED, model.state.failure)
    }

    @Test
    fun expiredStatusStillActivatesAValidGrant() {
        val repo =
            Fake().apply {
                statusExpired = true
                awaitFailure =
                    PairingRequestExpiredException(IllegalStateException("request expired"))
                grantValid = true
            }
        val model = CompanionViewModel(repo, { it() }, { it() })
        model.join("http://gateway", "tv", "")
        model.refresh()
        assertTrue(repo.paired)
        assertNull(repo.pending)
        assertEquals("APPROVED", model.state.phase)
    }

    @Test
    fun requestTimeoutDoesNotAbandonPendingAttempt() {
        val repo = Fake().apply { awaitFailure = java.net.SocketTimeoutException("timeout") }
        val model = CompanionViewModel(repo, { it() }, { it() })
        model.join("http://gateway", "tv", "")
        model.refresh()
        assertFalse(repo.paired)
        assertTrue(model.state.failed)
        assertNotNull(repo.pending)
    }

    @Test
    fun proofTransportFailureDoesNotAbandonExpiredPendingAttempt() {
        val repo =
            Fake().apply {
                awaitFailure =
                    PairingRequestExpiredException(IllegalStateException("request expired"))
                activationFailure = java.net.SocketTimeoutException("proof timeout")
            }
        val model = CompanionViewModel(repo, { it() }, { it() })
        model.join("http://gateway", "tv", "")
        model.refresh()
        assertFalse(repo.paired)
        assertTrue(model.state.failed)
        assertNotNull(repo.pending)
    }

    @Test
    fun qrNetworkFailureSurvivesAutomaticRefreshUntilRetry() {
        val repo =
            Fake().apply {
                joinFailure =
                    PairingJoinNetworkException(java.net.SocketTimeoutException("timeout"))
            }
        val model = CompanionViewModel(repo, { it() }, { it() })
        model.join("", "", "scanned QR")
        assertEquals(CompanionViewModel.Failure.QR_NETWORK, model.state.failure)

        model.refresh()
        assertEquals(CompanionViewModel.Failure.QR_NETWORK, model.state.failure)
        assertTrue(model.state.failed)

        model.retry()
        assertNull(model.state.failure)
        assertFalse(model.state.failed)
    }

    @Test
    fun approvedQrRemainsInactiveUntilStatusProofSucceeds() {
        val repo =
            Fake().apply {
                joinPhase = "APPROVED"
                phase = "APPROVED"
                activationFails = true
            }
        val model = CompanionViewModel(repo, { it() }, { it() })
        model.join("", "", "scanned QR")
        assertTrue(model.state.failed)
        assertFalse(repo.paired)
        assertNotNull(repo.pending)

        repo.activationFails = false
        CompanionViewModel(repo, { it() }, { it() }).refresh()
        assertTrue(repo.paired)
        assertNull(repo.pending)
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
