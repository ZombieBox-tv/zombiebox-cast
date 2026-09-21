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
