package kr.contest.pleosassistant.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.contest.pleosassistant.status.Reading

/** 기능 3 이 분위기 보정에 쓰는 바깥 신호: 와이퍼가 도는지, 전조등이 켜졌는지 */
interface ExteriorSource {
    val wipersOn: StateFlow<Reading<Boolean>>
    val headlightsOn: StateFlow<Reading<Boolean>>
    fun start()
    fun stop()
}

/**
 * AAOS 표준 CarPropertyManager 로 와이퍼(WINDSHIELD_WIPERS_STATE, Android 14+) · 전조등(HEADLIGHTS_STATE)을 읽는다.
 * Pleos Vehicle SDK 에도 Wiper · Light 도메인이 있지만 공개 문서에서 메서드 이름을 확인하지 못해(AAR 디컴파일 금지)
 * 다른 파트(변속 단 · 차속)처럼 표준 속성을 쓴다. 지원하지 않거나 권한이 없으면 Unsupported 이고, 그때는 시간대 · 날씨로만 정한다.
 */
class CarExteriorSource(private val context: Context) : ExteriorSource {

    private val _wipers = MutableStateFlow<Reading<Boolean>>(Reading.Loading)
    override val wipersOn: StateFlow<Reading<Boolean>> = _wipers.asStateFlow()

    private val _headlights = MutableStateFlow<Reading<Boolean>>(Reading.Loading)
    override val headlightsOn: StateFlow<Reading<Boolean>> = _headlights.asStateFlow()

    private var car: Car? = null
    private var manager: CarPropertyManager? = null

    /** 와이퍼는 앞 · 뒤 유리(area)마다 값이 와서, 하나라도 돌면 켜진 것으로 본다 */
    private val wiperAreas = mutableMapOf<Int, Boolean>()

    override fun start() {
        if (manager != null) return
        try {
            val connected = Car.createCar(context) ?: run {
                _wipers.value = Reading.Unavailable
                _headlights.value = Reading.Unavailable
                return
            }
            val m = connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            car = connected
            manager = m
            subscribe(m, VehiclePropertyIds.HEADLIGHTS_STATE, HEADLIGHT_PERMISSION, _headlights)
            val wiperId = wiperPropertyId
            if (wiperId != null) subscribe(m, wiperId, WIPER_PERMISSION, _wipers) else _wipers.value = Reading.Unsupported
        } catch (e: Exception) {
            Log.w(TAG, "차량 연결 실패: $e")
            _wipers.value = Reading.Unavailable
            _headlights.value = Reading.Unavailable
        }
    }

    private fun subscribe(m: CarPropertyManager, property: Int, permission: String, target: MutableStateFlow<Reading<Boolean>>) {
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "${VehiclePropertyIds.toString(property)}: 권한 없음 ($permission)")
            target.value = Reading.Unsupported
            return
        }
        try {
            if (m.getCarPropertyConfig(property) == null) {
                Log.i(TAG, "${VehiclePropertyIds.toString(property)}: 이 차는 지원하지 않음")
                target.value = Reading.Unsupported
                return
            }
            m.registerCallback(callback, property, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            Log.i(TAG, "${VehiclePropertyIds.toString(property)} 구독 시작")
        } catch (e: Exception) {
            Log.w(TAG, "${VehiclePropertyIds.toString(property)} 구독 실패: $e")
            target.value = if (e is SecurityException) Reading.Unsupported else Reading.Unavailable
        }
    }

    override fun stop() {
        runCatching { manager?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val v = value.value as? Int ?: return
            when (value.propertyId) {
                VehiclePropertyIds.HEADLIGHTS_STATE -> {
                    // 0 꺼짐 · 1 켜짐 · 2 주간주행등. 주간주행등은 밤이 아니다
                    _headlights.value = Reading.Value(v == HEADLIGHT_ON)
                }
                else -> {
                    // WindshieldWipersState: 0 기타 · 1 꺼짐 · 2 켜짐 · 3 정비
                    wiperAreas[value.areaId] = v == WIPER_ON
                    _wipers.value = Reading.Value(wiperAreas.values.any { it })
                }
            }
            Log.i(TAG, "${VehiclePropertyIds.toString(value.propertyId)} = $v")
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "${VehiclePropertyIds.toString(propertyId)} 구독 오류")
        }
    }

    companion object {
        /**
         * WINDSHIELD_WIPERS_STATE 는 컴파일에 쓰는 android.car.jar(API 35)에 없어서, 기기의 Car 라이브러리에서 이름으로 찾는다.
         * 없으면(Android 14 미만 등) null → 와이퍼 신호 없이 날씨 · 시간대로만 정한다.
         */
        val wiperPropertyId: Int? by lazy {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return@lazy null
            runCatching { VehiclePropertyIds::class.java.getField("WINDSHIELD_WIPERS_STATE").getInt(null) }.getOrNull()
        }
        const val HEADLIGHT_PERMISSION = "android.car.permission.CAR_EXTERIOR_LIGHTS"
        const val WIPER_PERMISSION = "android.car.permission.READ_WINDSHIELD_WIPERS"
        private const val HEADLIGHT_ON = 1
        private const val WIPER_ON = 2
        private const val TAG = "CarExteriorSource"
    }
}
