package io.github.diegog0477.zombiebox.cast.features.companion.data

import android.content.SharedPreferences
import android.os.Build
import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.CompanionRepository
import io.github.diegog0477.zombiebox.cast.features.companion.domain.repository.TrustedTarget
import io.github.diegog0477.zombiebox.shared.GatewayDiscovery
import io.github.diegog0477.zombiebox.shared.companion.*
import org.json.JSONArray
import org.json.JSONObject

class GatewayCompanionRepository(private val prefs: SharedPreferences) : CompanionRepository {
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

    override fun nearbyTargets(address: String) = CompanionWire.targets(address)

    override fun join(address: String, targetId: String, qr: String) =
        CompanionWire.join(
            address,
            "",
            qr,
            (Build.MANUFACTURER + " " + Build.MODEL).take(80),
            targetId = if (qr.isEmpty()) targetId else "",
            clientKey = clientKey(),
        )

    override fun await(attempt: PairingAttempt) = CompanionWire.await(attempt)

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

    private fun save(profile: JSONObject, status: CompanionStatus) {
        profile.put("name", status.grant.targetName)
        val existing =
            records().filter { it.getString("id") != profile.getString("id") }.takeLast(15)
        val values = JSONArray()
        (existing + profile).forEach { values.put(it) }
        check(
            prefs
                .edit()
                .putString("trustedCompanions", values.toString())
                .putString("gateway", profile.getString("gateway"))
                .putString("device", profile.getString("id"))
                .putString("targetName", status.grant.targetName)
                .putString("token", profile.getString("token"))
                .putBoolean("companion", true)
                .commit()
        )
    }

    private fun records(): List<JSONObject> {
        val values =
            try {
                JSONArray(prefs.getString("trustedCompanions", "[]"))
            } catch (_: Exception) {
                return emptyList()
            }
        return (0 until minOf(16, values.length())).map { values.getJSONObject(it) }
    }

    override fun activate(attempt: PairingAttempt) {
        val profile =
            JSONObject()
                .put("gateway", attempt.gateway)
                .put("id", attempt.request.id)
                .put("token", attempt.token)
        save(profile, checked(profile))
    }

    override fun status(): CompanionStatus = checked(current())

    override fun reconnect(): CompanionStatus {
        check(paired)
        try {
            return status()
        } catch (_: Exception) {}
        val original = current()
        for (gateway in GatewayDiscovery().scan().take(4)) {
            if (gateway.address == original.getString("gateway")) continue
            val candidate = JSONObject(original.toString()).put("gateway", gateway.address)
            try {
                val status = checked(candidate)
                save(candidate, status)
                return status
            } catch (_: Exception) {}
        }
        throw IllegalStateException("Trusted gateway unavailable")
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
