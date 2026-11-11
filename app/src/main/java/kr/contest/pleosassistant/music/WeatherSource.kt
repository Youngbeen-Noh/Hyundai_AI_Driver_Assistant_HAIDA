package kr.contest.pleosassistant.music

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** 지금 위치의 날씨. 모르면 null (위치 권한 · 통신 없음) */
fun interface WeatherSource {
    suspend fun current(): Weather?
}

/**
 * Open-Meteo(키 없는 공개 날씨 API)로 지금 날씨를 받는다. 위치는 마지막으로 알려진 위치만 쓴다(정밀 위치 불필요).
 * 기능계획서 9장은 외부 API 를 우리 서버가 중계하게 되어 있지만, 이 API 는 키가 없어 서버가 생기기 전까지 바로 부른다.
 * 좌표는 소수 둘째 자리(약 1km)로 줄여 보낸다.
 */
class OpenMeteoWeatherSource(private val context: Context) : WeatherSource {

    override suspend fun current(): Weather? = withContext(Dispatchers.IO) {
        val location = lastLocation() ?: return@withContext null.also { Log.i(TAG, "위치를 몰라 날씨를 건너뜀") }
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=weather_code",
                location.latitude,
                location.longitude,
            ),
        )
        runCatching {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            try {
                parseOpenMeteo(conn.inputStream.bufferedReader().readText())
            } finally {
                conn.disconnect()
            }
        }.onFailure { Log.w(TAG, "날씨 요청 실패: $it") }
            .getOrNull()
            ?.also { Log.i(TAG, "날씨: $it") }
    }

    private fun lastLocation(): Location? {
        val granted = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        return try {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        } catch (e: SecurityException) {
            null
        }
    }

    private companion object {
        const val TAG = "OpenMeteoWeather"
        const val TIMEOUT_MS = 5_000
    }
}

/** Open-Meteo 응답의 current.weather_code(WMO 코드)를 [Weather] 로 */
fun parseOpenMeteo(json: String): Weather? {
    val current = JSONObject(json).optJSONObject("current") ?: return null
    if (!current.has("weather_code")) return null
    return weatherFromWmo(current.getInt("weather_code"))
}

/** WMO 날씨 코드: 0 맑음 · 1~3 구름 · 45 · 48 안개 · 51~67 · 80~82 비 · 71~77 · 85 · 86 눈 · 95~99 뇌우 */
fun weatherFromWmo(code: Int): Weather = when (code) {
    0 -> Weather.CLEAR
    in 51..67, in 80..82, in 95..99 -> Weather.RAIN
    in 71..77, 85, 86 -> Weather.SNOW
    else -> Weather.CLOUDY
}
