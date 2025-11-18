package com.mcandle.bledemo

import android.content.Context
import android.util.Log
import vpos.apipackage.At

/**
 * BLE Connect/Send 관리자
 * AT Command 기반으로 디바이스 연결 및 데이터 송수신 처리
 */
class BleConnection(private val context: Context) {

    companion object {
        private const val TAG = "BleConnection"

        // 타임아웃 설정 (ms)
        private const val CONNECT_TIMEOUT = 10000
        private const val SEND_TIMEOUT = 5000
        private const val RECV_TIMEOUT = 5000
        private const val DEFAULT_TIMEOUT = 1000

        // 버퍼 크기
        private const val BUFFER_SIZE = 2048

        // 테스트 모드 (true로 설정하면 Mock 응답 사용)
        var TEST_MODE = false

        // Hex 변환 유틸리티
        private fun bytesToHex(bytes: ByteArray): String {
            return bytes.joinToString(" ") { String.format("%02X", it) }
        }
    }

    // 연결 상태
    private var connectedHandle: Int? = null
    private var targetMacAddress: String? = null

    // TRX 채널 설정
    private var writeChannel: Int = 3
    private var notifyChannel: Int = 2
    private var writeType: Int = 0  // 0: Write Without Response, 1: Write

    /**
     * 연결 상태 확인
     */
    fun isConnected(): Boolean = connectedHandle != null

    /**
     * 현재 연결된 Handle 반환
     */
    fun getConnectionHandle(): Int? = connectedHandle

    /**
     * 디바이스에 연결
     * @param macAddress 대상 디바이스 MAC 주소 (XX:XX:XX:XX:XX:XX 형식)
     * @return ConnectionResult (성공시 handle 포함)
     */
    fun connectToDevice(macAddress: String): ConnectionResult {
        Log.d(TAG, "Connecting to device: $macAddress")

        // 테스트 모드
        if (TEST_MODE) {
            Log.d(TAG, "[TEST MODE] Simulating connection to $macAddress")
            Thread.sleep(1000) // 연결 시뮬레이션
            connectedHandle = 1
            targetMacAddress = macAddress
            return ConnectionResult.Success(1)
        }

        try {
            // AT+CONNECT 명령 전송
            val connectCmd = "AT+CONNECT=,$macAddress\r\n"
            val cmdBytes = connectCmd.toByteArray()

            Log.i(TAG, ">>> SEND CMD: $connectCmd")
            Log.i(TAG, ">>> SEND HEX: ${bytesToHex(cmdBytes)}")

            val sendResult = At.Lib_ComSend(cmdBytes, cmdBytes.size)
            Log.i(TAG, ">>> SEND Result: $sendResult")
            if (sendResult < 0) {
                Log.e(TAG, "Failed to send connect command: $sendResult")
                return ConnectionResult.Error("Connect 명령 전송 실패: $sendResult")
            }

            // 응답 수신 (연결 타임아웃 적용)
            val response = ByteArray(BUFFER_SIZE)
            val recvLen = IntArray(1)
            val recvResult = At.Lib_ComRecvAT(response, recvLen, CONNECT_TIMEOUT / 50, CONNECT_TIMEOUT)

            Log.i(TAG, "<<< RECV Result: $recvResult, len=${recvLen[0]}")

            // 데이터가 있으면 파싱 시도 (에러 코드와 무관하게)
            if (recvLen[0] > 0) {
                val responseStr = String(response, 0, recvLen[0]).trim()
                Log.i(TAG, "<<< RECV STR: $responseStr")
                Log.i(TAG, "<<< RECV HEX: ${bytesToHex(response.copyOf(recvLen[0]))}")

                // 응답 파싱: "OK\r\n5C:02:72:26:55:88 CONNECTED 1"
                val handle = parseConnectResponse(responseStr)
                if (handle != null) {
                    connectedHandle = handle
                    targetMacAddress = macAddress
                    Log.d(TAG, "Connected successfully, handle: $handle")
                    return ConnectionResult.Success(handle)
                } else {
                    Log.e(TAG, "Failed to parse connect response")
                    return ConnectionResult.Error("연결 응답 파싱 실패: $responseStr")
                }
            }

            // 데이터 없고 에러면 실패
            if (recvResult < 0) {
                Log.e(TAG, "Failed to receive connect response: $recvResult, len=${recvLen[0]}")
                return ConnectionResult.Error("Connect 응답 수신 실패")
            }

            return ConnectionResult.Error("연결 응답 없음")

        } catch (e: Exception) {
            Log.e(TAG, "Connect exception", e)
            return ConnectionResult.Error("연결 예외: ${e.message}")
        }
    }

    /**
     * UUID 채널 스캔 (Custom UUID 사용시)
     * @return UuidScanResult (채널 목록 포함)
     */
    fun scanUuidChannels(): UuidScanResult {
        Log.d(TAG, "Scanning UUID channels")

        try {
            val scanCmd = "AT+UUID_SCAN=1\r\n"
            val cmdBytes = scanCmd.toByteArray()

            Log.i(TAG, ">>> SEND CMD: $scanCmd")
            Log.i(TAG, ">>> SEND HEX: ${bytesToHex(cmdBytes)}")

            val sendResult = At.Lib_ComSend(cmdBytes, cmdBytes.size)
            Log.i(TAG, ">>> SEND Result: $sendResult")
            if (sendResult < 0) {
                return UuidScanResult.Error("UUID_SCAN 명령 전송 실패")
            }

            val response = ByteArray(BUFFER_SIZE)
            val recvLen = IntArray(1)
            val recvResult = At.Lib_ComRecvAT(response, recvLen, SEND_TIMEOUT / 50, SEND_TIMEOUT)

            Log.i(TAG, "<<< RECV Result: $recvResult, len=${recvLen[0]}")
            if (recvResult < 0 || recvLen[0] <= 0) {
                return UuidScanResult.Error("UUID_SCAN 응답 수신 실패")
            }

            val responseStr = String(response, 0, recvLen[0]).trim()
            Log.i(TAG, "<<< RECV STR: $responseStr")
            Log.i(TAG, "<<< RECV HEX: ${bytesToHex(response.copyOf(recvLen[0]))}")

            val channels = parseUuidScanResponse(responseStr)
            return UuidScanResult.Success(channels)

        } catch (e: Exception) {
            Log.e(TAG, "UUID scan exception", e)
            return UuidScanResult.Error("UUID 스캔 예외: ${e.message}")
        }
    }

    /**
     * TRX 채널 설정
     * @param writeCh Write 채널 번호
     * @param notifyCh Notify 채널 번호
     * @param type Write 타입 (0: Without Response, 1: With Response)
     */
    fun setTrxChannel(writeCh: Int, notifyCh: Int, type: Int): Boolean {
        val handle = connectedHandle ?: return false

        Log.d(TAG, "Setting TRX channel: write=$writeCh, notify=$notifyCh, type=$type")

        try {
            val trxCmd = "AT+TRX_CHAN=$handle,$writeCh,$notifyCh,$type\r\n"
            val cmdBytes = trxCmd.toByteArray()

            Log.i(TAG, ">>> SEND CMD: $trxCmd")
            Log.i(TAG, ">>> SEND HEX: ${bytesToHex(cmdBytes)}")

            val sendResult = At.Lib_ComSend(cmdBytes, cmdBytes.size)
            Log.i(TAG, ">>> SEND Result: $sendResult")
            if (sendResult < 0) {
                Log.e(TAG, "Failed to send TRX_CHAN command")
                return false
            }

            val response = ByteArray(BUFFER_SIZE)
            val recvLen = IntArray(1)
            val recvResult = At.Lib_ComRecvAT(response, recvLen, DEFAULT_TIMEOUT / 50, DEFAULT_TIMEOUT)

            Log.i(TAG, "<<< RECV Result: $recvResult, len=${recvLen[0]}")
            if (recvResult >= 0 && recvLen[0] > 0) {
                val responseStr = String(response, 0, recvLen[0]).trim()
                Log.i(TAG, "<<< RECV STR: $responseStr")
                Log.i(TAG, "<<< RECV HEX: ${bytesToHex(response.copyOf(recvLen[0]))}")

                if (responseStr.contains("OK")) {
                    writeChannel = writeCh
                    notifyChannel = notifyCh
                    writeType = type
                    return true
                }
            }

            return false

        } catch (e: Exception) {
            Log.e(TAG, "TRX_CHAN exception", e)
            return false
        }
    }

    /**
     * 데이터 송신
     * @param data 전송할 데이터
     * @param timeout 타임아웃 (ms)
     * @return SendResult
     */
    fun sendData(data: ByteArray, timeout: Int = SEND_TIMEOUT): SendResult {
        val handle = connectedHandle ?: return SendResult.Error("연결되지 않음")

        Log.d(TAG, "Sending data: ${data.size} bytes")

        // 테스트 모드
        if (TEST_MODE) {
            Log.d(TAG, "[TEST MODE] Simulating send: ${String(data)}")
            Thread.sleep(500)
            return SendResult.Success
        }

        try {
            // 1. AT+SEND 명령으로 송신 준비
            val sendCmd = "AT+SEND=$handle,${data.size},$timeout\r\n"
            val cmdBytes = sendCmd.toByteArray()

            Log.i(TAG, ">>> SEND CMD: $sendCmd")
            Log.i(TAG, ">>> SEND HEX: ${bytesToHex(cmdBytes)}")

            val sendResult = At.Lib_ComSend(cmdBytes, cmdBytes.size)
            Log.i(TAG, ">>> SEND Result: $sendResult")
            if (sendResult < 0) {
                return SendResult.Error("SEND 명령 전송 실패")
            }

            // 2. 응답 확인: "OK\r\nINPUT_BLE_DATA:XX"
            val response = ByteArray(BUFFER_SIZE)
            val recvLen = IntArray(1)
            val recvResult = At.Lib_ComRecvAT(response, recvLen, DEFAULT_TIMEOUT / 50, DEFAULT_TIMEOUT)

            Log.i(TAG, "<<< RECV Result: $recvResult, len=${recvLen[0]}")
            if (recvResult < 0 || recvLen[0] <= 0) {
                return SendResult.Error("SEND 응답 수신 실패")
            }

            val responseStr = String(response, 0, recvLen[0]).trim()
            Log.i(TAG, "<<< RECV STR: $responseStr")
            Log.i(TAG, "<<< RECV HEX: ${bytesToHex(response.copyOf(recvLen[0]))}")

            if (!responseStr.contains("INPUT_BLE_DATA")) {
                return SendResult.Error("예상치 못한 응답: $responseStr")
            }

            // 3. 실제 데이터 전송
            Log.i(TAG, ">>> DATA SEND: ${String(data)}")
            Log.i(TAG, ">>> DATA HEX: ${bytesToHex(data)}")
            val dataSendResult = At.Lib_ComSend(data, data.size)
            Log.i(TAG, ">>> DATA Result: $dataSendResult")
            if (dataSendResult < 0) {
                return SendResult.Error("데이터 전송 실패")
            }

            // 4. 전송 완료 응답 확인
            val finalResponse = ByteArray(BUFFER_SIZE)
            val finalLen = IntArray(1)
            val finalResult = At.Lib_ComRecvAT(finalResponse, finalLen, timeout / 50, timeout)

            Log.i(TAG, "<<< FINAL Result: $finalResult, len=${finalLen[0]}")
            if (finalResult >= 0 && finalLen[0] > 0) {
                val finalStr = String(finalResponse, 0, finalLen[0]).trim()
                Log.i(TAG, "<<< FINAL STR: $finalStr")
                Log.i(TAG, "<<< FINAL HEX: ${bytesToHex(finalResponse.copyOf(finalLen[0]))}")

                if (finalStr.contains("OK")) {
                    return SendResult.Success
                }
            }

            return SendResult.Error("전송 완료 확인 실패")

        } catch (e: Exception) {
            Log.e(TAG, "Send exception", e)
            return SendResult.Error("전송 예외: ${e.message}")
        }
    }

    /**
     * 데이터 수신 대기
     * @param timeout 타임아웃 (ms)
     * @return ReceiveResult
     */
    fun receiveData(timeout: Int = RECV_TIMEOUT): ReceiveResult {
        if (connectedHandle == null) {
            return ReceiveResult.Error("연결되지 않음")
        }

        Log.d(TAG, "Waiting for data, timeout: $timeout ms")

        // 테스트 모드
        if (TEST_MODE) {
            Thread.sleep(timeout.toLong())
            // 랜덤하게 데이터 수신 시뮬레이션
            return if (Math.random() > 0.7) {
                val testData = "TEST_RESPONSE_${System.currentTimeMillis()}"
                Log.d(TAG, "[TEST MODE] Simulating receive: $testData")
                ReceiveResult.Success(testData.toByteArray())
            } else {
                ReceiveResult.Timeout
            }
        }

        try {
            val response = ByteArray(BUFFER_SIZE)
            val recvLen = IntArray(1)
            val recvResult = At.Lib_ComRecvAT(response, recvLen, timeout / 50, timeout)

            Log.i(TAG, "<<< RECV Result: $recvResult, len=${recvLen[0]}")
            if (recvResult < 0) {
                Log.i(TAG, "<<< RECV Timeout (result < 0)")
                return ReceiveResult.Timeout
            }

            if (recvLen[0] <= 0) {
                Log.i(TAG, "<<< RECV Timeout (len <= 0)")
                return ReceiveResult.Timeout
            }

            val responseStr = String(response, 0, recvLen[0])
            Log.i(TAG, "<<< RECV STR: $responseStr")
            Log.i(TAG, "<<< RECV HEX: ${bytesToHex(response.copyOf(recvLen[0]))}")

            // +RECEIVED: prefix 확인 및 데이터 추출
            val data = parseReceivedData(responseStr)
            if (data != null) {
                return ReceiveResult.Success(data)
            }

            // +RECEIVED가 아닌 경우 전체 데이터 반환
            return ReceiveResult.Success(response.copyOf(recvLen[0]))

        } catch (e: Exception) {
            Log.e(TAG, "Receive exception", e)
            return ReceiveResult.Error("수신 예외: ${e.message}")
        }
    }

    /**
     * 연결 해제
     * @return 성공 여부
     */
    fun disconnect(): Boolean {
        val handle = connectedHandle ?: return true  // 이미 연결 안됨

        Log.d(TAG, "Disconnecting handle: $handle")

        // 테스트 모드
        if (TEST_MODE) {
            Log.d(TAG, "[TEST MODE] Simulating disconnect")
            Thread.sleep(300)
            connectedHandle = null
            targetMacAddress = null
            return true
        }

        try {
            val disconnectCmd = "AT+DISCE=$handle\r\n"
            val cmdBytes = disconnectCmd.toByteArray()

            Log.i(TAG, ">>> SEND CMD: $disconnectCmd")
            Log.i(TAG, ">>> SEND HEX: ${bytesToHex(cmdBytes)}")

            val sendResult = At.Lib_ComSend(cmdBytes, cmdBytes.size)
            Log.i(TAG, ">>> SEND Result: $sendResult")
            if (sendResult < 0) {
                Log.e(TAG, "Failed to send disconnect command")
                // 상태는 초기화
                connectedHandle = null
                targetMacAddress = null
                return false
            }

            val response = ByteArray(BUFFER_SIZE)
            val recvLen = IntArray(1)
            At.Lib_ComRecvAT(response, recvLen, DEFAULT_TIMEOUT / 50, DEFAULT_TIMEOUT)

            Log.i(TAG, "<<< RECV Result: len=${recvLen[0]}")
            if (recvLen[0] > 0) {
                val responseStr = String(response, 0, recvLen[0]).trim()
                Log.i(TAG, "<<< RECV STR: $responseStr")
                Log.i(TAG, "<<< RECV HEX: ${bytesToHex(response.copyOf(recvLen[0]))}")
            }

            connectedHandle = null
            targetMacAddress = null
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Disconnect exception", e)
            connectedHandle = null
            targetMacAddress = null
            return false
        }
    }

    /**
     * Connect 응답 파싱
     * "OK\r\n5C:02:72:26:55:88 CONNECTED 1" -> handle = 1
     */
    private fun parseConnectResponse(response: String): Int? {
        try {
            // "CONNECTED X" 패턴 찾기
            val regex = Regex("CONNECTED\\s+(\\d+)")
            val match = regex.find(response)
            if (match != null) {
                return match.groupValues[1].toIntOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse connect response error", e)
        }
        return null
    }

    /**
     * UUID Scan 응답 파싱
     * "-CHAR:1 UUID:052A,Indicate;" -> UuidChannel
     */
    private fun parseUuidScanResponse(response: String): List<UuidChannel> {
        val channels = mutableListOf<UuidChannel>()

        try {
            val regex = Regex("-CHAR:(\\d+)\\s+UUID:([A-Fa-f0-9]+),([^;]+);")
            val matches = regex.findAll(response)

            for (match in matches) {
                val charNum = match.groupValues[1].toIntOrNull() ?: continue
                val uuid = match.groupValues[2]
                val props = match.groupValues[3].split(",").map { it.trim() }

                channels.add(UuidChannel(charNum, uuid, props))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse UUID scan response error", e)
        }

        return channels
    }

    /**
     * 수신 데이터 파싱
     * "+RECEIVED:data" -> data bytes
     */
    private fun parseReceivedData(response: String): ByteArray? {
        try {
            if (response.startsWith("+RECEIVED:")) {
                val data = response.substring(10)
                return data.toByteArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse received data error", e)
        }
        return null
    }
}

/**
 * 연결 결과
 */
sealed class ConnectionResult {
    data class Success(val handle: Int) : ConnectionResult()
    data class Error(val message: String) : ConnectionResult()
}

/**
 * UUID 스캔 결과
 */
sealed class UuidScanResult {
    data class Success(val channels: List<UuidChannel>) : UuidScanResult()
    data class Error(val message: String) : UuidScanResult()
}

/**
 * UUID 채널 정보
 */
data class UuidChannel(
    val charNumber: Int,
    val uuid: String,
    val properties: List<String>
)

/**
 * 송신 결과
 */
sealed class SendResult {
    object Success : SendResult()
    data class Error(val message: String) : SendResult()
}

/**
 * 수신 결과
 */
sealed class ReceiveResult {
    data class Success(val data: ByteArray) : ReceiveResult()
    object Timeout : ReceiveResult()
    data class Error(val message: String) : ReceiveResult()
}
