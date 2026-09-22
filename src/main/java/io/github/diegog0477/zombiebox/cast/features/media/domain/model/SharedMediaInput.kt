package io.github.diegog0477.zombiebox.cast.features.media.domain.model

import java.net.URI

/** Sharing stages one local document, never a URL, receiver command or automatic upload. */
object SharedMediaInput {
    fun accept(action: String?, mime: String?, locator: String?, count: Int): String? {
        if (
            action != "android.intent.action.SEND" ||
                count != 1 ||
                mime == null ||
                !(mime.startsWith("audio/") || mime.startsWith("video/")) ||
                locator.isNullOrBlank() ||
                locator.length > 4096
        )
            return null
        return try {
            val uri = URI(locator)
            if (
                uri.scheme != "content" ||
                    uri.rawAuthority.isNullOrBlank() ||
                    uri.rawFragment != null ||
                    uri.rawUserInfo != null
            )
                null
            else locator
        } catch (_: Exception) {
            null
        }
    }
}
