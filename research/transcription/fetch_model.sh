#!/bin/sh
# Downloads the Basic Pitch model (Spotify, Apache-2.0) into models/.
set -e
cd "$(dirname "$0")"
tmp=$(mktemp -d)
python3 -m pip download --no-deps --quiet basic-pitch==0.4.0 -d "$tmp"
python3 -m zipfile -e "$tmp"/basic_pitch-0.4.0-*.whl "$tmp/x"
mkdir -p models
cp "$tmp/x/basic_pitch/saved_models/icassp_2022/nmp.onnx" models/
rm -rf "$tmp"
echo "models/nmp.onnx ready"
