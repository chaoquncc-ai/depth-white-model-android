# Place Depth Anything V2 ONNX files here (not committed).
#
# Small (`depth_anything_v2_vits.onnx`, ~100MB) is fetched by CI into this folder
# before assembleDebug so the APK ships with the default model.
#
# Local packaging:
#   ./scripts/download-models.sh small --assets
#
# Base / Large must NOT be copied here (too large for the APK).
# The app copies a bundled Small asset to internal files/models/ on first use
# and skips the network download.
