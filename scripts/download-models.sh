#!/usr/bin/env bash
# Download Depth Anything V2 ONNX weights.
# Default: Small (~100MB) into ./models/ (gitignored).
#
# Usage:
#   ./scripts/download-models.sh
#   ./scripts/download-models.sh small
#   ./scripts/download-models.sh small --assets   # copy Small into APK assets (CI)
#   ./scripts/download-models.sh base
#   ./scripts/download-models.sh all
#
# Only Small should be packaged in the APK. Base/Large stay on-demand downloads.
# Do not commit *.onnx; GitHub Actions fetches Small into assets before assembleDebug.

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/models"
ASSETS="$ROOT/app/src/main/assets/models"
mkdir -p "$OUT" "$ASSETS"

MODE="small"
COPY_ASSETS=0
for arg in "$@"; do
  case "$arg" in
    --assets) COPY_ASSETS=1 ;;
    small|base|large|all) MODE="$arg" ;;
    *)
      echo "Unknown argument: $arg (use small|base|large|all [--assets])" >&2
      exit 1
      ;;
  esac
done

download() {
  local name="$1"
  local dest="$2"
  if [[ -f "$dest" ]]; then
    local bytes
    bytes="$(wc -c < "$dest" | tr -d ' ')"
    if [[ "$bytes" -gt 50000000 ]]; then
      echo "Already exists: $dest ($(du -h "$dest" | cut -f1))"
      return
    fi
    echo "Removing incomplete $dest ($bytes bytes)"
    rm -f "$dest"
  fi
  local urls=(
    "https://huggingface.co/yuvraj108c/Depth-Anything-2-Onnx/resolve/main/$name"
    "https://hf-mirror.com/yuvraj108c/Depth-Anything-2-Onnx/resolve/main/$name"
  )
  echo "Downloading $name -> $dest ..."
  local ok=0
  for url in "${urls[@]}"; do
    echo "Trying $url"
    if curl -L --fail --retry 5 --retry-delay 2 --retry-all-errors \
        -A "DepthWhiteModelAndroid/1.0" \
        -o "$dest.part" "$url"; then
      mv "$dest.part" "$dest"
      ok=1
      break
    fi
    rm -f "$dest.part"
  done
  if [[ "$ok" -ne 1 ]]; then
    echo "Failed to download $name" >&2
    exit 1
  fi
  local bytes
  bytes="$(wc -c < "$dest" | tr -d ' ')"
  if [[ "$bytes" -lt 50000000 ]]; then
    echo "Downloaded $name looks too small ($bytes bytes)" >&2
    rm -f "$dest"
    exit 1
  fi
  echo "Saved $dest ($(du -h "$dest" | cut -f1))"
}

SMALL_NAME="depth_anything_v2_vits.onnx"

case "$MODE" in
  small) download "$SMALL_NAME" "$OUT/$SMALL_NAME" ;;
  base) download depth_anything_v2_vitb.onnx "$OUT/depth_anything_v2_vitb.onnx" ;;
  large) download depth_anything_v2_vitl.onnx "$OUT/depth_anything_v2_vitl.onnx" ;;
  all)
    download "$SMALL_NAME" "$OUT/$SMALL_NAME"
    download depth_anything_v2_vitb.onnx "$OUT/depth_anything_v2_vitb.onnx"
    download depth_anything_v2_vitl.onnx "$OUT/depth_anything_v2_vitl.onnx"
    ;;
esac

if [[ "$COPY_ASSETS" -eq 1 ]]; then
  if [[ "$MODE" != "small" && "$MODE" != "all" ]]; then
    echo "--assets only packages Small; ignoring for $MODE" >&2
    exit 1
  fi
  src="$OUT/$SMALL_NAME"
  dest="$ASSETS/$SMALL_NAME"
  if [[ ! -f "$src" ]]; then
    echo "Missing $src" >&2
    exit 1
  fi
  cp -f "$src" "$dest"
  bytes="$(wc -c < "$dest" | tr -d ' ')"
  if [[ "$bytes" -lt 50000000 ]]; then
    echo "Bundled Small looks too small ($bytes bytes)" >&2
    exit 1
  fi
  echo "Copied Small into APK assets: $dest ($(du -h "$dest" | cut -f1))"
  echo "Do not commit this file; it is gitignored."
fi
