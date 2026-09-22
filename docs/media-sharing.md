# Media files (dev.30)

Home's Media segment opens a native dark/green feature screen with a selected
Media segment, document card, file selection, transfer progress, Send and Cancel /
Stop actions. Screen/Audio return to the requested capture mode. Display strings
have English defaults and Spanish translations. No native library is added.

The [Android document picker](https://developer.android.com/training/data-storage/shared/documents-files)
grants access to the file explicitly selected by the user. Cast accepts openable
`content:` audio/video documents with a known size of 1 byte through 256 MiB.
It requests no broad storage permission, MediaProjection permission or microphone
permission for file sending, and does not run a phone HTTP server. Unknown-size
and virtual-only documents are not silently copied into an unbounded phone cache.

A feature repository owns document metadata/streams, JSON and a snapshot of the
approved target credentials. Its ViewModel owns state/actions/cancellation. The
shared protocol transport verifies gateway identity, streams bounded buffers with
fixed length and no redirects, and closes network/file reads on cancellation or
the five-minute upload deadline. No complete file is loaded into RAM.

Keep this screen open until the gateway accepts the handoff. Leaving/recreating
it while a transfer is unfinished cancels the attempt; background and resumable
uploads are not implemented. After acceptance, the TV can continue independently
of the phone. Reopening Media asks the gateway for that companion's active file,
so Stop remains available after Activity recreation. The displayed receipt means
accepted by the gateway, never confirmed speaker/video output on the TV. Capture
history remains specific to screen/app-audio capture, not fabricated file history.

The gateway checks actual media streams and the TV's evidence, then direct-plays,
remuxes or transcodes. It rejects unavailable/unsupported receivers and enforces
existing handoff consent. An older gateway without `mediaAvailable` is rejected.
The current running dev.22 gateway must be upgraded before this feature works.

Remaining: URL media, playlists/queues, thumbnails, transfer persistence/background
service, larger or unknown-size files, detailed error presentation, complete visual
finishing and physical compatibility. This increment does not promise playback of
DRM-protected documents or bypass Android/content protections.
