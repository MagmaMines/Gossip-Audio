# Neural voices and licensing

All speech generation uses sherpa-onnx on-device, never Android's system TTS. Extra voices are downloaded only after confirmation in Voices; after installation synthesis works offline. The catalog includes Piper/VITS, Supertonic 3 int8, and full-precision Kokoro models; no model guarantees perfect pronunciation. Listen and evaluate on your target devices.

| Voice | Language | Availability | Provenance and license caveat |
|---|---|---|---|
| Lessac (`en_US-lessac-medium`) | English (US) | Bundled, legacy ID `riya` | **Non-commercial/research restricted**: its [model card](https://huggingface.co/rhasspy/piper-voices/blob/main/en/en_US/lessac/medium/MODEL_CARD) cites the [Blizzard 2013 Lessac recordings](https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html). Do not use commercially without appropriate rights. |
| Cori (`en_GB-cori-medium`) | English (UK) | Optional ~67 MB download | The [model card](https://huggingface.co/rhasspy/piper-voices/blob/main/en/en_GB/cori/medium/MODEL_CARD) says trained from scratch using public-domain LibriVox recordings. Model/data rights and third-party software licenses are distinct; review them for your use. |
| LJ Speech (`en_US-ljspeech-medium`) | English (US) | Optional ~67 MB download | [Voice creator Bryce Beattie](https://brycebeattie.com/files/tts/) states the model was trained from scratch on the public-domain LJ Speech dataset and lists its license as public domain. |
| Padmavathi (`te_IN-padmavathi-medium`) | Telugu (India) | Optional ~64 MB download | The [model card](https://huggingface.co/rhasspy/piper-voices/blob/main/te/te_IN/padmavathi/medium/MODEL_CARD) credits PravalX and [AI4Bharat IndicVoices-R](https://huggingface.co/datasets/ai4bharat/indicvoices_r), CC BY 4.0; attribution and license terms apply. The card does not specify the training initialization or a separate model-weights license: confirm these with the publisher before commercial deployment. The sherpa-compatible conversion is provided by [JesusAnswers](https://github.com/jesusanswersapp/jesusanswers/releases/tag/voices-v1). |

The app verifies pinned SHA-256 hashes before installing downloads. Voice downloads contact GitHub and its CDN; text is not uploaded. English voices expect Latin-script English; Padmavathi expects Telugu script. The eSpeak NG phonemization data, sherpa-onnx runtime and their dependencies have their own licenses (including GPL obligations for eSpeak NG); review source, notices, attribution and distribution duties independently. This debug build is not a legal clearance or a commercial release.

## 1.4 multilingual downloads

| Voice | Source | Terms |
|---|---|---|
| Hindi, Japanese, French | Shared [Supertonic 3](https://huggingface.co/Supertone/supertonic-3) int8 ONNX (~129 MB download), converted by sherpa-onnx | [BigScience Open RAIL-M](https://huggingface.co/Supertone/supertonic-3/blob/main/LICENSE): no-charge commercial use and distribution subject to its use restrictions, disclosure and redistribution conditions. Review section 5 and Attachment A; this is not an unrestricted Apache license. |
| Mandarin Chinese | [Kokoro 1.1-zh](https://huggingface.co/hexgrad/Kokoro-82M-v1.1-zh), sherpa-onnx full-precision export (~365 MB download) | Model is Apache 2.0; retain applicable copyright/license notices. The model creator says the Chinese data was permissively provided by LongMaoData. |

OpenRAIL-M Attachment A requires intelligible disclosure when disseminating generated content, in addition to its other use restrictions. If redistributing the Supertonic model, provide recipients the license and preserve the required notices; review the full license for all conditions.

Downloads are checksum-pinned and install to app-private storage. A single Supertonic download unlocks all three languages. Actual speech generation is offline; model availability and subjective quality vary across devices. These model terms do not override third-party rights or the separate licenses of the app's eSpeak and runtime dependencies. The older bundled Lessac remains non-commercial/research restricted; Telugu Padmavathi provenance remains unresolved, as described above. No blanket commercial clearance is implied for the entire app or all voices.
