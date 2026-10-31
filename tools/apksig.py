#!/usr/bin/env python3
"""Minimal but real APK signature verifier (APK Signature Scheme v2 + v3).

Why this exists: an `.apk` is a ZIP container, so "is it a real app or just a renamed zip?"
can only be answered by checking that a signed digest covers the whole archive and that the
signature verifies against the certificate baked into it. This module does that with the
standard library only — no `apksigner`, no SDK, no network.

It implements, per the Android source (AOSP `ApkSignatureSchemeV2Verifier`):
  * the APK Signing Block layout (magic, sizes, ID-value pairs)
  * the v2 signer block: signed-data / signatures / public-key
  * the content digest over 1 MiB chunks (0xA5 prefix, 0x5A top-level digest)
  * RSA PKCS#1 v1.5 verification with SHA-256 / SHA-512 and a tiny DER reader

Supported: RSA (PKCS#1 v1.5, SHA-256/SHA-512). Not supported: ECDSA/PSS signers — the
verifier reports them instead of pretending.
"""
import hashlib
import struct

APK_SIG_BLOCK_MAGIC = b"APK Sig Block 42"
V2_BLOCK_ID = 0x7109871A
V3_BLOCK_ID = 0xF05368C0

# signature algorithm ids (AOSP apksig)
ALG_RSA_PSS_SHA256 = 0x0101
ALG_RSA_PSS_SHA512 = 0x0102
ALG_RSA_PKCS1_SHA256 = 0x0103
ALG_RSA_PKCS1_SHA512 = 0x0104
ALG_ECDSA_SHA256 = 0x0201
ALG_ECDSA_SHA512 = 0x0202
ALG_DSA_SHA256 = 0x0301

ALG_NAMES = {
    ALG_RSA_PSS_SHA256: "RSA-PSS/SHA-256",
    ALG_RSA_PSS_SHA512: "RSA-PSS/SHA-512",
    ALG_RSA_PKCS1_SHA256: "RSA-PKCS1/SHA-256",
    ALG_RSA_PKCS1_SHA512: "RSA-PKCS1/SHA-512",
    ALG_ECDSA_SHA256: "ECDSA/SHA-256",
    ALG_ECDSA_SHA512: "ECDSA/SHA-512",
    ALG_DSA_SHA256: "DSA/SHA-256",
}

# DER prefix of DigestInfo for PKCS#1 v1.5
DIGEST_INFO_PREFIX = {
    "sha256": bytes.fromhex("3031300d060960864801650304020105000420"),
    "sha512": bytes.fromhex("3051300d060960864801650304020305000440"),
}


class LengthPrefixed:
    """Reader for the uint32-length-prefixed little-endian sequences used inside the block."""

    def __init__(self, data):
        self.data = data
        self.pos = 0

    def u32(self):
        v = struct.unpack_from("<I", self.data, self.pos)[0]
        self.pos += 4
        return v

    def item(self):
        n = self.u32()
        if self.pos + n > len(self.data):
            raise ValueError("truncated length-prefixed item")
        out = self.data[self.pos:self.pos + n]
        self.pos += n
        return out

    def sequence(self):
        inner = self.item()
        return LengthPrefixed(inner)

    def remaining(self):
        return len(self.data) - self.pos


def _find_signing_block(blob):
    eocd = blob.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise ValueError("no ZIP end-of-central-directory record")
    cd_offset = struct.unpack_from("<I", blob, eocd + 16)[0]
    if blob[cd_offset - 16:cd_offset] != APK_SIG_BLOCK_MAGIC:
        # Not necessarily an error: a v1-only APK has no signing block.
        return None
    block_size = struct.unpack_from("<Q", blob, cd_offset - 24)[0]
    block_start = cd_offset - block_size - 8
    if block_start < 0:
        raise ValueError("signing block size out of range")
    if struct.unpack_from("<Q", blob, block_start)[0] != block_size:
        raise ValueError("signing block header/footer size mismatch")
    return block_start, cd_offset, block_size, eocd


def _pairs(blob, block_start, block_end):
    """Yield (id, value) pairs of the APK Signing Block."""
    pos = block_start + 8
    while pos + 12 <= block_end:
        length = struct.unpack_from("<Q", blob, pos)[0]
        ident = struct.unpack_from("<I", blob, pos + 8)[0]
        value_start = pos + 12
        value_end = pos + 8 + length
        if value_end > block_end or length < 4:
            raise ValueError("malformed signing block pair")
        yield ident, blob[value_start:value_end]
        pos = value_end


def content_digest(blob, block_start, cd_offset, eocd_offset, hash_name="sha256"):
    """The v2/v3 APK content digest.

    Sections, each chunked independently in 1 MiB slices (a chunk never spans sections):
      1. contents of ZIP entries  — everything before the APK Signing Block
      2. central directory
      3. end-of-central-directory record, with its "central directory offset" field
         rewritten to the pre-signing value (apksig does the same ifdef-style patch)

    Per-chunk digest = H(0xA5 || uint32le(chunk length) || chunk)
    Final digest     = H(0x5A || uint32le(chunk count) || chunk digests)
    """
    eocd = bytearray(blob[eocd_offset:])
    if len(eocd) >= 20 and eocd[:4] == b"PK\x05\x06":
        struct.pack_into("<I", eocd, 16, block_start)
    sections = [blob[:block_start], blob[cd_offset:eocd_offset], bytes(eocd)]
    chunk_digests = []
    chunk_size = 1024 * 1024
    for section in sections:
        for start in range(0, len(section), chunk_size):
            chunk = section[start:start + chunk_size]
            h = hashlib.new(hash_name)
            h.update(b"\xa5")
            h.update(struct.pack("<I", len(chunk)))
            h.update(chunk)
            chunk_digests.append(h.digest())
    top = hashlib.new(hash_name)
    top.update(b"\x5a")
    top.update(struct.pack("<I", len(chunk_digests)))
    for d in chunk_digests:
        top.update(d)
    return top.digest(), len(chunk_digests)


# --------------------------------------------------------------------- tiny DER reader

def _der(data, offset):
    tag = data[offset]
    first = data[offset + 1]
    if first & 0x80:
        n = first & 0x7F
        length = int.from_bytes(data[offset + 2:offset + 2 + n], "big")
        header = 2 + n
    else:
        length = first
        header = 2
    start = offset + header
    return tag, data[start:start + length], start + length


def _rsa_public_key(spki):
    """SubjectPublicKeyInfo DER -> (modulus, exponent)."""
    tag, seq, _ = _der(spki, 0)                    # SEQUENCE SubjectPublicKeyInfo
    _t, _alg, next_off = _der(seq, 0)              # AlgorithmIdentifier
    tag, bitstring, _ = _der(seq, next_off)        # BIT STRING
    if tag != 0x03:
        raise ValueError("expected BIT STRING in SubjectPublicKeyInfo")
    key = bitstring[1:]                            # drop 'unused bits' byte
    _t, rsaseq, _ = _der(key, 0)                   # SEQUENCE RSAPublicKey
    _t, modulus, off = _der(rsaseq, 0)
    _t, exponent, _ = _der(rsaseq, off)
    return int.from_bytes(modulus, "big"), int.from_bytes(exponent, "big")


def _cert_public_key(cert_der):
    tag, cert, _ = _der(cert_der, 0)               # Certificate
    if tag != 0x30:
        raise ValueError("not a DER certificate")
    tag, tbs, _ = _der(cert, 0)                    # TBSCertificate
    pos = 0
    if tbs[0] == 0xA0:                             # [0] version
        _t, _v, pos = _der(tbs, 0)
    fields = []
    while pos < len(tbs) and len(fields) < 6:      # serial, sigAlg, issuer, validity, subject, SPKI
        start = pos
        t, v, pos = _der(tbs, pos)
        fields.append((t, v, start, pos))
    if len(fields) < 6:
        raise ValueError("unexpected TBSCertificate layout")
    _t, _v, spki_start, spki_end = fields[5]
    return _rsa_public_key(tbs[spki_start:spki_end])


def _verify_pkcs1(public_key, signature, digest, hash_name):
    n, e = public_key
    k = (n.bit_length() + 7) // 8
    if len(signature) != k:
        return False
    m = pow(int.from_bytes(signature, "big"), e, n)
    em = m.to_bytes(k, "big")
    prefix = b"\x00\x01"
    if not em.startswith(prefix):
        return False
    rest = em[2:]
    sep = rest.find(b"\x00")
    if sep < 8:
        return False
    if rest[:sep] != b"\xff" * sep:
        return False
    tail = rest[sep + 1:]
    expected = DIGEST_INFO_PREFIX[hash_name] + digest
    return tail == expected


def verify(blob):
    """Return a report dict: {schemes, signers:[{algorithm, certificate, digest_ok, signature_ok}], ...}"""
    block = _find_signing_block(blob)
    if block is None:
        return {"ok": False, "schemes": [], "reason": "no APK Signing Block (v1-only or unsigned)"}
    block_start, cd_offset, block_size, eocd_offset = block
    report = {
        "ok": False,
        "schemes": [],
        "block_bytes": block_size,
        "signers": [],
        "reason": "",
    }
    digests_by_hash = {}
    for name in ("sha256", "sha512"):
        digests_by_hash[name], chunks = content_digest(
            blob, block_start, cd_offset, eocd_offset, name
        )
    report["content_digest_sha256"] = digests_by_hash["sha256"].hex()
    report["chunks"] = chunks

    v2 = None
    try:
        for ident, value in _pairs(blob, block_start, cd_offset - 24):
            if ident == V2_BLOCK_ID:
                report["schemes"].append("v2")
                v2 = value
            elif ident == V3_BLOCK_ID:
                report["schemes"].append("v3")
    except ValueError as exc:
        report["reason"] = f"malformed signing block: {exc}"
        return report
    if v2 is None:
        report["reason"] = "no v2 signer block (v3-only APKs are not parsed here)"
        return report

    try:
        signers = LengthPrefixed(v2).sequence()
    except ValueError as exc:
        report["reason"] = f"malformed v2 signer block: {exc}"
        return report
    count = 0
    while signers.remaining() > 0:
        signer = signers.sequence()
        signed_data = signer.item()                 # length-prefixed signed data (must be verified verbatim)
        signatures = signer.sequence()
        public_key = signer.item()
        count += 1

        # --- signature over signed_data
        sig_alg = 0
        signature = b""
        while signatures.remaining() > 0:
            # signature record: length-prefixed (uint32 algorithm, length-prefixed signature)
            record = signatures.sequence()
            alg = record.u32()
            sig = record.item()
            if alg in (ALG_RSA_PKCS1_SHA256, ALG_RSA_PKCS1_SHA512):
                sig_alg, signature = alg, sig
                break
        info = {"algorithm": ALG_NAMES.get(sig_alg, hex(sig_alg)), "signature_ok": False, "digest_ok": False}
        hash_name = "sha256" if sig_alg == ALG_RSA_PKCS1_SHA256 else "sha512"
        expected_digest = digests_by_hash[hash_name]

        # --- digests + certificate from signed_data
        sd = LengthPrefixed(signed_data)
        digests = sd.sequence()
        certs = sd.sequence()
        first_digest = None
        while digests.remaining() > 0:
            # digest record: length-prefixed (uint32 algorithm, length-prefixed digest)
            record = digests.sequence()
            alg = record.u32()
            value = record.item()
            if first_digest is None:
                first_digest = (alg, value)
            if alg == sig_alg:
                info["digest_ok"] = value == expected_digest
                info["chunk_digest"] = value.hex()[:32]
        cert_der = certs.item()
        info["certificate_bytes"] = len(cert_der)
        info["certificate_sha256"] = hashlib.sha256(cert_der).hexdigest()[:32]

        try:
            public = _cert_public_key(cert_der)
            info["key_bits"] = (public[0].bit_length() // 8) * 8
            digest = hashlib.new(hash_name, signed_data).digest()
            info["signature_ok"] = _verify_pkcs1(public, signature, digest, hash_name)
        except Exception as exc:                     # noqa: BLE001 - report, never crash the verifier
            info["error"] = str(exc)

        report["signers"].append(info)

    report["signer_count"] = count
    report["ok"] = bool(
        count > 0
        and all(s.get("signature_ok") and s.get("digest_ok") for s in report["signers"])
    )
    if not report["ok"]:
        report["reason"] = "signature or content digest did not verify"
    return report


def verify_file(path):
    with open(path, "rb") as fh:
        return verify(fh.read())


if __name__ == "__main__":
    import json
    import sys

    target = sys.argv[1] if len(sys.argv) > 1 else "dist/GossipAudio-1.0.0-release.apk"
    result = verify_file(target)
    print(json.dumps(result, indent=2))
    sys.exit(0 if result.get("ok") else 1)
