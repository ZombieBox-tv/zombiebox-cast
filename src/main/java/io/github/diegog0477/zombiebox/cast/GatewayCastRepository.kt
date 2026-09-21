package io.github.diegog0477.zombiebox.cast

import android.content.SharedPreferences
import android.os.Build
import io.github.diegog0477.zombiebox.shared.GatewayApi
import java.net.URI
import java.util.UUID
import org.json.JSONObject

class GatewayCastRepository(private val prefs: SharedPreferences) : CastRepository {
    val api =
        GatewayApi().apply {
            configure(
                prefs.getString("gateway", "")!!,
                prefs.getString("device", "")!!,
                prefs.getString("token", "")!!,
            )
        }

    override fun pair(address: String, code: String) {
        val base = address.trim().trimEnd('/')
        val uri = URI(base)
        require(
            uri.scheme in listOf("http", "https") &&
                uri.host != null &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null
        )
        val id =
            prefs.getString("installation", null)
                ?: UUID.randomUUID().toString().also {
                    prefs.edit().putString("installation", it).commit()
                }
        val candidate = GatewayApi().apply { this.base = base }
        val result =
            try {
                candidate.request(
                    "POST",
                    "/v1/devices/register",
                    JSONObject()
                        .put("clientVersion", "cast-0.1.0-dev.5")
                        .put("protocolVersion", 1)
                        .put("installationId", id)
                        .put("pairingCode", code)
                        .put(
                            "platform",
                            JSONObject()
                                .put("androidApi", Build.VERSION.SDK_INT)
                                .put("manufacturer", Build.MANUFACTURER)
                                .put("model", Build.MODEL),
                        )
                        .put("display", JSONObject().put("touch", true)),
                )
            } finally {
                candidate.close()
            }
        api.configure(base, result.getString("deviceId"), result.getString("deviceToken"))
        prefs
            .edit()
            .putString("gateway", api.base)
            .putString("device", api.device)
            .putString("token", api.token)
            .commit()
    }

    override fun receivers(): List<Receiver> {
        val result = api.request("GET", "/v1/cast/receivers")
        check(result.optBoolean("relayAvailable"))
        val items = result.getJSONArray("receivers")
        return (0 until items.length())
            .map { items.getJSONObject(it) }
            .filter { it.optBoolean("online") }
            .map {
                Receiver(
                    it.getString("deviceId"),
                    it.optString("name").ifEmpty { it.getString("deviceId") },
                )
            }
    }

    override fun create(receiver: String): CastGrant {
        val result = api.request("POST", "/v1/cast", JSONObject().put("receiverId", receiver))
        return CastGrant(
            result.getString("castId"),
            URI(api.base).host,
            result.getInt("rtspPort"),
            result.getString("publishPath"),
            result.getString("publishUser"),
            result.getString("publishToken"),
        )
    }

    override fun stop(id: String) {
        api.request("DELETE", "/v1/cast/$id")
    }
}
