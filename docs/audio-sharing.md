# Audio-only sharing

Select Audio on Home, choose the approved receiver, then Start audio sharing.
The public backend requires API29+, RECORD_AUDIO permission and a fresh Android
MediaProjection consent. It captures permitted playback from media/game/unknown
usages, never microphone input. No virtual display, Surface or video encoder is
created in Audio mode. The device/API floor for installing Cast remains API21;
older phones keep Screen and Remote and see an Audio availability explanation.

The source application, user profile and Android/OEM policies can block capture.
Silence is an observation, not proof that policy blocked an app. With denied audio
permission or unavailable capture, Audio mode stops rather than starting screen
capture. Screen mode keeps its previous optional-audio/video-only fallback.
The [Android capture contract](https://developer.android.com/media/platform/av-capture)
requires explicit consent and honors source playback policy. No root, privileged
backend or protected-content bypass is introduced.

## Negotiation and lifecycle

Cast requests `mode: AUDIO`. The gateway must explicitly echo AUDIO and grant
AAC-LC at 44.1 kHz, stereo, 128 kbit/s; no video budget is returned. An older gateway
that ignores the mode is rejected and its grant is released. SCREEN remains the
omission default for existing clients. Companion mode is still bound to the
approved target; receiver casting consent, handoff policy, ownership, leases,
revocation and finite concurrency are unchanged. Full and Edge share the core.
Audio only requires the AAC/HLS candidate path, not an H.264 decoder. Known failed
AAC/HLS probes reject the request; unknown support remains unverified.

The sender announces a single MPEG4-GENERIC AAC track over RTSP/TCP, channel 0.
It sends no video SDP or packets. Audio writes also drive RTSP keepalives, using a
monotonic clock. Encoder output configuration must match the advertised AAC format
before any access unit is sent. Screen+A/V retains video channel 0 and audio channel
2. The [MediaMTX HLS reader](https://mediamtx.org/docs/read/hls) carries AAC; the
configured MPEG-TS HLS variant feeds the existing authenticated client stream.

The service injects a CaptureEncoder: projection video or audio-only. Audio capture
has three bounded recovery attempts after the initial attempt, backoff, packet
progress timeout and transport-first cleanup to unblock audio writers. System
projection revocation stops the foreground service. A missing audio backend fails
instead of adding a dummy black video track. End-to-end runtime remains unverified.

## UI and history

The selected Screen/Audio segment is green. Audio hides video-quality, framing and
video latency controls; it explains app-audio consent and source restrictions.
Settings are persisted and locked during consent/capture. Notification, current
state and history distinguish audio sharing. History storage version 2 reads all
version 1 records as Screen and retains its 30-record limit and deletion policy.
Media-file selection/upload/URL casting remains a separate pending workflow.

Host checks cover mode/API policy, explicit old-gateway rejection policy, RTSP
track/payload/keepalive behavior, history migration, gateway audio plans/permissions
and schema compatibility. A synthetic FFmpeg AAC stream through the exact pinned
MediaMTX 1.21.1 image decodes as one audio track from MPEG-TS HLS. That is relay
verification, not Android capture, OEM, speaker output, latency or battery proof.
