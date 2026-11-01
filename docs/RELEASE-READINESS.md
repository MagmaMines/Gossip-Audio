# Release readiness (no fairy dust included)

The current source has not been built or tested in the present environment. The checked-in `dist` APKs and reports are historical and do not verify 1.1.0. The app is not yet certified production- or commercial-ready.

Before distributing:

1. Run the build and JVM tests in [BUILD.md](BUILD.md); archive output, commit hash and checksums.
2. On real Android 7, 9, 12, 13 and 15 devices, test typing, dictation permissions, every preset with installed/missing locale data, error and retry, playback, interruptions, background media controls and notifications.
3. Export WAV/MP3/M4A with and without artwork; play each in independent players and check duration, content, tags and Downloads/share access. Test unavailable AAC encoders and low storage.
4. Test dark/light/system theme, small screens, rotation, TalkBack, large font sizes, reduced motion, notification denial and reminders after reboot.
5. Audit speech provider privacy, data retention, backup and deletion. Test offline behavior separately for each provider and language.
6. Audit third-party LGPL obligations in `licenses/`, voice-provider terms and commercial usage rights; obtain legal review. Secure a private signing key and test upgrade paths. Complete Android/Play policy, crash, accessibility, performance and security reviews.

No test can promise zero failures. The best plan is to make failure visible, test on real devices, and keep an eye on releases. Butterflies are excellent at animation, less excellent at QA.
