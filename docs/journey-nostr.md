# Roadstr Journey: Nostr privacy and badge issuance

Journey works locally without a Nostr account. Tracking and Journey sync are separate opt-in settings and both default to off.

## Private Journey sync

The client stores versioned `roadstr.journey.v1` device shards as NIP-78 kind 30078 events. Each device owns separate addressable chunks; session IDs are deduplicated and a signed, encrypted manifest commits the exact chunk hashes. This prevents one phone's snapshot from replacing another phone's history and prevents a partial relay response from being treated as a complete device state.

Event content is encrypted to the user's own public key with NIP-44 before signing. Public tags contain only the format namespace and hashed device addressing. They never contain routes, coordinates, start/end addresses, or trip facts. If the active signer cannot encrypt and decrypt NIP-44, sync stops and retains local data; there is no cleartext fallback.

NIP-44 does not hide the account pubkey, event time, IP address, app namespace, or approximate ciphertext size. Relay replication also means deleting local history does not guarantee deletion of already published events. The UI states this during local deletion.

Simultaneous sessions from different devices remain distinct. Roadstr flags time overlaps for review instead of claiming it can prove that two GPS records represent the same real-world trip.

## NIP-58 achievements

The Android client implements strict parsing/building for:

- kind 30009 badge definitions;
- kind 8 immutable awards;
- kind 10008 profile badges, preserving existing badges and sets;
- kind 30008 badge sets.

Each catalog achievement exposes a stable NIP-58 `d` identifier. Local achievement unlocks remain independent from Nostr.

Official awards are intentionally not emitted by the APK. A future Roadstr issuer service must hold the issuer private key outside the app, validate an explicit award policy, and publish kind 30009 definitions and kind 8 awards. GPS-derived distance is client evidence, not an antifraud proof, and official/public wording must not imply otherwise.

No production issuer pubkey or stable HTTPS badge asset host exists in this checkout. Consequently the client does not fabricate definition events or asset URLs. Once those deployment inputs exist, the existing NIP-58 client can verify an award and, only after an explicit user action, append its `a`/`e` pair to kind 10008 without overwriting badges from other projects.

The local catalogue uses seventeen distinct code-native vector marks. These are deliberately independent from the public NIP-58 image URLs: an issuer deployment must publish stable raster or vector exports over HTTPS and reference those exact assets from the signed kind 30009 definitions.

## Enabling an official issuer in a build

The default `roadstr_badge_issuer_pubkey` Android resource is empty, so public-badge controls stay visibly unavailable and local achievements continue to work. A controlled distribution may override that resource with the 64-character lowercase hex public key of the Roadstr issuer. The private key must never be added to the repository, resource files, Gradle properties, or APK.

When configured, the catalogue still does not publish automatically. For each unlocked achievement the user must choose **Show on Nostr profile**. The client then:

1. fetches and verifies the issuer's kind 30009 definition, including a stable HTTPS image;
2. fetches and verifies a matching kind 8 award for the active account;
3. asks the active signer to update kind 10008;
4. preserves every existing profile-badge tag from Roadstr and other issuers.
