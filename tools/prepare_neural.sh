#!/usr/bin/env bash
# Fetch pinned, separately licensed runtime and model. No device TTS required.
set -euo pipefail
cd "$(dirname "$0")/.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
runtime=https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.17/sherpa-onnx-1.12.17.aar
model=https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-vctk-medium-int8.tar.bz2
curl -fL --retry 3 "$runtime" -o "$work/runtime.aar"
curl -fL --retry 3 "$model" -o "$work/model.tar.bz2"
echo '96853e39b5b93f90c7d161cace8eb0898339c7dd6017c9f92975cb330e9d66fd  '"$work/runtime.aar" | sha256sum -c -
echo 'a506a3798c69bd9116f07d6ee6d4a502dd9e35742349fd56bafa192acba93f95  '"$work/model.tar.bz2" | sha256sum -c -
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
