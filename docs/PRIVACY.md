# Privacy

Neural speech generation, encoding and local playback run on-device. The app uses INTERNET for optional voice downloads from GitHub and its CDN, and for Google AdMob ads and User Messaging Platform (UMP) consent checks. Downloads can be large: about 129 MB for the shared Supertonic language pack and 365 MB for Mandarin. Automatic language hints are evaluated locally. Text is not sent for synthesis, and no account is required. Text and clips are stored in app-private storage; user-exported files are saved to Downloads and can be shared. Android backup behavior follows the app's backup rules and device settings.

Dictation uses a separate Android recognition service; that service may send audio to its provider even when offline is preferred. Review its privacy policy and installed offline packs. Microphone and notification permissions are optional and requested for the relevant features.

## Advertising and privacy choices

A Google AdMob banner may appear in the main app. The Google Mobile Ads and UMP SDKs communicate with Google and may collect or use IP address, approximate location, device and advertising identifiers, app information, diagnostics, and ad interaction data, subject to device settings, consent and Google's policies. GossipAudio does not intentionally pass the text being synthesized or your clips to the ad SDK. Ad personalization and choices depend on the user's region and the messages the publisher configures in AdMob. See [Google's advertising privacy information](https://policies.google.com/technologies/ads) and [Google Mobile Ads SDK data disclosures](https://developers.google.com/admob/android/privacy/play-data-disclosure). Where UMP requires it, Settings provides **Ad privacy choices**. Ads do not appear offline; speech synthesis remains on-device.

The publisher must configure and publish applicable AdMob Privacy & messaging forms, review regional requirements, publish an appropriate privacy policy and complete accurate store data-safety disclosures before distribution. This document is not a substitute for legal advice.
