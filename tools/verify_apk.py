#!/usr/bin/env python3
"""Verify the built APK: structure, binary manifest, signing blocks and dex contents.

This intentionally avoids external tooling (no aapt/apksigner available in the
sandbox) and parses Android's binary XML + APK Signing Block by hand.
"""
import struct, sys, zipfile, os, json, hashlib, re

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import apksig  # noqa: E402  (local module, real v2/v3 signature verification)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def gradle_version():
    """Read the expected version straight from build.gradle.kts so bumps never desync CI."""
    try:
        text = open(os.path.join(ROOT, "app", "build.gradle.kts"), encoding="utf-8").read()
        name = re.search(r'versionName\s*=\s*"([^"]+)"', text).group(1)
        code = re.search(r"versionCode\s*=\s*(\d+)", text).group(1)
        return name, code
    except Exception:
        return None, None


EXPECTED_NAME, EXPECTED_CODE = gradle_version()
APK = sys.argv[1] if len(sys.argv) > 1 else "dist/GossipAudio-%s-release.apk" % (EXPECTED_NAME or "1.0.0")

RES_STRING_POOL = 0x0001
RES_XML = 0x0003
RES_XML_START_ELEMENT = 0x0102
RES_XML_END_ELEMENT = 0x0103
RES_XML_RESOURCE_MAP = 0x0180

TYPE_NULL = 0x00
TYPE_REFERENCE = 0x01
TYPE_STRING = 0x03
TYPE_INT_DEC = 0x10
TYPE_INT_HEX = 0x11
TYPE_INT_BOOLEAN = 0x12


def read_string_pool(data, off):
    header_size = struct.unpack_from("<H", data, off + 2)[0]
    count = struct.unpack_from("<I", data, off + 8)[0]
    flags = struct.unpack_from("<I", data, off + 16)[0]
    strings_start = struct.unpack_from("<I", data, off + 20)[0]
    utf8 = bool(flags & (1 << 8))
    offsets = [struct.unpack_from("<I", data, off + header_size + i * 4)[0] for i in range(count)]
    out = []
    base = off + strings_start
    for o in offsets:
        p = base + o
        if utf8:
            # utf16 length then utf8 length, both variable length
            def var_len(p):
                n = data[p]
                if n & 0x80:
                    return ((n & 0x7F) << 8) | data[p + 1], p + 2
                return n, p + 1
            _, p = var_len(p)
            blen, p = var_len(p)
            out.append(data[p:p + blen].decode("utf-8", "replace"))
        else:
            n = struct.unpack_from("<H", data, p)[0]
            p += 2
            if n & 0x8000:
                n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", data, p)[0]
                p += 2
            out.append(data[p:p + n * 2].decode("utf-16-le", "replace"))
    return out


def parse_axml(data):
    """Return a nested list of {tag, attrs, children} describing the document."""
    off = 8  # skip XML file header
    strings = []
    resmap = []
    root = {"tag": "#root", "attrs": {}, "children": []}
    stack = [root]
    while off < len(data):
        chunk_type, header_size, size = struct.unpack_from("<HHI", data, off)
        if size <= 0:
            break
        if chunk_type == RES_STRING_POOL:
            strings = read_string_pool(data, off)
        elif chunk_type == RES_XML_RESOURCE_MAP:
            n = (size - header_size) // 4
            resmap = [struct.unpack_from("<I", data, off + header_size + i * 4)[0] for i in range(n)]
        elif chunk_type == RES_XML_START_ELEMENT:
            ext = off + header_size
            ns, name_idx, attr_start, attr_size, attr_count = struct.unpack_from("<IIHHH", data, ext)
            attrs = {}
            p = ext + attr_start
            for _ in range(attr_count):
                a_ns, a_name, a_raw = struct.unpack_from("<III", data, p)
                v_size, v_res0, a_type = struct.unpack_from("<HBB", data, p + 12)
                a_data = struct.unpack_from("<i", data, p + 16)[0]
                p += 20  # ns(4)+name(4)+raw(4)+size(2)+res0(1)+type(1)+data(4)
                key = strings[a_name] if a_name < len(strings) else str(a_name)
                if a_type == TYPE_STRING:
                    val = strings[a_data] if a_data < len(strings) else str(a_data)
                elif a_type == TYPE_INT_BOOLEAN:
                    val = "true" if a_data else "false"
                elif a_type == TYPE_INT_HEX:
                    val = hex(a_data)
                elif a_type == TYPE_REFERENCE:
                    val = "@ref/0x%08x" % a_data
                else:
                    val = str(a_data)
                attrs[key] = val
            node = {"tag": strings[name_idx] if name_idx < len(strings) else "?", "attrs": attrs, "children": []}
            stack[-1]["children"].append(node)
            stack.append(node)
        elif chunk_type == RES_XML_END_ELEMENT:
            if len(stack) > 1:
                stack.pop()
        off += size
    return root


def walk(node, out):
    out.append(node)
    for c in node["children"]:
        walk(c, out)


def verify(apk):
    problems, notes = [], []
    z = zipfile.ZipFile(apk)
    names = z.namelist()
    total = sum(i.file_size for i in z.infolist())  # uncompressed payload size

    # --- required entries
    for req in ["AndroidManifest.xml", "resources.arsc"]:
        if req not in names:
            problems.append(f"missing {req}")
    dexes = sorted(n for n in names if n.startswith("classes") and n.endswith(".dex"))
    if not dexes:
        problems.append("no classes.dex")
    for n in names:
        if n.endswith(".so"):
            notes.append(f"native lib present: {n}")
    pngs = [n for n in names if n.startswith("res/") and n.endswith(".png")]
    if len(pngs) < 4:
        problems.append(f"too few packaged images ({len(pngs)})")
    notes.append("packaged PNG resources: %d (names are obfuscated by AGP)" % len(pngs))

    # --- manifest
    manifest = parse_axml(z.read("AndroidManifest.xml"))
    nodes = []
    walk(manifest, nodes)
    tags = {}
    for n in nodes:
        tags.setdefault(n["tag"], []).append(n)
    app = tags.get("application", [None])[0]
    if not app:
        problems.append("no <application>")
        return problems, notes, {}
    manifest_info = {
        "package": tags["manifest"][0]["attrs"].get("package"),
        "versionName": tags["manifest"][0]["attrs"].get("versionName"),
        "versionCode": tags["manifest"][0]["attrs"].get("versionCode"),
        "minSdk": tags["uses-sdk"][0]["attrs"].get("minSdkVersion") if "uses-sdk" in tags else None,
        "targetSdk": tags["uses-sdk"][0]["attrs"].get("targetSdkVersion") if "uses-sdk" in tags else None,
        "appClass": app["attrs"].get("name"),
        "label": app["attrs"].get("label"),
        "icon": app["attrs"].get("icon"),
        "permissions": [n["attrs"].get("name") for n in tags.get("uses-permission", [])],
        "activities": [n["attrs"].get("name") for n in tags.get("activity", [])],
        "services": [n["attrs"].get("name") for n in tags.get("service", [])],
        "receivers": [n["attrs"].get("name") for n in tags.get("receiver", [])],
        "providers": [n["attrs"].get("name") for n in tags.get("provider", [])],
    }
    if manifest_info["package"] != "xyz.beyoneer.gossipaudio":
        problems.append(f"unexpected package {manifest_info['package']}")
    if EXPECTED_NAME and manifest_info["versionName"] != EXPECTED_NAME:
        problems.append(
            f"versionName {manifest_info['versionName']} does not match build.gradle.kts ({EXPECTED_NAME})"
        )
    if EXPECTED_CODE and str(manifest_info["versionCode"]) != str(EXPECTED_CODE):
        problems.append(
            f"versionCode {manifest_info['versionCode']} does not match build.gradle.kts ({EXPECTED_CODE})"
        )
    if not manifest_info["activities"]:
        problems.append("no activity declared")
    launcher = False
    for n in tags.get("activity", []):
        for c in n["children"]:
            if c["tag"] == "intent-filter":
                for cc in c["children"]:
                    if cc["tag"] == "action" and cc["attrs"].get("name") == "android.intent.action.MAIN":
                        launcher = True
    if not launcher:
        problems.append("launcher activity has no MAIN action")
    if "android.permission.INTERNET" in manifest_info["permissions"]:
        notes.append("INTERNET permission declared (audio still local)")
    else:
        notes.append("no INTERNET permission — audio pipeline is genuinely offline")

    # --- signatures (APK Signing Block sits right before the central directory)
    with open(apk, "rb") as fh:
        blob = fh.read()
    eocd = blob.rfind(b"PK\x05\x06")
    cd_off = struct.unpack_from("<I", blob, eocd + 16)[0]
    sig_ids = []
    sigblock = blob[cd_off - 16:cd_off] == b"APK Sig Block 42"
    if sigblock:
        total = struct.unpack_from("<Q", blob, cd_off - 24)[0]
        start = cd_off - total - 8
        p = start + 8
        while p < cd_off - 24:
            pair_len = struct.unpack_from("<Q", blob, p)[0]
            pair_id = struct.unpack_from("<I", blob, p + 8)[0]
            sig_ids.append(pair_id)
            p += 8 + pair_len
    v1 = [n for n in names if n.startswith("META-INF/") and n.endswith((".RSA", ".DSA", ".EC", ".SF"))]
    schemes = []
    if v1:
        schemes.append("v1")
    if 0x7109871A in sig_ids:
        schemes.append("v2")
    if 0xF05368C0 in sig_ids:
        schemes.append("v3")
    if not schemes:
        problems.append("APK appears unsigned")
    notes.append("signature schemes: %s (block ids: %s)" % (
        ", ".join(schemes) if schemes else "none",
        ", ".join(hex(i) for i in sig_ids) if sig_ids else "none"))
    notes.append("v1 signature files: %s" % (", ".join(v1) if v1 else "none (fine for minSdk 24+)"))

    # --- real cryptographic verification: digest covers the archive, RSA signature covers the signer
    sig = apksig.verify(blob)
    if sig.get("ok"):
        for signer in sig["signers"]:
            notes.append(
                "signature VERIFIED: %s / RSA-%d, certificate sha256 %s..., %d-byte signed block, %d chunks"
                % (signer.get("algorithm"), signer.get("key_bits", 0),
                   signer.get("certificate_sha256"), sig.get("block_bytes"), sig.get("chunks"))
            )
        manifest_info["signature"] = {
            "verified": True,
            "schemes": sig["schemes"],
            "algorithm": sig["signers"][0].get("algorithm"),
            "key_bits": sig["signers"][0].get("key_bits"),
            "certificate_sha256": sig["signers"][0].get("certificate_sha256"),
        }
    else:
        problems.append("signature did not verify: %s" % (sig.get("reason") or "unknown reason"))
        manifest_info["signature"] = {"verified": False, "reason": sig.get("reason")}

    # --- dex sanity: our classes must exist in the dex string table
    dex_blob = b"".join(z.read(n) for n in dexes)
    for cls in [
        b"Lxyz/beyoneer/gossipaudio/ui/MainActivity;",
        b"Lxyz/beyoneer/gossipaudio/GossipApp;",
        b"Lxyz/beyoneer/gossipaudio/playback/PlaybackService;",
        b"Lxyz/beyoneer/gossipaudio/core/NanoVoice;",
        b"Lxyz/beyoneer/gossipaudio/core/Mp3Encoder;",
        b"Lru/wowa/jump3r/mp3/Lame;",
    ]:
        if cls not in dex_blob:
            problems.append(f"dex missing {cls.decode()}")
    notes.append("dex files: %s (%d bytes uncompressed)" % (", ".join(dexes), sum(z.getinfo(n).file_size for n in dexes)))

    # --- resource string pool should contain the brand + tagline
    arsc = z.read("resources.arsc")
    for needle in ["GossipAudio", "Beyoneer XYZ", "gossiping inreal"]:
        found = needle.encode("utf-8") in arsc or needle.encode("utf-16-le") in arsc
        if not found:
            problems.append(f"resources.arsc missing string: {needle}")

    manifest_info["entries"] = len(names)
    manifest_info["uncompressed_bytes"] = sum(i.file_size for i in z.infolist())
    manifest_info["dex_bytes"] = sum(z.getinfo(n).file_size for n in dexes)
    manifest_info["apk_sha256"] = hashlib.sha256(open(apk, "rb").read()).hexdigest()
    return problems, notes, manifest_info


if __name__ == "__main__":
    problems, notes, info = verify(APK)
    print("=== APK verification:", APK)
    print(json.dumps(info, indent=2))
    print("\n--- notes")
    for n in notes:
        print(" *", n)
    print("\n--- problems")
    if problems:
        for p in problems:
            print(" !", p)
        sys.exit(1)
    print(" none — APK structure, manifest, signing and dex contents check out")
