package io.github.diegog0477.zombiebox.cast.features.companion.presentation.viewmodel

import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.CompanionRepository
import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.TrustedTarget
import io.github.diegog0477.zombiebox.shared.companion.*

class CompanionViewModel(
    private val repository: CompanionRepository,
    private val execute: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
) {
    data class State(
        val busy: Boolean = false,
        val failed: Boolean = false,
        val comparison: String = "",
        val phase: String = "",
        val target: CompanionStatus? = null,
        val targets: List<TrustedTarget> = emptyList(),
        val nearby: List<PairingTarget> = emptyList(),
    )

    var state = State(targets = repository.targets())
        private set

    var observer: ((State) -> Unit)? = null
    var pairingObserver: ((State) -> Unit)? = null
    var paired: (() -> Unit)? = null
    private var pending: PairingAttempt? = null
    private var closed = false
    private var deferred: (() -> Unit)? = null

    fun discoverTargets(address: String) =
        work(defer = true) {
            state.copy(nearby = repository.nearbyTargets(address), phase = "SELECT_TARGET")
        }

    fun join(address: String, targetId: String, qr: String) =
        work(defer = true) {
            val attempt = repository.join(address, targetId, qr)
            pending = attempt
            if (attempt.request.state == "APPROVED") {
                repository.activate(attempt)
                pending = null
                state.copy(
                    comparison = "",
                    phase = "APPROVED",
                    target = repository.status(),
                    targets = repository.targets(),
                    nearby = emptyList(),
                )
            } else
                state.copy(
                    comparison = attempt.request.comparison,
                    phase = attempt.request.state,
                    target = null,
                    nearby = emptyList(),
                )
        }

    fun refresh(reconnect: Boolean = false) = work {
        val attempt = pending
        if (attempt != null) {
            val request = repository.await(attempt)
            if (request.state == "APPROVED") {
                repository.activate(attempt)
                pending = null
                state.copy(
                    comparison = "",
                    phase = "APPROVED",
                    target = repository.status(),
                    targets = repository.targets(),
                )
            } else {
                if (request.state == "DENIED") pending = null
                state.copy(phase = request.state)
            }
        } else if (repository.paired) {
            state.copy(
                target = if (reconnect) repository.reconnect() else repository.status(),
                phase = "CONNECTED",
                targets = repository.targets(),
            )
        } else state.copy(target = null, targets = repository.targets())
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

    private fun work(defer: Boolean = false, task: () -> State) {
        if (closed) return
        if (state.busy) {
            if (defer) deferred = { work(true, task) }
            return
        }
        state = state.copy(busy = true, failed = false)
        observer?.invoke(state)
        pairingObserver?.invoke(state)
        execute {
            val next =
                try {
                    task().copy(busy = false, failed = false)
                } catch (_: Exception) {
                    state.copy(busy = false, failed = true)
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
