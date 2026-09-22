# Share a phone file to the selected TV

After pairing/selecting a TV in Devices, Android's Sharesheet can open Zombie Cast
for one audio/video document. It stages the document on the Media screen. Send is
always explicit; sharing never initiates mirroring or uploads automatically.
Normal picker selection remains available. No broad storage permission is added.

The exported entry accepts ACTION_SEND with audio/video MIME and one content URI.
HTTP/file URIs, multiple or conflicting payloads and malformed/oversized locators
are rejected. The repository checks the content provider's MIME and document size;
the gateway still validates actual file headers and FFprobe output. The incoming
Intent cannot supply a gateway address, pairing token or receiver ID. Those values
are snapshotted from the existing selected pairing. Active projection is not replaced.

If pairing is missing, the companion opens so the user can pair first; the user must
share again afterward. Existing accepted TV media is restored and must be stopped
before another file is selected. A failed Stop keeps selection/send blocked so an
uncertain old lease is not replaced by a second transfer. Unknown or over-256-MiB
documents remain unsupported. No signed media URL or persistent URI grant is stored.

Recreation can inspect the original share again; unfinished foreground transfers
retain their existing cancellation policy. Background/resumable uploads, multiple
file queues and URL sending remain development work. JVM input/lifecycle tests and
APK checks do not establish file-provider or TV acceptance on physical devices.
