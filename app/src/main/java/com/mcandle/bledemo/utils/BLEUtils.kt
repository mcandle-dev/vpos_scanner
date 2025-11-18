package com.mcandle.bledemo.utils

object BLEUtils {
    /** ✅ ASCII 문자열을 HEX 문자열로 변환 */
    fun asciiToHex(ascii: String): String {
        return ascii.toByteArray(Charsets.UTF_8)
            .joinToString(" ") { String.format("%02X", it) }
    }

    /** ✅ HEX 문자열을 ASCII 문자열로 변환 (오류 방지 추가) */
    fun hexToAscii(hex: String): String {
        return try {
            // 🔹 HEX 데이터가 올바른지 검증
            if (!hex.matches(Regex("^[0-9A-Fa-f ]+\$"))) {
                return hex // 변환하지 않고 그대로 반환 (예: "")
            }

            hex.split(" ")
                .filter { it.isNotEmpty() }
                .map { it.toInt(16).toByte() }
                .toByteArray()
                .toString(Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            hex // 변환 실패 시 원본 HEX 문자열 반환
        }
    }

    // 공백없이 입력될때  변환 가능
    //
    fun hexStreamToAscii(hex: String): String {
        val output = StringBuilder()
        var i = 0
        while (i < hex.length) {
            val str = hex.substring(i, i + 2)
            output.append(str.toInt(16).toChar())
            i += 2
        }
        return output.toString()
    }
}
