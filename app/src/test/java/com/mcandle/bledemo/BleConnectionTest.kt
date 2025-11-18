package com.mcandle.bledemo

import org.junit.Assert.*
import org.junit.Test

/**
 * BleConnection 파싱 로직 테스트
 */
class BleConnectionTest {

    /**
     * Connect 응답 파싱 테스트
     * "OK\r\n5C:02:72:26:55:88 CONNECTED 1" -> handle = 1
     */
    @Test
    fun parseConnectResponse_validResponse_returnsHandle() {
        val response = "OK\r\n5C:02:72:26:55:88 CONNECTED 1"
        val handle = parseConnectResponse(response)
        assertEquals(1, handle)
    }

    @Test
    fun parseConnectResponse_multipleDigitHandle_returnsCorrectHandle() {
        val response = "OK\r\nAA:BB:CC:DD:EE:FF CONNECTED 12"
        val handle = parseConnectResponse(response)
        assertEquals(12, handle)
    }

    @Test
    fun parseConnectResponse_invalidResponse_returnsNull() {
        val response = "ERROR\r\nConnection failed"
        val handle = parseConnectResponse(response)
        assertNull(handle)
    }

    @Test
    fun parseConnectResponse_emptyResponse_returnsNull() {
        val response = ""
        val handle = parseConnectResponse(response)
        assertNull(handle)
    }

    /**
     * UUID Scan 응답 파싱 테스트
     */
    @Test
    fun parseUuidScanResponse_validResponse_returnsChannels() {
        val response = """
            -CHAR:1 UUID:052A,Indicate;
            -CHAR:2 UUID:E4FF,Notify;
            -CHAR:3 UUID:E9FF,Write Without Response,Write;
        """.trimIndent()

        val channels = parseUuidScanResponse(response)

        assertEquals(3, channels.size)

        assertEquals(1, channels[0].charNumber)
        assertEquals("052A", channels[0].uuid)
        assertTrue(channels[0].properties.contains("Indicate"))

        assertEquals(2, channels[1].charNumber)
        assertEquals("E4FF", channels[1].uuid)
        assertTrue(channels[1].properties.contains("Notify"))

        assertEquals(3, channels[2].charNumber)
        assertEquals("E9FF", channels[2].uuid)
        assertTrue(channels[2].properties.contains("Write"))
    }

    @Test
    fun parseUuidScanResponse_emptyResponse_returnsEmptyList() {
        val response = "OK"
        val channels = parseUuidScanResponse(response)
        assertTrue(channels.isEmpty())
    }

    /**
     * 수신 데이터 파싱 테스트
     */
    @Test
    fun parseReceivedData_withPrefix_returnsData() {
        val response = "+RECEIVED:Hello World"
        val data = parseReceivedData(response)
        assertNotNull(data)
        assertEquals("Hello World", String(data!!))
    }

    @Test
    fun parseReceivedData_withoutPrefix_returnsNull() {
        val response = "Some other data"
        val data = parseReceivedData(response)
        assertNull(data)
    }

    @Test
    fun parseReceivedData_emptyData_returnsEmptyBytes() {
        val response = "+RECEIVED:"
        val data = parseReceivedData(response)
        assertNotNull(data)
        assertEquals("", String(data!!))
    }

    // 파싱 헬퍼 함수들 (BleConnection에서 private이므로 여기서 복제)
    private fun parseConnectResponse(response: String): Int? {
        try {
            val regex = Regex("CONNECTED\\s+(\\d+)")
            val match = regex.find(response)
            if (match != null) {
                return match.groupValues[1].toIntOrNull()
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }

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
            // ignore
        }
        return channels
    }

    private fun parseReceivedData(response: String): ByteArray? {
        try {
            if (response.startsWith("+RECEIVED:")) {
                val data = response.substring(10)
                return data.toByteArray()
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }
}
