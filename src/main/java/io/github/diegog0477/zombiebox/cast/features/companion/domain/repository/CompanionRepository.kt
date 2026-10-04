package io.github.diegog0477.zombiebox.cast.features.companion.domain.repository

import io.github.diegog0477.zombiebox.shared.companion.*

data class TrustedTarget(val id: String, val name: String)

class PairingJoinNetworkException(cause: Exception) :
    Exception("Could not reach the gateway from the pairing QR", cause)

class PairingRequestExpiredException(cause: Exception) :
    Exception("The pairing request expired", cause)

class PairingGrantUnavailableException(cause: Exception) :
    Exception("The approved pairing grant is unavailable", cause)

interface CompanionRepository {
    val paired: Boolean

    fun join(address: String, targetId: String, qr: String): PairingAttempt

    fun nearbyTargets(address: String): List<PairingTarget>

    fun await(attempt: PairingAttempt): PairingRequest

    /** A local recovery hint only. It never grants access until activate verifies the gateway. */
    fun pendingAttempt(): PairingAttempt?

    fun discardPending()

    /** Return only a gateway-verified status; persist the grant in the same operation. */
    fun activate(attempt: PairingAttempt): CompanionStatus

    fun status(): CompanionStatus

    fun reconnect(): CompanionStatus

    fun send(action: String, provider: String)

    fun sendText(text: String, inputId: String)

    fun forget()

    fun targets(): List<TrustedTarget>

    fun select(id: String)
}
