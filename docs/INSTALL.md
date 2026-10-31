# Install GossipAudio (the APK, not a zip)

> Short version: the download is **`GossipAudio-1.0.2-release.apk`** — one file, tap it, install it.
> You do **not** need to unzip anything, and you should not rename it to `.zip`.

## 0. One link, one file

**[⬇ `GossipAudio-1.0.2-release.apk`](https://github.com/MagmaMines/Gossip-Audio/releases/download/v1.0.2/GossipAudio-1.0.2-release.apk)** — tap it on the phone and install.
The same file is attached to the [v1.0.2 release](https://github.com/MagmaMines/Gossip-Audio/releases/tag/v1.0.2)
and committed at [`dist/GossipAudio-1.0.2-release.apk`](../dist/GossipAudio-1.0.2-release.apk).

### "I got `gossipaudio-apk.zip` instead" — that was GitHub, not us

GitHub Actions **always** re-packs workflow artifacts into a zip. Our old CI step was named
`GossipAudio-apk` and uploaded `dist/`, so the artifact download arrived as `GossipAudio-apk.zip`.
That step is gone: CI now uploads `GossipAudio-CI-reports` (logs and reports only) and the APK is
delivered as a real file through the two links above. If you still see a zip, you are on an old run
page or an old download — ignore it and use the `.apk`.

## 1. The file you want

| File | What it is | Use it? |
|---|---|---|
| [`dist/GossipAudio-1.0.2-release.apk`](../dist/GossipAudio-1.0.2-release.apk) | Signed release build (RSA-4096, APK Signature Scheme v2) | **Yes — this is the app** |
| [`dist/GossipAudio-1.0.2-debug.apk`](../dist/GossipAudio-1.0.2-debug.apk) | Same code, debug signature, app id ends in `.debug` | Only if you develop |
| [`dist/GossipAudio-1.0.2.aab`](../dist/GossipAudio-1.0.2.aab) | Play-Store bundle, **not installable by hand** | Upload to Play Console only |

Both APKs are built by CI from the exact commit in [`dist/BUILD_INFO.txt`](../dist/BUILD_INFO.txt)
and re-verified on every run by `tools/verify_apk.py`.

## 2. "But an APK *is* a zip…"

Correct, and this is the part everyone trips over:

* `.apk` files use the ZIP container format (deflated entries + a central directory). That is how
  Android stores the manifest, classes, and images in one file.
* It is **not** an archive you extract. The only thing that makes it an app is the **Android binary
  manifest** (`AndroidManifest.xml` encoded as AXML) plus the **APK Signing Block** that covers the
  whole file. Android validates both before installing.

So this project ships **zero `.zip` files**. If a download arrives with a `.zip` suffix you grabbed a
source archive from GitHub's green "Code" button, not the app.

You can prove the file is a signed, installable app yourself:

```bash
python3 tools/verify_apk.py dist/GossipAudio-1.0.2-release.apk
```

```
* signature VERIFIED: RSA-PKCS1/SHA-512 / RSA-4096, certificate sha256 ead721ae…, 4088-byte signed block, 4 chunks
* dex files: classes.dex (2.8 MB uncompressed)
--- problems
 none — APK structure, manifest, signing and dex contents check out
```

`tools/apksig.py` is not a wrapper around `apksigner`; it re-implements the v2 verifier: it rebuilds
the 1 MiB content digest (with the EOCD offset patched exactly like AOSP does), then checks the
PKCS#1 v1.5 / SHA-512 signature against the certificate's RSA key. Flip one byte anywhere in the
archive and it fails. Try it:

```bash
python3 - <<'PY'
data = bytearray(open("dist/GossipAudio-1.0.2-release.apk", "rb").read())
data[700000] ^= 1
open("/tmp/tampered.apk", "wb").write(data)
PY
python3 tools/apksig.py /tmp/tampered.apk   # exit code 1, digest_ok: false
```

## 3. Install on a real phone

1. Copy the APK to the phone (USB, Google Drive, Telegram — anything).
2. Tap the file. Android will say *"For your security, your phone is not allowed to install unknown
   apps from this source."*
3. Tap **Settings → Allow from this source → Install**. (Path varies: on Android 13+ it is
   *Install unknown apps* per source app.)
4. Play Protect may ask to scan it. Allow the scan — it is a normal signed app, and it will pass.
5. Open **GossipAudio**, skip the intro, type, generate, download.

First launch asks for nothing. Microphone and notification permissions are requested **only** when
you use dictation or turn on reminders.

## 4. Install over USB / ADB

```bash
adb install -r dist/GossipAudio-1.0.2-release.apk
```

`-r` reinstalls over an older build. If you previously installed the debug build, uninstall it first —
Android refuses to replace an app whose signature changed:

```bash
adb uninstall xyz.beyoneer.gossipaudio.debug
```

## 5. Updating

Every later release uses the same signing key (unless you rotate the keystore), so a newer
`GossipAudio-x.y.z-release.apk` simply installs over the old one and keeps your library and settings.
Signing key material lives in `keystore/gossipaudio-release.jks`; see
[`BUILD.md`](BUILD.md#signing) before publishing anything.

## 6. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| "App not installed" | Stale install with a different signature | `adb uninstall xyz.beyoneer.gossipaudio` then retry |
| "There was a problem parsing the package" | Truncated/renamed download | Re-download the 1.3 MB `.apk`; do not unzip it |
| "Blocked by Play Protect" | Unknown developer | *Install anyway* / *More details → Install anyway* |
| Nothing happens on tap | File manager treats it as an archive | Open it with *Package installer* (long-press → Open with) |
| Downloads missing | Android 9 and below needs storage permission | Grant it, or use the in-app share sheet |

## 7. What actually works offline

Everything. The app has **no `INTERNET` permission** — you can install it, fly to an island, and still
render 10 voices to WAV/MP3/M4A. That is not marketing: it is verifiable in the manifest, and
`tools/verify_apk.py` prints it on every build.
