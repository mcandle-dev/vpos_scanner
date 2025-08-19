# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android application for experimenting with Bluetooth Low Energy (BLE) scanning and advertising using a vendor-provided library. The app serves as a testing platform for VPOS (likely a payment/POS) system that communicates via BLE beacons.

**Key Technologies:**
- Android SDK (API 24-35)
- Kotlin/Java mixed codebase
- BLE (Bluetooth Low Energy) functionality
- Vendor library: `libVpos3893_release_20250729.aar` (vpos.apipackage.At API)
- Gradle build system with Kotlin DSL

## Build and Development Commands

### Building the Project
```bash
# Build debug version
./gradlew assembleDebug

# Build release version
./gradlew assembleRelease

# Clean build
./gradlew clean

# Install debug APK to connected device
./gradlew installDebug
```

### Testing Commands
```bash
# Run unit tests
./gradlew test

# Run Android instrumented tests
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests="com.mcandle.bledemo.ExampleUnitTest"
```

### Development Commands
```bash
# Check for dependency updates
./gradlew dependencyUpdates

# Generate lint report
./gradlew lint

# Check Kotlin code style (if ktlint is added)
./gradlew ktlintCheck
```

## Architecture and Code Structure

### Core Components

**MainActivity.kt** - Central controller managing BLE operations and UI
- Handles BLE scanning lifecycle (start/stop/toggle)
- Manages device discovery and display via RecyclerView
- Processes payment dialogs and beacon configuration
- Coordinates with BLE advertising functionality

**BleScan.java** - BLE scanning engine and data processor
- Wraps vendor At API for master mode control and scanning
- Parses advertisement data packets into structured JSON
- Provides async scanning with callback-based results
- Handles low-level BLE data reception and filtering

**DeviceModel.kt** - Data class for discovered BLE devices
- Stores device information: MAC, RSSI, name, UUIDs, manufacturer/service data
- Provides helper methods for hex data conversion
- Includes timestamp tracking for device discovery

**BLEDeviceAdapter.kt** - RecyclerView adapter for device list display
- Renders discovered devices with signal strength indicators
- Shows manufacturer data, service UUIDs, and service data
- Handles device selection for payment processing

**BLEAdvertiseDialogFragment.kt** - Modal for BLE advertising control
- Manages timed advertising sessions (10-second duration)
- Provides manual start/stop/restart controls
- Integrates with MainActivity's sendAdvertise/stopAdvertise methods

**BLEUtils.kt** - Utility functions for data conversion
- ASCII ↔ HEX string conversions for beacon data
- Hex stream processing for advertisement parsing

### Key Architectural Patterns

**Vendor Library Integration**: The app heavily relies on the `vpos.apipackage.At` API for BLE operations. This library handles low-level BLE master mode, scanning, and beacon configuration.

**Async Data Processing**: BLE scanning runs on background threads with coroutine-based data processing to prevent UI blocking. Device parsing and filtering happen on Dispatchers.Default.

**Callback-Driven Architecture**: Uses listener interfaces (ScanResultListener, DataReceiveListener) to handle asynchronous BLE events and data reception.

**Beacon Configuration System**: Supports dynamic beacon parameter configuration using SharedPreferences storage, allowing customization of Company ID, Major/Minor UUIDs, and custom data.

## Important Development Notes

### BLE Workflow
1. **Master Mode**: Must be enabled before scanning (`enableMasterMode(true)`)
2. **Scanning**: Start with `startNewScan()` or continuous scanning via `toggleScan()`
3. **Data Processing**: Advertisement packets are parsed into JSON structures
4. **Device Management**: Devices are cached and updated with latest RSSI/data
5. **Advertising**: Beacon parameters must be configured before advertising

### Vendor Library Dependencies
- The `libVpos3893_release_20250729.aar` file is critical for BLE functionality
- API calls include: `Lib_EnableMaster`, `Lib_AtStartNewScan`, `Lib_ComRecvAT`, `Lib_SetBeaconParams`, `Lib_EnableBeacon`
- Error codes from the library should be handled appropriately

### Data Flow
- Raw BLE data → JSON parsing → DeviceModel objects → RecyclerView display
- Advertisement data includes manufacturer data, service UUIDs, and service data
- Payment processing extracts card/phone numbers from service UUID data

### Testing Strategy
- Unit tests are located in `app/src/test/java/`
- Instrumented tests in `app/src/androidTest/java/`
- BLE functionality requires physical device testing (not emulator compatible)

### Common Development Tasks
- When modifying BLE scanning logic, test with physical devices as emulators don't support BLE properly
- Beacon configuration changes require understanding of the vendor library's parameter format
- UI updates from BLE callbacks must use `runOnUiThread` or coroutine main dispatchers
- RSSI values and device caching logic should maintain existing device state preservation patterns