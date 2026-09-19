#!/usr/bin/env bash
# Download Depth Anything V2 ONNX weights for the Android app.
# Default: Small (~100MB). Pass base / large / all as needed.
#
# Usage:
#   ./scripts/download-models.sh
#   ./scripts/download-models.sh small
#   ./scripts/download-models.sh base
#   ./scripts/download-models.sh all
#
# Files are stored in ./models/ (gitignored). The app downloads the same
# URLs on first use; this script is for offline / CI pre-seeding:
#   adb push models/depth_anything_v2_vits.onnx /sdcard/Download/
# then copy into the app's files/models/ directory if desired.

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/models"
mkdir -p "$OUT"

MODE="${1:-small}"

download() {
  local name="$1"
  local dest="$OUT/$name"
  if [[ -f "$dest" ]]; then
    echo "Already exists: $dest"
    return
  fi
  local urls=(
    "https://huggingface.co/yuvraj108c/Depth-Anything-2-Onnx/resolve/main/$name"
    "https://hf-mirror.com/yuvraj108c/Depth-Anything-2-Onnx/resolve/main/$name"
  )
  echo "Downloading $name ..."
  local ok=0
  for url in "${urls[@]}"; do
    if curl -L --fail --retry 3 -o "$dest.part" "$url"; then
      mv "$dest.part" "$dest"
      ok=1
      break
    fi
  done
  if [[ "$ok" -ne 1 ]]; then
    echo "Failed to download $name" >&2
    rm -f "$dest.part"
    exit 1
  fi
  echo "Saved $dest ($(du -h "$dest" | cut -f1))"
}

case "$MODE" in
  small) download depth_anything_v2_vits.onnx ;;
  base) download depth_anything_v2_vitb.onnx ;;
  large) download depth_anything_v2_vitl.onnx ;;
  all)
    download depth_anything_v2_vits.onnx
    download depth_anything_v2_vitb.onnx
    download depth_anything_v2_vitl.onnx
    ;;
  *)
    echo "Unknown mode: $MODE (use small|base|large|all)"
    exit 1
    ;;
esac
