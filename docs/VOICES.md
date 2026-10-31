# The ten voices, the eight moods, and the maths in between

## The roster

| # | Voice | Gender | Language | Region | Pitch axis | Rate | Timbre | Engine |
|---|---|---|---|---|---|---|---|---|
| 1 | **Riya** | Female | `en-IN` | India 🇮🇳 | 1.12 | 1.02 | 0.62 | device TTS → NanoVoice |
| 2 | **Ananya** | Female | `hi-IN` | India 🇮🇳 | 1.20 | 0.98 | 0.70 | device TTS → NanoVoice |
| 3 | **Maya** | Female | `en-US` | USA 🇺🇸 | 1.16 | 1.06 | 0.58 | device TTS → NanoVoice |
| 4 | **Sakura** | Female | `ja-JP` | Japan 🇯🇵 | 1.24 | 1.00 | 0.74 | device TTS → NanoVoice |
| 5 | **Arjun** | Male | `en-IN` | India 🇮🇳 | 0.86 | 1.02 | 0.44 | device TTS → NanoVoice |
| 6 | **Rohan** | Male | `hi-IN` | India 🇮🇳 | 0.82 | 0.96 | 0.40 | device TTS → NanoVoice |
| 7 | **Jordan** | Male | `en-US` | USA 🇺🇸 | 0.74 | 0.95 | 0.34 | device TTS → NanoVoice |
| 8 | **Oliver** | Male | `en-GB` | UK 🇬🇧 | 0.90 | 1.00 | 0.50 | device TTS → NanoVoice |
| 9 | **Kenji** | Male | `ja-JP` | Japan 🇯🇵 | 0.95 | 0.98 | 0.46 | device TTS → NanoVoice |
| 10 | **Beyoneer Bot** | Not human | `en-US` | The Internet 🤖 | 1.00 | 1.04 | 0.92 | **NanoVoice only** (ring-modulated, not human on purpose) |

Counts are asserted by `PipelineTest.catalogHasTenVoicesAndEightStyles`:
`4 female`, `5 male`, `1 non-human`, `10 total` — so a future refactor cannot quietly break the brief.

The Bot is deliberately NanoVoice-only: it must sound like a machine, and it must work on a phone
with no TTS engine installed at all.

### What the numbers do

* `pitch` scales the base frequency: `F0 = 118 Hz × voice.pitch × userPitch` (clamped 70–330 Hz),
  so Riya lands around 132 Hz and Jordan around 87 Hz.
* `rate` scales speaking speed: phones-per-second × `rate × style.rateFactor × userSpeed`.
* `timbre` shapes the formant scale (`1 + (pitch − 1) × 0.55`), i.e. how “big” the vocal tract sounds.

## The eight moods

| Style | Pitch × | Rate × | What it does |
|---|---|---|---|
| **Natural** | 1.00 | 1.00 | nothing — the voice as designed |
| **Gossip** | 1.05 | 1.08 | higher, faster, leaning in |
| **Cheerful** | 1.10 | 1.05 | brighter, bouncy |
| **Dramatic** | 0.94 | 0.92 | slower, deeper, cinematic |
| **Whisper** | 0.92 | 0.90 | quieter, close-mic feel |
| **News** | 0.98 | 1.06 | even, urgent, “breaking” |
| **Roast** | 1.08 | 1.12 | fast and sharp |
| **Robo** | 0.86 | 0.96 | quantised pitch, machine-flat |

All eight are real, measured multipliers applied **before** synthesis, so the exported file keeps the
mood. `PipelineTest.stylesChangeRateAndPitchNumerically` checks the table stays within sane bounds and
that a slower rate genuinely produces longer audio.

## NanoVoice, step by step

```
"Hello, bestie!" 
   │  TextKit.sanitize
   ├─ splitSentences()            → ["Hello, bestie!"], flagged question/exclaim, pause lengths
   ├─ parseWords()                → tokens: words, punctuation, whitespace → (word, trailingPauseMs)
   ├─ phonemize(word)             → ["HH","AH","L","OW"]   (letter-to-sound rules + dictionary exceptions)
   ├─ syllableCount / defaultStress
   └─ frame timeline              → Frame(durationMs, F1, F2, F3, voicing, noise, amp, F0)
                                   vowels: vowel-triangle targets + glides
                                   nasals: low F1, nasal zero
                                   liquids: F3 ~ 2500 Hz
                                   fricatives: noise-dominant
                                   stops: silence + burst
                                   silences: punctuation
```

Rendering (`NanoVoice.render`) is a source-filter model:

1. **Glottal source** — a pulse train at `F0` with harmonics rolled off at 1/f, plus vibrato
   (`vibrato > 0` → ±0.6 % at 5 Hz) and optional quantisation for the robot.
2. **Formant filters** — three second-order resonators at F1/F2/F3 per frame, cross-faded between
   frames so there is no zipper noise.
3. **Noise** — bandpassed white noise mixed in by the `noise` coefficient (fricatives, breathiness).
4. **Prosody** — linear declination across the sentence, a +18 % rise at the last syllable of questions,
   +8 % emphasis on exclamations, and stress-based amplitude bumps.
5. **Post** — DC removal, normalise to −2 dBFS, 12 ms fade-in / 40 ms fade-out.

Output: 22 050 Hz, 16-bit, mono. Roughly **1 ms of CPU per word** on a modern phone (measured in the
in-app Diagnostics self-test), which is why the butterfly loader is a decoration rather than a necessity.

### Accuracy disclaimer, stated proudly

NanoVoice is a rule-based formant synthesiser, not a neural network. It is intelligible, expressive and
distinct per voice, but it will never sound like a studio narrator. That is exactly why the app prefers
the device's neural TTS whenever one exists, and why the voice chip in the player tells you which engine
rendered the file (`On-device neural TTS` vs `NanoVoice DSP`). No false promises — just audio that always
comes out.

## Voice demos

CI renders four voices with the real engine and commits them to the repo:

| File | What to listen for |
|---|---|
| [`dist/samples/voice-riya.wav`](../dist/samples/voice-riya.wav) | brighter female voice |
| [`dist/samples/voice-maya.wav`](../dist/samples/voice-maya.wav) | American-English female, slightly faster |
| [`dist/samples/voice-arjun.wav`](../dist/samples/voice-arjun.wav) | Indian-English male |
| [`dist/samples/voice-bot.wav`](../dist/samples/voice-bot.wav) | the non-human one |
| [`dist/samples/pipeline-sample.mp3`](../dist/samples/pipeline-sample.mp3) | MP3 + ID3v2.3, straight from the encoder |

Measured with `tools/verify_audio.py`:

```
pipeline-sample.mp3  173 MPEG-2 Layer III frames · 128 kbps · 22050 Hz · mono · 4.52 s
                     ID3v2.3: TIT2, TPE1, TALB, COMM, TYER, TSSE
voice-*.wav          RIFF/WAVE · PCM 16-bit · 22050 Hz · mono · 4.2–4.4 s
```

Each voice file has a different pitch (measured fundamental ≈ 134–146 Hz in the rendered demos) and a
different file hash — they are genuinely different renders, not one file copied ten times.

## Languages

| Input | Who speaks it | Notes |
|---|---|---|
| English (typed in Latin) | device TTS, else NanoVoice 2 | Rule-based G2P: digraphs, magic-e, syllabic stress, schwa reduction, ~360 irregular words and contractions. |
| Hindi (Devanagari) | device TTS `hi-IN`, else NanoVoice 2 | Matras, virama, anusvara/visarga, precomposed nukta, word-final schwa deletion, irregular words (`है`, `मैं`, `क्या`…). |
| Hinglish (romanised Hindi) | device TTS `hi-IN`, else NanoVoice 2 | `kh/gh/chh/bh/dh/th/ph/jh` + `aa/ee/oo/ai/au`; switches per message when ≥25 % of words look Hindi, so `kaise ho bhai` never gets read as English. |
| Japanese, other locales | device TTS | The on-device engine handles the language; NanoVoice reads Latin text with English rules as a last resort. |

A message like `मेरा नाम Riya है` is fine: the phonemiser switches per word, not per message.

## The engine actually used

Every render records an engine label (`system` or `nano`) that the Player screen shows under **Engine**.
A system-TTS capture is accepted only if it has real level, a plausible amount of voiced audio for the
amount of text, and a sane duration — otherwise it is discarded and NanoVoice renders the line, so the
"installed engine but no voice data" case can never hand you silence or clicks again.
