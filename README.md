# zombiebox-cast

Phone-first API21+ companion, remote and MediaProjection sender APK.

This is an independent repository in the Zombie Box workspace. Remotes and hosted
releases are not configured yet; local commits/tags and dependency pins are real.

Application ID: `io.github.diegog0477.zombiebox.cast`; minSdk21.

```sh
make deps-check  # ../zombiebox-protocol or ZOMBIE_PROTOCOL_DIR
make build test
```

Uses its own Gradle wrapper/settings and the shared library from the pinned protocol
repository. It does not depend on Client source. `make deps` can restore the locked
protocol checkout after its remote is configured. JDK21 and SDK35 are required.

`features/casting` separates domain contracts, data, ViewModel, platform capture
and RTSP/RTP transport. MediaProjection owns video capture; API29 playback audio
is isolated and subject to platform/content permission. Receiver-aware profiles,
rotation reconfiguration and bounded encoder recovery are implemented. Actual
A/V/latency evidence remains open. Never claim all applications permit audio
capture or mirroring.

Output: `build/outputs/apk/debug/cast-debug.apk`.

## Development rules

Run `make format` and `make format-check`. Formatters are pinned and downloaded
on first use. See [AGENTS.md](AGENTS.md), [history provenance](docs/history.md),
[component work](docs/PLANNING.md) and [local milestone registry](docs/milestones.json).
The central workspace owns product-wide ADRs, the original specification, the UI
reference, M0–M11 exit gates and the complete development/validation gap audit.
Physical devices over USB/ADB are the default; automated checks do not establish
legacy runtime or end-to-end account/media compatibility.

Dev.11 updates the pinned shared protocol/transport for the playback request timeout.
The sender's capture/rotation/audio behavior is unchanged; no new Cast runtime
compatibility is claimed by this dependency checkpoint.

Dev.12 consumes CastGrant video constraints instead of always encoding 720p:
receiver-specific maximum dimensions, fps and bitrate are validated and applied.
Source aspect ratio is retained with 16-pixel alignment. Unknown receiver support
uses a conservative candidate; rotation reconfiguration, audio eligibility feedback,
encoder recovery and physical evidence remain open.

Dev.13: Updates the protocol dependency only. Sender capture/rotation/audio behavior and APK version are unchanged.

Dev.16: One-display capture rotation and finite encoder/RTSP recovery, isolated resize callbacks, sample-based audio feedback and video-only fallback. Cast versionCode 7.

## License

First-party code: [GPL-3.0-only](LICENSE). See [NOTICE](NOTICE) for third-party scope.

Dev.19: Updates the additive protocol dependency only; sender code and APK version remain unchanged.

Dev.20: Consumes additive dev.20 protocol; sender implementation and APK version remain unchanged.

Dev.21: Consumes the additive shared dev.21 protocol/transport. Sender code and APK version remain unchanged.

## dev.22 increment

Requests consented receiver replacement; the gateway retains the previous receiver until Cast readiness. APK versionCode 22; encoder/capture implementation unchanged.
No product or physical acceptance gate closes.

## dev.23 increment

Automatic gateway discovery, refresh/selection and viewport/system-bar correction. Full visual redesign and QR trust remain planned.
No product or physical acceptance gate closes.


## dev.24 increment

Phone-first dark/green dashboard, QR/manual consent, saved target selection/revocation, gateway proof/reconnection and a discrete Remote screen. Cast may adopt assessed native dependencies; this increment adds only Java ZXing. APK versionCode 24.
Full visual/capture policy, extended Remote, HEVC/4K and other product gates remain open; physical acceptance stays deferred.

See the [native dependency policy](docs/native-dependencies.md) and [QR assessment](docs/qr-dependency.md). The strict no-native rule belongs to the TV thin Client.

## dev.25 increment

Consumes the additive protocol contract; capture and full visual redesign are unchanged and remain pending.

## dev.26 increment

Persisted Auto/480p/720p limits and low-latency preference now drive the actual
encoder budget through a platform-free ViewModel and injected preference store.
Limits cannot raise the gateway's receiver budget. Low latency caps the target at
1 Mbit/s and uses a one-second keyframe interval instead of two seconds; it does
not guarantee end-to-end delay. Current sending remains H.264, at most 720p/30fps.
Preferences freeze while requesting capture or sharing. Audio still requires
API29 and the source application's permission.

The Home source card has an original decorative phone illustration, navigation
uses quiet surfaces with a green selected label, and tabs/scroll offsets restore
after Activity recreation. Revocation invalidates pending receiver results and
revokes abandoned grants. English/Spanish strings accompany the new controls.

Verification: debug build, lint and 18 JVM tests pass. APK audit remains minSdk21
with no native libraries. Full visual acceptance, orientation lock, 1080p sending,
Media/audio-only flows, session history and extended Remote remain open; no
physical acceptance or milestone completion is claimed.

## dev.27 increment

Adds negotiated 1080p ceilings, capability-filtered surface encoder selection, bounded configuration/runtime fallback and AVC output profile/level checks. Output orientation Auto/Portrait/Landscape is persisted; fixed aspect-preserving framing is enabled on API32+ and unavailable with an explanation on older phones. Header/navigation icons and a provider-accent Remote grid refine the reference composition. Actual configured dimensions are visible. Complete visual, OEM and physical acceptance remain open.

## dev.28 increment

Activity now shows the last 30 actual mirroring sessions on this phone: receiver
name, start/end timestamps, sender state, last configured video format and last
observed audio state. Storage and lifecycle policy sit behind injected repositories;
the ViewModel and models are platform-free. No stream, URL, token or screenshot is
added to history. Existing Android backup exclusions apply.

An unfinished session from an earlier process is shown as interrupted with an
unknown end time. Reopening the app never restores projection consent or starts
capture. Terminal records ignore late encoder/heartbeat callbacks. Clearing history
requires confirmation and preserves active sessions. Corrupt/unknown storage falls
back to an empty history; retention is bounded to 30 newest starts, independent of
wall-clock changes. SharedPreferences uses asynchronous persistence, so abrupt
power loss can lose the latest update. Receiver playback remains unverified.

Media/audio-only flows, pending-pairing recreation, advanced Remote/backend choices,
older-platform fixed framing, OEM integration and final visual acceptance remain
open. 1080p negotiation and API32+ fixed framing were implemented in dev.27 and are
still conditional on capability/evidence. No native dependency is added.

Host verification: 27 Cast JVM tests, debug build/lint and formatting/architecture
checks pass. APK audit: 1,376,115 bytes, minSdk21, signature verified, no native
libraries. Physical projection/audio/visual acceptance remains deferred.

## dev.29 increment

Audio-only API29+ mode uses consented playback capture and AAC publishing without a virtual display or video encoder. Mode-aware controls/history, old-gateway rejection, bounded recovery and audio-only RTSP keepalive are implemented. No native dependency added.
Product milestones and physical acceptance remain open.

See [Audio sharing](docs/audio-sharing.md). Host verification: 30 JVM tests,
debug build/lint and APK audit pass (1,383,104 bytes, minSdk21, no native libraries).

## dev.30 increment

Native Media file screen, document consent, bounded upload/progress/cancel, accepted-file restoration and Stop. No capture/storage-wide permissions or native dependency added. Foreground-only upload; URL sending remains pending.
Product milestones and deferred physical gates remain open.
