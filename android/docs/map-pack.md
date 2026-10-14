# Offline Map Packs for ResQ-Mesh Lite

## Overview
ResQ-Mesh Lite supports offline street maps rendered locally using Mapsforge and `.map` vector map files stored on the device. No network connection or internet permission is required.

## Map File Source & License
- **Verified Download Link**: [Mapsforge Kerala Map (v5 format)](https://download.mapsforge.org/maps/v5/asia/india/kerala.map)
- **Approximate File Size**: ~50 MB – 150 MB
- **License & Attribution**: 
  - Map data © OpenStreetMap contributors, available under the Open Database License (ODbL).
  - Mapsforge rendering library is licensed under the GNU Lesser General Public License (LGPL v3).

## Installation Instructions
1. Download the `.map` file (e.g., `kerala.map`).
2. Rename the file to `region.map`.
3. Place the file on the device using one of two methods:
   - **ADB Push**:
     ```bash
     adb shell mkdir -p /sdcard/Android/data/com.resqedge.lite/files/maps
     adb push region.map /sdcard/Android/data/com.resqedge.lite/files/maps/region.map
     ```
   - **In-App Import**:
     Open the Responder mode, navigate to the **Map** tab, select **Street**, and tap the **Import map file** button to select `region.map` from device storage.

## Operational Note
For live disaster deployments, a pre-bundled per-district map pack (`region.map`) should be provisioned onto responder devices prior to monsoon season or entering the disconnected disaster zone.
