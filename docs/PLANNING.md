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

## dev.30 increment

Native Media file screen, document consent, bounded upload/progress/cancel, accepted-file restoration and Stop. No capture/storage-wide permissions or native dependency added. Foreground-only upload; URL sending remains pending.
Product milestones and deferred physical gates remain open.
