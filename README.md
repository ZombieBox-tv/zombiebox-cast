# zombiebox-cast

Independent API21+ MediaProjection sender APK.

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
rotation/recovery and actual A/V/latency evidence remain open. Never claim all
applications permit audio capture or mirroring.

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
