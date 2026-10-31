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

## NanoVoice 2, step by step

```
"Hello, bestie!"
   │  TextKit.sanitize
   ├─ textToEvents()          → (phone, durationMs, stress) list
   │    English: letter-to-sound rules + 360-entry lexicon + contractions,
   │             stress landing, unstressed vowels reduced to schwa
   │    हिन्दी : matras, virama, anusvara, nukta, final schwa deletion + lexicon
   │    Hinglish: kh/gh/chh/bh/dh/th/ph/jh + aa/ee/oo/ai/au
   ├─ expand()                → stops: closure → burst → aspiration
   │                            affricates: closure → burst → long aspiration
   │                            diphthongs: nucleus → glide target
   └─ render()                → PCM
```

`render()` is a source–filter model, and the important part is that **the source never stops**:

1. **Control-rate tracks** — the event list becomes 1.45 ms blocks of F1–F4, B1–B3, voicing,
   frication, gain, nasal coupling and F0, each smoothed with a one-pole filter (formants τ = 17–20 ms,
   voicing 11 ms, frication 7 ms). Filter coefficients are recomputed per block, not per sample.
2. **One glottal source** — a Rosenberg pulse (open quotient 0.62) runs continuously for the whole
   utterance, with ±0.2 % period jitter and ±3 % per-period shimmer; radiation is the first difference
   of the flow. Voicing is a *level*, never a gate.
3. **Vocal tract** — four two-pole resonators in parallel (70/95/140/250 Hz bandwidths), each normalised
   to 0 dB at its own formant and summed 1.00 / 0.90 / 0.55 / 0.30, a 950 Hz
   anti-resonator for nasals, a band-passed noise generator for fricatives (−9 dB for /s/, −12 dB for
   /ʃ/ relative to a vowel) and one-pole-filtered noise for breathiness.
4. **Prosody** — 22 % F0 declination across a sentence, +10 % on stressed syllables, 0.9 Hz
   micro-melody, optional vibrato, and a 42 Hz ring modulator for the robot voice.
5. **Master** — 99.9th-percentile normalisation (a single transient cannot squash the sentence),
   hard clamp, 16-bit PCM.

Word gap is 30 ms, comma 170 ms, full stop 330 ms; a voiceless closure is 32 ms plus a 9 ms burst and
22 ms of aspiration, so the rhythm lands around 150–160 words per minute at `1.00×`.

Output: 22 050 Hz, 16-bit, mono, rendered in a few milliseconds per sentence on the CPU.

CI measures the result on every build: **harmonic ratio 0.85–0.95** (energy that lands on F0 harmonics),
autocorrelation **0.72–0.80** at the pitch period, and the energy split across five bands. `PipelineTest`
fails the build below 0.65 harmonic ratio, or if less than 4 % of the energy lands in 800–4000 Hz
(i.e. if the voice collapses back into a buzzer).

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
| [`dist/samples/voice-hindi.wav`](../dist/samples/voice-hindi.wav) | Devanagari input, Hindi phonemes |
| [`dist/samples/voice-hinglish.wav`](../dist/samples/voice-hinglish.wav) | `kaise ho bhai` in romanised Hindi |

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
