# Background media transfer

A non-exported dataSync foreground service owns the injected MediaViewModel,
repository, one transfer and immutable target/credential snapshot. Activities bind
only for rendering/document consent. Explicit Send forwards the content URI grant
to that service; rotation, Back and task dismissal do not close an active transfer.
A notification returns to Media or cancels. Android 13+ notification permission is
requested when sending; denial still permits the foreground service, with control
through Media/Android active-apps UI. Channel APIs are isolated from API21.

The gateway still bounds known-size uploads to 256 MiB and five minutes. The phone
has a finite 20-minute operation/wake-lock deadline (including preparation), handles
Android's foreground timeout and releases foreground resources after handoff/failure.
START_NOT_STICKY prevents a killed process from silently replaying a URI. Already
accepted TV playback survives service teardown; reopening restores server ownership.
Unfinished work is cancelled on service destruction; temporary gateway uploads have
existing expiry/sweep. No new screen, microphone or storage-wide capture is involved.

Host tests cover model cancellation/fencing and UI detachment. Actual Activity/FGS,
URI-provider grants, Doze/task removal and notification behavior require later Android
acceptance. This is not resumable upload, a persistent playlist or URL casting.

Platform contract: [Android dataSync foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types#data-sync).
