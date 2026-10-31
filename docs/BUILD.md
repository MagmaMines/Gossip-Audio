# Build, sign and ship

## TL;DR

```bash
git clone https://github.com/MagmaMines/Gossip-Audio.git
cd Gossip-Audio
gradle assembleRelease          # or ./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

Requirements: **JDK 17**, **Android SDK 35** (`platforms;android-35`, `build-tools;35.0.0`), **Gradle 8.11+**.
Nothing else — no NDK, no Node, no Python needed for the app itself.

---

## 1. Project layout

```
build.gradle.kts                 root build script (AGP 8.7.3, Kotlin 2.0.21)
settings.gradle.kts              single module ":app"
gradle.properties                JVM args, AndroidX off, non-transitive R
app/build.gradle.kts             app config: xyz.beyoneer.gossipaudio, 1.0.0, minSdk 24, targetSdk 35, signing
app/src/main/java/xyz/...        the app (48 Kotlin files)
app/src/main/java/ru/wowa/...    vendored jump3r LAME port (82 Java files, LGPL-2.1)
app/src/main/res                 strings, colours, themes, mipmaps, logo
app/src/test/java/...            JVM pipeline tests
keystore/gossipaudio-release.jks demo release keystore (alias "gossipaudio")
tools/                           icon generator, APK verifier, audio verifier, vendor script
.github/workflows/android.yml    CI: build → test → verify → publish dist back into the repo
dist/                            built APKs, AAB, build report, verification report, sample audio
```

## 2. Signing

`app/build.gradle.kts` reads the keystore from the environment, with demo defaults:

| Variable | Default |
|---|---|
| `GA_STORE_PASSWORD` | `GossipAudio#2025` |
| `GA_KEY_ALIAS` | `gossipaudio` |
| `GA_KEY_PASSWORD` | `GossipAudio#2025` |
| `GA_KEYSTORE` | `keystore/gossipaudio-release.jks` |

```bash
export GA_STORE_PASSWORD='…'
export GA_KEY_ALIAS='…'
export GA_KEY_PASSWORD='…'
gradle assembleRelease
```

> **Before a store release:** generate your own keystore, delete the demo one, and never commit real
> passwords. The demo key exists so CI can produce an installable, updatable APK out of the box:
>
> ```bash
> keytool -genkeypair -v -keystore keystore/my-release.jks -alias myalias \
>   -keyalg RSA -keysize 4096 -validity 10000 -storetype JKS
> ```

The release APK is signed with the **v2** scheme (minSdk 24), so it installs on Android 7.0+ and
upgrades cleanly over older installs signed with the same key. `tools/verify_apk.py` prints the scheme.

## 3. Build variants

| Task | Output | Notes |
|---|---|---|
| `assembleDebug` | `app-debug.apk` | applicationId suffix `.debug`, can coexist with release |
| `assembleRelease` | `app-release.apk` | minified-by-default? no: `isMinifyEnabled = false` for readable stack traces; R8 shrinking optional |
| `bundleRelease` | `app-release.aab` | Play Store upload |
| `testDebugUnitTest` | `app/build/test-samples/` | renders real audio for inspection |

## 4. CI pipeline (`.github/workflows/android.yml`)

```
push ─► Checkout ─► JDK 17 ─► Gradle 8.11.1 ─► SDK check ─► SDK install
     ─► assembleDebug assembleRelease bundleRelease   (--continue, full log to ci-logs/)
     ─► testDebugUnitTest                             (8 pipeline tests, log to ci-logs/)
     ─► Prepare dist/                                 (APKs, AAB, BUILD_INFO.txt, samples)
     ─► Verify artifacts                              (tools/verify_apk.py + tools/verify_audio.py)
     ─► Upload workflow artifact
     ─► Commit dist/ back into the branch             ("ci: publish APK + build report [skip ci]")
     └─ on failure: commit ci-logs/failure.txt with compiler errors, failing tests, verifier output
```

Why publish into the repo? Because Actions artifacts expire and are not reachable from every
environment; committing `dist/` means **the APK in the repository is always the one that matches the
source tree**, with a `BUILD_INFO.txt` listing the commit SHA, Java version and SHA-256 hashes.

Self-diagnosing failure capture is deliberate: compiler errors (`e: file:line:col`), failing test XML,
and the verifier report all end up in `ci-logs/failure.txt`, so a failed build can be debugged from a
`git pull` alone.

## 5. Verifying a build yourself

```bash
python3 tools/verify_apk.py dist/GossipAudio-1.0.0-release.apk
python3 tools/verify_audio.py dist/samples
```

The APK verifier parses Android's **binary XML** manifest and the **APK Signing Block** without any
SDK tooling. It checks: package name, version, min/target SDK, application class, permissions,
activities/services/receivers, launcher intent filter, signing schemes, dex contents (including the
LAME classes), and that the brand strings exist in `resources.arsc`.

The audio verifier parses RIFF chunks, ID3v2.3 frames and MPEG-1/2 Layer III frame headers, and reports
bitrate, sample rate, channel mode, frame count and duration.

Expected output (release build):

```
package: xyz.beyoneer.gossipaudio   versionName: 1.0.0   minSdk: 24   targetSdk: 35
permissions: VIBRATE, RECORD_AUDIO, POST_NOTIFICATIONS, FOREGROUND_SERVICE(+MEDIA_PLAYBACK),
             WAKE_LOCK, RECEIVE_BOOT_COMPLETED, SCHEDULE_EXACT_ALARM, WRITE_EXTERNAL_STORAGE(≤28)
activities: ui.MainActivity   services: playback.PlaybackService
receivers: notify.ReminderReceiver, notify.BootReceiver
signature: v2   ·   dex: classes.dex (2.8 MB)   ·   no INTERNET permission
```

## 6. Regenerating icons

```bash
python3 tools/make_icons.py         # needs Pillow
```

It takes `GossipAudio.png` (500×500) and writes every density of `ic_launcher`, `ic_launcher_round`,
`ic_launcher_foreground`, plus `drawable-nodpi/gossipaudio_logo.png`, `gossipaudio_glyph.png` and the
1024×1024 Play Store icon in `assets/store/`.

## 7. Common problems

| Symptom | Fix |
|---|---|
| `SDK location not found` | set `ANDROID_HOME` or create `local.properties` with `sdk.dir=…` |
| `Unsupported class file major version` | you are on JDK 21+; the project targets JVM 17 (Gradle 8.11 supports 21, but keep 17 for parity with CI) |
| `Keystore file not found` | either add `keystore/gossipaudio-release.jks` or set `GA_KEYSTORE` |
| Robolectric tests cannot download `android-all` | CI has network; locally, run once online or pre-populate `~/.m2` |
| `dist/` conflicts after a CI run | `git pull --rebase` — CI commits there on purpose |
