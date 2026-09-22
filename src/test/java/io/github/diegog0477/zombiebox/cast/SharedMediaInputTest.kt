package io.github.diegog0477.zombiebox.cast

import io.github.diegog0477.zombiebox.cast.features.media.domain.model.SharedMediaInput
import org.junit.Assert.*
import org.junit.Test

class SharedMediaInputTest {
    @Test
    fun acceptsOneContentDocumentOnly() {
        for (mime in listOf("video/mp4", "audio/flac")) assertEquals(
            "content://documents/media/42",
            SharedMediaInput.accept(
                "android.intent.action.SEND",
                mime,
                "content://documents/media/42",
                1,
            ),
        )
    }

    @Test
    fun rejectsRemoteFilesAmbiguousActionsAndOversizedInputs() {
        for (uri in
            listOf(
                "https://example.org/movie.mp4",
                "file:///secret",
                "content:/file",
                "content://user@host/file",
                "content://host/file#fragment",
                "x".repeat(4097),
            )) assertNull(
            SharedMediaInput.accept("android.intent.action.SEND", "video/mp4", uri, 1)
        )
        assertNull(
            SharedMediaInput.accept(
                "android.intent.action.SEND_MULTIPLE",
                "video/mp4",
                "content://a/b",
                2,
            )
        )
        assertNull(
            SharedMediaInput.accept("android.intent.action.SEND", "text/plain", "content://a/b", 1)
        )
        assertNull(
            SharedMediaInput.accept("android.intent.action.SEND", "video/mp4", "content://a/b", 2)
        )
    }
}
