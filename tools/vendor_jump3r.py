#!/usr/bin/env python3
"""Vendor the pure-Java LAME port (jump3r) used for MP3 export.

GossipAudio ships MP3 encoding with zero native code, so this copies the
`mp3` + `mpg` packages of https://github.com/tsichevski/jump3r (a Java port of
LAME, LGPL-2.1) into the app source tree and removes the bits that need
javax.sound (CLI/decoder classes we do not use) plus Eclipse JDT annotations.

Usage: python3 tools/vendor_jump3r.py /path/to/jump3r-checkout
"""
import os
import shutil
import sys

SRC = sys.argv[1] if len(sys.argv) > 1 else "/tmp/jump3r/src/main/java/ru/wowa/jump3r"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DST = os.path.join(ROOT, "app", "src", "main", "java", "ru", "wowa", "jump3r")

SKIP = {"GetAudio.java", "Parse.java", "package-info.java"}

def clean(text: str) -> str:
    text = text.replace("\r\n", "\n")
    out = []
    for line in text.split("\n"):
        if "org.eclipse.jdt.annotation" in line:
            continue
        line = line.replace("@Nullable ", "").replace("@NonNull ", "")
        line = line.replace("@Nullable", "").replace("@NonNull", "")
        out.append(line)
    return "\n".join(out)

count = 0
for pkg in ("mp3", "mpg"):
    src_dir = os.path.join(SRC, pkg)
    dst_dir = os.path.join(DST, pkg)
    os.makedirs(dst_dir, exist_ok=True)
    for name in sorted(os.listdir(src_dir)):
        if not name.endswith(".java") or name in SKIP:
            continue
        with open(os.path.join(src_dir, name), "r", encoding="utf-8", errors="replace") as fh:
            text = fh.read()
        with open(os.path.join(dst_dir, name), "w", encoding="utf-8") as fh:
            fh.write(clean(text))
        count += 1

readme = os.path.join(DST, "README.txt")
with open(readme, "w", encoding="utf-8") as fh:
    fh.write(
        "jump3r (Java port of LAME) — vendored subset\n"
        "=============================================\n"
        "Source : https://github.com/tsichevski/jump3r (copy of SourceForge jump3r)\n"
        "License: LGPL-2.1 (see /licenses/LGPL-2.1-jump3r-LAME.txt)\n"
        "Files  : mp3/*, mpg/* (GetAudio/Parse/CLI/LameEncoder omitted: javax.sound\n"
        "         is not available on Android; annotations stripped for zero deps)\n"
        "Used by: xyz.beyoneer.gossipaudio.core.Mp3Encoder for MP3 export.\n"
    )
print(f"vendored {count} java files into {DST}")
