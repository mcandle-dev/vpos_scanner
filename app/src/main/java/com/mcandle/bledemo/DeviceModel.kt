package com.mcandle.bledemo

import java.util.*

/**
 * BLE 장치 정보를 저장하는 데이터 클래스
 */
data class DeviceModel(
    var name: String = "Unknown",        // 장치 이름
    val address: String = "Unknown",     // MAC 주소
    var rssi: Int = 0,                    // 신호 강도 (RSSI)
    var txPower: Int? = null,             // Tx Power Level
    var serviceUuids: String = "",  // 단일 UUID (문자열)
    var serviceData:  String = "", // 단일 서비스 데이터 (문자열)
    var manufacturerData: String = "", // 단일 제조사 데이터 (문자열)
    var timestampNanos: Long = System.nanoTime() // 스캔된 시점의 타임스탬프
) {
    /** 제조사 데이터를 HEX 문자열로 반환 */
    fun getManufacturerDataHex(): String {
        return manufacturerData // 이미 hex string이라고 가정
    }

    /** 서비스 데이터를 HEX 문자열로 반환 */
    fun getServiceDataHex(): String {
        return serviceData // 이미 hex string이라고 가정
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
    serviceUuid: String,
    serviceData: String,
    manufacturerData: String
): DeviceModel {
    return DeviceModel(
        name = deviceName ?: "Unknown",
        address = macAddress,
        rssi = rssi,
        txPower = txPower,
        serviceUuids = serviceUuid,
        serviceData = serviceData,
        manufacturerData = manufacturerData,
        timestampNanos = System.nanoTime()
    )
}