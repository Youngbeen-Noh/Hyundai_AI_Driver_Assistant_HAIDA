package kr.contest.pleosassistant.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kr.contest.pleosassistant.memory.Gear
import kr.contest.pleosassistant.memory.Seat
import kr.contest.pleosassistant.status.Reading

/**
 * 좌석 착석은 Pleos Vehicle SDK(Seat: checkSeatOccupancyCapabilities → getCurrentSeatOccupancy → registerSeatOccupancy),
 * 변속 단은 Vehicle SDK 에 API 가 없어서 AAOS 표준 CarPropertyManager(GEAR_SELECTION, 권한 CAR_POWERTRAIN)로 읽는다.
 */
class PleosCabinSource(
    private val vehicle: Vehicle,
    private val context: Context,
) : CabinSource {

    private val _seats = MutableStateFlow<Map<Seat, Reading<Boolean>>>(Seat.entries.associateWith { Reading.Loading })
    override val seats: StateFlow<Map<Seat, Reading<Boolean>>> = _seats.asStateFlow()

    private val _gear = MutableStateFlow<Reading<Gear>>(Reading.Loading)
    override val gear: StateFlow<Reading<Gear>> = _gear.asStateFlow()

    private val watcher = VehicleWatcher(TAG)
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null

    override fun start() {
        if (watcher.active) return
        watcher.start()
        watchSeats()
        watchGear()
    }

    override fun stop() {
        watcher.stop()
        runCatching { propertyManager?.unregisterCallback(gearCallback) }
        runCatching { car?.disconnect() }
        propertyManager = null
        car = null
    }

    private fun watchSeats() {
        val seatApi = vehicle.getSeat()
        Seat.entries.forEach { seat ->
            val value = seat.toSeatValue()
            val area = SeatArea(value)
            watcher.watch(
                name = "seatOccupancy:$seat",
                publish = { reading -> _seats.update { it + (seat to reading) } },
                check = { ok, fail ->
                    seatApi.checkSeatOccupancyCapabilities(
                        { config, areas -> ok(config.readable() && areas.any { value in it.values }) },
                        fail,
                    )
                },
                get = { ok, fail ->
                    seatApi.getCurrentSeatOccupancy(area, { ok(it?.toOccupied()) }, fail)
                },
                register = { emit ->
                    val l = object : SeatOccupancyListener {
                        override fun onSeatOccupancyUpdated(occupancy: Boolean?) {
                            Log.i(TAG, "착석 $seat: SDK 값 $occupancy → 사람 있음 ${occupancy?.toOccupied()}")
                            emit(occupancy?.toOccupied())
                        }
                        override fun onFailed(e: Exception) = watcher.listenerFailed(e)
                    }
                    seatApi.registerSeatOccupancy(area, l)
                    val unregister: () -> Unit = { seatApi.unregisterSeatOccupancy(area, l) }
                    unregister
                },
            )
        }
    }

    private fun watchGear() {
        try {
            val connected = Car.createCar(context) ?: run {
                _gear.value = Reading.Unavailable
                return
            }
            car = connected
            val manager = connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            propertyManager = manager
            // 운전자가 고른 단(GEAR_SELECTION). 없는 차는 실제 단(CURRENT_GEAR)
            val property = GEAR_PROPERTIES.firstOrNull { manager.getCarPropertyConfig(it) != null }
            if (property == null) {
                Log.w(TAG, "변속 단 속성이 없거나 권한이 없음")
                _gear.value = Reading.Unsupported
                return
            }
            _gear.value = runCatching { manager.getIntProperty(property, GLOBAL_AREA).toGearReading() }
                .getOrElse { e ->
                    Log.w(TAG, "변속 단 읽기 실패: $e")
                    Reading.Unavailable
                }
            manager.registerCallback(gearCallback, property, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            Log.i(TAG, "변속 단 구독: ${VehiclePropertyIds.toString(property)}")
        } catch (e: Exception) {
            Log.w(TAG, "변속 단 연결 실패: $e")
            _gear.value = if (e is SecurityException) Reading.Unsupported else Reading.Unavailable
        }
    }

    private val gearCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val raw = value.value as? Int ?: return
            _gear.value = raw.toGearReading()
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "변속 단 구독 오류: ${VehiclePropertyIds.toString(propertyId)}")
        }
    }

    private companion object {
        const val TAG = "PleosCabin"
        const val GLOBAL_AREA = 0
        val GEAR_PROPERTIES = listOf(VehiclePropertyIds.GEAR_SELECTION, VehiclePropertyIds.CURRENT_GEAR)

        /**
         * Pleos Vehicle 2.3.2 의 착석 Boolean 은 AAOS 표준과 반대로 온다.
         * VHAL SEAT_OCCUPANCY 는 1 = 빈자리(VACANT), 2 = 사람 있음(OCCUPIED)인데, SDK 는 1 → true, 2 → false 를 준다
         * (Pleos 에뮬레이터 + Virtual Console 의 Empty · Occupied 로 확인, 2026-11-10. 차량 쪽 안전벨트 경고 서비스는 표준대로 읽는다).
         * 그래서 뒤집어 읽는다. SDK 가 고쳐지면 false 로 바꾼다.
         */
        const val SDK_SEAT_OCCUPANCY_INVERTED = true

        fun Boolean.toOccupied(): Boolean = if (SDK_SEAT_OCCUPANCY_INVERTED) !this else this

        /** 한국 차는 운전석이 왼쪽(ROW_1_LEFT) */
        fun Seat.toSeatValue(): SeatValue = when (this) {
            Seat.PASSENGER -> SeatValue.ROW_1_RIGHT
            Seat.REAR_LEFT -> SeatValue.ROW_2_LEFT
            Seat.REAR_RIGHT -> SeatValue.ROW_2_RIGHT
        }

        fun Int.toGearReading(): Reading<Gear> = when (this) {
            VehicleGear.GEAR_PARK -> Reading.Value(Gear.PARK)
            VehicleGear.GEAR_REVERSE -> Reading.Value(Gear.REVERSE)
            VehicleGear.GEAR_NEUTRAL -> Reading.Value(Gear.NEUTRAL)
            VehicleGear.GEAR_UNKNOWN -> Reading.Unavailable
            else -> Reading.Value(Gear.DRIVE) // D, 수동 1~9 단
        }
    }
}
