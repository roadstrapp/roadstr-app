#!/usr/bin/env python3
"""Golden test vectors for the Roadstr Nostr <-> compact binary (MeshCore) codec.

This is an INDEPENDENT implementation of the format: it shares no code with the
Kotlin app. It is stdlib-only (BIP-340 is transcribed from the reference
implementation in bitcoin/bips, and self-tested against the official vectors
below before anything is generated).

The format being implemented is the one of jooray/roadstr (nips/roadstr.md and
the reference Android client) -- see docs/interoperability/MESHCORE_NOSTR.md for
every place where the two jooray documents disagree and which side we follow.

Usage:
    python3 tools/interop/generate_vectors.py            # rewrites the JSON
    python3 tools/interop/generate_vectors.py --check    # fails if it is stale

Output: docs/interoperability/vectors/roadstr-nostr-meshcore-v1.json

The private keys in the output are throw-away TEST keys derived from a public
label. They must never be used for anything else.
"""
import argparse
import hashlib
import json
import os
import sys

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(REPO, "docs", "interoperability", "vectors", "roadstr-nostr-meshcore-v1.json")

# ── BIP-340 (transcribed from bitcoin/bips bip-0340/reference.py) ────────────

P = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F
N = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141
G = (
    0x79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798,
    0x483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8,
)


def _add(p1, p2):
    if p1 is None:
        return p2
    if p2 is None:
        return p1
    if p1[0] == p2[0] and p1[1] != p2[1]:
        return None
    if p1 == p2:
        lam = (3 * p1[0] * p1[0] * pow(2 * p1[1], P - 2, P)) % P
    else:
        lam = ((p2[1] - p1[1]) * pow(p2[0] - p1[0], P - 2, P)) % P
    x3 = (lam * lam - p1[0] - p2[0]) % P
    return (x3, (lam * (p1[0] - x3) - p1[1]) % P)


def _mul(point, k):
    result = None
    for i in range(256):
        if (k >> i) & 1:
            result = _add(result, point)
        point = _add(point, point)
    return result


def _tagged(tag, msg):
    th = hashlib.sha256(tag.encode()).digest()
    return hashlib.sha256(th + th + msg).digest()


def _int(b):
    return int.from_bytes(b, "big")


def _b32(x):
    return x.to_bytes(32, "big")


def _lift_x(x):
    if x >= P:
        return None
    y_sq = (pow(x, 3, P) + 7) % P
    y = pow(y_sq, (P + 1) // 4, P)
    if pow(y, 2, P) != y_sq:
        return None
    return (x, y if y % 2 == 0 else P - y)


def schnorr_pubkey(seckey: bytes) -> bytes:
    d0 = _int(seckey)
    assert 1 <= d0 <= N - 1
    return _b32(_mul(G, d0)[0])


def schnorr_sign(msg: bytes, seckey: bytes, aux: bytes) -> bytes:
    d0 = _int(seckey)
    assert 1 <= d0 <= N - 1 and len(msg) == 32 and len(aux) == 32
    pt = _mul(G, d0)
    d = d0 if pt[1] % 2 == 0 else N - d0
    t = bytes(a ^ b for a, b in zip(_b32(d), _tagged("BIP0340/aux", aux)))
    k0 = _int(_tagged("BIP0340/nonce", t + _b32(pt[0]) + msg)) % N
    assert k0 != 0
    r = _mul(G, k0)
    k = k0 if r[1] % 2 == 0 else N - k0
    e = _int(_tagged("BIP0340/challenge", _b32(r[0]) + _b32(pt[0]) + msg)) % N
    sig = _b32(r[0]) + _b32((k + e * d) % N)
    assert schnorr_verify(msg, _b32(pt[0]), sig)
    return sig


def schnorr_verify(msg: bytes, pubkey: bytes, sig: bytes) -> bool:
    if len(msg) != 32 or len(pubkey) != 32 or len(sig) != 64:
        return False
    pt = _lift_x(_int(pubkey))
    r, s = _int(sig[:32]), _int(sig[32:])
    if pt is None or r >= P or s >= N:
        return False
    e = _int(_tagged("BIP0340/challenge", sig[:32] + pubkey + msg)) % N
    rr = _add(_mul(G, s), _mul(pt, N - e))
    return rr is not None and rr[1] % 2 == 0 and rr[0] == r


def _self_test_bip340():
    # Official bitcoin/bips vectors 0 and 1.
    sk = bytes.fromhex("00" * 31 + "03")
    assert schnorr_pubkey(sk).hex() == "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9"
    sig = schnorr_sign(bytes(32), sk, bytes(32))
    assert sig.hex() == (
        "e907831f80848d1069a5371b402410364bdf1c5f8307b0084c55f1ce2dca8215"
        "25f66a4a85ea8b71e482a74f382d2ce5ebeee8fdb2172f477df4900d310536c0"
    ), sig.hex()
    sk = bytes.fromhex("b7e151628aed2a6abf7158809cf4f3c762e7160f38b4da56a784d9045190cfef")
    msg = bytes.fromhex("243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89")
    aux = bytes.fromhex("00" * 31 + "01")
    assert schnorr_sign(msg, sk, aux).hex() == (
        "6896bd60eeae296db48a229ff71dfe071bde413e6d43f917dc8dcf8c78de3341"
        "8906d11ac976abccb20b091292bff4ea897efcb639ea871cfa95f6de339e4b0a"
    )


# ── Format (jooray/roadstr nips/roadstr.md + reference client) ───────────────

TYPES = {  # value -> (wire string, effective TTL seconds)
    0: ("police", 7200), 1: ("speed_camera", 2592000), 2: ("traffic_jam", 3600),
    3: ("accident", 10800), 4: ("road_closure", 604800), 5: ("construction", 604800),
    6: ("hazard", 14400), 7: ("road_condition", 21600), 8: ("pothole", 604800),
    9: ("fog", 10800), 10: ("ice", 21600), 11: ("animal", 3600), 255: ("other", 7200),
}
RELAY_TTL = 1209600
BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
FLAG_CONFIRMATION = 0x08
FLAG_EPHEMERAL = 0x04  # informational only: the flags byte is NOT covered by the signature


def geohash(lat: float, lon: float, precision: int) -> str:
    lat_min, lat_max, lon_min, lon_max = -90.0, 90.0, -180.0, 180.0
    out, bits, count, is_lon = "", 0, 0, True
    while len(out) < precision:
        if is_lon:
            mid = (lon_min + lon_max) / 2
            if lon >= mid:
                bits, lon_min = (bits << 1) | 1, mid
            else:
                bits, lon_max = bits << 1, mid
        else:
            mid = (lat_min + lat_max) / 2
            if lat >= mid:
                bits, lat_min = (bits << 1) | 1, mid
            else:
                bits, lat_max = bits << 1, mid
        is_lon = not is_lon
        count += 1
        if count == 5:
            out += BASE32[bits]
            bits, count = 0, 0
    return out


def fmt_e7(raw: int) -> str:
    """Exact, locale-free decimal string of raw / 10^7 with 7 decimals."""
    sign = "-" if raw < 0 else ""
    mag = abs(raw)
    return f"{sign}{mag // 10_000_000}.{mag % 10_000_000:07d}"


def event_id(ev) -> str:
    ser = json.dumps(
        [0, ev["pubkey"], ev["created_at"], ev["kind"], ev["tags"], ev["content"]],
        separators=(",", ":"), ensure_ascii=False,
    )
    return hashlib.sha256(ser.encode("utf-8")).hexdigest()


def report_tags(lat_e7: int, lon_e7: int, type_name: str, created_at: int):
    lat, lon = lat_e7 / 1e7, lon_e7 / 1e7
    return [
        ["t", type_name],
        ["g", geohash(lat, lon, 4)], ["g", geohash(lat, lon, 5)], ["g", geohash(lat, lon, 6)],
        ["lat", fmt_e7(lat_e7)], ["lon", fmt_e7(lon_e7)],
        ["expiration", str(created_at + RELAY_TTL)],
        ["alt", f"Roadstr: {type_name} report"],
    ]


def confirmation_tags(ref_id: str, lat_e7: int, lon_e7: int, still_there: bool, created_at: int):
    lat, lon = lat_e7 / 1e7, lon_e7 / 1e7
    return [
        ["e", ref_id],
        ["g", geohash(lat, lon, 4)], ["g", geohash(lat, lon, 5)], ["g", geohash(lat, lon, 6)],
        ["status", "still_there" if still_there else "no_longer_there"],
        ["lat", fmt_e7(lat_e7)], ["lon", fmt_e7(lon_e7)],
        ["expiration", str(created_at + RELAY_TTL)],
        ["alt", "Roadstr: event confirmed" if still_there else "Roadstr: event denied"],
    ]


def test_key(label: str):
    sk = hashlib.sha256(("roadstr-interop-test-key/" + label).encode()).digest()
    aux = hashlib.sha256(("roadstr-interop-test-aux/" + label).encode()).digest()
    return sk, aux, schnorr_pubkey(sk)


def sign_event(label: str, created_at: int, kind: int, tags, content: str = ""):
    sk, aux, pub = test_key(label)
    ev = {"id": "", "pubkey": pub.hex(), "created_at": created_at, "kind": kind, "tags": tags, "content": content, "sig": ""}
    ev["id"] = event_id(ev)
    ev["sig"] = schnorr_sign(bytes.fromhex(ev["id"]), sk, aux).hex()
    return ev


def encode_report(ev, flags: int = 0, lat_e7=None, lon_e7=None, type_value=None) -> bytes:
    """Compact report: 110 bytes. lat/lon/type overrides exist only to craft broken vectors."""
    tags = {t[0]: t[1] for t in ev["tags"]}
    if lat_e7 is None:
        lat_e7 = parse_e7(tags["lat"])
    if lon_e7 is None:
        lon_e7 = parse_e7(tags["lon"])
    if type_value is None:
        type_value = next(v for v, (name, _) in TYPES.items() if name == tags["t"])
    out = bytes([flags]) + bytes.fromhex(ev["pubkey"]) + ev["created_at"].to_bytes(4, "big")
    out += lat_e7.to_bytes(4, "big", signed=True) + lon_e7.to_bytes(4, "big", signed=True)
    out += bytes([type_value]) + bytes.fromhex(ev["sig"])
    assert len(out) == 110
    return out


def encode_confirmation(ev, flags: int = 0) -> bytes:
    tags = {t[0]: t[1] for t in ev["tags"]}
    out = bytes([FLAG_CONFIRMATION | flags]) + bytes.fromhex(ev["pubkey"]) + ev["created_at"].to_bytes(4, "big")
    out += bytes.fromhex(tags["e"]) + bytes([1 if tags["status"] == "still_there" else 0]) + bytes.fromhex(ev["sig"])
    assert len(out) == 134
    return out


def parse_e7(text: str) -> int:
    sign = -1 if text.startswith("-") else 1
    whole, frac = text.lstrip("-").split(".")
    assert len(frac) == 7
    return sign * (int(whole) * 10_000_000 + int(frac))


def reference_truncating_encode(ev) -> bytes:
    """What the jooray reference encoder does: (lat * 1e7).toLong() truncates toward zero."""
    tags = {t[0]: t[1] for t in ev["tags"]}
    lat_e7 = int(float(tags["lat"]) * 1e7)
    lon_e7 = int(float(tags["lon"]) * 1e7)
    return encode_report(ev, lat_e7=lat_e7, lon_e7=lon_e7)


# ── Vector catalogue ─────────────────────────────────────────────────────────

T0 = 1_700_000_000


def report_vector(name, label, lat, lon, type_value=0, created_at=T0, flags=0, note=""):
    lat_e7, lon_e7 = parse_e7(lat), parse_e7(lon)
    ev = sign_event(label, created_at, 1315, report_tags(lat_e7, lon_e7, TYPES[type_value][0], created_at))
    return {
        "name": name, "class": "report", "note": note, "flags": flags,
        "latE7": lat_e7, "lonE7": lon_e7, "privateKey": test_key(label)[0].hex(),
        "binaryHex": encode_report(ev, flags=flags).hex(), "event": ev,
    }


def confirmation_vector(name, label, report_vec, still_there, created_at, flags=0, note=""):
    ref = report_vec["event"]
    tags = confirmation_tags(ref["id"], report_vec["latE7"], report_vec["lonE7"], still_there, created_at)
    ev = sign_event(label, created_at, 1316, tags)
    return {
        "name": name, "class": "confirmation", "note": note, "flags": FLAG_CONFIRMATION | flags,
        "privateKey": test_key(label)[0].hex(),
        "reference": {"eventId": ref["id"], "lat": fmt_e7(report_vec["latE7"]), "lon": fmt_e7(report_vec["lonE7"])},
        "binaryHex": encode_confirmation(ev, flags=flags).hex(), "event": ev,
    }


def build():
    valid = []
    valid.append(report_vector("report-paris-police", "alice", "48.8566140", "2.3522219", 0,
                               note="Worked example of the NIP."))
    valid.append(report_vector("report-negative-both", "bob", "-34.6037000", "-58.3816000", 3,
                               note="Both coordinates negative (int32 two's complement, '-' sign)."))
    valid.append(report_vector("report-max-corner", "alice", "90.0000000", "180.0000000", 6,
                               note="Largest legal coordinates."))
    valid.append(report_vector("report-min-corner", "alice", "-90.0000000", "-180.0000000", 7,
                               note="Smallest legal coordinates."))
    valid.append(report_vector("report-null-island", "carol", "0.0000000", "0.0000000", 2,
                               note="Zero must print as 0.0000000, never -0.0000000."))
    valid.append(report_vector("report-sub-degree-negative", "carol", "-0.0000005", "-0.5000000", 5,
                               note="Negative values below one degree keep their sign and leading 0."))
    valid.append(report_vector("report-one-lsb", "carol", "0.0000001", "-0.0000001", 8,
                               note="Smallest non-zero magnitude."))
    valid.append(report_vector("report-ephemeral-flag", "dave", "45.4642035", "9.1899960", 1, flags=FLAG_EPHEMERAL,
                               note="Flags byte 0x04. The flag is not signed: decoders treat it as a hint only."))
    valid.append(report_vector("report-created-at-above-int31", "erin", "41.9027835", "12.4963655", 9,
                               created_at=2_200_000_000,
                               note="created_at above 2^31 catches signed 32-bit decoders."))
    valid.append(report_vector("report-created-at-uint32-max", "erin", "41.9027835", "12.4963655", 10,
                               created_at=4_294_967_295,
                               note="Largest uint32; the expiration tag (+14 d) no longer fits in 32 bits."))
    valid.append(report_vector("report-type-other", "frank", "52.5200066", "13.4049540", 255,
                               note="Type 255 is representable only with an empty content."))
    valid.append(report_vector("report-type-animal", "frank", "59.3293235", "18.0685808", 11))
    # Coordinates a truncating encoder gets wrong (see the 'reference-truncation' group below).
    valid.append(report_vector("report-truncation-trap-a", "grace", "35.1976312", "-71.5429715", 4,
                               note="(35.1976312*1e7).toLong() == 351976311 in IEEE-754: a truncating encoder is off by one."))
    valid.append(report_vector("report-truncation-trap-b", "grace", "-12.3507796", "53.4982632", 4,
                               note="Same trap with the sign on the other axis."))
    # Geohash cell boundaries: >= ties decide the bit, so these pin the comparison.
    valid.append(report_vector("report-geohash-boundary-zero", "heidi", "0.0000000", "-0.0000001", 6,
                               note="lon one LSB below the first longitude split."))
    valid.append(report_vector("report-geohash-boundary-split", "heidi", "45.0000000", "90.0000000", 6,
                               note="Exactly on level-1 geohash split lines."))

    # Confirmations, bound to the reports above.
    by_name = {v["name"]: v for v in valid}
    paris = by_name["report-paris-police"]
    valid.append(confirmation_vector("confirmation-still-there", "bob", paris, True, T0 + 3600))
    valid.append(confirmation_vector("confirmation-no-longer-there", "carol", paris, False, T0 + 7200))
    valid.append(confirmation_vector("confirmation-negative-coordinates", "dave",
                                     by_name["report-negative-both"], True, T0 + 60,
                                     note="The referenced report has negative coordinates."))
    valid.append(confirmation_vector("confirmation-ephemeral-flag", "erin", paris, True, T0 + 120, flags=FLAG_EPHEMERAL))
    valid.append(confirmation_vector("confirmation-by-report-author", "alice", paris, True, T0 + 180,
                                     note="Same key as the report: still a valid, independent event."))

    return valid, build_non_representable(), build_reference_truncation(by_name)


def flip(data: bytes, index: int, mask: int) -> bytes:
    out = bytearray(data)
    out[index] ^= mask
    return bytes(out)


def build_invalid(by_name):
    rb = bytes.fromhex(by_name["report-paris-police"]["binaryHex"])
    out = []

    def add(name, cls, data, strict, note="", reference=None):
        entry = {"name": name, "class": cls, "note": note, "binaryHex": data.hex(), "expectStrict": strict}
        if reference is not None:
            entry["reference"] = reference
        out.append(entry)

    add("report-too-short", "report", rb[:-1], "BAD_LENGTH", "109 bytes: the SPECIFICATION heading says 109, the table sums to 110.")
    add("report-too-long", "report", rb + b"\x00", "BAD_LENGTH")
    add("report-empty", "report", b"", "BAD_LENGTH")
    add("report-version-1", "report", flip(rb, 0, 0x10), "BAD_VERSION", "Version nibble 1 is not defined.")
    add("report-version-15", "report", flip(rb, 0, 0xF0), "BAD_VERSION")
    add("report-reserved-bit-0", "report", flip(rb, 0, 0x01), "RESERVED_BITS", "Bits 1-0 are reserved and must be zero in v1.")
    add("report-reserved-bit-1", "report", flip(rb, 0, 0x02), "RESERVED_BITS")
    add("report-flagged-as-confirmation", "report", flip(rb, 0, 0x08), "BAD_LENGTH",
        "A 110-byte frame with the confirmation bit set is neither of the two layouts.")
    add("report-unknown-type-12", "report", rb[:45] + bytes([12]) + rb[46:], "UNKNOWN_TYPE",
        "Values 12-254 are reserved; they must NOT be mapped to 'other' (that would change the signed bytes).")
    add("report-unknown-type-254", "report", rb[:45] + bytes([254]) + rb[46:], "UNKNOWN_TYPE")
    big = (900_000_001).to_bytes(4, "big", signed=True)
    add("report-latitude-out-of-range", "report", rb[:37] + big + rb[41:], "COORDINATE_RANGE")
    add("report-latitude-int32-min", "report", rb[:37] + (-2**31).to_bytes(4, "big", signed=True) + rb[41:], "COORDINATE_RANGE")
    add("report-longitude-out-of-range", "report", rb[:41] + (1_800_000_001).to_bytes(4, "big", signed=True) + rb[45:], "COORDINATE_RANGE")
    add("report-longitude-negative-out-of-range", "report", rb[:41] + (-1_800_000_001).to_bytes(4, "big", signed=True) + rb[45:], "COORDINATE_RANGE")
    add("report-tampered-created-at", "report", flip(rb, 36, 0x01), "SIGNATURE_INVALID")
    add("report-tampered-latitude-lsb", "report", flip(rb, 40, 0x01), "SIGNATURE_INVALID")
    add("report-tampered-longitude-lsb", "report", flip(rb, 44, 0x01), "SIGNATURE_INVALID")
    add("report-tampered-type", "report", rb[:45] + bytes([1]) + rb[46:], "SIGNATURE_INVALID")
    add("report-tampered-pubkey", "report", flip(rb, 1, 0x01), "SIGNATURE_INVALID",
        "May also fail as an invalid x-only point; both map to SIGNATURE_INVALID.")
    add("report-tampered-signature", "report", flip(rb, 109, 0x01), "SIGNATURE_INVALID")
    add("report-zero-signature", "report", rb[:46] + bytes(64), "SIGNATURE_INVALID")
    add("report-all-zero", "report", bytes(110), "SIGNATURE_INVALID", "Structurally valid (v1, report, police at 0,0) but unsigned.")

    return out, rb


def finish_invalid(by_name, valid):
    """Confirmation-side invalid vectors need a valid confirmation as the base."""
    entries, _rb = build_invalid(by_name)
    conf = next(v for v in valid if v["name"] == "confirmation-still-there")
    cb = bytes.fromhex(conf["binaryHex"])
    ref = conf["reference"]

    def add(name, data, strict, note="", reference=ref):
        entries.append({"name": name, "class": "confirmation", "note": note, "binaryHex": data.hex(),
                        "expectStrict": strict, "reference": reference})

    add("confirmation-too-short", cb[:-1], "BAD_LENGTH")
    add("confirmation-too-long", cb + b"\x00", "BAD_LENGTH")
    add("confirmation-flagged-as-report", flip(cb, 0, 0x08), "BAD_LENGTH")
    add("confirmation-version-1", flip(cb, 0, 0x10), "BAD_VERSION")
    add("confirmation-reserved-bit", flip(cb, 0, 0x01), "RESERVED_BITS")
    add("confirmation-status-2", cb[:69] + bytes([2]) + cb[70:], "BAD_STATUS", "Status is 0 or 1; anything else is malformed.")
    add("confirmation-status-flipped", cb[:69] + bytes([0]) + cb[70:], "SIGNATURE_INVALID",
        "A relay flipping still_there into no_longer_there invalidates the signature.")
    tampered = flip(cb, 37, 0x01)
    add("confirmation-tampered-reference-id", tampered, "SIGNATURE_INVALID",
        "The report named by the damaged ID is resolved (same coordinates), but the signature covers the original ID.",
        reference={"eventId": tampered[37:69].hex(), "lat": ref["lat"], "lon": ref["lon"]})
    add("confirmation-reference-is-another-report", cb, "REFERENCE_MISMATCH",
        "The caller resolved a different report than the one the frame names.",
        reference={"eventId": "00" * 32, "lat": ref["lat"], "lon": ref["lon"]})
    add("confirmation-tampered-created-at", flip(cb, 36, 0x01), "SIGNATURE_INVALID")
    add("confirmation-wrong-reference-coordinates", cb, "SIGNATURE_INVALID",
        "The signed event embeds the report's coordinates: reconstructing it with a different report's coordinates must fail.",
        reference={"eventId": ref["eventId"], "lat": "-34.6037000", "lon": "-58.3816000"})
    return entries


def build_non_representable():
    """Valid, signed Nostr events that must NOT be encoded: a round trip would not reproduce the ID."""
    out = []
    lat_e7, lon_e7 = parse_e7("48.8566140"), parse_e7("2.3522219")
    good = report_tags(lat_e7, lon_e7, "police", T0)

    def add(name, label, tags, content, expect, kind=1315, note="", created_at=T0):
        ev = sign_event(label, created_at, kind, tags, content)
        out.append({"name": name, "note": note, "expectEncode": expect, "event": ev})

    add("content-is-not-empty", "alice", good, "Checking seatbelts", "NON_EMPTY_CONTENT",
        note="NIP: 'Events with non-empty .content cannot be represented in binary form.'")
    swapped = [good[0]] + [good[4], good[5]] + good[1:4] + good[6:]
    add("tags-in-roadstr-native-order", "alice", swapped, "", "NON_CANONICAL",
        note="Same information, different tag order -> different event ID.")
    add("latitude-with-six-decimals", "alice",
        [t if t[0] != "lat" else ["lat", "48.856614"] for t in good], "", "NON_CANONICAL")
    add("negative-zero-latitude", "alice",
        [t if t[0] != "lat" else ["lat", "-0.0000000"] for t in good], "", "NON_CANONICAL",
        note="A formatter that prints -0.0000000 produces an event no binary can reproduce.")
    add("expiration-is-type-ttl", "alice",
        [t if t[0] != "expiration" else ["expiration", str(T0 + 7200)] for t in good], "", "NON_CANONICAL",
        note="SPECIFICATION.md section 3.2 example (created_at + 2 h); the NIP and the client fix it at +14 d.")
    add("alt-missing", "alice", good[:-1], "", "NON_CANONICAL", note="alt is optional in the NIP but required to rebuild the ID.")
    add("alt-differs", "alice", good[:-1] + [["alt", "Roadstr: police report at 48.856614, 2.352222"]], "", "NON_CANONICAL",
        note="SPECIFICATION.md section 13.3 example; the NIP requires the exact canonical text.")
    add("extra-tag", "alice", good + [["client", "other"]], "", "NON_CANONICAL")
    add("unknown-type", "alice", [["t", "meteor-shower"]] + good[1:], "", "UNKNOWN_TYPE",
        note="Mapping it to 255 would silently change the signed 't' tag.")
    add("geohash-does-not-match-coordinates", "alice",
        [good[0], good[1], ["g", "u09tu"], ["g", "u09tun"]] + good[4:], "", "NON_CANONICAL",
        note="The g5/g6 values printed in SPECIFICATION.md section 15.1 for these coordinates; the real ones are u09tv/u09tvw.")
    add("latitude-out-of-range", "alice",
        [t if t[0] != "lat" else ["lat", "91.0000000"] for t in good], "", "COORDINATE_RANGE")
    add("created-at-does-not-fit-uint32", "alice", report_tags(lat_e7, lon_e7, "police", 4_294_967_296), "",
        "TIMESTAMP_RANGE", created_at=4_294_967_296,
        note="Otherwise canonical: only the 32-bit timestamp field makes it unrepresentable.")
    return out


def build_tolerated():
    """Events seen on public relays: the reference client formats lat/lon with the device locale."""
    out = []
    lat_e7, lon_e7 = parse_e7("49.0765652"), parse_e7("19.5999866")
    tags = report_tags(lat_e7, lon_e7, "police", T0)
    comma = [[t[0], t[1].replace(".", ",")] if t[0] in ("lat", "lon") else t for t in tags]
    ev = sign_event("zoe", T0, 1315, comma)
    out.append({
        "name": "report-decimal-comma", "class": "report",
        "note": "Seen in the wild (Slovak locale). A reader MAY show it; it can never be rebuilt from a frame.",
        "latE7": lat_e7, "lonE7": lon_e7, "expectEncode": "NON_CANONICAL", "event": ev,
    })
    return out


def build_reference_truncation(by_name):
    """Binaries a truncating encoder (the jooray reference) would emit for the *signed* event."""
    out = []
    for name in ("report-truncation-trap-a", "report-truncation-trap-b", "report-paris-police",
                 "report-negative-both", "report-sub-degree-negative"):
        v = by_name[name]
        data = reference_truncating_encode(v["event"])
        out.append({
            "name": "reference-truncation/" + name,
            "class": "report",
            "binaryHex": data.hex(),
            "matchesCorrectEncoding": data.hex() == v["binaryHex"],
            "expectedEventId": v["event"]["id"],
            "expectStrict": "SIGNATURE_INVALID" if data.hex() != v["binaryHex"] else None,
        })
    return out


def main():
    _self_test_bip340()
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail if the committed JSON is out of date")
    args = parser.parse_args()

    valid, non_repr, recover = build()
    tolerated = build_tolerated()
    by_name = {v["name"]: v for v in valid}
    invalid = finish_invalid(by_name, valid)

    # Independent self-verification of everything we are about to publish.
    for v in valid:
        ev = v["event"]
        assert event_id(ev) == ev["id"], v["name"]
        assert schnorr_verify(bytes.fromhex(ev["id"]), bytes.fromhex(ev["pubkey"]), bytes.fromhex(ev["sig"])), v["name"]
        data = bytes.fromhex(v["binaryHex"])
        assert len(data) == (110 if v["class"] == "report" else 134), v["name"]
    for v in non_repr:
        ev = v["event"]
        assert schnorr_verify(bytes.fromhex(ev["id"]), bytes.fromhex(ev["pubkey"]), bytes.fromhex(ev["sig"])), v["name"]

    doc = {
        "format": "roadstr-nostr-meshcore-vectors",
        "version": 1,
        "generator": "tools/interop/generate_vectors.py",
        "description": (
            "Golden vectors for the compact binary form of Roadstr road-event reports (Nostr kind 1315) and "
            "confirmations (kind 1316). 'valid': decoding binaryHex (with 'reference' for confirmations) must "
            "rebuild exactly 'event' (same id, pubkey, sig) and the signature must verify; encoding 'event' must "
            "give exactly binaryHex (except flags, which are not signed). 'invalid': decoding must fail with "
            "expectStrict. 'nonRepresentable': encoding must be refused with expectEncode. 'tolerated': genuine events with a known client bug (decimal comma) that a reader may accept but that must never be encoded. 'referenceTruncation': "
            "binaries emitted by the truncating reference encoder; strict decoding fails, tolerant decoding must "
            "recover expectedEventId."
        ),
        "warning": "All private keys here are public test keys. Never use them for anything else.",
        "constants": {
            "reportSize": 110, "confirmationSize": 134, "relayTtlSeconds": RELAY_TTL,
            "types": {str(k): v[0] for k, v in TYPES.items()},
            "effectiveTtlSeconds": {v[0]: v[1] for v in TYPES.values()},
        },
        "valid": valid,
        "invalid": invalid,
        "nonRepresentable": non_repr,
        "tolerated": tolerated,
        "referenceTruncation": recover,
    }
    text = json.dumps(doc, indent=2, ensure_ascii=False) + "\n"
    if args.check:
        current = open(OUT, encoding="utf-8").read() if os.path.exists(OUT) else ""
        if current != text:
            print("vectors are out of date: run tools/interop/generate_vectors.py", file=sys.stderr)
            return 1
        print("vectors are up to date")
        return 0
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as handle:
        handle.write(text)
    print(f"wrote {os.path.relpath(OUT, REPO)}: {len(valid)} valid, {len(invalid)} invalid, "
          f"{len(non_repr)} non-representable, {len(recover)} reference-truncation")
    return 0


if __name__ == "__main__":
    sys.exit(main())
