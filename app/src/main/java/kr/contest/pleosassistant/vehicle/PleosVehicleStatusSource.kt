package kr.contest.pleosassistant.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.LowWasherFluidWaringListener
import ai.pleos.playground.vehicle.listener.OilLevelStateListener
import kr.contest.pleosassistant.status.FluidLevel
import kr.contest.pleosassistant.status.Reading
import kr.contest.pleosassistant.status.VehicleSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pleos Vehicle SDK 로 기능 1 항목을 읽는다.
 * 항목마다 checkXxxCapabilities → getXxx → registerXxx 순서로 진행하고([VehicleWatcher]), 결과를 [snapshot] 에 모은다.
 * 지원하지 않거나 권한이 없으면 Unsupported, 그 밖의 실패는 Unavailable 로 둔다.
 *
 * 타이어 공기압과 경고등은 여기 없다: getLowTirePressure 는 기준값만 주고,
 * 경고등(getTelltalesInfo · registerTelltales)은 PRIVATE_CAR_SAFETY(시스템 앱 전용) 권한이 필요하다.
 */
class PleosVehicleStatusSource(private val vehicle: Vehicle) : VehicleStatusSource {

    private val _snapshot = MutableStateFlow(VehicleSnapshot())
    override val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()

    private val watcher = VehicleWatcher(TAG)

    override fun start() {
        if (watcher.active) return
        watcher.start()
        val safety = vehicle.getSafety()
        val fuel = vehicle.getFuel()
        val ev = vehicle.getEvBattery()
        val odometer = vehicle.getOdometer()

        watcher.watch(
            name = "engineOil",
            publish = into { s, r -> s.copy(engineOil = r) },
            check = { ok, fail -> safety.checkEngineOilLevelStateCapabilities({ c, _ -> ok(c.readable()) }, fail) },
            get = { ok, fail -> safety.getEngineOilLevelState({ ok(it?.toFluidLevel()) }, fail) },
            register = { emit ->
                val l = oilListener(emit)
                safety.registerEngineOilLevelState(l)
                val unregister: () -> Unit = { safety.unregisterEngineOilLevelState(l) }
                unregister
            },
        )
        watcher.watch(
            name = "transmissionOil",
            publish = into { s, r -> s.copy(transmissionOil = r) },
            check = { ok, fail -> safety.checkTransmissionOilLevelStateCapabilities({ c, _ -> ok(c.readable()) }, fail) },
            get = { ok, fail -> safety.getTransmissionOilLevelState({ ok(it?.toFluidLevel()) }, fail) },
            register = { emit ->
                val l = oilListener(emit)
                safety.registerTransmissionOilLevelState(l)
                val unregister: () -> Unit = { safety.unregisterTransmissionOilLevelState(l) }
                unregister
            },
        )
        watcher.watch(
            name = "brakeFluid",
            publish = into { s, r -> s.copy(brakeFluid = r) },
            check = { ok, fail -> safety.checkBrakeFluidLevelStateCapabilities({ c, _ -> ok(c.readable()) }, fail) },
            get = { ok, fail -> safety.getBrakeFluidLevelState({ ok(it?.toFluidLevel()) }, fail) },
            register = { emit ->
                val l = oilListener(emit)
                safety.registerBrakeFluidLevelState(l)
                val unregister: () -> Unit = { safety.unregisterBrakeFluidLevelState(l) }
                unregister
            },
        )
        watcher.watch(
            name = "washerFluid",
            publish = into { s, r -> s.copy(washerFluidLow = r) },
            check = { ok, fail -> safety.checkLowWasherFluidWarningStateCapabilities({ c -> ok(c.readable()) }, fail) },
            get = { ok, fail -> safety.hasLowWasherFluidWarning({ ok(it) }, fail) },
            register = { emit ->
                val l = object : LowWasherFluidWaringListener {
                    override fun onLowWasherFluidWaringUpdated(state: Boolean?) = emit(state)
                    override fun onFailed(e: Exception) = watcher.listenerFailed(e)
                }
                safety.registerLowWasherFluidWaringState(l)
                val unregister: () -> Unit = { safety.unregisterLowWasherFluidWaringState(l) }
                unregister
            },
        )
        watcher.watch(
            name = "fuelPercent",
            publish = into { s, r -> s.copy(fuelPercent = r) },
            check = { ok, fail -> fuel.checkFuelPercentCapabilities({ c -> ok(c.readable()) }, fail) },
            get = { ok, fail -> fuel.getFuelPercent({ ok(it) }, fail) },
            register = { emit ->
                val l = object : FuelPercentListener {
                    override fun onFuelPercentUpdated(value: Int?) = emit(value)
                    override fun onFailed(e: Exception) = watcher.listenerFailed(e)
                }
                fuel.registerFuelPercent(l)
                val unregister: () -> Unit = { fuel.unregisterFuelPercent(l) }
                unregister
            },
        )
        watcher.watch(
            name = "evBatteryPercent",
            publish = into { s, r -> s.copy(evBatteryPercent = r) },
            check = { ok, fail -> ev.checkEvBatteryStateOfChargeCapabilities({ c -> ok(c.readable()) }, fail) },
            get = { ok, fail -> ev.getEvBatteryStateOfCharge({ ok(it) }, fail) },
            register = { emit ->
                val l = object : EvBatteryStateOfChargeListener {
                    override fun onEvBatteryStateOfChargeUpdated(value: Int?) = emit(value)
                    override fun onFailed(e: Exception) = watcher.listenerFailed(e)
                }
                ev.registerEvBatteryStateOfCharge(l)
                val unregister: () -> Unit = { ev.unregisterEvBatteryStateOfCharge(l) }
                unregister
            },
        )
        watcher.watch(
            name = "odometerKm",
            publish = into { s, r -> s.copy(odometerKm = r) },
            check = { ok, fail -> odometer.checkCurrentDistanceDrivenCapabilities({ c -> ok(c.readable()) }, fail) },
            get = { ok, fail -> odometer.getCurrentDistanceDriven({ ok(it) }, fail) },
            register = { emit ->
                val l = object : DistanceDrivenListener {
                    override fun onDistanceDrivenUpdated(value: Float?) = emit(value)
                    override fun onFailed(e: Exception) = watcher.listenerFailed(e)
                }
                odometer.registerDistanceDriven(l)
                val unregister: () -> Unit = { odometer.unregisterDistanceDriven(l) }
                unregister
            },
        )
    }

    override fun stop() = watcher.stop()

    /** 항목 하나의 결과를 [snapshot] 의 제자리에 넣는다 */
    private fun <T> into(set: (VehicleSnapshot, Reading<T>) -> VehicleSnapshot): (Reading<T>) -> Unit =
        { reading -> _snapshot.update { set(it, reading) } }

    private fun oilListener(emit: (FluidLevel?) -> Unit) = object : OilLevelStateListener {
        override fun onOilLevelUpdated(level: OilLevelState?) = emit(level?.toFluidLevel())
        override fun onFailed(e: Exception) = watcher.listenerFailed(e)
    }

    private companion object {
        const val TAG = "PleosVehicleStatus"

        fun OilLevelState.toFluidLevel(): FluidLevel = when (this) {
            OilLevelState.NORMAL -> FluidLevel.NORMAL
            OilLevelState.LOW -> FluidLevel.LOW
            OilLevelState.CRITICALLY_LOW -> FluidLevel.CRITICALLY_LOW
            OilLevelState.HIGH -> FluidLevel.HIGH
            OilLevelState.ERROR -> FluidLevel.ERROR
        }
    }
}
