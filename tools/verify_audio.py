#!/usr/bin/env python3
"""Validate the audio the app produced: RIFF/WAVE headers and MPEG-1/2 Layer III frames + ID3v2 tags."""
import struct, sys, os, glob

BITRATES = {
    # (version_group, layer) -> kbps table ; index by bitrate_index
    ("mpeg1", 3): [0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0],
    ("mpeg2", 3): [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0],
}
RATES = {
    3: [44100, 48000, 32000, 0],   # MPEG1
    2: [22050, 24000, 16000, 0],   # MPEG2
    0: [11025, 12000, 8000, 0],    # MPEG2.5
}


def check_wav(path):
    data = open(path, "rb").read()
    if data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        return None, ["not a RIFF/WAVE file"]
    pos, fmt, data_size = 12, None, 0
    while pos + 8 <= len(data):
        cid = data[pos:pos + 4]
        size = struct.unpack_from("<I", data, pos + 4)[0]
        body = pos + 8
        if cid == b"fmt ":
            audio_format, channels, rate, byte_rate, block_align, bits = struct.unpack_from("<HHIIHH", data, body)
            fmt = dict(format=audio_format, channels=channels, rate=rate, bits=bits, block_align=block_align)
        elif cid == b"data":
            data_size = size
            break
        pos = body + size + (size & 1)
    problems = []
    if fmt is None:
        problems.append("missing fmt chunk")
    if data_size == 0:
        problems.append("empty data chunk")
    if fmt:
        if fmt["format"] != 1:
            problems.append("not PCM")
        if fmt["bits"] != 16:
            problems.append("not 16-bit")
        expected = data_size / (fmt["rate"] * fmt["channels"] * 2) * 1000
        fmt["duration_ms"] = round(expected)
        if expected < 300:
            problems.append("shorter than 300 ms")
    return fmt, problems


def check_mp3(path):
    data = open(path, "rb").read()
    problems = []
    tag = None
    if data[:3] == b"ID3":
        major, rev, flags = data[3], data[4], data[5]
        size = ((data[6] & 0x7F) << 21) | ((data[7] & 0x7F) << 14) | ((data[8] & 0x7F) << 7) | (data[9] & 0x7F)
        tag = dict(version=f"2.{major}.{rev}", bytes=size + 10, frames=[])
        body = data[10:10 + size]
        p = 0
        while p + 10 <= len(body):
            fid = body[p:p + 4]
            if not fid.strip(b"\x00"):
                break
            fsize = struct.unpack_from(">I", body, p + 4)[0]
            if fsize <= 0 or p + 10 + fsize > len(body):
                break
            tag["frames"].append((fid.decode("latin-1"), fsize))
            p += 10 + fsize
        start = 10 + size
    else:
        start = 0
        problems.append("no ID3v2 tag")
    # scan frames
    p = start
    frames = 0
    first = None
    total_bytes = 0
    while p + 4 <= len(data):
        if data[p] != 0xFF or (data[p + 1] & 0xE0) != 0xE0:
            p += 1
            continue
        h = data[p:p + 4]
        ver_bits = (h[1] >> 3) & 0x03
        layer_bits = (h[1] >> 1) & 0x03
        if layer_bits != 0x01:  # layer III
            p += 1
            continue
        br_idx = (h[2] >> 4) & 0x0F
        sr_idx = (h[2] >> 2) & 0x03
        padding = (h[2] >> 1) & 0x01
        if ver_bits == 3:
            table, group = BITRATES[("mpeg1", 3)], "mpeg1"
            mpeg_ver = 1
        elif ver_bits in (2, 0):
            table, group = BITRATES[("mpeg2", 3)], "mpeg2"
            mpeg_ver = 2 if ver_bits == 2 else 2.5
        else:
            p += 1
            continue
        bitrate = table[br_idx]
        rate = RATES[ver_bits][sr_idx]
        if bitrate == 0 or rate == 0:
            p += 1
            continue
        if mpeg_ver == 1:
            frame_len = int(144 * bitrate * 1000 / rate) + padding
        else:
            frame_len = int(72 * bitrate * 1000 / rate) + padding
        if frame_len < 24 or p + frame_len > len(data):
            p += 1
            continue
        if first is None:
            first = dict(version=mpeg_ver, bitrate_kbps=bitrate, rate=rate, channel_mode=(h[3] >> 6) & 0x03)
        frames += 1
        total_bytes += frame_len
        p += frame_len
    if frames < 10:
        problems.append(f"only {frames} MPEG frames")
    if first:
        samples_per_frame = 1152 if first["version"] == 1 else 576
        duration = frames * samples_per_frame / first["rate"] * 1000
        first["frames"] = frames
        first["duration_ms"] = round(duration)
        first["audio_bytes"] = total_bytes
    return dict(tag=tag, stream=first, size=len(data)), problems


def main():
    targets = sys.argv[1:] or sorted(glob.glob("dist/samples/*"))
    failures = 0
    for path in targets:
        if not os.path.isfile(path):
            continue
        if path.endswith(".wav"):
            info, problems = check_wav(path)
            kind = "WAV"
        elif path.endswith(".mp3"):
            info, problems = check_mp3(path)
            kind = "MP3"
        else:
            continue
        status = "OK  " if not problems else "FAIL"
        print(f"[{status}] {kind} {path}  {os.path.getsize(path)} bytes")
        if info:
            print("        ", info)
        for p in problems:
            print("         !", p)
        failures += bool(problems)
    print("\n%s" % ("all audio files valid" if not failures else f"{failures} audio file(s) invalid"))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
