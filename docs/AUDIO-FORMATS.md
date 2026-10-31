# Audio formats, byte by byte

GossipAudio writes three kinds of files. This page explains exactly what is inside each one, so a
reviewer (or a curious friend) can verify the app is not shipping a 4 KB “demo” file with a nice name.

---

## 1. WAV — the master

`core/Audio.kt` → `Wav.write(pcm)`. 44-byte canonical RIFF header, 16-bit PCM, mono:

| Offset | Size | Value | Notes |
|---|---|---|---|
| 0 | 4 | `RIFF` | |
| 4 | 4 | `36 + dataSize` | little-endian |
| 8 | 4 | `WAVE` | |
| 12 | 4 | `fmt ` | |
| 16 | 4 | `16` | PCM fmt chunk length |
| 20 | 2 | `1` | format = PCM |
| 22 | 2 | `1` | channels |
| 24 | 4 | `22050` | sample rate |
| 28 | 4 | `sampleRate × channels × 2` | byte rate |
| 32 | 2 | `2` | block align |
| 34 | 2 | `16` | bits per sample |
| 36 | 4 | `data` | |
| 40 | 4 | `dataSize` | `samples × 2` |
| 44 | … | samples | signed 16-bit LE |

The reader (`Wav.read`) is defensive: it walks chunks, supports extra chunks before `data`,
handles 8/16/24/32-bit and float32 PCM, and returns `null` instead of throwing on garbage.

Master files live in `files/clips/<id>.wav`. Re-exporting in another format never re-synthesises speech,
so downloads are instant and lossless.

---

## 2. MP3 — the real thing

`core/Mp3Encoder.kt` wires the vendored LAME port:

```kotlin
val ver = Version(); val ga = GainAnalysis(); val vbr = VBRTag(); val tak = Takehiro()
val common = Common(); val intf = Interface(common); val mpg = MPGLib(intf, common)
val bs = BitStream(ga, mpg, ver, vbr); val id3 = ID3Tag(bs, ver); val presets = Presets()
val qupvt = QuantizePVT(tak); val rv = Reservoir(bs); val qu = Quantize(bs, rv, qupvt, tak)
val lame = Lame(ga, bs, presets, qupvt, qu, vbr, ver, id3, mpg)
id3.setModules(bs, ver); tak.setModules(qupvt); vbr.setModules(lame, bs, ver)

val gfp = Lame.lame_init()
gfp.num_channels = 1; gfp.in_samplerate = rate; gfp.brate = bitrate
gfp.mode = MPEGMode.MONO; gfp.quality = 4; gfp.bWriteVbrTag = false; gfp.disable_reservoir = false
lame.lame_init_params(gfp)
// 1152-sample chunks of IntArray → lame_encode_buffer_int → lame_encode_flush → lame_close
```

Details that matter:

* **Sample-rate snapping** — LAME supports 8/11.025/12/16/22.05/24/32/44.1/48 kHz. Anything else is
  resampled first (`AudioFx.resample`, linear interpolation).
* **Bitrate clamping** — MPEG-2 Layer III (≤ 24 kHz) allows 8–160 kbps, MPEG-1 up to 320. The UI slider
  goes to 320, the encoder silently picks the nearest legal value for the stream's sample rate.
* **Mono only** — the pipeline is mono, so no joint-stereo surprises.
* **Reservoir disabled** — smaller frames, simpler streaming, better device compatibility.

Verified by CI on the actual generated file:

```
173 frames · MPEG-2 Layer III · 128 kbps · 22050 Hz · mono · 4.52 s · 72 306 bytes of audio
```

### ID3v2.3 tag

`core/Id3.kt` writes a hand-built ID3v2 tag, prepended to the MP3:

```
"ID3" | 0x03 0x00 | flags 0x00 | size (4×7-bit syncsafe)
  TIT2  title      (encoding 0x03 = UTF-8 or 0x01 UTF-16 with BOM for non-ASCII)
  TPE1  artist     ("GossipAudio · Beyoneer XYZ")
  TALB  album      ("GossipAudio")
  COMM  comment    (iTunNORM-safe comment frame with a language code)
  TYER  year
  TSSE  encoder settings ("GossipAudio / LAME-Java")
  APIC  cover art  (mime + picture type 3 + description + JPEG bytes) — optional
  padding (silence for future frames)
```

The header size is **syncsafe** (`7 bits per byte`), which is the classic bug in home-grown taggers —
`Id3.tagSize` and CI's `Tag + frames == file size` assertion guard against it.

---

## 3. M4A — the “audio image”

Two steps:

**a) AAC encode + mux** (`audio/AudioEngines.kt` → `AacEncoder.encodeToM4a`)

```
MediaCodec(MIMETYPE_AUDIO_AAC)  →  MediaMuxer(MP4, MUXER_OUTPUT_MPEG_4)
   format: AAC-LC, mono, 44100 or 48000 Hz, 128 kbps (configurable)
   profile: AACObjectLC, aac-profile, max-input-size = 16 KB
```

PCM is fed in 4 KB chunks; the encoder's output buffers are written with
`MediaCodec.BufferInfo` presentation times in microseconds. On devices without an AAC encoder the
exporter falls back to MP3 and tells the user.

**b) Metadata atoms** (`core/Mp4Tagger.kt` → `Mp4Tagger.tagFile`)

The MP4 box tree is parsed and a `udta` box is appended inside `moov`:

```
moov
 └─ udta
     ├─ meta  (full box, version+flags)
     │   ├─ hdlr  (mdir/appl)
     │   └─ ilst
     │       ├─ ©nam  title
     │       ├─ ©ART  artist
     │       ├─ ©alb  album
     │       ├─ ©cmt  comment
     │       ├─ ©day  year
     │       └─ covr  cover image (type 13 = JPEG, 14 = PNG)
     └─ (@too / free as needed)
```

`stco`/`co64` chunk offsets do not need patching because the tag is appended *inside* `moov` and the
media data (`mdat`) is never moved — the offsets are relative to the file start and `mdat` keeps its
position. If the layout is unexpected (e.g. `moov` after `mdat` with 64-bit boxes), `tag()` returns
`null` and the exporter keeps the untagged-but-valid M4A instead of corrupting it.

The result is what social apps and file managers expect from an “audio image”: **one MP4 file that is
audio plus a cover picture**.

---

## 4. Publishing to `Downloads/GossipAudio`

| Android | Mechanism |
|---|---|
| 10+ (API 29) | `MediaStore.Downloads` with `RELATIVE_PATH=Download/GossipAudio`, `IS_PENDING=1` during the copy, `IS_PENDING=0` after |
| 7–9 (API 24–28) | `WRITE_EXTERNAL_STORAGE` (declared `maxSdkVersion=28`) → `Environment.DIRECTORY_DOWNLOADS/GossipAudio` → `MediaScannerConnection.scanFile` |

A `content://` (or `file://`) `Uri` is returned, shown in a notification with a share action, and stored
on the clip as `lastExportUri` so it can be re-shared later.

---

## 5. What the app does *not* write

* **No OGG/Opus/FLAC** — would need extra encoders; WAV/MP3/M4A covers every practical use.
* **No video stream** — the “audio image” is an MP4 container with a cover, not an H.264 track.
* **No DRM, no watermark, no forced branding** in the audio itself (the tag mentions the app; that's it).
