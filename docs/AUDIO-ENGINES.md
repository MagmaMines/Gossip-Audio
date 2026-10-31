# Audio engines — what is free, what is open source, and what runs on the phone

The brief was: *"use free, open-source audio-generation libraries, support multiple languages, and let
the library work locally on the phone."* This page is the honest answer to that, including the options
that were rejected and exactly why.

## The short version

| Engine | Licence | Languages | Ships in the APK | Why / why not |
|---|---|---|---|---|
| **NanoVoice 4** (this repo, `core/NanoVoice.kt`) | MIT | English, Devanagari Hindi, romanised Hinglish | **Yes — always** | ~1 100 lines of pure Kotlin, no NDK, no model download, 4 ms to render a sentence. Guaranteed to work on every phone, in airplane mode. |
| **The phone's own TTS** (Google, Pico, Samsung…) | varies by vendor (Pico is Apache-2.0) | 30–90, including `hi-IN`, `en-IN`, `ja-JP` | Used when installed | Captured with `TextToSpeech.synthesizeToFile`, so the download is a real WAV, not a speaker recording. This is where the natural-sounding voices come from. |
| **Pico TTS** | Apache-2.0 | en-GB, en-US, de, es, fr, it | via the system engine | Small and permissively licensed, but no Hindi and no neural voices. On many phones it *is* the system engine, so GossipAudio already uses it when present. |
| **eSpeak NG** | GPL-3.0 | 100+ | Not bundled | The strongest language coverage, but it is a C library: needs an NDK build per ABI (~7 MB) plus ~10 MB of voice data, and GPL-3.0 has consequences for a closed-source Play listing. On phones where the vendor already ships it, GossipAudio uses it through the system TTS API for free. |
| **RHVoice** | LGPL-2.1 | English, Russian, Ukrainian, Georgian, Kazakh… | Not bundled | Very good quality for its languages, but no Hindi, and its Android SDK is a prebuilt native library. |
| **Piper / sherpa-onnx (VITS)** | Apache-2.0 / MIT | 30+, including Hindi | Not bundled (yet) | The only option here that is *neural* and supports Hindi offline. Costs 20–60 MB **per voice**, plus a 10–20 MB native runtime per ABI: a 60–100 MB APK. It also cannot be verified in this repository's CI (no NDK, no device), and shipping an unverifiable native path would break the "it must actually work" rule. The integration point is documented below. |
| **FreeTTS / MaryTTS** | BSD / MPL | English / many | Not bundled | Both are desktop Java from the 2000s; FreeTTS is English-only and MaryTTS expects a server. Neither survives an Android port without more risk than it removes. |

## What the app does, in order

1. **Ask the system engine.** `SpeechSynth` checks that a TTS engine exists *and* that its language is
   available for the voice's locale (`en-IN`, `hi-IN`, `en-US`, `ja-JP`). If yes, the line is rendered
   with `synthesizeToFile` at the voice's pitch and rate.
2. **Verify the capture.** Empty or near-silent output, a timeout (12 s) or an exception all count as a
   failure — a "successful" render that contains nothing is the worst outcome, so it is rejected and the
   pipeline falls through.
3. **Speak it with NanoVoice.** The same text, pitch, rate and style presets are rendered by the bundled
   engine. `Clip.engine` records `system` or `nano`, and the player shows which engine produced the file,
   so nothing is guessed.
4. **Offer the installer.** Settings → Voice lab → *Voice engine & languages* lists every engine on the
   phone and the status of each language, with a one-tap intent to install missing TTS data.

## Why not a neural model in the APK

Because this app has to be 1.4 MB and install on a ₹6 000 phone. A single Piper Hindi voice is ~30 MB of
ONNX weights; `onnxruntime` adds ~15 MB per ABI. That is a 60× larger download, needs a native build in
CI, and — the part that decided it — cannot be tested by the Robolectric suite, which runs on a plain
JVM. An engine that fails silently on a device is exactly the bug this release exists to kill.

If you want to add one later, the seam is already there:

```kotlin
// speech/SpeechSynth.kt
private fun nanoPath(...): Pcm = NanoVoice.speak(text, NanoVoice.Options(...))
```

Replace or wrap `nanoPath` with a sherpa-onnx `OfflineTts` call, put the `.onnx` voice in
`app/src/main/assets/`, and keep the existing quality gate — it measures harmonic energy and band shares
on the rendered PCM, so a bad model fails the build instead of the customer.

## Multi-language coverage, precisely

| Input | How it is spoken by NanoVoice | Notes |
|---|---|---|
| `Hello, I am Riya.` | English letter-to-sound rules + irregular-word lexicon | contractions (`we're`, `don't`, `it's`) are in the lexicon |
| `नमस्ते दोस्तों, आज की गॉसिप सुनो` | Devanagari → phonemes | matras, virama, anusvara, nukta, word-final schwa deletion |
| `kaise ho bhai, sab badhiya?` | romanised-Hindi rules | `kh/gh/chh/bh/dh/th/ph/jh`, `aa/ee/oo/ai/au` |
| `2025`, `₹499`, `12:30` | number/currency/time expansion | read as words, not digits |
| Anything else | the system engine if it has the locale, otherwise English rules | the engine card tells you which one will be used |

## Quality, measured

`PipelineTest.renderedAudioIsTonalSpeechNotNoise` renders a full sentence on every build and writes
`dist/samples/quality.txt`:

```
harmonic_ratio_median=1.000     # saturated: every voiced frame is fully harmonic
voiced_frames=140
duration_ms=4186
peak=31968
bands_percent=0-300=13.7 300-800=59.4 800-2000=15.8 2000-4000=3.1 4000-8000=7.9
```

The band row is the interesting one. Speech puts most of its energy in 300–800 Hz, a useful amount in
800–2000 Hz (vowel identity), a few percent in 2–4 kHz (consonant place) and a few percent above 4 kHz
(fricatives). The 1.0.2 release measured `14.3 / 26.9 / 22.3 / 15.6 / 20.8` — a fifth of the energy above
4 kHz, which is why it hissed. The measurements in `mixLab` (cascade vs parallel vs tilted parallel) are
what chose the final mix; they are recomputed on every push.
