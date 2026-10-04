package io.github.diegog0477.zombiebox.cast.features.companion.data

import android.content.SharedPreferences
import android.os.Build
import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.CompanionRepository
import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.PairingGrantUnavailableException
import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.PairingJoinNetworkException
import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.PairingRequestExpiredException
import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.TrustedTarget
import io.github.diegog0477.zombiebox.shared.GatewayDiscovery
import io.github.diegog0477.zombiebox.shared.GatewayFailure
import io.github.diegog0477.zombiebox.shared.companion.*
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

class GatewayCompanionRepository(private val prefs: SharedPreferences) : CompanionRepository {
    private companion object {
        const val PENDING_KEY = "companionPendingAttempt"
        const val GATEWAY_HINT_KEY = "companionGatewayHint"
    }

    override val paired
        get() =
            prefs.getBoolean("companion", false) && !prefs.getString("token", "").isNullOrEmpty()

    private fun clientKey(): String {
        val current = prefs.getString("pairingClientKey", "").orEmpty()
        if (current.matches(Regex("[0-9a-f]{64}"))) return current
        val bytes = ByteArray(32).apply { java.security.SecureRandom().nextBytes(this) }
        val key = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        check(prefs.edit().putString("pairingClientKey", key).commit())
        return key
    }

    override fun nearbyTargets(address: String): List<PairingTarget> {
        val targets = CompanionWire.targets(address)
        rememberGatewayHint(address)
        return targets
    }

    override fun join(address: String, targetId: String, qr: String): PairingAttempt {
        val attempt =
            try {
                CompanionWire.join(
                    address,
                    "",
                    qr,
                    (Build.MANUFACTURER + " " + Build.MODEL).take(80),
                    targetId = if (qr.isEmpty()) targetId else "",
                    clientKey = clientKey(),
                )
            } catch (error: IOException) {
                if (qr.isNotEmpty()) throw PairingJoinNetworkException(error)
                throw error
            }
        storePending(attempt)
        if (address.isNotBlank()) {
            try {
                rememberGatewayHint(address)
            } catch (_: IllegalArgumentException) {
                // The QR address still owns the attempt; a malformed hint cannot discard it.
            }
        }
        return attempt
    }

    override fun await(attempt: PairingAttempt): PairingRequest =
        try {
            CompanionWire.await(attempt)
        } catch (error: GatewayFailure) {
            if (error.status == 410) throw PairingRequestExpiredException(error)
            throw error
        }

    private fun current() =
        JSONObject()
            .put("gateway", prefs.getString("gateway", ""))
            .put("id", prefs.getString("device", ""))
            .put("token", prefs.getString("token", ""))

    private fun transport(profile: JSONObject = current()) =
        CompanionTransport(
            profile.getString("gateway"),
            profile.getString("id"),
            profile.getString("token"),
        )

    private fun checked(profile: JSONObject): CompanionStatus {
        val api = transport(profile)
        return try {
            CompanionWire.status(api)
        } finally {
            api.close()
        }
    }

    private fun save(profile: JSONObject, status: CompanionStatus, clearPending: Boolean = false) {
        profile.put("name", status.grant.targetName)
        val existing =
            records().filter { it.getString("id") != profile.getString("id") }.takeLast(15)
        val values = JSONArray()
        (existing + profile).forEach { values.put(it) }
        val edit =
            prefs
                .edit()
                .putString("trustedCompanions", values.toString())
                .putString("gateway", profile.getString("gateway"))
                .putString("device", profile.getString("id"))
                .putString("targetName", status.grant.targetName)
                .putString("token", profile.getString("token"))
                .putBoolean("companion", true)
        if (clearPending) edit.remove(PENDING_KEY)
        check(edit.commit())
    }

    private fun records(): List<JSONObject> {
        val values =
            try {
                JSONArray(prefs.getString("trustedCompanions", "[]"))
            } catch (_: Exception) {
                return emptyList()
            }
        return (0 until minOf(16, values.length())).mapNotNull { values.optJSONObject(it) }
    }

    private fun storePending(attempt: PairingAttempt) {
        val value =
            JSONObject()
                .put("gateway", CompanionTransport.address(attempt.gateway))
                .put("id", attempt.request.id)
                .put("targetId", attempt.request.targetId)
                .put("name", attempt.request.name)
                .put("comparison", attempt.request.comparison)
                .put("state", attempt.request.state)
                .put("token", attempt.token)
                .put("savedAt", System.currentTimeMillis())
        check(prefs.edit().putString(PENDING_KEY, value.toString()).commit())
    }

    override fun pendingAttempt(): PairingAttempt? {
        val raw = prefs.getString(PENDING_KEY, null) ?: return null
        return try {
            val value = JSONObject(raw)
            value.getLong("savedAt")
            val id = value.getString("id")
            val token = value.getString("token")
            require(id.matches(Regex("[0-9a-f]{32}")))
            require(token.matches(Regex("[0-9a-f]{64}")))
            val state = value.getString("state")
            require(state == "PENDING" || state == "APPROVED")
            PairingAttempt(
                CompanionTransport.address(value.getString("gateway")),
                PairingRequest(
                    id,
                    value.getString("targetId"),
                    value.getString("name"),
                    value.getString("comparison"),
                    state,
                ),
                token,
            )
        } catch (_: Exception) {
            discardPending()
            null
        }
    }

    override fun discardPending() {
        check(prefs.edit().remove(PENDING_KEY).commit())
    }

    private fun rememberGatewayHint(address: String) {
        val normalized = CompanionTransport.address(address)
        prefs.edit().putString(GATEWAY_HINT_KEY, normalized).apply()
    }

    private fun candidateAddresses(original: String): List<String> {
        val candidates = LinkedHashSet<String>()
        fun add(value: String?) {
            if (value.isNullOrBlank() || candidates.size >= 4) return
            try {
                candidates.add(CompanionTransport.address(value))
            } catch (_: Exception) {
                // Ignore stale routing hints. None authorize a pairing.
            }
        }
        add(original)
        add(prefs.getString(GATEWAY_HINT_KEY, null))
        records().takeLast(16).asReversed().forEach { add(it.optString("gateway")) }
        return candidates.toList()
    }

    private class CandidateFailure(val allCandidatesDenied: Boolean, cause: Exception?) :
        IllegalStateException("Trusted gateway unavailable", cause)

    private fun checkedAtCandidates(profile: JSONObject): Pair<JSONObject, CompanionStatus> {
        val failures = mutableListOf<Exception>()
        val tried = LinkedHashSet<String>()
        fun checkAddress(address: String): Pair<JSONObject, CompanionStatus>? {
            if (!tried.add(address)) return null
            val candidate = JSONObject(profile.toString()).put("gateway", address)
            try {
                val status = checked(candidate)
                if (status.grant.id == candidate.getString("id")) return Pair(candidate, status)
            } catch (error: Exception) {
                failures.add(error)
            }
            return null
        }
        for (address in candidateAddresses(profile.getString("gateway"))) {
            checkAddress(address)?.let {
                return it
            }
        }
        try {
            for (gateway in GatewayDiscovery().scan().take(4)) {
                checkAddress(gateway.address)?.let {
                    return it
                }
            }
        } catch (_: Exception) {
            // Discovery supplies only routing candidates; prior/manual URLs remain usable.
        }
        throw CandidateFailure(
            failures.isNotEmpty() && failures.all { it is GatewayFailure && it.status == 403 },
            failures.lastOrNull(),
        )
    }

    override fun activate(attempt: PairingAttempt): CompanionStatus {
        val pending = pendingAttempt()
        check(
            pending != null &&
                pending.request.id == attempt.request.id &&
                pending.token == attempt.token
        )
        val profile =
            JSONObject()
                .put("gateway", attempt.gateway)
                .put("id", attempt.request.id)
                .put("token", attempt.token)
        val (verified, status) =
            try {
                checkedAtCandidates(profile)
            } catch (error: CandidateFailure) {
                if (error.allCandidatesDenied) throw PairingGrantUnavailableException(error)
                throw error
            }
        check(status.grant.targetId == attempt.request.targetId)
        save(verified, status, clearPending = true)
        return status
    }

    override fun status(): CompanionStatus = checked(current())

    override fun reconnect(): CompanionStatus {
        check(paired)
        val (profile, status) = checkedAtCandidates(current())
        if (profile.getString("gateway") != prefs.getString("gateway", "")) save(profile, status)
        return status
    }

    override fun send(action: String, provider: String) {
        val api = transport()
        try {
            CompanionWire.send(api, action, provider)
        } finally {
            api.close()
        }
    }

    override fun sendText(text: String, inputId: String) {
        val api = transport()
        try {
            CompanionWire.sendText(api, text, inputId)
        } finally {
            api.close()
        }
    }

    override fun forget() {
        val profile = current()
        val api = transport(profile)
        try {
            api.request("DELETE", "/v1/companion/session")
        } finally {
            api.close()
        }
        val values = JSONArray()
        records()
            .filter { it.getString("id") != profile.getString("id") }
            .forEach { values.put(it) }
        check(
            prefs
                .edit()
                .putString("trustedCompanions", values.toString())
                .remove("token")
                .remove("device")
                .putBoolean("companion", false)
                .commit()
        )
    }

    override fun targets() =
        records().map { TrustedTarget(it.getString("id"), it.optString("name")) }

    override fun select(id: String) {
        val profile = records().first { it.getString("id") == id }
        save(profile, checked(profile))
    }
}
