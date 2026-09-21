# Cast native dependency policy

Cast is a handheld companion and sender. Unlike the TV thin Client, it may use
native libraries when the measured benefit justifies the cost. Platform and JVM
implementations remain valid choices; no native dependency is currently bundled.

Before adopting a dependency, record its pinned version, license and source,
the capability or experience it improves, platform/JVM alternatives, APK size per
ABI, minimum Android version, CPU/RAM impact, maintenance and security update
plan. Include fallback behavior on unsupported devices and distinguish measured
results from estimates or pending physical evidence. A positive comparison can
justify adoption within the authorized development scope; this policy does not
add a separate approval ceremony.

`native-dependencies.json` inventories each packaged `.so` with its exact ZIP
`path` and a repository-relative `assessment` Markdown path. The workspace APK
audit checks that inventory and reports uncompressed sizes. An empty inventory
means none are currently approved/bundled, not that JNI is forbidden in Cast.
The Client APK retains an unconditional no-`.so` gate.
