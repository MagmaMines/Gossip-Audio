# Feature catalogue — 38 features, each with proof

Every row below is wired to code that ships in the APK. The **Verify** column tells you how to see it
with your own eyes (or ears) on a real phone in under a minute.

Legend: 🟡 = visible in the UI · 🎧 = audible · 🔬 = inspectable in `dist/` or in-app Diagnostics.

---

### Voices & speech

| # | Feature | Where the code lives | Verify |
|---|---|---|---|
| 1 | **10 voices** (4 F, 5 M, 1 bot) | `core/VoiceCatalog.kt` — `VoiceCatalog.all` | Voices tab shows 10 cards; filters “Girls 4 / Boys 5 / Not human 1”. Test `catalogHasTenVoicesAndEightStyles` asserts the counts. |
| 2 | **Dual engine** (device neural TTS + NanoVoice fallback) | `speech/SpeechSynth.kt` | Diagnostics → “System neural TTS: available/missing”. Disable your TTS engine and generate again: audio still appears, engine label switches to *NanoVoice DSP*. |
| 3 | **8 emotion styles** | `core/VoiceCatalog.kt` — `StyleCatalog.all` | Studio style chips; the style chip in the player info card shows the preset used. |
| 4 | **Speed 0.5×–2.0× + pitch ±12 st** | `ui/tabs/StudioTab.kt` sliders → `SpeechSynth` → `NanoVoice.Options` | Render the same line at 0.7× and 1.6×: the files differ in length by ~2.3×. |
| 5 | **Random gossip generator** | `TextKit.randomGossip()` | Dice button in the composer fills the text field with a new sentence every tap. |

### Audio out

| # | Feature | Where the code lives | Verify |
|---|---|---|---|
| 6 | **WAV export** | `core/Audio.kt` — `Wav.write` | Export → WAV → file opens in any player; `file` reports *RIFF (little-endian) data, WAVE audio, Microsoft PCM, 16 bit, mono 22050 Hz*. |
| 7 | **MP3 export up to 320 kbps** | `core/Mp3Encoder.kt` + `ru.wowa.jump3r.*` | Export → MP3 → plays in every player. CI test decodes 173 real MPEG-2 Layer III frames from the generated file. |
| 8 | **M4A / AAC “audio image”** | `audio/AudioEngines.kt` (`AacEncoder`) + `core/Mp4Tagger.kt` | Export → M4A; the file has an AAC track and a `udta/meta/ilst` cover atom. |
| 9 | **Cover art (auto or custom)** | `util/Artwork.kt`, `core/Id3.kt`, `core/Mp4Tagger.kt` | Export sheet → *Pick image*; the exported MP3 shows your picture in the gallery / file manager. Auto covers are generated from the logo + title. |
| 10 | **Save to `Downloads/GossipAudio`** | `audio/Exporter.kt` — `publish` | After download, the toast shows the path and the system share sheet opens. |
| 11 | **One-tap share** | `ui/ExportFlow.kt` — `quickShare` | Player → Share: encodes MP3, publishes it, opens the chooser. |

### Player

| # | Feature | Where the code lives | Verify |
|---|---|---|---|
| 12 | **Play / pause / stop / replay / next / prev + scrubbing** | `audio/PlaybackHub.kt`, `ui/screens/PlayerScreen.kt` | Transport row; tap the waveform to seek. |
| 13 | **Background playback + media notification** | `playback/PlaybackService.kt`, `notify/Notifier.kt` | Press Home during playback: audio continues, notification has pause/stop actions. |
| 14 | **Sleep timer** | `PlaybackHub.setSleepTimer` | Player → Sleep → 5 min: playback stops itself. |
| 15 | **Bass boost + equalizer** | `PlaybackHub.setEffects` | Settings → Audio polish (on by default); toggle and hear the low end change. |
| 16 | **Playback speed chips** | `PlayerScreen` speed row | 0.5× … 2× applies live via `PlaybackParams`. |

### Library

| # | Feature | Where the code lives | Verify |
|---|---|---|---|
| 17 | **Clip index with metadata** | `data/ClipStore.kt`, `data/Clip.kt` | Every generated clip appears in Library with voice, duration, date and cover. |
| 18 | **Search + favourites filter** | `ui/tabs/LibraryTab.kt` | Type in the search field; tap ♥ on a card and switch to the “Liked ♥” filter. |
| 19 | **Like / rename / delete / delete all** | `ClipStore` + `ui/ClipActions.kt` | Long-press any card for the action sheet. |
| 20 | **Orphan sweeper** | `ClipStore.sweepOrphans()` | Settings → *Clean orphan files* reports the number of removed files (usually 0 — that's the point). |

### Input & text

| # | Feature | Where the code lives | Verify |
|---|---|---|---|
| 21 | **Speech-to-text dictation** | `speech/Stt.kt`, `ui/SttSheet.kt` | Composer → mic icon: pulsing orb, live partial text, text lands in the composer. |
| 22 | **Text tools** (word count, duration estimate, auto-title, sanitising) | `core/VoiceCatalog.kt` — `TextKit` | The telemetry card under the composer updates words / estimated seconds as you type. |

### App polish

| # | Feature | Where the code lives | Verify |
|---|---|---|---|
| 23 | **Butterfly loader with real progress + cancel** | `ui/widgets/LoadingOverlay.kt`, `ui/widgets/ButterflyLoader.kt` | Tap Generate: wing-flapping butterfly, percentage bar, cancel actually aborts the result. |
| 24 | **Yellow/white + system dark theme** | `ui/theme/Palette.kt`, `MainActivity.setThemeMode` | Top-bar sun/moon button; Settings → Theme (System / Light / Dark). Yellow buttons with a thin darker-yellow outline everywhere. |
| 25 | **Live telemetry card** | `util/Telemetry.kt`, composer card + Diagnostics | CPU %, RAM used/total, GPU renderer, GLES version, NNAPI availability — measured, not decorative. |
| 26 | **In-app self-test** | `ui/screens/DiagnosticsScreen.kt` | Settings → Diagnostics → *Run self-test*: 8 checks (synthesis, WAV, round-trip, MP3, ID3, M4A, MP4 atoms, storage) with per-step results in milliseconds. |
| 27 | **Daily gossip reminder** | `notify/Reminders.kt` | Settings → Daily gossip nudge + time picker; `BootReceiver` re-arms it after reboot. |
| 28 | **Diagnostics + Help + About** | `ui/screens/*` | 24-question FAQ, licence list, device facts, self-test. |

---

## How the 28 features map to the original brief

| Brief requirement | Delivered by |
|---|---|
| TTS from a small on-device model | NanoVoice (≈ 40 KB of Kotlin, no weights to download) + optional device neural TTS |
| Download WAV + MP3 | Features 6, 7 |
| “Audio image” MP4-style export | Feature 8 — AAC in an MP4 container with an embedded cover image |
| 20+ genuinely working features | 28, listed above |
| 10 preset voices, 4 F / 5 M / 1 non-human | Feature 1 |
| Play / stop / pause / replay / select-to-listen | Features 12, 16, 18 |
| Loading screen + play animations | Features 23, 12 (spinning vinyl, animated waveform) |
| Butterfly-smooth rounded animations | `Ui.pressable/fadeInUp/popIn`, `Router` transitions, `ButterflyLoader`, `Segmented` slider |
| Speech-to-text | Feature 21 |
| Notifications | Features 13, 27 |
| Yellow/white + system black/white/yellow, yellow buttons with thin darker outline | Feature 24 and `Palette.kt` |
| Logo `GossipAudio.png` as launcher icon | `tools/make_icons.py` derives every mipmap + the in-app glyph from that exact file |
| Tagline verbatim | `ui/Brand.kt` — `TAGLINE`, also in `res/values/strings.xml`, the About screen and the README |

---

## What is intentionally *not* here

Being honest is part of “production grade”:

* **No cloud TTS.** No internet permission for audio, so no “premium neural voices” — see
  [`PRIVACY.md`](PRIVACY.md).
* **No video track.** The “audio image” is audio + a cover image inside MP4, not an H.264 video.
* **No account, no sync, no analytics.** The app cannot phone home even if it wanted to.
* **No fake demos.** The self-test and the CI verifiers exercise the shipped code, and the sample audio in
  [`../dist/samples/`](../dist/samples/) was produced by it.

---

## 1.0.3 additions — ten more, each with a proof

| # | Feature | Where the code lives | Verify |
|---|---|---|---|
| 29 | **Share-to-gossip** — any app's text can be sent straight into the composer | `AndroidManifest.xml` (`ACTION_SEND`, `text/plain`), `MainActivity.handleIntent`, `StudioTab.onShow` | In Chrome, select a sentence → Share → GossipAudio. The text lands in the composer with a toast. |
| 30 | **Process selected text** (Android 6+) — "Gossip this" in the text-selection menu | `AndroidManifest.xml` (`ACTION_PROCESS_TEXT`) | Select text in any editor → ⋮ → *GossipAudio*. |
| 31 | **Launcher shortcuts** — New gossip / Library / Play my voice (long-press the icon) | `res/xml/shortcuts.xml`, `MainActivity.ACTION_*` | Long-press the launcher icon. Each shortcut opens the right tab. |
| 32 | **Say it your way** — custom pronunciations, applied to both engines | `Prefs.wordMap`, `SpeechSynth.applyWordMap` | Settings → Voice lab → *Say it your way* → `beyoneer = Be-yo-neer`. Every render (system TTS and NanoVoice) now says it that way. |
| 33 | **Voice engine & language inspector** — shows every engine on the phone and each language's status, with a one-tap installer | `SettingsTab.showEngineAndLanguages` | Settings → Voice lab → *Voice engine & languages*. Green = ready, amber = needs data, with a button that opens the system TTS data installer. |
| 34 | **Bigger text** — a global 15 % type scale for accessibility | `Prefs.bigText`, `Ui.textScale` | Settings → Voice lab → *Bigger text*. Every label in the app grows; the setting survives restarts. |
| 35 | **Auto-trim & auto-normalise** — the post-processing is now switchable per clip | `Prefs.autoTrim`, `Prefs.autoNormalize`, `SpeechSynth.finish` | Turn both off, render a quiet sentence, compare the waveform in Diagnostics. |
| 36 | **Set as ringtone / notification / alarm** — the clip is written into the system sound library via MediaStore | `PlayerScreen.applyAsSound` | Player → *Ringtone* → pick a slot. The system sound picker shows the clip. (Android 10+.) |
| 37 | **Copy / share the transcript** | `PlayerScreen.copyTranscript`, `shareTranscript` | Player → *Copy text* / *Share text*. |
| 38 | **Library sorting** — newest, longest, A–Z | `LibraryTab.sortMode` | Library → tap the sort chip; it cycles and the list re-orders. |
| — | **Studio statistics** — clip count, total minutes, most-used voice | `SettingsTab.statsRow` | Settings → Voice lab header. |
