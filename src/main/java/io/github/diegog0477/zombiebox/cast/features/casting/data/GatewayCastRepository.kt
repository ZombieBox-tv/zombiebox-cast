package io.github.diegog0477.zombiebox.cast.features.casting.data

import android.content.SharedPreferences
import android.os.Build
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CastGrant
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CastVideo
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.Receiver
import io.github.diegog0477.zombiebox.cast.features.casting.domain.repository.CastRepository
import io.github.diegog0477.zombiebox.shared.GatewayApi
import io.github.diegog0477.zombiebox.shared.companion.CompanionTransport
import io.github.diegog0477.zombiebox.shared.companion.CompanionWire
import java.net.URI
import java.util.UUID
import org.json.JSONObject

class GatewayCastRepository(private val prefs: SharedPreferences) : CastRepository {
    private val api =
        GatewayApi().apply {
            configure(
                prefs.getString("gateway", "")!!,
                prefs.getString("device", "")!!,
                prefs.getString("token", "")!!,
            )
        }

    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        if (!prefs.getBoolean("companion", false)) return api.request(method, path, body)
        val scoped = CompanionTransport(api.base, api.device, api.token)
        return try {
            scoped.request(method, path.replace("/v1/cast", "/v1/companion/cast"), body)
        } finally {
            scoped.close()
        }
    }

    fun reload() =
        api.configure(
            prefs.getString("gateway", "")!!,
            prefs.getString("device", "")!!,
            prefs.getString("token", "")!!,
        )

    val address
        get() = api.base

    val paired
        get() = api.token.isNotEmpty()

    fun renew(id: String) {
        request("PUT", "/v1/cast/$id")
    }

    fun ready(id: String) {
        request("POST", "/v1/cast/$id/ready")
    }

    fun close() = api.close()

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
                        .put("clientVersion", "cast-0.1.0-dev.24")
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
            .putBoolean("companion", false)
            .putString("gateway", api.base)
            .putString("device", api.device)
            .putString("token", api.token)
            .commit()
    }

    override fun receivers(): List<Receiver> {
        if (prefs.getBoolean("companion", false)) {
            val scoped = CompanionTransport(api.base, api.device, api.token)
            val status =
                try {
                    CompanionWire.status(scoped)
                } finally {
                    scoped.close()
                }
            return if (status.castAvailable)
                listOf(Receiver(status.grant.targetId, status.grant.targetName))
            else emptyList()
        }
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
        val result =
            request(
                "POST",
                "/v1/cast",
                JSONObject()
                    .put("receiverId", receiver)
                    .put("replaceExisting", true)
                    .put("maxVideoHeight", 1080),
            )
        val video = result.optJSONObject("video")
        return CastGrant(
            result.getString("castId"),
            URI(api.base).host,
            result.getInt("rtspPort"),
            result.getString("publishPath"),
            result.getString("publishUser"),
            result.getString("publishToken"),
            CastVideo(
                video?.optInt("maxWidth", 640) ?: 640,
                video?.optInt("maxHeight", 360) ?: 360,
                video?.optInt("fps", 24) ?: 24,
                video?.optInt("bitrate", 800000) ?: 800000,
            ),
        )
    }

    override fun stop(id: String) {
        request("DELETE", "/v1/cast/$id")
    }
}
