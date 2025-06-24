# VPOS BLE App Test

This project is a minimal Android application for experimenting with Bluetooth Low Energy (BLE) scanning using a vendor provided library.

## Requirements
- **Compile SDK:** 35
- **Target SDK:** 35
- **Minimum SDK:** 24

## Building
From the repository root run:

```bash
./gradlew assembleDebug
```

The app includes the external library `libVpos3893_debug_20250307.aar` under `app/libs` which contains the `At` API used for BLE communication.

## How BLE Scanning Works
- `BleScan` wraps calls to the `At` API. It enables master mode and starts a scan with `Lib_AtStartNewScan`.
- A background thread repeatedly reads data from `Lib_ComRecvAT`, parses advertisement packets and returns results via the `ScanResultListener` callback.
- `MainActivity` receives these results, logging them to `Logcat`.

### MainActivity and Layout
The main layout (`activity_main.xml`) contains three buttons:
1. **Master** – invokes `enableMasterMode`.
2. **Scan** – starts a one‑time scan via `startNewScan`.
3. **ComRev** – toggles continuous scanning and data reception.

These buttons call `Step1`, `Step2` and `toggleScan` respectively within `MainActivity`.
