# GossipAudio

**Your words, their wings.** 🦋

An Android voice studio by **Beyoneer XYZ**.

<img src="GossipAudio.png" alt="GossipAudio logo" width="160">

## One neural voice

This version has one English (US) voice: full-precision **Piper Lessac medium**, a pretrained VITS neural model originating from [Hugging Face](https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/lessac/medium). The model and sherpa-onnx runtime are bundled into the APK and run on the CPU. **Speech generation does not call the Android system TTS engine or any cloud service.** The separate dictation feature does use your Android speech recognizer and may use the network.

Unlike the previous int8 multi-speaker model, this model is full-precision and single-speaker. Float samples are scaled before 16-bit PCM conversion if necessary, to avoid hard clipping; short fades limit clicks at chunk boundaries. Results still depend on the model, text, and listening hardware: objective checks can't prove a voice is natural or completely free of artifacts. WAV preserves the master; MP3 and AAC are lossy. Playback effects are off by default to avoid coloring or boosting the voice; you can enable them in Settings.

The voice is English-only. Hindi, Japanese, voice cloning, GPU inference and other voices are not included. This version targets 64-bit ARM Android devices (Android 7+). It is a debug build, not a commercial release.

## Studio features

Type or dictate, preview the voice, adjust speed, generate, play/pause/replay/seek, and keep clips in the library. The interface also offers WAV, MP3 and M4A export, optional MP3/M4A artwork, sharing, favourites, search, rename/delete, background playback, media notification, sleep timer, reminders, themes, reduced motion, haptics and diagnostics. Device support and permissions vary; M4A artwork is metadata, not a video.

See [build instructions](docs/BUILD.md), [voice details](docs/VOICES.md), [privacy](docs/PRIVACY.md) and [release readiness](docs/RELEASE-READINESS.md). Third-party notices: [licenses/NOTICE.md](licenses/NOTICE.md). Older APKs and historical documentation may describe different engines. Review upstream model and dataset licensing before commercial use.
