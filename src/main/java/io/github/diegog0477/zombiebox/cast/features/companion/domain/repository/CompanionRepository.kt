package io.github.diegog0477.zombiebox.cast.features.companion.domain.repository

import io.github.diegog0477.zombiebox.shared.companion.*

data class TrustedTarget(val id: String, val name: String)

interface CompanionRepository {
    val paired: Boolean

    fun join(address: String, targetId: String, qr: String): PairingAttempt

    fun nearbyTargets(address: String): List<PairingTarget>

    fun await(attempt: PairingAttempt): PairingRequest

    fun activate(attempt: PairingAttempt)

    fun status(): CompanionStatus

    fun reconnect(): CompanionStatus

    fun send(action: String, provider: String)

    fun sendText(text: String, inputId: String)

    fun forget()

    fun targets(): List<TrustedTarget>

    fun select(id: String)
}
