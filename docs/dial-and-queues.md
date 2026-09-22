# Native launch and gateway-owned URL queues

Pairing → **Open Client on TV** probes DIAL SSDP on private IPv4 LAN addresses.
The sender accepts only same-host numeric HTTP locations, bounded XML without DTDs,
and an existing `ZombieBox` app endpoint. A user must choose a device and launch it;
the sender confirms `running` before reporting success. Failed launches have a
bounded cooldown. It sends only `screen=home`, never a pairing secret. If native
DIAL is absent, disabled, unregistered or unreachable, open the Client manually and
use ordinary discovery, URL or its five-minute QR invitation.

This uses the [published Fire TV DIAL contract](https://developer.amazon.com/docs/fire-tv/dial-integration.html).
It does not register an app name or invent a Google TV/Vizio private API. The Client's
Whisperplay entry remains opt-in; DIAL launch and actual media reception are separate.

Media → **Web links & queue** accepts up to sixteen public direct HTTP(S) file URLs.
Android text shares only prefill this screen; pressing Play queue is required.
The gateway downloads/probes each file, then advances on owned TV completion. Closing
this screen keeps the queue running; Stop queue, receiver replacement, revocation or
failure ends it. Status restores by asking the gateway, without saving source URLs
on the phone. Entries have 256-MiB/two-minute download limits. HTML, arbitrary
manifests, private-network URLs and URL usernames/passwords are unsupported.

Native launch, real provider media and OEM behavior await physical acceptance.
