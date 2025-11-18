# BLE Beacon to Connect/Send 리팩토링 작업 기록

## 작업 개요

**작업 일자**: 2025-11-18
**Branch**: `claude/refactor-ble-beacon-01Hne8QGCQUvUZmnk8zft7L2`
**목표**: BLE 통신 방식을 beacon 광고에서 AT Command 기반 connect/send로 변경

---

## 변경 전/후 비교

### 현재 (v1.0) - Beacon 방식
```
Scan → SetBeaconParams → EnableBeacon → 10초 광고 (단방향)
```

### 변경 후 (v2.0) - Connect/Send 방식
```
Scan 중지 → AT+CONNECT → (UUID_SCAN) → (TRX_CHAN) → AT+SEND → 수신 대기 → AT+DISCE
```

| 구분 | 현재 (Beacon) | 변경 후 (Connect/Send) |
|------|---------------|------------------------|
| **통신 방식** | 단방향 broadcast | 양방향 연결 |
| **연결 여부** | 연결 없음 | GATT 연결 수립 |
| **API 사용** | `Lib_SetBeaconParams`, `Lib_EnableBeacon` | `Lib_ComSend`, `Lib_ComRecvAT` |
| **데이터 전송** | Advertisement 패킷에 포함 | GATT Write로 직접 전송 |
| **응답 수신** | 불가능 | `+RECEIVED:` 로 수신 가능 |

---

## 변경된 파일 목록

### 신규 생성 파일

| 파일 | 경로 | 역할 |
|------|------|------|
| `BleConnection.kt` | `app/src/main/java/com/mcandle/bledemo/` | AT Command 기반 연결/송수신 관리자 |
| `BLEConnectDialogFragment.kt` | `app/src/main/java/com/mcandle/bledemo/` | 연결/송수신 Dialog UI 로직 |
| `dialog_ble_connect.xml` | `app/src/main/res/layout/` | 연결 Dialog 레이아웃 |
| `BleConnectionTest.kt` | `app/src/test/java/com/mcandle/bledemo/` | 유닛 테스트 |
| `android-build.yml` | `.github/workflows/` | GitHub Actions 자동 빌드 |

### 수정된 파일

| 파일 | 주요 변경 내용 |
|------|----------------|
| `DeviceModel.kt` | `ConnectionState` enum 및 연결 상태 필드 추가 |
| `MainActivity.kt` | Mobile Payment → Connect Dialog 전환, `stopScan()` public 메서드 추가, Beacon 설정 UI 제거 |
| `activity_main.xml` | BEACON/MASTER → SCAN/CONNECT, Advertise → Connect |
| `item_beacon_info.xml` | Config Beacon 섹션 제거 (Company ID, Major/Minor UUID 등) |
| `BLEDeviceAdapter.kt` | 연결 상태에 따른 배경색 표시 추가 |

---

## 주요 구현 내용

### 1. BleConnection.kt - AT Command 관리자

```kotlin
class BleConnection(private val context: Context) {
    companion object {
        var TEST_MODE = false  // 테스트 모드
    }

    fun connectToDevice(macAddress: String): ConnectionResult
    fun scanUuidChannels(): UuidScanResult
    fun setTrxChannel(writeCh: Int, notifyCh: Int, type: Int): Boolean
    fun sendData(data: ByteArray, timeout: Int): SendResult
    fun receiveData(timeout: Int): ReceiveResult
    fun disconnect(): Boolean
}
```

**AT Command 매핑**:
| 메서드 | AT Command |
|--------|------------|
| `connectToDevice()` | `AT+CONNECT=,{MAC}\r\n` |
| `scanUuidChannels()` | `AT+UUID_SCAN=1\r\n` |
| `setTrxChannel()` | `AT+TRX_CHAN={h},{w},{n},{t}\r\n` |
| `sendData()` | `AT+SEND={h},{len},{timeout}\r\n` + data |
| `disconnect()` | `AT+DISCE={handle}\r\n` |

### 2. DeviceModel.kt - 연결 상태 필드

```kotlin
enum class ConnectionState {
    DISCONNECTED,   // 연결 안됨
    CONNECTING,     // 연결 중
    CONNECTED,      // 연결됨
    SENDING,        // 데이터 송신 중
    RECEIVING,      // 데이터 수신 중
    ERROR           // 오류
}

data class DeviceModel(
    // 기존 필드...
    var connectionHandle: Int? = null,
    var isConnected: Boolean = false,
    var connectionState: ConnectionState = ConnectionState.DISCONNECTED
)
```

### 3. BLEConnectDialogFragment.kt - 연결 Dialog

**UI 구성**:
- 디바이스 정보 (이름, MAC)
- 연결 상태 표시 (색상 + Progress)
- 송신 데이터 입력
- Connect / Send / Disconnect / Close 버튼
- 수신 로그 영역

**흐름**:
1. Connect 버튼 → `bleConnection.connectToDevice()`
2. Send 버튼 → `bleConnection.sendData()`
3. 백그라운드에서 수신 대기 → 로그 표시
4. Disconnect 버튼 → `bleConnection.disconnect()`

### 4. UI 변경

**activity_main.xml**:
- BEACON/MASTER 스위치 → SCAN/CONNECT 스위치
- Advertise 버튼 → Connect 버튼

**item_beacon_info.xml**:
- Config Beacon 섹션 전체 제거:
  - Company ID
  - Major UUID (전화번호)
  - Minor UUID
  - Custom UUID
- Display Title 섹션은 유지

---

## AT Command 상세 흐름

### Connect & Send Flow

```
[사용자가 Send Data 버튼 클릭]
    ↓
┌─────────────────────────────────────┐
│ 1. 스캔 중지 (필요시)                │
│    At.Lib_AtStopScan()              │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ 2. 대상 디바이스에 연결               │
│    AT+CONNECT=,XX:XX:XX:XX:XX:XX    │
│    → 응답: "OK\r\n{MAC} CONNECTED 1" │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ 3. (선택) UUID 채널 스캔             │
│    AT+UUID_SCAN=1                   │
│    → 응답: 서비스/Characteristic 목록 │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ 4. (선택) TRX 채널 설정              │
│    AT+TRX_CHAN=1,3,2,0              │
│    → 응답: "OK"                      │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ 5. 데이터 송신 준비                   │
│    AT+SEND=1,10,1000                │
│    → 응답: "OK\r\nINPUT_BLE_DATA:10" │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ 6. 실제 데이터 전송                   │
│    {actual_data}                    │
│    → 응답: "OK"                      │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ 7. 응답 데이터 수신 대기              │
│    Lib_ComRecvAT()                  │
│    → 응답: "+RECEIVED:{data}"       │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ 8. 연결 해제                         │
│    AT+DISCE=1                       │
│    → 응답: "OK"                      │
└─────────────────────────────────────┘
```

---

## 테스트 방법

### 1. 유닛 테스트 (디바이스 없이)

```bash
./gradlew test --tests="com.mcandle.bledemo.BleConnectionTest"
```

테스트 항목:
- `parseConnectResponse` - AT+CONNECT 응답 파싱
- `parseUuidScanResponse` - UUID_SCAN 응답 파싱
- `parseReceivedData` - +RECEIVED: 데이터 파싱

### 2. TEST_MODE 사용 (디바이스 없이 UI 테스트)

```kotlin
// MainActivity.kt 또는 BLEConnectDialogFragment.kt에서
BleConnection.TEST_MODE = true
```

TEST_MODE 활성화 시:
- Connect → 1초 후 성공 (Handle: 1)
- Send → 0.5초 후 성공
- Receive → 타임아웃 또는 랜덤 테스트 데이터 수신
- Disconnect → 0.3초 후 성공

### 3. 실제 디바이스 테스트

```bash
# APK 빌드 및 설치
./gradlew installDebug

# Logcat 모니터링
adb logcat -s BleConnection:D MainActivity:D
```

**테스트 시나리오**:
1. Start 버튼으로 스캔
2. 디바이스 선택 → Payment Dialog
3. Mobile Payment → Connect Dialog
4. Connect 버튼 → 연결 상태 확인
5. 데이터 입력 → Send 버튼
6. 수신 로그 확인
7. Disconnect 버튼

### 예상 로그 출력

```
D/BleConnection: Connecting to device: 5C:02:72:26:55:88
D/BleConnection: Connect response: OK
5C:02:72:26:55:88 CONNECTED 1
D/BleConnection: Connected successfully, handle: 1
D/BleConnection: Sending data: 10 bytes
D/BleConnection: SEND response: OK
INPUT_BLE_DATA:10
D/BleConnection: Final send response: OK
D/BleConnection: Received: +RECEIVED:RESPONSE_DATA
D/BleConnection: Disconnecting handle: 1
D/BleConnection: Disconnect response: OK
```

---

## GitHub Actions 설정

### 자동 빌드 트리거

- `main` 또는 `master` 브랜치로 push
- `main` 또는 `master` 브랜치로 PR 생성

### 자동 실행 작업

| Job | 내용 |
|-----|------|
| **build** | Debug/Release APK 빌드 및 업로드 |
| **test** | 유닛 테스트 실행 및 결과 업로드 |

### APK 다운로드

1. GitHub 저장소의 **Actions** 탭 이동
2. 실행된 workflow 선택
3. 하단 **Artifacts** 섹션에서 다운로드:
   - `debug-apk` - Debug APK
   - `release-apk` - Release APK
   - `test-results` - 테스트 결과

---

## 커밋 히스토리

```
42032c8 Add GitHub Actions workflow for automatic APK build
4de66fc Add unit tests and TEST_MODE for BLE connection testing
87032d4 Make gradlew executable
1da09ae Refactor BLE communication from beacon to connect/send
```

---

## 향후 작업 (TODO)

- [ ] TRX 채널 설정 UI 추가 (Write Channel, Notify Channel, Write Type)
- [ ] 연결 타임아웃 설정 UI
- [ ] 다중 디바이스 연결 지원
- [ ] 연결 상태 자동 복구 (재연결)
- [ ] 수신 데이터 파싱 및 처리 로직 확장
- [ ] Release 빌드 서명 설정

---

## 참고 사항

### AT Command 모드 진입 (`+++`)

현재 앱은 이미 AT Command 모드에서 동작 중이므로 `+++` 진입 단계는 생략됨.

### Threading 주의사항

- AT Command는 blocking 호출이므로 반드시 백그라운드 스레드에서 실행
- UI 업데이트는 `withContext(Dispatchers.Main)` 또는 `runOnUiThread` 사용

### 오류 코드

```
-2500: Communication timeout
-2501: Wrong length
-2502: Communication error
-2503: Wrong data
-2504: Wrong command
-2505: EDC error
-2506: Other error
-2507: CRC16 error
-2508: Open failed
-2509: Send error
-2510: Receive error
```

---

## 작업 완료 체크리스트

- [x] BleConnection.kt 생성 - AT Command 기반 연결/송수신 관리자
- [x] DeviceModel.kt 수정 - 연결 상태 필드 추가
- [x] dialog_ble_connect.xml 생성 - 연결 Dialog UI
- [x] BLEConnectDialogFragment.kt 생성 - Dialog 로직
- [x] MainActivity.kt 수정 - 연결 흐름 통합
- [x] activity_main.xml 수정 - 메인 화면 UI
- [x] item_beacon_info.xml 수정 - 설정 화면
- [x] BLEDeviceAdapter.kt 수정 - 연결 상태 표시
- [x] 유닛 테스트 추가
- [x] TEST_MODE 추가
- [x] GitHub Actions 설정
- [x] 변경사항 커밋 및 푸시
