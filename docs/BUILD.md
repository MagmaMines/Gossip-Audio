# Build GossipAudio

Requirements: JDK 17, Gradle 8.13, Android SDK platform 35 and build tools 35.0.0, and network access to GitHub releases, Google Maven and Maven Central. No Gradle wrapper is checked in.

```sh
./tools/prepare_neural.sh  # downloads checksum-pinned runtime and full-precision model
gradle --no-daemon assembleDebug
gradle --no-daemon testDebugUnitTest --tests '*PipelineTest' --tests '*SpeechChunkTest' --tests '*AutoVoiceTest'
```

The debug APK at `app/build/outputs/apk/debug/app-debug.apk` targets ARM64 Android 7+ and uses application ID `xyz.beyoneer.gossipaudio.debug`. For an x86_64 emulator, build and run instrumentation tests with `gradle -PneuralTestEmulator=true connectedDebugAndroidTest`. Android inference tests verify non-silent unclipped PCM and WAV/MP3 encoding, not subjective speech quality or physical devices.

The script pins sherpa-onnx 1.13.8 and the full-precision `vits-piper-en_US-ljspeech-medium` archive by SHA-256. The [model creator](https://brycebeattie.com/files/tts/) states the model is public domain and trained from scratch. Assets are generated into ignored files. Extra downloaded voices, eSpeak and runtime each have separate provenance and license obligations; see VOICES.md.

For the 1.6.0 build, debug uses Google's test ad unit. The `release` build uses the production AdMob app and banner IDs in `docs/ADVERTISING.md`. An unsigned release cannot be installed until signed. CI's workflow uploads the unsigned ARM64 release APK and the x86_64 emulator debug APK as an Actions artifact; the ARM64 debug APK is published in `dist`. If Git stores the debug APK in numbered `.part-*` files because of GitHub's file limit, reconstruct it with `cat dist/GossipAudio-1.6.0-neural-debug.apk.part-* > dist/GossipAudio-1.6.0-neural-debug.apk` and verify with `sha256sum -c dist/GossipAudio-1.6.0-neural-debug.sha256`.

To build a privately signed release, set `GA_STORE_FILE`, `GA_STORE_PASSWORD`, `GA_KEY_ALIAS`, and `GA_KEY_PASSWORD` in a secure environment before `gradle assembleRelease bundleRelease`. Without all four, the release output is unsigned. Never use the historical public demo key. Verify with `apksigner`, test on physical devices and complete legal, policy, security and accessibility reviews before commercial distribution.
