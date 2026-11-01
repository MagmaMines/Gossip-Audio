# GossipAudio

**Your words, their wings.** 🦋

An Android voice studio by **Beyoneer XYZ**.

<img src="GossipAudio.png" alt="GossipAudio logo" width="160">

## Neural voices

GossipAudio runs neural Piper/VITS voices locally with sherpa-onnx; it does **not** use Android system TTS for speech generation. The bundled US English Lessac voice is **restricted to non-commercial/research use** based on its training recordings; the previous build failed to flag this restriction. Optional British English Cori, American English LJ Speech and Telugu Padmavathi voices can be downloaded from the Voices tab, after which synthesis is offline. The Telugu model card cites CC BY 4.0 data but does not specify model-weight licensing or training initialization, so confirm those rights before commercial deployment. See [voice provenance and licensing](docs/VOICES.md), including separate eSpeak obligations.

Dictation uses a separate Android recognition service and may use the network. The APK targets ARM64 Android 7+ and is a debug build, not a commercial release. Voice quality varies with language and device; no subjective quality guarantee is implied.

## Download and listen

- [GossipAudio 1.3.0 neural debug APK](dist/GossipAudio-1.3.0-neural-debug.apk) (ARM64, Android 7+)
- [Neural voice sample (WAV)](dist/GossipAudio-1.3.0-neural-sample.wav), generated through the app's speech pipeline on an Android 10 emulator

The 1.3.0 CI build ran JVM tests and Android-emulator tests for all four neural voices, voice downloads, WAV/MP3 export, and cold-start/theme layout. Tests are not a substitute for listening or physical-device testing. This is not a listening-quality certification or a substitute for testing physical devices.

## Studio features

Type or dictate, preview the voice, adjust speed, generate, play/pause/replay/seek, and keep clips in the library. The interface also offers WAV, MP3 and M4A export, optional MP3/M4A artwork, sharing, favourites, search, rename/delete, background playback, media notification, sleep timer, reminders, themes, reduced motion, haptics and diagnostics. Device support and permissions vary; M4A artwork is metadata, not a video.

See [build instructions](docs/BUILD.md), [voice details](docs/VOICES.md), [privacy](docs/PRIVACY.md) and [release readiness](docs/RELEASE-READINESS.md). Third-party notices: [licenses/NOTICE.md](licenses/NOTICE.md). Older APKs and historical documentation may describe different engines. Review upstream model and dataset licensing before commercial use.
