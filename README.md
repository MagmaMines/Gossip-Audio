# GossipAudio

**Your words, their wings.** 🦋

A yellow, white and black Android voice studio by **Beyoneer XYZ**.

<img src="GossipAudio.png" alt="GossipAudio logo" width="160">

## Neural speech

This version replaces Android system text-to-speech with an **embedded quantized Piper VITS English model**, running on the CPU through sherpa-onnx. Ten entries select ten speakers from the same model. No system TTS engine, account, or network is needed for speech generation. Model inference is on-device, not cloud AI. Android speech-to-text dictation remains a separate system recognition service and may use a network.

The included model is English-only. Hindi, Japanese, voice cloning, separate US/Indian/Japanese voice models, guaranteed gender identities, independent pitch control and GPU acceleration are **not** included. Style presets adjust speed; they do not guarantee emotions. The model, accents and voice quality need real-device listening tests. English text is accepted; other scripts are rejected instead of being silently handed to a different engine.

## What is in the studio?

Type or dictate, preview and select voices, adjust speaking speed, generate, play/pause/replay/seek, and save clips. The existing interface also offers WAV, MP3 and M4A export, optional artwork on MP3/M4A, share, favourites, library search, rename/delete, background playback, notification controls, sleep timer, reminders, themes, reduced motion, haptics and diagnostics. Hardware support and permissions vary. M4A artwork is metadata, not video. Encoding and playback need real-device validation.

## Build and status

See [docs/BUILD.md](docs/BUILD.md). The 1.0.x APKs in `dist/` are historical and still use the old speech path. A new debug APK, if successfully built, is named `GossipAudio-1.1.0-neural-debug.apk`. Debug builds are for testing, not commercial distribution. Consult [release readiness](docs/RELEASE-READINESS.md) and review model, dataset, eSpeak and runtime licenses before shipping; no claim of production certification or zero bugs is made.

The sample logo is `GossipAudio.png`. Third-party notices are in [licenses/NOTICE.md](licenses/NOTICE.md). Other older files under `docs/`, `dist/` and `ci-logs/` may describe historical builds.
