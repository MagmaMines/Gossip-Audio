# The audio stack, rebuilt (2.0)

Three separate things were wrong, and they compounded into "it is not even audio":

1. **Playback was running a bass booster.** `MediaPlayer` + `android.media.audiofx.BassBoost(320)` +
   `PresetReverb`. BassBoost at 320/1000 is roughly +8…+12 dB below 200 Hz on most devices, applied to a
   file already normalised near full scale. That is the boom *and* the crackle: the waveform was clipping
   inside the device DSP, not in our file.
2. **The synthesiser's vowels were silent** (1.0.2), then the parallel formant bank hissed 20.8 % of its
   energy above 4 kHz (1.0.3).
3. **The good engine was ditched silently.** When the phone's TTS returned anything the sanity check
   disliked, the app swapped in the formant synthesiser without telling anyone. That is why it sounded
   like a 1985 answering machine and why nothing in the UI explained it.

## What 2.0 does now

| Layer | Library | Licence | Why this one |
|---|---|---|---|
| Playback | **AndroidX Media3 ExoPlayer 1.5.1** | Apache-2.0 | Real media engine: Sonic time-stretch (speed changes, pitch does not), audio focus (pauses for calls, ducks under notifications), gapless queueing, no legacy `audiofx` toys in the path. |
| Device speech | **Android `TextToSpeech`** | platform (Apache-2.0 AOSP / vendor) | The only on-device *neural* voices that ship on a real phone. We now prefer `*-local` voices over `*-network` ones so it works in airplane mode. |
| Bundled speech | **NanoVoice** (this repo, `core/NanoVoice.kt`) | MIT | Zero dependencies, zero download, ~4 ms per sentence, English + Devanagari Hindi + romanised Hinglish. Used **only** when the phone has no engine at all — and the UI says so, with a one-tap installer. |
| MP3 | **jump3r** (LAME port) | LGPL-2.1 | In-repo, real MPEG frames, verified by `tools/verify_audio.py`. |
| WAV / DSP | ours | MIT | 16-bit PCM, trim / normalise / fade, all unit-tested. |

### What we evaluated and did not ship

| Option | Licence | Verdict |
|---|---|---|
| **Piper / sherpa-onnx (VITS, neural)** | Apache-2.0 | Best quality available offline, and the only one that gives Hindi neural speech. Rejected for the *default* build because it adds ~30 MB per voice plus ~25 MB of native runtime per ABI (a 60–100 MB APK) and needs an NDK build this repo cannot verify in CI. Integration point: `SpeechSynth.nanoPath()` — drop the AAR + `.onnx` voice in and keep the existing quality gate. |
| **eSpeak NG** | GPL-3.0 | 100+ languages, but a C library + ~10 MB of voice data, and GPL-3.0 constrains a Play release. Where a vendor already ships it, we use it through the system TTS API for free. |
| **RHVoice** | LGPL-2.1 | Excellent for its languages, none of which are Hindi, and prebuilt native only. |
| **Pico TTS** | Apache-2.0 | Small, permissive, English/German/Spanish/French/Italian only. On many phones it *is* the system engine, so we already use it when present. |
| **Kokoro-82M** | Apache-2.0 | Beautiful English, but ~300 MB of weights and no Hindi; desktop-class memory. |

## Signal path, end to end

```
text ──► word map ──► device TTS (local voice preferred) ──► WAV (22–24 kHz)
                                  │ fallback only if NO engine exists
                                  └─► NanoVoice: cascade formants, 130 Hz HPF,
                                      −6 dB shelf @320 Hz, 2-pole 4.2 kHz LPF,
                                      −3 dBFS master
WAV ──► ExoPlayer (Sonic speed, audio focus, no EQ) ──► speaker
WAV ──► LAME/jump3r ──► MP3 128 kbps
```

Nothing boosts anything. There is no EQ, no bass boost and no reverb in the signal path — the setting that
used to switch them on now opens a card explaining exactly that.

## Proving it without ears

* `PipelineTest.renderedAudioIsTonalSpeechNotNoise` renders a full sentence every CI run and fails the
  build if the harmonic ratio drops below 0.65 or if the band balance collapses (a buzzer signature).
* `tools/verify_audio.py` parses every published WAV: RIFF header, sample rate, duration, RMS, peak.
* **Settings → Voice lab → Compare the engines (A/B)** renders the same sentence with the device voice and
  with NanoVoice and plays them back to back on the phone.
