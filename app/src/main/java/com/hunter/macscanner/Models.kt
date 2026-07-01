package com.hunter.macscanner

data class LocationInfo(
    val country: String = "Unknown",
    val countryCode: String = "XX",
    val city: String = "Unknown",
    val ip: String = ""
) {
    fun getSummary(): String {
        val flag = countryCode.ifEmpty { "XX" }.map { 0x1F1E6 + it.code - 'A'.code }.toCharArray().concatToString()
        return if (city != "Unknown") "$flag $country, $city" else "$flag $country"
    }
}

data class ScanResult(
    val mac: String,
    val status: String,
    val username: String? = null,
    val password: String? = null,
    val expireDate: String? = null,
    val statusMessage: String? = null,
    val isTrial: Boolean = false,
    val responseTime: Float = 0f
)

data class ScannerStats(
    val totalScanned: Int = 0,
    val validFound: Int = 0,
    val scanRate: Float = 0f,
    val progress: Float = 0f,
    val serverLocation: String = ""
)

sealed class ScannerState {
    object Idle : ScannerState()
    data class Loading(val message: String) : ScannerState()
    data class Scanning(val stats: ScannerStats, val latestResult: ScanResult?) : ScannerState()
    object Stopped : ScannerState()
    data class Error(val message: String) : ScannerState()
}