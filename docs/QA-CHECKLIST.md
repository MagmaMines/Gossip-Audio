# QA checklist — 10 minutes, every box verifiable

Open the app and work down this list. Anything that fails is a bug worth reporting; the in-app
**Settings → Diagnostics → Run self-test** covers the audio pipeline automatically (8 checks).

## Install & launch
- [ ] `dist/GossipAudio-1.0.5-release.apk` installs without "invalid package" (v2 signature verified in CI).
- [ ] Launcher icon is the yellow logo, label reads **GossipAudio**.
- [ ] Cold start shows the butterfly splash, then onboarding (first run) or the Studio tab.
- [ ] Rotating the phone does not lose the composer text or stop playback.

## Audio quality (the 1.0.5 fix)
- [ ] Generate a sentence, save WAV, play it: continuous voice, no ticks/hiss between words.
- [ ] Type `नमस्ते दोस्तों, आज की गॉसिप सुनो।` → audio is Hindi-ish, not silence.
- [ ] Type `kaise ho bhai, sab badhiya hai?` → Hinglish, not spelled-out English.
- [ ] On a phone without TTS data, generate anyway: NanoVoice fills in, engine label says NanoVoice.
- [ ] Player → Engine shows `system` when your phone's engine produced it.

## Studio
- [ ] Typing updates the word count and the estimated duration live.
- [ ] Dice fills with a random sentence; the mic opens the dictation sheet (permission prompt appears once).
- [ ] Voice strip shows 10 voices; tapping one changes the chip label and the voice in the composer.
- [ ] Style chips switch between the 8 moods.
- [ ] Speed and pitch sliders move smoothly (no jank, no snap-back).
- [ ] Generate shows the butterfly overlay with a percentage; **Cancel** stops it and produces no clip.
- [ ] Result appears in the recents carousel and the mini-player; audio is audible and intelligible.

## Player
- [ ] Play / pause / stop / replay / next / previous all act on the right clip.
- [ ] Tapping the waveform seeks; the elapsed/total labels track.
- [ ] Speed chips change playback speed live without pitch artefacts.
- [ ] Sleep timer 5 min stops playback when it expires.
- [ ] Home button + screen off: audio keeps playing, notification shows pause/stop.

## Responsiveness & polish
- [ ] On a 320 dp phone and on a tablet, every tab scrolls without a single clipped card edge.
- [ ] Tablet: the reading column stops growing and centres (≈680 dp cap).
- [ ] Horizontal rows (voices, styles, speed) fade at the edge instead of chopping a card.
- [ ] Raise the system font size to 130 %: nothing overlaps, nothing is cut off.
- [ ] TalkBack: every icon button announces a name; switches announce on/off.
- [ ] Reduce motion ON: no splash flight, no fade storms, UI still fully usable.

## Export
- [ ] Download → WAV produces a `.wav` in `Downloads/GossipAudio` that plays in the system player.
- [ ] Download → MP3 produces a `.mp3` that plays and shows a title/artist/cover.
- [ ] Download → M4A produces a `.m4a` (AAC) with cover art.
- [ ] Pick image → custom cover appears in the export preview and inside the exported file.
- [ ] Share encodes MP3 and opens the system share sheet.
- [ ] On Android 9 and below, the storage permission rationale appears once and the file still lands in Downloads.

## Library
- [ ] Every generated clip appears with voice, duration, date and cover.
- [ ] Search filters by title/text/voice; the "Liked ♥" filter shows only favourites.
- [ ] Like / rename / delete / delete all behave and the list refreshes immediately.
- [ ] Clean orphan files reports 0 on a healthy install.

## Settings & system
- [ ] Theme: System / Light / Dark applies instantly, and yellow buttons keep their thin darker outline.
- [ ] Reduce motion stops the butterfly/transition animations.
- [ ] Default format + quality persist across restarts.
- [ ] Daily reminder: enable, pick a time, kill the app — the notification arrives.
- [ ] Diagnostics: live CPU/RAM/GPU values update every ~1.4 s; self-test reports 8/8 passed.
- [ ] Help shows 24 answers and they expand/collapse.
- [ ] About shows the exact tagline, the version and the licence list.

## Regression checks run by CI
- [ ] `PipelineTest` 8/8 green (synthesis, WAV round-trip, MP3 frames + ID3, styles, effects, text kit, silence).
- [ ] `tools/verify_apk.py` → "none — APK structure, manifest, signing and dex contents check out".
- [ ] `tools/verify_audio.py` → "all audio files valid".

## Commercial-readiness pass (2026-10-31)

Added after the first release; all of it is enforced by `AccessibilityTest` or visible in the code.

| Check | How to verify |
|---|---|
| Every icon-only button is named | TalkBack on: swipe through Studio/Voices/Library/Settings — no "unlabelled button" |
| Switches announce their state | Settings → any toggle: "Daily gossip nudge, switch, off" |
| Sliders are operable without touch | Focus "Download quality", swipe up/down changes it; TalkBack reads the new value |
| Haptics preference is real | Settings → Haptics on: every press ticks. Off: silence. |
| Reduce motion is real | Settings → Reduce motion on: no press scaling, no pop-ins, no glow; router cuts instantly |
| Back gesture | Android 14/15: open About → back returns to the shell; back again leaves the app |
| Denied notifications | Turn on "Daily gossip nudge", deny permission: switch springs back off and a toast explains |
| Reminders after update | Enable the nudge, update the app, the alarm is re-registered (`MY_PACKAGE_REPLACED`) |
| Process death | Enable "Don't keep activities", background the app, return: the shell appears instead of a blank screen |
| Backup scope | `adb shell bmgr backupnow xyz.beyoneer.gossipaudio` backs up prefs only — not the audio masters |
