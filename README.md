# Android Mirror / AirCast

Reserved future API 21+ sender APK: `io.github.diegog0477.zombiebox.cast`.
The client APK uses `io.github.diegog0477.zombiebox.client`.

Per [ADR 0018](../docs/adr/0018-android-application-identities.md), the author selected a separate future sender application, superseding the original single-APK boundary in specification section 29. Both belong to this monorepo. No sender APK/build is implemented yet.

M7 will own MediaProjection, foreground-service/permission lifecycle and the MediaMTX gateway relay. Internal audio depends on API, permissions and content. iOS AirPlay remains a separate UxPlay gateway integration.
