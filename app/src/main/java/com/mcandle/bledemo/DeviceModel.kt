package com.mcandle.bledemo

import java.util.*

/**
 * BLE 장치 정보를 저장하는 데이터 클래스
 */
data class DeviceModel(
    val name: String = "Unknown",        // 장치 이름
    val address: String = "Unknown",     // MAC 주소
    var rssi: Int = 0,                    // 신호 강도 (RSSI)
    var txPower: Int? = null,             // Tx Power Level
    var serviceUuids: List<UUID> = emptyList(),  // 서비스 UUID 목록
    var serviceData: Map<UUID, ByteArray> = emptyMap(), // 서비스 데이터 (UUID → byte[])
    var manufacturerData: Map<Int, ByteArray> = emptyMap(), // 제조사 데이터 (제조사 ID → byte[])
    var timestampNanos: Long = System.nanoTime() // 스캔된 시점의 타임스탬프
) {
    /** 제조사 데이터를 HEX 문자열로 변환 */
    fun getManufacturerDataHex(): Map<String, String> {
        return manufacturerData.mapKeys { "0x%04X".format(it.key) }
            .mapValues { bytesToHex(it.value) }
    }

    /** 서비스 데이터를 HEX 문자열로 변환 */
    fun getServiceDataHex(): Map<String, String> {
        return serviceData.mapKeys { it.key.toString() }
            .mapValues { bytesToHex(it.value) }
    }

    /** 바이트 배열을 HEX 문자열로 변환 */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { String.format("%02X", it) }
    }
}

fun createDeviceModel(
    deviceName: String?,
    macAddress: String,
    rssi: Int,
    txPower: Int?,
    serviceUuids: List<UUID>,
    serviceData: Map<UUID, ByteArray>,
    manufacturerData: Map<Int, ByteArray>
): DeviceModel {
    return DeviceModel(
        name = deviceName ?: "Unknown",
        address = macAddress,
        rssi = rssi,
        txPower = txPower,
        serviceUuids = serviceUuids,
        serviceData = serviceData,
        manufacturerData = manufacturerData,
        timestampNanos = System.nanoTime()
    )
}