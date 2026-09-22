# Android release identity

Client and Cast use a single author-selected RSA-3072 release identity, distinct
from development/debug keys. Application IDs remain separate; sharing a signer
does not grant either APK access to the other APK's private data.

Certificate SHA256:
`52bfdfc7614d1f357730cdb8757a470c227eda167be89f728ee6429678ce9756`

Release APKs require both v1 (legacy) and v2 signatures. Certificate matching,
non-debuggable manifests, stable package/minSdk and zip alignment are checked
before publication. These checks do not establish runtime compatibility or remove
Android's sideload/Play Protect policies.

The private key and password are never stored in Git, APKs, release attachments or
CI. Preserve the signing identity for updates. Existing debug-signed installations
cannot update to this release identity in place; uninstalling loses local app data.
Back up application configuration before deliberately changing installation tracks.

This checkpoint remains experimental; physical acceptance is deferred.
