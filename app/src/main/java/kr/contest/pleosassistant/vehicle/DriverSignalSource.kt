package kr.contest.pleosassistant.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.listener.SeatBeltNotBuckledWarningListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.contest.pleosassistant.status.Reading

/** 조향각 샘플. @property deg 운전대 각도(°), @property t 단조 시계(ms, elapsedRealtime 기준. 차속 샘플과 같은 시계) */
data class SteeringSample(val deg: Double, val t: Long)

/** 기능 5 가 쓰는 운전자 신호: 운전대 각도(급조향)와 운전석 안전벨트 미착용 경고 */
interface DriverSignalSource {
    val steeringStatus: StateFlow<Reading<Unit>>
    val steering: SharedFlow<SteeringSample>

    /** 운전석 안전벨트 미착용 경고가 켜져 있으면 true */
    val beltWarning: StateFlow<Reading<Boolean>>
    fun start()
    fun stop()
}

/**
 * Pleos Vehicle SDK 로 읽는다(기능계획서 7장).
 * - 운전대 각도: SteeringWheel.checkSteeringWheelAngleCapabilities → registerSteeringWheelAngle
 * - 안전벨트: Safety.registerSeatBeltNotBuckledWarning(운전석). 지원 확인 API 가 없어 바로 구독하고, 실패하면 Unsupported/Unavailable
 */
class PleosDriverSignalSource(private val vehicle: Vehicle) : DriverSignalSource {

    private val _steeringStatus = MutableStateFlow<Reading<Unit>>(Reading.Loading)
    override val steeringStatus: StateFlow<Reading<Unit>> = _steeringStatus.asStateFlow()

    private val _steering = MutableSharedFlow<SteeringSample>(extraBufferCapacity = 64)
    override val steering: SharedFlow<SteeringSample> = _steering.asSharedFlow()

    private val _beltWarning = MutableStateFlow<Reading<Boolean>>(Reading.Loading)
    override val beltWarning: StateFlow<Reading<Boolean>> = _beltWarning.asStateFlow()

    private val watcher = VehicleWatcher(TAG)

    override fun start() {
        if (watcher.active) return
        watcher.start()
        val wheel = vehicle.getSteeringWheel()
        watcher.watch<Float>(
            name = "steeringWheelAngle",
            publish = { reading ->
                when (reading) {
                    is Reading.Value -> {
                        Log.d(TAG, "운전대 각도: ${reading.value}")
                        _steeringStatus.value = Reading.Value(Unit)
                        _steering.tryEmit(SteeringSample(reading.value.toDouble(), SystemClock.elapsedRealtime()))
                    }
                    else -> _steeringStatus.value = reading.map()
                }
            },
            check = { ok, fail -> wheel.checkSteeringWheelAngleCapabilities({ c, _ -> ok(c.readable()) }, fail) },
            // 첫 값을 읽는 API 는 없다. 값이 바뀌면 구독으로 들어온다
            get = { _, _ -> },
            register = { emit ->
                val l = object : SteeringWheelAngleListener {
                    override fun onSteeringWheelAngleUpdated(value: Float?) = emit(value)
                    override fun onFailed(e: Exception) = watcher.listenerFailed(e)
                }
                wheel.registerSteeringWheelAngle(l)
                val unregister: () -> Unit = { wheel.unregisterSteeringWheelAngle(l) }
                unregister
            },
        )
        val safety = vehicle.getSafety()
        val driver = SeatArea(SeatValue.ROW_1_LEFT)
        watcher.watch<Boolean>(
            name = "seatBeltNotBuckledWarning",
            publish = { reading ->
                if (reading is Reading.Value) Log.i(TAG, "운전석 안전벨트 미착용 경고: ${reading.value}")
                _beltWarning.value = reading
            },
            check = { ok, _ -> ok(true) },
            get = { _, _ -> },
            register = { emit ->
                val l = object : SeatBeltNotBuckledWarningListener {
                    override fun onSeatBeltNotBuckledWarningUpdated(value: Boolean?) = emit(value)
                    override fun onFailed(e: Exception) = watcher.listenerFailed(e)
                }
                safety.registerSeatBeltNotBuckledWarning(driver, l)
                val unregister: () -> Unit = { safety.unregisterSeatBeltNotBuckledWarning(driver, l) }
                unregister
            },
        )
    }

    override fun stop() = watcher.stop()

    private fun Reading<Float>.map(): Reading<Unit> = when (this) {
        Reading.Unsupported -> Reading.Unsupported
        Reading.Loading -> Reading.Loading
        else -> Reading.Unavailable
    }

    private companion object {
        const val TAG = "PleosDriver"
    }
}
