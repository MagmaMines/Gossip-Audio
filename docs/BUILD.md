# Build GossipAudio

Requirements: JDK 17, Gradle 8.11.1, Android SDK platform 35 and build tools 35.0.0, and network access to GitHub releases, Google Maven and Maven Central. There is no Gradle wrapper checked in.

```sh
./tools/prepare_neural.sh  # download and checksum-verify model + runtime
gradle --no-daemon assembleDebug
gradle --no-daemon testDebugUnitTest --tests '*PipelineTest' --tests '*SpeechChunkTest'
```

Install `app/build/outputs/apk/debug/app-debug.apk` on a supported arm64-v8a or x86_64 device. The debug application ID is `xyz.beyoneer.gossipaudio.debug`; it is not an update to the release app. Neural JNI inference requires testing on an actual Android device; JVM tests do not verify the model or hardware playback.

The preparation script pins and verifies sherpa-onnx 1.12.17 and `vits-piper-en_GB-vctk-medium-int8` assets using SHA-256. They are generated into ignored files, not committed into source. For a release, independently verify their provenance, the model/dataset/eSpeak terms and all redistribution obligations.

For private release signing, provide `GA_STORE_FILE`, `GA_STORE_PASSWORD`, `GA_KEY_ALIAS`, and `GA_KEY_PASSWORD` in a secure environment before `gradle assembleRelease bundleRelease`. Without all four, the release output is unsigned. Never use the public historical demo key in this repo. Verify with `apksigner`, test installation and upgrade paths, and complete legal, policy, security, accessibility and device QA before commercial distribution.
