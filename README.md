# GossipAudio

**Your words, their wings.** 🦋

An Android voice studio by **Beyoneer XYZ**.

<img src="GossipAudio.png" alt="GossipAudio logo" width="160">

## Neural voices

GossipAudio runs Piper/VITS, Supertonic and Kokoro neural voices locally, without Android system TTS. The bundled LJ Speech and optional Cori models are described by their creator as public domain. Optional Supertonic voices include Hindi, Japanese, French, Spanish, German, Italian, Portuguese, Korean and Arabic; OpenRAIL-M permits commercial use but has use restrictions and generated-content disclosure requirements. Optional Mandarin Kokoro is Apache 2.0. Tap ⓘ beside each voice to see terms. Read [voice provenance and licensing](docs/VOICES.md): model licenses do not remove rights in your input, output, or third-party software. Earlier Lessac and Padmavathi voices are no longer offered; any old audio retains its original obligations.

Ads, consent checks, optional voice downloads and dictation use the network. Dictation uses a separate Android recognition service. The signed release targets ARM64 Android 7+; physical-device, ad-serving and policy checks are still needed before distribution. Voice quality varies with language and device; no subjective quality guarantee is implied.

## Download and listen

[Download GossipAudio 1.6.2 release APK](dist/GossipAudio-1.6.2-release.apk) — one signed ARM64 Android 7+ APK with the supplied live AdMob banner ID.

CI verified the release signature and embedded ad IDs, and ran unit and Android-emulator neural/UI tests on a debug build with ad requests disabled. Actual ad serving, consent messaging, physical-device behavior and listening quality have not been verified.

If updating an app signed with another key, Android will reject the update. Back up app data before uninstalling. Keep the separately supplied signing-key archive safe; it is required to sign updates with this new key.

## Advertising

One banner appears below the top bar on the main tabs, separated from controls; it is hidden while editing text and on Settings and full-screen pages. Consent information is updated via Google's UMP before the ad SDK is initialized. Privacy options appear in Settings when required. If consent or an ad is unavailable, the banner area collapses. See [privacy](docs/PRIVACY.md) and [ad setup](docs/ADVERTISING.md).

**The 1.6.2 release uses the supplied live banner unit.** Debug builds use Google test ads and do not earn revenue. Actual serving and earnings depend on AdMob account status, consent setup, demand and compliance.

## Automatic voice selection

Tap **Auto voice** in the text composer. Language hints are evaluated offline. Ambiguous or unsupported text requires manual selection; Han-only text may be either Chinese or Japanese. A detected language requires its model to be installed. Select a voice manually to turn auto mode off.

## Studio features

Type or dictate, preview the voice, adjust speed, generate, play/pause/replay/seek, and keep clips in the library. The interface also offers WAV, MP3 and M4A export, optional MP3/M4A artwork, sharing, favourites, search, rename/delete, background playback, media notification, sleep timer, reminders, themes, reduced motion, haptics and diagnostics. Device support and permissions vary; M4A artwork is metadata, not a video.

See [build instructions](docs/BUILD.md), [voice details](docs/VOICES.md), [privacy](docs/PRIVACY.md) and [release readiness](docs/RELEASE-READINESS.md). Third-party notices: [licenses/NOTICE.md](licenses/NOTICE.md). Older APKs and historical documentation may describe different engines. Review upstream model and dataset licensing before commercial use.
