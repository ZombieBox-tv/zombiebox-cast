package io.github.diegog0477.zombiebox.cast.features.history.domain.model

enum class SessionPhase(val terminal: Boolean = false) {
    STARTING,
    SHARING,
    RECOVERING,
    STOPPED(true),
    FAILED(true),
    INTERRUPTED(true),
}

enum class SessionAudio {
    DISABLED,
    WAITING,
    UNAVAILABLE,
    CAPTURING,
    SILENT,
}

/** Local evidence only: configured format and observed sender state, never playback proof. */
data class CaptureSession(
    val id: String,
    val processId: String,
    val receiver: String,
    val startedAt: Long,
    val phase: SessionPhase = SessionPhase.STARTING,
    val endedAt: Long? = null,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 0,
    val audio: SessionAudio = SessionAudio.DISABLED,
)
