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

---

## 전체 구조 및 BLE 동작 방식 (Korean)

### 주요 클래스 및 역할

- **MainActivity (Kotlin)**
  - 앱의 메인 화면 및 BLE 기능의 중심 컨트롤러입니다.
  - BLE 스캔 시작/정지, 마스터 모드 전환, 광고(Advertise) 다이얼로그 호출 등 UI 이벤트를 처리합니다.
  - BLE 스캔 결과를 받아 리스트로 표시하며, 기기 선택 시 상세 다이얼로그를 띄웁니다.

- **BleScan (Java)**
  - BLE 스캔의 핵심 로직을 담당합니다.
  - 벤더 라이브러리의 `At` API를 래핑하여 마스터 모드 전환, 스캔 시작/정지, 데이터 수신 콜백 등을 제공합니다.
  - 스캔 결과는 `ScanResultListener` 콜백을 통해 전달됩니다.

- **DeviceModel (Kotlin)**
  - BLE 기기 정보를 담는 데이터 클래스입니다.
  - 기기명, MAC 주소, RSSI, Tx Power, 서비스 UUID, 제조사 데이터, 서비스 데이터 등을 포함합니다.
  - 제조사/서비스 데이터를 HEX 문자열로 변환하는 유틸리티 메서드도 포함되어 있습니다.

- **BLEDeviceAdapter (Kotlin)**
  - BLE 기기 리스트를 RecyclerView로 표시하는 어댑터입니다.
  - 각 기기의 신호 세기, 이름, UUID, 제조사 데이터 등을 보기 좋게 표시합니다.
  - 리스트 아이템 클릭 시 MainActivity로 이벤트를 전달합니다.

- **BLEAdvertiseDialogFragment (Kotlin)**
  - BLE 광고(Advertise) 기능을 위한 다이얼로그 프래그먼트입니다.
  - 광고 시작/정지/재시작, 타이머 표시, 상태 안내 등을 제공합니다.
  - 광고 시작 시 MainActivity의 `sendAdvertise()`를 호출합니다.

- **BLEUtils (Kotlin)**
  - ASCII <-> HEX 변환, HEX 스트림 변환 등 BLE 데이터 처리에 필요한 유틸리티 함수들을 제공합니다.

---

## BLE 동작 흐름 요약

1. **마스터 모드 전환**  
   - "Master" 버튼 클릭 시 BLE 모듈을 마스터 모드로 전환합니다.

2. **스캔 시작/정지**  
   - "Scan" 버튼: 한 번만 스캔을 실행합니다.
   - "ComRev" 버튼: 스캔을 지속적으로 실행/정지(toggle)합니다.
   - 스캔 결과는 콜백을 통해 MainActivity로 전달되어 리스트에 반영됩니다.

3. **기기 리스트 및 상세 정보**  
   - 스캔된 BLE 기기들은 RecyclerView에 표시됩니다.
   - 리스트 아이템 클릭 시 결제 정보 등 상세 다이얼로그가 표시됩니다.

4. **BLE 광고(Advertise)**  
   - "Advertise" 버튼 클릭 시 광고 다이얼로그가 표시되고, 광고가 시작됩니다.
   - 10초 타이머 후 자동 종료되며, 수동 중단/재시작도 가능합니다.

---

## 기타 참고

- BLE 관련 모든 주요 로직은 `app/src/main/java/com/mcandle/bledemo/` 경로에 위치합니다.
- 벤더 라이브러리(`At` API)는 `vpos.apipackage` 네임스페이스로 사용됩니다.
- BLE 데이터 변환 및 처리에는 `BLEUtils`의 유틸리티 함수가 활용됩니다.
