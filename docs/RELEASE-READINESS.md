> **Update:** Lessac is research/non-commercial restricted. Cori and Padmavathi have separate provenance; Padmavathi model license and initialization require confirmation. The 1.3.0 downloads, synthesis and cold-start/theme layout passed Android emulator checks; physical-device and listening tests remain necessary; see [VOICES.md](VOICES.md).

# Release readiness

This project is not yet certified production- or commercial-ready. The 1.2.0 debug APK was built and its neural speech pipeline, WAV round-trip and MP3 encoding were checked on an Android 10 x86_64 emulator. The emulator generated the WAV sample in `dist/` and found no hard-clipped PCM samples. An Android emulator can check that the neural model loads and generates non-silent audio, and that WAV/MP3 data is created, but those checks cannot measure naturalness, pronunciation or freedom from every artifact. Listen to the generated sample and test on real phones, speakers and headphones before release.

Before distribution: test language handling, long text, storage and permissions; verify WAV, MP3 and M4A output in independent players; test playback interruptions, notifications, reminders, accessibility, themes and upgrade paths; audit privacy and model/runtime/eSpeak license terms; sign with a private production key; and complete Play policy, security and performance reviews.
