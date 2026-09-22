package io.github.diegog0477.zombiebox.cast.features.media.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import io.github.diegog0477.zombiebox.cast.features.media.domain.model.MediaDocument
import io.github.diegog0477.zombiebox.cast.features.media.domain.repository.MediaRepository
import io.github.diegog0477.zombiebox.shared.companion.CompanionTransport
import java.util.UUID
import org.json.JSONObject

/** Credentials/target are snapshotted at screen creation; selection cannot retarget a transfer. */
class GatewayMediaRepository(
    private val resolver: ContentResolver,
    private val base: String,
    private val device: String,
    private val token: String,
) : MediaRepository {
    @Volatile private var transport: CompanionTransport? = null
    @Volatile private var cancelled = false
    @Volatile private var mediaId = ""

    override fun restore(): String? {
        val api = CompanionTransport(base, device, token)
        return try {
            val result = api.request("GET", "/v1/companion/media")
            if (result.optString("state") == "ACCEPTED") {
                mediaId = result.getString("mediaId")
                require(mediaId.matches(Regex("[0-9a-f]{32}")))
                result.getString("title")
            } else null
        } finally {
            api.close()
        }
    }

    override fun inspect(locator: String): MediaDocument {
        val uri = Uri.parse(locator)
        require(uri.scheme == "content")
        val mime = resolver.getType(uri).orEmpty()
        require(mime.startsWith("video/") || mime.startsWith("audio/"))
        return resolver
            .query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )!!
            .use { cursor ->
                check(cursor.moveToFirst())
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val size =
                    if (sizeIndex < 0 || cursor.isNull(sizeIndex)) -1L
                    else cursor.getLong(sizeIndex)
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val name = if (nameIndex < 0) "" else cursor.getString(nameIndex).orEmpty()
                MediaDocument(locator, name.filter { !it.isISOControl() }.take(120), size)
            }
    }

    override fun send(document: MediaDocument, progress: (Int) -> Unit) {
        check(document.supportedSize && !cancelled && mediaId.isEmpty())
        val api = CompanionTransport(base, device, token)
        transport = api
        mediaId = UUID.randomUUID().toString().replace("-", "")
        try {
            check(!cancelled)
            val status = api.request("GET", "/v1/companion/status")
            check(status.optBoolean("mediaAvailable"))
            resolver.openInputStream(Uri.parse(document.locator))!!.use { input ->
                val result = api.upload(mediaId, document.bytes.toInt(), input, progress)
                check(
                    result.getString("mediaId") == mediaId &&
                        result.getString("state") == "UPLOADED"
                )
            }
            check(!cancelled)
            val result =
                api.request(
                    "POST",
                    "/v1/companion/media/$mediaId/play",
                    JSONObject().put("title", document.title),
                )
            check(!cancelled && result.getString("state") == "ACCEPTED")
        } catch (error: Exception) {
            try {
                stop()
            } catch (_: Exception) {}
            throw error
        } finally {
            api.close()
            transport = null
        }
    }

    override fun cancel() {
        cancelled = true
        transport?.close()
    }

    override fun stop() {
        val id = mediaId
        if (id.isNotEmpty()) {
            val api = CompanionTransport(base, device, token)
            try {
                api.request("DELETE", "/v1/companion/media/$id")
            } finally {
                api.close()
            }
        }
        mediaId = ""
        cancelled = false
    }
}
