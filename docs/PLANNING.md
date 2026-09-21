# zombiebox-cast: component work

The product milestones relevant to this repository are M7, M11.
The local registry is a component projection of the workspace plan. Closing a
component task does not close a product-wide milestone or a physical validation gate.

Current increment: independent repository/build/dependency boundaries with filtered
history. Remaining feature development follows the ordered workspace audit:
tracks/subtitles and lifecycle; provider navigation/virtualization; measured
capabilities/native-first health; remote media adaptation; receiver finishing;
Edge operations and reproducible releases. Implement only this component's part,
and evolve shared protocol contracts in their owning repository.

Keep a separate validation track for hardware/account/latency/memory evidence.
Use development checkpoint tags until complete exit gates are evidenced. Hosted
issues/milestones can be attached to the shared GitHub Project once remotes exist.

## dev.11 increment

Updates the shared protocol/transport dependency only. Receiver-aware profiles, rotation/recovery and audio eligibility remain open.
No product milestone or physical/account gate is completed by this checkpoint.

## dev.12 increment

Consumes receiver video limits for resolution/fps/bitrate; aligned aspect-ratio sizing. Rotation/audio recovery and physical evidence remain open.

## dev.13 increment

Updates the protocol dependency only. Sender capture/rotation/audio behavior and APK version are unchanged.
No product milestone or physical/account gate closes with this checkpoint.

## dev.16 increment

One-display capture rotation and finite encoder/RTSP recovery, isolated resize callbacks, sample-based audio feedback and video-only fallback. Cast versionCode 7.
Product exit gates and physical/account acceptance remain open.

## dev.17 increment

Adds GPL notices to the APK (versionCode 8) and updates the protocol pin. Capture behavior is unchanged from dev.16.

No product milestone or physical gate is closed.

## dev.21 increment

Consumes the additive shared dev.21 protocol/transport. Sender code and APK version remain unchanged.
No physical, account or product milestone closes.

Verification: 6 JVM tests, debug/release compilation, lint and APK audit pass. The debug APK is 971,753 bytes, minSdk 21 and contains no native libraries. Physical capture/audio/rotation remain unverified.

## dev.22 increment

Requests consented receiver replacement; the gateway retains the previous receiver until Cast readiness. APK versionCode 22; encoder/capture implementation unchanged.
No product or physical acceptance gate closes.

Verification: 6 JVM tests, debug/release builds and lint pass. Physical capture/audio/reception remains unverified.

## dev.23 increment

LAN discovery, selectable gateway candidates and system-bar/viewport correction; APK versionCode 23. QR and full redesign remain planned.
Product exit gates and deferred physical acceptance remain open.

Accepted next scope (ADR0029 in the workspace): the new Cast reference composition,
target-approved QR pairing and revocable trust, and an independent companion Remote
screen with D-pad/media/provider controls. Remote control does not request projection
or audio permission and cannot approve the target's pairing/privilege dialogs.
Optional root/OEM capture is planned; dedicated 21:9 layout support is outside V1.

Verification: 8 JVM tests, debug/unsigned-release builds, lint and APK audit pass. Debug APK: 983,167 bytes, minSdk21 and no native libraries. English/Spanish resources are complete for the current screen. Physical/visual behavior remains unverified.


## dev.24 increment

Phone-first dark/green dashboard, QR/manual consent, saved target selection/revocation, gateway proof/reconnection and a discrete Remote screen. Cast may adopt assessed native dependencies; this increment adds only Java ZXing. APK versionCode 24.
Full visual/capture policy, extended Remote, HEVC/4K and other product gates remain open; physical acceptance stays deferred.

Verification: 13 JVM tests, debug/unsigned-release builds and lint pass. Debug APK: 1,310,760 bytes, minSdk21, with no `.so` files currently bundled. Gateway-generated QR decoding, consent states and deferred scan results have host coverage. Camera, focus, A/V and visual/device acceptance remain unverified.
