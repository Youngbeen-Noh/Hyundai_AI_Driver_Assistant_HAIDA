package kr.contest.pleosassistant.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.contest.pleosassistant.status.Reading

/** 차속 샘플. @property t 단조 시계(ms, elapsedRealtime 기준) */
data class SpeedSample(val kmh: Double, val t: Long)

/** 기능 5 가 쓰는 차속 */
interface SpeedSource {
    /** 읽을 수 있는지(권한 · 지원 여부) */
    val status: StateFlow<Reading<Unit>>

    /** 샘플마다 흘려 보낸다. 같은 값이 와도 시간이 달라 놓치지 않게 SharedFlow 로 둔다 */
    val samples: SharedFlow<SpeedSample>
    fun start()
    fun stop()
}

/**
 * 차속은 Pleos Vehicle SDK 에 없어 AAOS 표준 CarPropertyManager(PERF_VEHICLE_SPEED, m/s)로 읽는다(기능계획서 7장 · 11장).
 * 권한 android.car.permission.CAR_SPEED 는 실행 중에 받는다. 받기 전에는 Unsupported 를 알리고, 받은 뒤 start() 를 다시 부른다.
 * 에뮬레이터에서는 Virtual Console 의 속도가 그대로 들어온다.
 */
class CarSpeedSource(private val context: Context) : SpeedSource {

    private val _status = MutableStateFlow<Reading<Unit>>(Reading.Loading)
    override val status: StateFlow<Reading<Unit>> = _status.asStateFlow()

    private val _samples = MutableSharedFlow<SpeedSample>(extraBufferCapacity = 64)
    override val samples: SharedFlow<SpeedSample> = _samples.asSharedFlow()

    private var car: Car? = null
    private var manager: CarPropertyManager? = null

    override fun start() {
        if (manager != null) return
        if (context.checkSelfPermission(PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            _status.value = Reading.Unsupported
            return
        }
        try {
            val connected = Car.createCar(context) ?: run {
                _status.value = Reading.Unavailable
                return
            }
            val m = connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            if (m.getCarPropertyConfig(VehiclePropertyIds.PERF_VEHICLE_SPEED) == null) {
                Log.w(TAG, "차속 속성이 없거나 권한이 없음")
                _status.value = Reading.Unsupported
                connected.disconnect()
                return
            }
            m.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FAST)
            car = connected
            manager = m
            _status.value = Reading.Value(Unit)
            Log.i(TAG, "차속 구독 시작")
        } catch (e: Exception) {
            Log.w(TAG, "차속 연결 실패: $e")
            _status.value = if (e is SecurityException) Reading.Unsupported else Reading.Unavailable
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
            val mps = (value.value as? Float) ?: return
            _samples.tryEmit(SpeedSample(kmh = mps * 3.6, t = value.timestamp / 1_000_000))
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "차속 구독 오류")
        }
    }

    companion object {
        const val PERMISSION = "android.car.permission.CAR_SPEED"
        private const val TAG = "CarSpeedSource"
    }
}
