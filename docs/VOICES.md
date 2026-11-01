# Voice

The app uses **one** pretrained English (US) voice, Piper `en_US-lessac-medium`, in its full-precision ONNX form, distributed in the sherpa-onnx compatible model archive. The model originates from [Piper voice models on Hugging Face](https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/lessac/medium). The bundled model runs in the app through sherpa-onnx CPU inference; speech generation never calls the Android TTS service and needs no internet connection.

The model is not multilingual. Hindi and Japanese are not included. Speech recognition is separate and uses the Android recognition service. The model is a small VITS neural model, not a guarantee of studio-quality or universally perfect pronunciation. This full-precision version replaces the previous int8 multi-speaker model to avoid possible quantization artifacts. PCM conversion scales peaks before conversion rather than clipping samples, and avoids heavy normalization; brief fades reduce chunk-edge clicks.

As with any pretrained voice, review upstream model, dataset, eSpeak and runtime licenses and terms before commercial distribution.
