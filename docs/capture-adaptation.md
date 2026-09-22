# Capture adaptation and fixed framing

The gateway grants at most 1080p/30fps/4 Mbit/s only when the sender explicitly
requests the expanded ceiling and the receiver has fresh, advancing H.2641080p
and HLS evidence. Memory-constrained receivers retain their smaller budget. A
legacy request (no `maxVideoHeight`) never receives the new ceiling. Current
senders can still use an older gateway; it returns the prior bounded profile.

User Auto/480p/720p/1080p preferences only reduce that grant. Before each encoder
startup, the injected surface-encoder factory considers at most 16 AVC encoders,
checks Surface input, profile/level, aligned dimensions, frame rate and bitrate,
and tries at most 12 configurations. Configuration success is not a playback probe.
The outgoing SPS must identify AVC Baseline at or below the requested level.
Actual configuration dimensions/fps appear beneath the source card.

Configuration rejection can select a lower tier or another encoder. Repeated
stream failures lower the candidate tier within the existing three-retry budget;
one virtual display remains associated with each projection consent. Output must
continue advancing; the existing timeout and lease handling remain in force.
This is bounded recovery, not continuous network-driven adaptive bitrate, evidence
of hardware acceleration or certification of an OEM implementation.

## Output orientation

Auto preserves the captured source's aspect ratio. Portrait and Landscape select
fixed output canvases within the same receiver budget; they do not force another
app's Activity orientation. Since [Android 12L preserves and centers capture
aspect ratio on the projection surface](https://developer.android.com/media/grow/media-projection),
fixed framing is offered on API32+. Black bars are expected when source and canvas
ratios differ. Earlier versions retain Auto and explain the unavailable controls.
A future measured GPU/OEM adapter may add older-device fixed framing; this version
does not pretend that resizing alone provides it.

Capability discovery uses [VideoCapabilities](https://developer.android.com/reference/android/media/MediaCodecInfo.VideoCapabilities)
and [codec profile/format configuration](https://developer.android.com/reference/android/media/MediaCodec).
These are candidate declarations, not measured sustained encoding guarantees.
No root, native library, device-specific condition or privileged policy change is
introduced. Physical rotation, source-window resizing, A/V, encoder health and
visual qualification stay deferred until the implementation track is complete.
