# QR reader dependency assessment

The companion scanner uses `com.google.zxing:core:3.3.3` (Apache-2.0), pinned to
upstream `463d1ea7ed44f2fd6f46e234af1dba9616512e66` in the workspace reference
lock. Only the Java decoder is packaged, exclusively in Cast. Camera ownership,
permissions and preview lifetime remain in our Android platform adapter.

ZXing's documented newer 3.4+ Android baseline is API24 without extra compatibility
work. This API21 sender therefore pins the older Java-compatible core rather than
raising the installation floor. This is a maintenance tradeoff, not a claim that
an old version is inherently safer. Reassess updates/desugaring before public
release; record runtime compatibility and dependency vulnerability review there.

The scanner only decodes QR, limits preview luminance to 1280×720, allows one frame
at a time at most five times/second, and rejects payloads longer than 1024 chars.
The shared mapper validates the version, locator and exact identifier lengths;
a successful scan is not pairing approval. Camera permission is requested only
on Scan. Six-digit manual pairing and LAN discovery remain camera-free fallbacks.

Upstream LICENSE and NOTICE are included in `src/main/assets/legal/zxing/`.
The JVM test decodes a PNG produced by the pinned Go encoder, matching the JSON
fixture. It is interoperability evidence, not physical camera evidence. No JNI
library is added and the strict Client packaging gate is unchanged.

Sources: https://github.com/zxing/zxing/wiki/Getting-Started-Developing and
https://github.com/zxing/zxing/releases/tag/zxing-3.3.3.
