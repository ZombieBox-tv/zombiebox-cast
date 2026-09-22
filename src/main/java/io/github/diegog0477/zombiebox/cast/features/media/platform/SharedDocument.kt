package io.github.diegog0477.zombiebox.cast.features.media.platform

import android.content.Intent
import android.net.Uri
import io.github.diegog0477.zombiebox.cast.features.media.domain.model.SharedMediaInput

object SharedDocument {
    @Suppress("DEPRECATION")
    fun read(intent: Intent): String? =
        try {
            val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            val clip = intent.clipData
            // Ambiguous payloads are rejected; do not silently choose one of several documents.
            if (
                clip != null &&
                    (clip.itemCount != 1 || (stream != null && clip.getItemAt(0).uri != stream))
            )
                null
            else
                SharedMediaInput.accept(
                    intent.action,
                    intent.type,
                    (stream ?: clip?.getItemAt(0)?.uri)?.toString(),
                    1,
                )
        } catch (_: Exception) {
            null
        }
}
