# Local mirroring history

`features/history` owns immutable session models, a serialized store contract,
lifecycle policy and an Android-free ViewModel. `LocalHistoryStore` adapts private
SharedPreferences to a bounded versioned binary codec. Activity composes the
repository/ViewModel/UI; ProjectionService independently composes the same store
and records lifecycle observations. No gateway contract or credentials change.

## Evidence and retention

A record starts after the service receives consent and a relay grant, before
projection/encoder setup. Permission cancellation and failed grant requests are
not recorded as mirroring sessions. The record has a new local identifier, process
identifier, receiver display name (120 characters maximum), timestamps, phase,
configured dimensions/fps and last audio observation. It deliberately excludes
stream URLs, tokens, screenshots and media samples. Names are supplied receiver
metadata; the UI makes no protocol inference from them.

Starting, sharing, recovering, failed and stopped reflect service observations.
Configured dimensions are not decoded-frame proof; capturing/silent audio states
are observations at the sender. Terminal records cannot be resurrected by late
callbacks. End timestamps clamp to the start if the system clock moves backward;
no elapsed playback duration is inferred from wall-clock timestamps.

Only the 30 most recent starts are retained in insertion order. Store transactions
serialize UI/service/encoder updates within this single Android process. Writes use
SharedPreferences.apply: in-process visibility is immediate, disk writes are
asynchronous. Abrupt power loss may lose the newest observation. Android backup is
already disabled for this app; history is local and is not synchronized upstream.

## Reopening and deletion

A different process identifier marks unfinished records interrupted, with unknown
end time. Recreating an Activity in the same live process does not mark an active
session interrupted. No projection token is persisted and no automatic restart is
attempted. Confirmed deletion removes terminal/interrupted records only; delayed
callbacks cannot recreate a removed record. The separate latest remote-command
receipt remains ephemeral and is clearly labeled.

The codec limits input size, count, strings, enums and numeric ranges. Unknown,
truncated or corrupt history becomes empty without blocking the feature. Host tests
cover round trips, retention/clock changes, invalid input, recovery/failure,
process recreation and clearing during active capture. Physical UI/service/process
kill acceptance remains deferred.

Dev.29: history codec version 2 adds Screen/Audio mode. Version 1 remains readable
as Screen; Audio records have no configured video dimensions.
