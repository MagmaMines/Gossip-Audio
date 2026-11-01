# Architecture — how GossipAudio actually works

> If you only remember one thing: **text goes in, PCM comes out, PCM becomes files, and no byte of it
> touches a server.** Everything below is the long version of that sentence.

Contents
1. [Bird's-eye view](#1-birds-eye-view)
2. [Module map](#2-module-map)
3. [The speech pipeline](#3-the-speech-pipeline)
4. [Encoding & tagging](#4-encoding--tagging)
5. [Storage model](#5-storage-model)
6. [Playback](#6-playback)
7. [UI architecture (custom Views, no AndroidX)](#7-ui-architecture-custom-views-no-androidx)
8. [Threading rules](#8-threading-rules)
9. [Error handling & fallbacks](#9-error-handling--fallbacks)
10. [Why these choices](#10-why-these-choices)

---

## 1. Bird's-eye view

```
┌───────────────────────────────────────────────────────────────────────────┐
│  UI layer (custom Views)                                                  │
│  Splash → Onboarding → Shell{Studio, Voices, Library, Settings}           │
│                     + Player, About, Diagnostics, Help, sheets, overlays  │
├───────────────────────────────────────────────────────────────────────────┤
│  Controller objects                                                      │
│  SpeechSynth · Stt · Exporter · ExportFlow · PlaybackHub · ClipStore      │
├───────────────────────────────────────────────────────────────────────────┤
│  Pure-Kotlin engines (JVM-testable, zero Android deps)                    │
│  NanoVoice · AudioFx · Wav · Mp3Encoder(+LAME/Java) · Id3 · Mp4Tagger      │
├───────────────────────────────────────────────────────────────────────────┤
│  Android platform                                                         │
│  TextToSpeech · SpeechRecognizer · MediaCodec/MediaMuxer · MediaPlayer ·   │
│  AudioEffect · MediaStore · NotificationManager · AlarmManager            │
└───────────────────────────────────────────────────────────────────────────┘
```

The essential split: **engines are pure Kotlin**, so CI runs them on a plain JVM
(`app/src/test/java/.../PipelineTest.kt`) and proves the audio path works without an emulator.
Only the top two layers touch `android.*`.

---

## 2. Module map

| Package | Files | Responsibility |
|---|---|---|
| `xyz.beyoneer.gossipaudio` | `GossipApp.kt` | Application: builds `Prefs`, `ClipStore`, notification channels, playback hub, schedules reminders, sweeps orphans on a background thread. |
| `core` | `Audio.kt`, `VoiceCatalog.kt`, `NanoVoice.kt`, `Id3.kt`, `Mp3Encoder.kt`, `Mp4Tagger.kt` | PCM type, WAV writer/reader, effects, voice + style catalog, text utilities, the NanoVoice synthesiser, ID3v2.3 writer, LAME wiring, MP4 metadata boxes. **No Android imports.** |
| `ru.wowa.jump3r.mp3` + `.mpg` | 82 vendored files | Pure-Java LAME encoder (mp3) and the MPEG decoder tables it needs (mpg). LGPL-2.1, see `licenses/`. |
| `data` | `Clip.kt`, `ClipStore.kt`, `Prefs.kt` | Clip model + JSON serialisation, thread-safe index with atomic writes, preference facade. |
| `speech` | `SpeechSynth.kt`, `Stt.kt` | Engine selection, chunking, progress callbacks, TTS→PCM capture, recogniser wrapper. |
| `audio` | `AudioEngines.kt`, `Exporter.kt`, `PlaybackHub.kt` | AAC/MediaCodec encoder, MediaStore publishing, single playback controller with listeners. |
| `notify` | `Notifier.kt`, `Reminders.kt` | Channels, playback + export + reminder notifications, daily alarm scheduling, boot re-arm. |
| `playback` | `PlaybackService.kt` | Foreground service that keeps audio alive with a media notification. |
| `ui` | 20 files | Custom-view UI: theme, widgets, screens, tabs, overlays, sheets, export flow, clip actions. |
| `util` | `Telemetry.kt`, `Artwork.kt` | Device facts (CPU/RAM/GPU/GLES/NNAPI), bitmap loading, brand cover rendering. |

---

## 3. The speech pipeline

```
composer text
   │
   ├─ sanitized by TextKit.sanitize (whitespace, emoji safe, length cap)
   │
   ├─ SpeechSynth.synthesize(text, voice, style, pitch, rate, callback)
   │      │
   │      ├─ voice.engine == SYSTEM_TTS and a TTS engine exists?
   │      │        → TextToSpeech.synthesizeToFile(temp.wav, utteranceId)
   │      │        → utterance progress listener maps 0..100 % to 0.15..0.95 progress
   │      │        → read the file, convert to Pcm(24 kHz mono)
   │      │
   │      └─ otherwise (or on any TTS failure)
   │               → NanoVoice.speak(text, Options(...))
   │
   └─ AudioFx.normalize(-2 dB) → AudioFx.fade(12 ms in, 40 ms out)
             │
             └─ Exporter.writeMaster(...) → files/clips/<id>.wav
                          └─ ClipStore.add(Clip(...)) → clips.json
```

### Engine selection rules

1. `Stt`/`SpeechSynth` never block the caller; everything is dispatched to a worker thread and
   reported back through a `Callback` on the main thread.
2. The system engine is preferred **only** when it can actually speak the requested locale;
   otherwise `NanoVoice` renders the line.
3. Any exception, timeout (`12 s` watchdog per engine) or empty result falls through to `NanoVoice`.
   The fallback is recorded in `Clip.engine` (`"system"` or `"nano"`) and shown in the player.

### NanoVoice 4 (the guaranteed engine)

NanoVoice is a source–filter formant synthesiser, not a sample player and not a neural model.
The 1.0.1 implementation restarted the voice for every phoneme (own attack/release, coefficients
recomputed per sample), which produced the "ticks/hiss" users reported: only 31–52 % of the energy
landed on harmonics of F0. 1.0.1's replacement was still wrong, for a reason worth writing down: every
vowel was declared without `av`, which defaults to 0.0, so the voiced path was multiplied by zero and the
only audible content was consonant noise — normalised up to full scale. 1.0.4 fixes that (`av = 1.0` on
all 19 vowels) and rebuilds the tract around what the measurements said:

1. **Phonemise** — English letter-to-sound rules plus a lexicon of irregular common words and
   contractions; Devanagari Hindi (independent vowels, consonants, matras, virama, anusvara,
   precomposed nukta, word-final schwa deletion, a small irregular-word lexicon); romanised Hinglish
   (`kh/gh/chh/bh/dh/th/ph/jh`, `aa/ee/oo/ai/au`) with per-message language detection.
2. **Build events** — one `(phone, ms, stress)` list. Stops expand to `closure → burst → aspiration`,
   affricates to `closure → burst → longer aspiration`, diphthongs to two targets. Pauses follow
   punctuation (comma 170 ms, sentence 330 ms, blank line 240 ms, word gap 52 ms).
3. **Control-rate tracks** — the event list is sampled into 1.45 ms blocks of `F1..F4`, `B1..B3`,
   voicing, frication, gain, nasal coupling, frication centre and F0, then one-pole smoothed
   (formants τ = 17–20 ms, voicing 11 ms, frication 7 ms). Coefficients are updated once per block,
   never per sample.
4. **Continuous glottal source** — a Rosenberg pulse (open quotient 0.62) runs for the whole utterance
   with period jitter (±0.2 %) and per-period shimmer (±3 %); radiation is the first difference of the
   glottal flow. F0 declines 22 % across a sentence, +10 % on stressed syllables, optional vibrato.
5. **Vocal tract** — four *cascaded* two-pole resonators, which is one tube, which is a vocal tract.
   Bandwidths are 25–35 % wider than the textbook male values (F1 ≈ 95 Hz, F2 ≈ 125 Hz, F3 ≈ 175 Hz):
   narrow formants look right on a spectrogram and put 79 % of the energy under 800 Hz.
   A parallel bank — every formant normalised to the same loudness — was tried and measured 20.8 % of
   the energy above 4 kHz, which is a snake, not a sentence.
   Two corrections make the cascade speak: a *partial* second difference on the source (`src = rad −
   0.55·radPrev`, +5 dB/oct above 1 kHz, coherent with every formant so nothing cancels) and a two-pole
   4.2 kHz roll-off on the voiced path only — /s/ and /sh/ come from the noise branch, not from there.
   A nasal anti-resonator at 950 Hz handles nasal coupling; shaped noise through a band-pass resonator
   gives fricatives, and one-pole low-passed noise gives breathiness.
6. **Master** — 99.9th-percentile normalisation (one transient cannot squash the sentence), hard clamp,
   16-bit PCM at the requested sample rate (22 050 Hz by default).

Quality is asserted by `PipelineTest.renderedAudioIsTonalSpeechNotNoise`, which measures the harmonic
ratio of voiced frames in CI and fails the build below 0.65 (v2 measures 0.85–0.92).

Videos: see `dist/samples/voice-*.wav` — rendered by this exact code during CI.

Details and the full parameter table: [`VOICES.md`](VOICES.md).

---

## 4. Encoding & tagging

| Output | Encoder | Container/metadata |
|---|---|---|
| **WAV** | none (PCM 16-bit) | 44-byte RIFF header, written by `Wav.write` |
| **MP3** | `Mp3Encoder` → vendored LAME (`lame_init` → `lame_init_params` → `lame_encode_buffer_int` in 1152-sample chunks → `lame_encode_flush` → `lame_close`) | `Id3.attachToMp3` prepends an ID3v2.3 tag (`TIT2`, `TPE1`, `TALB`, `COMM`, `TYER`, `TSSE`, optional `APIC` cover) |
| **M4A / "audio image"** | `AacEncoder` → `MediaCodec` AAC-LC at 44.1/48 kHz, muxed by `MediaMuxer` | `Mp4Tagger.tagFile` appends `udta/meta/ilst` atoms with `©nam`, `©ART`, `©alb`, `©cmt`, `©day`, `covr` |

Why LAME in Java instead of `MediaCodec` MP3? Android does **not** guarantee an MP3 encoder —
many devices only decode. Shipping the encoder removes that lottery, and it is deterministic across
devices (verified in CI by `mp3EncoderProducesRealFramesAndTags`).

Why `MediaCodec` for AAC? AAC-LC *is* guaranteed on every Android device since API 16, and hardware
encoding is faster than any pure-Java alternative.

Bitrate selection is clamped per sample rate (`Mp3Encoder.clampBitrate`) because MPEG-2 Layer III
tops out at 160 kbps — the app picks the nearest legal value instead of producing a broken stream.

File format internals, byte-by-byte: [`AUDIO-FORMATS.md`](AUDIO-FORMATS.md).

### Publishing to Downloads

```
Exporter.publish(context, output)
   ├─ API 29+  → MediaStore.Downloads.insert(RELATIVE_PATH = "Download/GossipAudio", IS_PENDING=1)
   │             → copy bytes → IS_PENDING=0 → return content Uri
   └─ API 24-28 → Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)/GossipAudio
                 → copy → MediaScannerConnection.scanFile → file Uri
```

The `Uri` comes back to the UI, is stored on the clip (`lastExportUri`), triggers a notification with
a share action, and opens the system share sheet.

---

## 5. Storage model

```
files/
├── clips.json                 # index: array of Clip objects
├── clips/<id>.wav             # 22 050 Hz mono master, the source of truth for re-export
├── covers/<id>.jpg            # square cover art (generated or user-picked)
└── exports/                   # temporary encoder output before MediaStore publish
cache/
├── previews/<voice>-<style>.wav  # voice-demo cache, regenerated on demand
├── tts_<uuid>.wav               # system-TTS capture buffers (deleted after read)
└── cover-<id>.jpg               # picked images before cropping
```

`ClipStore` is a `synchronized` list with:

* **atomic writes** — `clips.json.tmp` then `renameTo`, so a crash mid-write cannot corrupt the index;
* **listeners** — screens subscribe to refresh without polling;
* **`sweepOrphans()`** — removes `clips/*.wav` whose id is not in the index (runs on app start and from
  Settings/Library);
* **`totalBytes()` / dir sizes** — used by the storage card in Settings.

`Clip` JSON:

```json
{
  "id": "a1b2c3d4e5f6a7b8", "title": "Monday feels", "text": "…",
  "voiceId": "riya", "styleId": "gossip", "engine": "nano",
  "format": "WAV", "sampleRate": 22050, "durationMs": 4373,
  "sizeBytes": 192872, "createdAt": 1761696000000,
  "liked": false, "fileName": "a1b2c3d4e5f6a7b8.wav",
  "coverName": "a1b2c3d4e5f6a7b8.jpg", "lastExportUri": null
}
```

---

## 6. Playback

`PlaybackHub` is a singleton controller around **one** `MediaPlayer`:

```
playFile(file, clipId, title, cover)   → resets, prepares async, attaches audio session
toggle/pause/resume/stop/replay        → state machine, all main-thread safe
seekTo/seekBy/setSpeed                 → clamps to duration, applies PlaybackParams
setEffects(enabled)                    → Equalizer + BassBoost on the session
setReverb(on)                          → PresetReverb / EnvironmentalReverb
setSleepTimer(minutes)                 → Handler postDelayed → stop()
```

Every mutation computes a new immutable `State(clipId, title, playing, prepared, positionMs,
durationMs, speed, coverPath, queueSize, queueIndex, buffering)` and pushes it to listeners.
Screens (`ShellScreen` mini-player, `PlayerScreen`, `LibraryTab`) simply redraw from `State`.

`PlaybackService` is a foreground service with `foregroundServiceType="mediaPlayback"`; it shows a
`MediaStyle` notification with play/pause, stop/replay and the session token, keeps the process alive
when the screen is off, and is started/stopped by the hub as playback begins/ends.

---

## 7. UI architecture (custom Views, no AndroidX)

No AndroidX, no Material, no Compose — the whole UI is framework `View`s plus hand-drawn `Canvas`
graphics. That is why the APK is 1.3 MB instead of 8 MB, and why it starts instantly on old phones.

**Theme** — `ui/theme/Palette.kt` derives every colour from three brand values, and flips for dark mode:

```kotlin
yellow #F5C518 · yellowBright #FFD84D · yellowDeep #8A6A00
ink    #0B0B0C · paper #FFFDF5 · muted #6E6A5E
```

**Widgets** (`ui/widgets`) — all drawn in code:
`BrandButton` (PRIMARY/GHOST/INK/DANGER, loading spinner, press scale), `Chip`, `ToggleSwitch`,
`Segmented` (sliding indicator), `NeonSlider`, `WaveformView` (seekable), `Sparkline`,
`AvatarView`, `CoverView`, `MiniBars`, `VinylView`, `ButterflyLoader` (flapping wings + progress),
`GlowBackdrop` (radial washes + dot texture), `IconView` (34 hand-written vector glyphs), plus
`LoadingOverlay` (percentage + cancel) and `Overlays` (toast, bottom sheet, confirm, prompt, info).

**Navigation** — `Router` keeps a stack of `Screen`s inside a `FrameLayout`; `push`/`pop` cross-fade and
rise the content (`PathInterpolator` easing), `pop` honours `Screen.onBack()` overrides.
`MainActivity.onBackPressed()` first dismisses the top overlay, then asks the router.

**Tabs** — `ShellScreen` hosts four `Tab`s in a `FrameLayout` with a yellow 4-cell bottom bar,
plus a mini-player that appears whenever `PlaybackHub.state.clipId != null`.

**Motion** — every animation goes through `Ui.pressable/fadeInUp/popIn` or a `ValueAnimator`, and all
of it is skipped when **Reduce motion** is on or the system's animator duration scale is 0.

---

## 8. Threading rules

| Work | Thread | Report back |
|---|---|---|
| NanoVoice rendering | worker `Thread` | `Handler(mainLooper).post { callback(...) }` |
| System TTS | TTS service thread + main-thread `UtteranceProgressListener` | same |
| MP3/AAC/ID3 | worker `Thread` (from `Exporter`/`ExportFlow`) | main-thread `post` |
| Artwork decode | caller thread, small bitmaps | direct |
| Clip index I/O | `synchronized`, called from UI (files are KB-scale) | listeners |
| Playback position | `Handler` tick 200 ms | listeners |

Golden rule enforced in review: *no file I/O, no synthesis and no encoder call on the main thread.*
The UI shows `LoadingOverlay` with real progress because the work reports fractions as it goes.

---

## 9. Error handling & fallbacks

| Failure | Behaviour |
|---|---|
| No TTS engine / TTS timeout | NanoVoice renders the line; player shows "NanoVoice DSP" as the engine |
| TTS speaks the wrong locale | locale match check → NanoVoice for that line |
| `MediaCodec` AAC unavailable | export falls back to MP3 and says so in a toast |
| MediaStore insert denied | file kept in app storage, toast explains where |
| Storage permission missing (API ≤ 28) | in-app rationale → permission request → retry |
| Microphone denied | dictation sheet explains, composer still works |
| Notification denied | playback still works, only the notification is missing |
| Corrupt/missing master | player refuses politely, offers delete, `sweepOrphans` cleans up |
| Out-of-memory on huge text | text is chunked at 420 chars for NanoVoice and 700 for TTS |

Nothing throws into the UI: every background task wraps its body in `try/catch (Throwable)` and
converts failures into a toast plus a `Clip`-less state.

---

## 10. Why these choices

* **Framework-only UI** → 1.3 MB APK, no dependency drift, no ProGuard mysteries, works offline forever.
* **Two speech engines** → the app can never be "broken because Google TTS is not installed".
* **Java LAME** → MP3 works on every device the app supports, byte-identical across phones.
* **Masters kept as WAV** → changing bitrate/format later never re-synthesises speech, so re-exports are
  instant and lossless.
* **JVM tests** → the audio pipeline is provable in CI without an emulator; see `dist/VERIFICATION.txt`.
