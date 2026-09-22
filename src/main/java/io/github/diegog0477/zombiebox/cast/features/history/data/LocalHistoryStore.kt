package io.github.diegog0477.zombiebox.cast.features.history.data

import android.content.SharedPreferences
import android.util.Base64
import io.github.diegog0477.zombiebox.cast.features.history.domain.CaptureHistory
import io.github.diegog0477.zombiebox.cast.features.history.domain.model.CaptureSession
import io.github.diegog0477.zombiebox.cast.features.history.domain.repository.HistoryStore
import java.util.UUID

class LocalHistoryStore(private val preferences: SharedPreferences) : HistoryStore {
    companion object {
        const val KEY = "captureHistory"
        private val lock = Any()
        private val processId = UUID.randomUUID().toString()

        fun create(preferences: SharedPreferences) =
            CaptureHistory(
                LocalHistoryStore(preferences),
                processId,
                System::currentTimeMillis,
                { UUID.randomUUID().toString() },
            )
    }

    override fun read(): List<CaptureSession> = synchronized(lock) { load() }

    override fun update(change: (List<CaptureSession>) -> List<CaptureSession>) {
        synchronized(lock) {
            val before = load()
            val after = change(before)
            if (after != before) {
                preferences
                    .edit()
                    .putString(
                        KEY,
                        Base64.encodeToString(HistoryCodec.encode(after), Base64.NO_WRAP),
                    )
                    .apply()
            }
        }
    }

    private fun load(): List<CaptureSession> {
        return try {
            val text = preferences.getString(KEY, "").orEmpty()
            if (text.length > HistoryCodec.MAX_BYTES * 2) emptyList()
            else HistoryCodec.decode(Base64.decode(text, Base64.NO_WRAP))
        } catch (_: Exception) {
            emptyList()
        }
    }
}
