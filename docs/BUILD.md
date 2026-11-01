# Build GossipAudio

Requirements: JDK 17, Gradle 8.11.1, Android SDK platform 35 and build tools 35.0.0, and network access to GitHub releases, Google Maven and Maven Central. No Gradle wrapper is checked in.

```sh
./tools/prepare_neural.sh  # downloads checksum-pinned runtime and full-precision model
gradle --no-daemon assembleDebug
gradle --no-daemon testDebugUnitTest --tests '*PipelineTest' --tests '*SpeechChunkTest'
```

The debug APK at `app/build/outputs/apk/debug/app-debug.apk` targets ARM64 Android 7+ and uses application ID `xyz.beyoneer.gossipaudio.debug`. For an x86_64 emulator, build and run instrumentation tests with `gradle -PneuralTestEmulator=true connectedDebugAndroidTest`. Android inference tests verify non-silent unclipped PCM and WAV/MP3 encoding, not subjective speech quality or physical devices.

The script pins sherpa-onnx 1.13.8 and `vits-piper-en_US-lessac-medium` with SHA-256 checksums. Assets are generated into ignored files; original model source is [Hugging Face](https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/lessac/medium). Lessac is non-commercial/research-restricted; do not use it commercially without rights. Extra downloaded voices, eSpeak and runtime each have separate provenance and license obligations; see VOICES.md.

To build a privately signed release, set `GA_STORE_FILE`, `GA_STORE_PASSWORD`, `GA_KEY_ALIAS`, and `GA_KEY_PASSWORD` in a secure environment before `gradle assembleRelease bundleRelease`. Without all four, the release output is unsigned. Never use the historical public demo key. Verify with `apksigner`, test on physical devices and complete legal, policy, security and accessibility reviews before commercial distribution.
