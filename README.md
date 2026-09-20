# Android Mirror / AirCast

Retained from the initial workspace. This is neither a separate repository nor a second APK. Specification section 29 and milestone M7 place the MediaProjection sender inside zombie-client on API >=21, isolated from legacy class loading.

No independent build at bootstrap. Add an Android library boundary with a guarded factory after the legacy/client player spike. MediaMTX provides gateway relay. Internal audio depends on API, permissions and content; iOS AirPlay is a separate UxPlay gateway integration.
