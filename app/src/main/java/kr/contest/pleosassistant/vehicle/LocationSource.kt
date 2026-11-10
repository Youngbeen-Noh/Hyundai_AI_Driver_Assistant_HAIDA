package kr.contest.pleosassistant.vehicle

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.contest.pleosassistant.hazard.GeoPoint
import kr.contest.pleosassistant.status.Reading

/** 기능 6 이 쓰는 현재 위치 */
interface LocationSource {
    val position: StateFlow<Reading<GeoPoint>>

    /** 위치 권한이 없으면 Unsupported 를 알리고 끝낸다. 권한을 받은 뒤 다시 부르면 그때 시작한다 */
    fun start()
    fun stop()
}

/**
 * Android 표준 LocationManager 로 위치를 받는다.
 * 기능계획서는 Pleos FusedLocation SDK(addProximityAlert)를 쓰기로 했지만, Pleos Playground 에서 아직 '준비 중'이라
 * (gradle/libs.versions.toml 참고) 승인 전까지 표준 위치로 대신하고, 근접 판단은 [kr.contest.pleosassistant.hazard.onPosition] 이 한다.
 * 에뮬레이터에서는 `adb emu geo fix <경도> <위도>` 로 위치를 옮긴다.
 */
class AndroidLocationSource(private val context: Context) : LocationSource {

    private val _position = MutableStateFlow<Reading<GeoPoint>>(Reading.Loading)
    override val position: StateFlow<Reading<GeoPoint>> = _position.asStateFlow()

    private val manager = context.getSystemService(LocationManager::class.java)
    private var listening = false

    private val listener = LocationListener { location -> _position.value = Reading.Value(location.toPoint()) }

    @SuppressLint("MissingPermission") // 바로 위에서 권한을 확인한다
    override fun start() {
        if (listening) return
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            _position.value = Reading.Unsupported
            return
        }
        val providers = PROVIDERS.filter { runCatching { manager?.isProviderEnabled(it) == true }.getOrDefault(false) }
        if (manager == null || providers.isEmpty()) {
            Log.w(TAG, "켜진 위치 제공자가 없음")
            _position.value = Reading.Unavailable
            return
        }
        providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { _position.value = Reading.Value(it.toPoint()) }
        providers.forEach { provider ->
            runCatching { manager.requestLocationUpdates(provider, INTERVAL_MS, 0f, listener, Looper.getMainLooper()) }
                .onFailure { Log.w(TAG, "$provider 구독 실패: $it") }
        }
        listening = true
        Log.i(TAG, "위치 구독: $providers")
    }

    override fun stop() {
        if (!listening) return
        runCatching { manager?.removeUpdates(listener) }
        listening = false
    }

    private fun Location.toPoint() = GeoPoint(latitude, longitude)

    private companion object {
        const val TAG = "LocationSource"
        const val INTERVAL_MS = 1_000L
        val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    }
}
