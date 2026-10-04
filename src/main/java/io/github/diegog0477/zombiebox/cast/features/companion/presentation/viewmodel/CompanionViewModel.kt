package io.github.diegog0477.zombiebox.cast.features.companion.presentation.viewmodel

import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.CompanionRepository
import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.PairingGrantUnavailableException
import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.PairingJoinNetworkException
import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.PairingRequestExpiredException
import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.TrustedTarget
import io.github.diegog0477.zombiebox.shared.companion.*

class CompanionViewModel(
    private val repository: CompanionRepository,
    private val execute: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
) {
    enum class Failure {
        GENERAL,
        QR_NETWORK,
        EXPIRED,
    }

    data class State(
        val busy: Boolean = false,
        val failed: Boolean = false,
        val failure: Failure? = null,
        val comparison: String = "",
        val phase: String = "",
        val target: CompanionStatus? = null,
        val targets: List<TrustedTarget> = emptyList(),
        val nearby: List<PairingTarget> = emptyList(),
    )

    private var pending: PairingAttempt? = repository.pendingAttempt()

    var state =
        State(
            targets = repository.targets(),
            comparison = pending?.request?.comparison.orEmpty(),
            phase = pending?.request?.state.orEmpty(),
        )
        private set

    var observer: ((State) -> Unit)? = null
    var pairingObserver: ((State) -> Unit)? = null
    var paired: (() -> Unit)? = null
    private var closed = false
    private var deferred: (() -> Unit)? = null

    fun discoverTargets(address: String) =
        work(defer = true) {
            state.copy(nearby = repository.nearbyTargets(address), phase = "SELECT_TARGET")
        }

    fun join(address: String, targetId: String, qr: String) =
        work(
            defer = true,
            failureFor = {
                if (it is PairingJoinNetworkException) Failure.QR_NETWORK else Failure.GENERAL
            },
        ) {
            val attempt = repository.join(address, targetId, qr)
            pending = attempt
            if (attempt.request.state == "APPROVED") {
                complete(attempt)
            } else
                state.copy(
                    failed = false,
                    failure = null,
                    comparison = attempt.request.comparison,
                    phase = attempt.request.state,
                    target = null,
                    nearby = emptyList(),
                )
        }

    fun refresh(reconnect: Boolean = false) = refreshState(reconnect, retry = false)

    fun retry(reconnect: Boolean = false) = refreshState(reconnect, retry = true)

    private fun refreshState(reconnect: Boolean, retry: Boolean) =
        work(clearFailure = retry, preserveFailure = true) {
            val attempt = repository.pendingAttempt()
            pending = attempt
            if (attempt != null) {
                val request =
                    try {
                        repository.await(attempt)
                    } catch (statusError: Exception) {
                        try {
                            return@work complete(attempt)
                        } catch (proofError: Exception) {
                            if (
                                statusError is PairingRequestExpiredException &&
                                    proofError is PairingGrantUnavailableException
                            ) {
                                repository.discardPending()
                                pending = null
                                return@work state.copy(
                                    comparison = "",
                                    phase = "EXPIRED",
                                    target = null,
                                    nearby = emptyList(),
                                    failed = true,
                                    failure = Failure.EXPIRED,
                                )
                            }
                            if (proofError is PairingGrantUnavailableException) throw statusError
                            throw proofError
                        }
                    }
                if (request.state == "APPROVED") {
                    complete(attempt)
                } else {
                    if (request.state == "DENIED") {
                        repository.discardPending()
                        pending = null
                    }
                    state.copy(
                        comparison = request.comparison,
                        phase = request.state,
                        failed = false,
                        failure = null,
                    )
                }
            } else if (repository.paired) {
                state.copy(
                    target = if (reconnect) repository.reconnect() else repository.status(),
                    phase = "CONNECTED",
                    comparison = "",
                    targets = repository.targets(),
                    failed = false,
                    failure = null,
                )
            } else
                state.copy(
                    target = null,
                    phase = "",
                    comparison = "",
                    targets = repository.targets(),
                    failed = false,
                    failure = null,
                )
        }

    fun send(action: String, provider: String = "") {
        if (state.target?.remoteOnline != true) return
        work {
            repository.send(action, provider)
            state.copy(phase = "SENT")
        }
    }

    fun sendText(text: String) {
        val target = state.target ?: return
        if (!target.remoteOnline || target.textInputId.isEmpty()) return
        work(defer = true) {
            repository.sendText(text, target.textInputId)
            state.copy(phase = "SENT")
        }
    }

    fun select(id: String) =
        work(defer = true) {
            repository.select(id)
            state.copy(
                target = repository.status(),
                phase = "APPROVED",
                targets = repository.targets(),
            )
        }

    fun forget() =
        work(defer = true) {
            repository.forget()
            pending = null
            State(targets = repository.targets(), phase = "FORGOTTEN")
        }

    private fun complete(attempt: PairingAttempt): State {
        val target = repository.activate(attempt)
        pending = null
        return state.copy(
            failed = false,
            failure = null,
            comparison = "",
            phase = "APPROVED",
            target = target,
            targets = repository.targets(),
            nearby = emptyList(),
        )
    }

    private fun work(
        defer: Boolean = false,
        clearFailure: Boolean = true,
        preserveFailure: Boolean = false,
        failureFor: (Exception) -> Failure = { Failure.GENERAL },
        task: () -> State,
    ) {
        if (closed) return
        if (state.busy) {
            if (defer) deferred = { work(true, clearFailure, preserveFailure, failureFor, task) }
            return
        }
        val retainedFailure = if (preserveFailure && !clearFailure) state.failure else null
        state = state.copy(busy = true, failed = retainedFailure != null, failure = retainedFailure)
        observer?.invoke(state)
        pairingObserver?.invoke(state)
        execute {
            val next =
                try {
                    val value = task()
                    val failure =
                        value.failure
                            ?: if (
                                value.phase in
                                    listOf(
                                        "APPROVED",
                                        "CONNECTED",
                                        "DENIED",
                                        "EXPIRED",
                                        "FORGOTTEN",
                                    )
                            )
                                null
                            else retainedFailure
                    value.copy(busy = false, failed = failure != null, failure = failure)
                } catch (error: Exception) {
                    val failure = retainedFailure ?: failureFor(error)
                    state.copy(busy = false, failed = true, failure = failure)
                }
            deliver {
                if (!closed) {
                    state = next
                    observer?.invoke(state)
                    pairingObserver?.invoke(state)
                    if (next.phase in listOf("APPROVED", "FORGOTTEN")) paired?.invoke()
                    val nextAction = deferred
                    deferred = null
                    nextAction?.invoke()
                }
            }
        }
    }

    fun close() {
        closed = true
        deferred = null
        observer = null
        pairingObserver = null
        paired = null
    }
}
