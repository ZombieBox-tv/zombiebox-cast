# Zombiebox Cast

Separate API 21+ APK: `io.github.diegog0477.zombiebox.cast`. It shares only the Android connection library with the legacy client. Build from the repository root:

```sh
make cast-build cast-test
```

APK: `build/outputs/apk/debug/cast-debug.apk`. Pair to the gateway, select an online client that has enabled receiving, and approve Android's screen-capture prompt. A foreground service owns MediaProjection and MediaCodec; RTSP/TCP publishes H.264 to MediaMTX. Android 10+ can optionally capture permitted application audio as AAC. No microphone input is selected. Older Android sends video only.

The sender renews a short gateway lease. Receiver Stop, notification Stop, consent revocation, startup failure and network loss close publication. Lifecycle and packetization have host tests; actual MediaProjection/audio/encoder/rotation behavior requires a physical sender. See [mirroring setup and limitations](../docs/development/mirroring.md).
