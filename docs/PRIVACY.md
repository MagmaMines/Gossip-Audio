# Privacy — short, because there is not much to say

**GossipAudio does not have the `INTERNET` permission.** Not "we promise not to upload" — the app
literally cannot open a socket. `tools/verify_apk.py` prints the permission list from the built APK:

```
permissions: VIBRATE, RECORD_AUDIO, POST_NOTIFICATIONS, FOREGROUND_SERVICE,
             FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK, RECEIVE_BOOT_COMPLETED,
             SCHEDULE_EXACT_ALARM, WRITE_EXTERNAL_STORAGE (maxSdkVersion 28)
```

## What leaves the device

Nothing. Text, audio, covers, preferences and the clip index stay in app-private storage.
Exports only leave when **you** tap Download or Share, and then only to the folder or app you choose.

## Why each permission exists

| Permission | Why | What happens if you refuse |
|---|---|---|
| `VIBRATE` | haptic tick on buttons | no vibration, everything else fine |
| `RECORD_AUDIO` | speech-to-text dictation | dictation disabled, typing still works |
| `POST_NOTIFICATIONS` (33+) | playback controls + export/reminder notifications | playback still works, no notification |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | keep audio alive with the screen off | background playback stops when the app is killed |
| `WAKE_LOCK` | keep the CPU awake while the service plays | playback may stutter on aggressive OEMs |
| `RECEIVE_BOOT_COMPLETED` | re-arm the daily reminder after a reboot | reminder stops after a reboot |
| `SCHEDULE_EXACT_ALARM` | fire the reminder at the minute you picked | reminder may arrive a few minutes late |
| `WRITE_EXTERNAL_STORAGE` (≤ Android 9 only) | write `Downloads/GossipAudio` on old devices | downloads stay inside app storage |

## Data the app stores

| Data | Where | Delete it via |
|---|---|---|
| Clips (audio, cover, metadata) | `files/clips/`, `files/covers/`, `files/clips.json` | Library → delete / delete all, or Android app settings → Clear storage |
| Preferences | `SharedPreferences` (`gossipaudio_prefs`) | Clear storage |
| Dictation buffers, TTS captures, covers pending crop | `cache/` | Clear storage (or the OS reclaims it) |

There is no account, no identifier, no analytics SDK, no crash reporter, no ad library. The dependency
list at runtime is **the Android framework only** — see `app/build.gradle.kts`: `implementation` has
zero entries.

## Third-party code

* **jump3r / LAME** — pure Java, compiled into the APK, no telemetry, works offline. LGPL-2.1.
* Everything else is original code in this repository.

## Kids / GDPR / DPDP

Because nothing is collected or transmitted, there is no personal data to process, export or erase on a
server. The Play Store data-safety form for this app is: *no data collected, no data shared*.
