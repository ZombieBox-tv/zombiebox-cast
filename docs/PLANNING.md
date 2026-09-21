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
