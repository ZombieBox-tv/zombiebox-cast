# Pairing and Remote — dev.34

Select a discovered gateway or enter its URL, find screens, select the TV, compare
the generated six-digit code and accept locally on that TV. No pre-created TV code
is needed. Rejection can optionally suppress the requesting installation/source
for 24 hours; the TV checkbox is initially clear. Suppression is gateway-side and
survives restart. A random installation key is kept privately, not a hardware ID.

Alternatively open Pair phone on the TV and scan its single-use QR. Its secret
is valid for at most five minutes from creation and directly creates the scoped
grant; scanning does not require a second TV acceptance. Expiry/replay is enforced
by the gateway. The grant is revocable and still cannot administer the gateway or
bypass projection/receiver preferences. Old stored invitations keep old semantics.

Remote uses an explicit central directional pad, icon transport/volume rows and
three-column service marks. Icons have spoken labels and long-press descriptions;
service marks use local vector geometry, not a new native or network dependency.
Marks identify independent services and imply no endorsement. The Fire TV reference
in the workspace informs placement, not a copied proprietary view implementation.

Keyboard expands an on-screen text field. With a supported TV field focused,
Paste inserts at its selection. The focus lease, two-second command expiry and
at-most-once handling prevent redirect/replay into another field. Text is not
persisted or included in receipts. Passwords and settings/consent/system dialogs
are excluded; no OS-wide keyboard injection or Accessibility service is installed.
Phone input filters and the TV's own field limits still apply. Physical rendering,
TalkBack, keyboard focus and TV delivery remain unverified.
