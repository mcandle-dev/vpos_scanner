# BLE Scanner 디버깅 노트 (2024-11-19)

## 오늘 수정한 파일들

### 1. BleScan.java
- **broadcastName 키 오타 수정**: `"broadcastfName"` → `"broadcastName"` (line 95)
- **기본값 제거**: `"MCan"` → `""`
- **deviceMap 위치 변경**: while 루프 밖으로 이동하여 ADV/RSP 데이터 누적 관리

### 2. MainActivity.kt
- **SharedPreferences 이름 수정**: `"MyPrefs"` → `"scanInfo"` (line 129)
- **Service Data 파싱 수정**: `optString` → `optJSONArray` 사용 (line 195-198)
- **showPaymentDialog 수정**: serviceUuids → serviceData 사용, Hex to ASCII 변환 추가
- **stopScan UI 스레드 처리**: `runOnUiThread { btn3.text = "Start" }`

### 3. BLEDeviceAdapter.kt
- **parseServiceUuidForMembership → parseServiceDataForMembership**: Service Data에서 카드번호/전화번호 파싱
- **Hex to ASCII 변환** 추가
- **bindMembershipDevice**: `device.serviceUuids` → `device.serviceData`

### 4. BLEConnectDialogFragment.kt
- **주문번호 파라미터 추가**: `ARG_ORDER_NUMBER`
- **송신 데이터 기본값 설정**: `etSendData.setText(orderNumber)`
- **ScrollView 캐스팅 수정**: `tvReceivedLog.parent as ScrollView`

### 5. BleConnection.kt
- **+++ 코드 제거**: 이미 AT 모드 상태이므로 불필요
- **상세 로그 추가**: 모든 AT Command 송수신에 `>>> SEND`, `<<< RECV` 로그
- **응답 처리 개선**: `recvLen > 0`이면 에러 코드와 무관하게 파싱 시도

### 6. dialog_ble_connect.xml
- **Device Name + MAC 한 줄로** 표시
- **마진 축소**: 16dp → 8dp
- **수신 로그 영역 축소**: 120dp → 80dp

---

## 현재 남은 이슈

### Connect 응답 문제
```
<<< RECV STR: OK
<<< RECV HEX: 4F 4B 0D 0A
Failed to parse connect response
```

**원인**: "OK"만 먼저 수신되고, "XX:XX:XX:XX:XX:XX CONNECTED 1" 응답은 나중에 옴

**해결 방안**:
1. "OK" 수신 후 추가 응답 대기 필요
2. 또는 parseConnectResponse에서 "OK"도 처리하고 추가 응답 기다리기
3. CONNECT_TIMEOUT(10초) 동안 여러 번 수신 시도

### 예상 수정 코드
```kotlin
// 첫 응답 확인
if (responseStr.contains("OK") && !responseStr.contains("CONNECTED")) {
    // 추가 응답 대기
    val response2 = ByteArray(BUFFER_SIZE)
    val recvLen2 = IntArray(1)
    val recvResult2 = At.Lib_ComRecvAT(response2, recvLen2, CONNECT_TIMEOUT / 50, CONNECT_TIMEOUT)

    if (recvLen2[0] > 0) {
        val responseStr2 = String(response2, 0, recvLen2[0]).trim()
        Log.i(TAG, "<<< RECV STR2: $responseStr2")
        // CONNECTED 파싱
        val handle = parseConnectResponse(responseStr2)
        // ...
    }
}
```

---

## Service Data 파싱 구조

**원본 데이터**: `"31 32 33 34 35 36 37 38 31 32 33 34 35 36 37 38 31 32 33 34 "`

**ASCII 변환**: `"12345678123456781234"`

**파싱 결과**:
- 카드번호 (앞 16자리): `1234 5678 1234 5678`
- 전화번호 (뒤 4자리): `1234`
- 표시: `1234님 (1234 5678 1234 5678)`

---

## AT Command 흐름

### 스캔
1. `AT+ROLE=1` - Master 모드 확인
2. `AT+OBSERVER=1,2,,Mcan,0,,` - 스캔 시작 (필터: Mcan)
3. MAC:XX:XX,RSSI:-XX,ADV:... / RSP:... - 스캔 결과

### 연결
1. `AT+OBSERVER=0` - 스캔 중지
2. `AT+CONNECT=,XX:XX:XX:XX:XX:XX` - 연결 시도
3. `OK` → `XX:XX:XX:XX:XX:XX CONNECTED 1` - 연결 완료

### 송신
1. `AT+SEND=handle,size,timeout`
2. `OK\r\nINPUT_BLE_DATA:XX`
3. (실제 데이터 전송)
4. `OK` - 전송 완료

---

## 내일 할 일

1. **Connect 응답 처리 수정** - "OK" 후 추가 응답 대기
2. **연결 성공 테스트**
3. **Send 기능 테스트**
4. **Receive 기능 테스트**
