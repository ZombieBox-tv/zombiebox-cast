package io.github.diegog0477.zombiebox.cast.features.history.data

import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CaptureMode
import io.github.diegog0477.zombiebox.cast.features.history.domain.CaptureHistory
import io.github.diegog0477.zombiebox.cast.features.history.domain.model.*
import java.io.*

/** Versioned, bounded local storage. No grant, URL, token or captured content is stored. */
internal object HistoryCodec {
    const val MAX_BYTES = 32768

    fun encode(values: List<CaptureSession>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(2)
            output.writeInt(values.size.coerceAtMost(CaptureHistory.LIMIT))
            for (value in values.take(CaptureHistory.LIMIT)) {
                output.writeUTF(value.id.take(64))
                output.writeUTF(value.processId.take(64))
                output.writeUTF(value.receiver.take(120))
                output.writeLong(value.startedAt)
                output.writeUTF(value.phase.name)
                output.writeLong(value.endedAt ?: -1)
                output.writeInt(value.width)
                output.writeInt(value.height)
                output.writeInt(value.fps)
                output.writeUTF(value.audio.name)
                output.writeUTF(value.mode.name)
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): List<CaptureSession> {
        if (bytes.size > MAX_BYTES) return emptyList()
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val version = input.readInt()
                require(version in 1..2)
                val count = input.readInt()
                require(count in 0..CaptureHistory.LIMIT)
                val values =
                    List(count) {
                        val id = input.readUTF()
                        val process = input.readUTF()
                        val receiver = input.readUTF()
                        val started = input.readLong()
                        val phase = SessionPhase.valueOf(input.readUTF())
                        val ended = input.readLong().takeIf { it >= 0 }
                        val width = input.readInt()
                        val height = input.readInt()
                        val fps = input.readInt()
                        val audio = SessionAudio.valueOf(input.readUTF())
                        val mode =
                            if (version == 2) CaptureMode.valueOf(input.readUTF())
                            else CaptureMode.SCREEN
                        require(
                            id.length in 1..64 && process.length in 1..64 && receiver.length <= 120
                        )
                        require(
                            started >= 0 && (ended == null || (phase.terminal && ended >= started))
                        )
                        require(width in 0..1920 && height in 0..1920 && fps in 0..30)
                        CaptureSession(
                            id,
                            process,
                            receiver,
                            started,
                            phase,
                            ended,
                            width,
                            height,
                            fps,
                            audio,
                            mode,
                        )
                    }
                require(input.available() == 0)
                values
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
