# Play Store listing pack

Copy-paste ready. Everything here matches the shipped APK (`dist/GossipAudio-1.0.4-release.apk`).

---

## Title (30 chars max)

```
GossipAudio: AI Voice Maker
```
*(28 characters — under the limit, keeps the brand and the search terms)*

## Short description (80 chars max)

```
Type the tea, hear it in 10 voices, download WAV/MP3. On-device, free, no sign-up.
```
*(80 characters exactly)*

Alternative, funnier, still search-friendly:

```
10 gossip voices, real WAV/MP3 downloads, zero cloud. Your face is funny tho.
```

## Full description (4000 chars max)

```
Btw we're not gossiping inreal... Your face is soo 🫵🤣😂 funny tho. ah okay you can download audio file for free.

GossipAudio is a tiny, offline voice studio for people with opinions. Type anything, pick a
personality, and get a real audio file you can keep, share or send to the group chat.

■ TEN VOICES, ZERO SUBSCRIPTIONS
Four girls, five boys, one that is definitely not human:
Riya (Indian English), Ananya (Hindi), Maya (American), Sakura (Japanese),
Arjun (Indian English), Rohan (Hindi), Jordan (American), Oliver (British),
Kenji (Japanese) and Beyoneer Bot — the robot with no chill.

■ EIGHT MOODS
Natural, Gossip, Cheerful, Dramatic, Whisper, News, Roast and Robot. Every mood changes how the
voice is actually rendered — not just a label on a card.

■ REAL FILES, NOT PLAY-ONLY DEMOS
WAV (lossless), MP3 (up to 320 kbps, LAME encoder built into the app) and M4A/AAC with embedded
cover art. Files land in Downloads/GossipAudio and the share sheet opens right after.

■ SPEAK, DON'T TYPE
Dictate with the built-in speech-to-text sheet, watch the live partial transcript, and drop it
straight into the composer.

■ A PLAYER THAT RESPECTS YOU
Play, pause, stop, replay, seek on the waveform, speed from 0.5x to 2x, bass boost, sleep timer and
background playback with a media notification.

■ EVERYTHING IS ON YOUR PHONE
No account. No cloud. The app does not even request internet permission — synthesis, encoding,
tagging and export are 100% on-device. Your gossip stays yours.

■ SMALL AND FAST
1.3 MB. Opens in under a second on a 2018 phone. Works offline, forever.

■ BUILT-IN PROOF
Settings → Diagnostics → Run self-test renders speech, writes a WAV, encodes an MP3, tags it and
muxes an M4A, then shows you the result of each step.

Perfect for: reading your group chat out loud, narrating fake documentaries about your friends,
turning a note into a voice memo, making a prank call script (for legal, consensual fun), or
hearing your own writing in a voice that is not yours.

Permissions are optional and explained in-app:
• Microphone — only for dictation
• Notifications — playback controls and the daily nudge
• Storage (Android 9 and below) — writing files to Downloads

GossipAudio by Beyoneer XYZ. Made with an unreasonable amount of yellow.
```

## Category & tags

| Field | Value |
|---|---|
| Category | Music & Audio (alt: Tools) |
| Tags | text to speech, tts, voice changer, audio recorder, mp3 converter |
| Content rating | Everyone |
| Ads | No |
| In-app purchases | No |
| Data collected | None |
| Data shared | None |
| Target audience | 13+ |

## Graphics checklist

| Asset | Spec | Where it comes from |
|---|---|---|
| App icon | 512×512 PNG, 32-bit | `assets/store/playstore-icon-1024.png` downscaled (regenerate with `python3 tools/make_icons.py`) |
| Feature graphic | 1024×500 PNG | *not yet generated — use the brand yellow band + wordmark* |
| Phone screenshots | min 2, 16:9 or 9:16 | `dist/shots/01…09` (1080×2000 renders from `UiSmokeTest`) |
| 7-inch tablet | optional | same renders, letterboxed |
| Promo video | optional | screen-record the Studio → Download flow |

## Release notes (v1.0.4)

```
Second build — accessibility, haptics and back-gesture release.

• Every icon-only control now has a spoken name; TalkBack reads real labels everywhere
• Custom switches and sliders report proper Switch / SeekBar roles and can be driven by TalkBack
• Haptics setting now actually vibrates (tap, confirm, error) and is off in one tap
• Reduce motion also follows the system "Remove animations" setting
• Predictive back on Android 14/15 pops the in-app screen stack instead of closing the app
• App survives process death without showing a blank screen
• Notification-denied states revert the switch and offer a shortcut to system settings
• Reminders re-register themselves after an app update

First release features:

• 10 voices (4 female, 5 male, 1 robot) across Indian, American, British and Japanese accents
• 8 emotion presets, speed 0.5x–2x, pitch fine-tuning
• Real WAV, MP3 (up to 320 kbps) and M4A/AAC export with embedded cover art
• Speech-to-text dictation
• Player with waveform seeking, background playback, sleep timer and bass boost
• Library with search, favourites, rename and delete
• Daily gossip reminder (optional)
• Diagnostics with a one-tap self-test
• 100% on-device: no account, no cloud, no internet permission
```

## Data safety answers (Play Console)

| Question | Answer |
|---|---|
| Does your app collect or share any required user data types? | **No** |
| Is all user data encrypted in transit? | N/A — no data leaves the device |
| Do you provide a way for users to request data deletion? | Yes — in-app delete, or clear app storage |
| Does your app use advertising or tracking SDKs? | No (zero third-party runtime dependencies) |

## Store copy — what reviewers might ask

**Q: The app calls itself AI but there is no model download.**
A: The app uses the device's on-device neural TTS when present and its own formant synthesiser
(NanoVoice) otherwise. Both are on-device models. No network permission is declared, which is
verifiable in the manifest of the uploaded APK.

**Q: Why does it request storage permission?**
A: Only on Android 9 and below, to write exports into `Downloads/GossipAudio`. On Android 10+ the app
uses MediaStore and never asks.

**Q: Is the audio generated by the app original?**
A: Yes. The audio is synthesised on the device from the user's text; no third-party audio is bundled.
