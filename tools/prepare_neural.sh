#!/usr/bin/env bash
# Fetch pinned, separately licensed runtime and model. No device TTS required.
set -euo pipefail
cd "$(dirname "$0")/.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
runtime=https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar
model=https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-ljspeech-medium.tar.bz2
curl -fL --retry 3 "$runtime" -o "$work/runtime.aar"
curl -fL --retry 3 "$model" -o "$work/model.tar.bz2"
echo '633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96  '"$work/runtime.aar" | sha256sum -c -
echo '3dfb4b759d8be032a4903a9538d128b0fda2a06ab1de6cbc2d93a97e2dd83dba  '"$work/model.tar.bz2" | sha256sum -c -
mkdir -p "$work/unpacked" app/libs app/src/main/assets/neural
tar -xjf "$work/model.tar.bz2" -C "$work/unpacked"
model_file=$(find "$work/unpacked" -name '*.onnx' -type f | head -1)
tokens=$(find "$work/unpacked" -name tokens.txt -type f | head -1)
data=$(find "$work/unpacked" -name espeak-ng-data -type d | head -1)
test -n "$model_file" && test -n "$tokens" && test -n "$data"
cp "$work/runtime.aar" app/libs/sherpa-onnx.aar
cp "$model_file" app/src/main/assets/neural/model.onnx
cp "$tokens" app/src/main/assets/neural/tokens.txt
rm -rf app/src/main/assets/neural/espeak-ng-data
cp -a "$data" app/src/main/assets/neural/espeak-ng-data
# Keep the model archive's license / provenance files for review.
find "$work/unpacked" -maxdepth 2 -type f ! -name '*.onnx' ! -name 'tokens.txt' -printf '%p\n'
echo 'Neural runtime and English model prepared.'
