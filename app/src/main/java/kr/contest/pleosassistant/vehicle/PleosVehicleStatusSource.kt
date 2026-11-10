package kr.contest.pleosassistant.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.ErrorCode
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.LowWasherFluidWaringListener
import ai.pleos.playground.vehicle.listener.OilLevelStateListener
import android.os.Handler
import android.os.Looper
import android.util.Log
import kr.contest.pleosassistant.status.FluidLevel
import kr.contest.pleosassistant.status.Reading
import kr.contest.pleosassistant.status.VehicleSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pleos Vehicle SDK 로 기능 1 항목을 읽는다.
 * 항목마다 checkXxxCapabilities → getXxx → registerXxx 순서로 진행하고, 결과를 [snapshot] 에 모은다.
 * 지원하지 않거나 권한이 없으면 Unsupported, 그 밖의 실패는 Unavailable 로 둔다.
 *
 * 타이어 공기압과 경고등은 여기 없다: getLowTirePressure 는 기준값만 주고,
 * 경고등(getTelltalesInfo · registerTelltales)은 PRIVATE_CAR_SAFETY(시스템 앱 전용) 권한이 필요하다.
 */
class PleosVehicleStatusSource(private val vehicle: Vehicle) : VehicleStatusSource {

    private val _snapshot = MutableStateFlow(VehicleSnapshot())
    override val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val unregisters = mutableListOf<() -> Unit>()
    private var started = false

    override fun start() {
        if (started) return
        started = true
        val safety = vehicle.getSafety()
        val fuel = vehicle.getFuel()
        val ev = vehicle.getEvBattery()
        val odometer = vehicle.getOdometer()

        watch(
            name = "engineOil",
            set = { s, r -> s.copy(engineOil = r) },
            check = { ok, fail -> safety.checkEngineOilLevelStateCapabilities({ c, _ -> ok(c) }, fail) },
            get = { ok, fail -> safety.getEngineOilLevelState({ ok(it?.toFluidLevel()) }, fail) },
            register = { emit ->
                val l = oilListener(emit)
                safety.registerEngineOilLevelState(l)
                val unregister: () -> Unit = { safety.unregisterEngineOilLevelState(l) }
                unregister
            },
        )
        watch(
            name = "transmissionOil",
            set = { s, r -> s.copy(transmissionOil = r) },
            check = { ok, fail -> safety.checkTransmissionOilLevelStateCapabilities({ c, _ -> ok(c) }, fail) },
            get = { ok, fail -> safety.getTransmissionOilLevelState({ ok(it?.toFluidLevel()) }, fail) },
            register = { emit ->
                val l = oilListener(emit)
                safety.registerTransmissionOilLevelState(l)
                val unregister: () -> Unit = { safety.unregisterTransmissionOilLevelState(l) }
                unregister
            },
        )
        watch(
            name = "brakeFluid",
            set = { s, r -> s.copy(brakeFluid = r) },
            check = { ok, fail -> safety.checkBrakeFluidLevelStateCapabilities({ c, _ -> ok(c) }, fail) },
            get = { ok, fail -> safety.getBrakeFluidLevelState({ ok(it?.toFluidLevel()) }, fail) },
            register = { emit ->
                val l = oilListener(emit)
                safety.registerBrakeFluidLevelState(l)
                val unregister: () -> Unit = { safety.unregisterBrakeFluidLevelState(l) }
                unregister
            },
        )
        watch(
            name = "washerFluid",
            set = { s, r -> s.copy(washerFluidLow = r) },
            check = { ok, fail -> safety.checkLowWasherFluidWarningStateCapabilities({ c -> ok(c) }, fail) },
            get = { ok, fail -> safety.hasLowWasherFluidWarning({ ok(it) }, fail) },
            register = { emit ->
                val l = object : LowWasherFluidWaringListener {
                    override fun onLowWasherFluidWaringUpdated(state: Boolean?) = emit(state)
                    override fun onFailed(e: Exception) = listenerFailed(e)
                }
                safety.registerLowWasherFluidWaringState(l)
                val unregister: () -> Unit = { safety.unregisterLowWasherFluidWaringState(l) }
                unregister
            },
        )
        watch(
            name = "fuelPercent",
            set = { s, r -> s.copy(fuelPercent = r) },
            check = { ok, fail -> fuel.checkFuelPercentCapabilities({ c -> ok(c) }, fail) },
            get = { ok, fail -> fuel.getFuelPercent({ ok(it) }, fail) },
            register = { emit ->
                val l = object : FuelPercentListener {
                    override fun onFuelPercentUpdated(value: Int?) = emit(value)
                    override fun onFailed(e: Exception) = listenerFailed(e)
                }
                fuel.registerFuelPercent(l)
                val unregister: () -> Unit = { fuel.unregisterFuelPercent(l) }
                unregister
            },
        )
        watch(
            name = "evBatteryPercent",
            set = { s, r -> s.copy(evBatteryPercent = r) },
            check = { ok, fail -> ev.checkEvBatteryStateOfChargeCapabilities({ c -> ok(c) }, fail) },
            get = { ok, fail -> ev.getEvBatteryStateOfCharge({ ok(it) }, fail) },
            register = { emit ->
                val l = object : EvBatteryStateOfChargeListener {
                    override fun onEvBatteryStateOfChargeUpdated(value: Int?) = emit(value)
                    override fun onFailed(e: Exception) = listenerFailed(e)
                }
                ev.registerEvBatteryStateOfCharge(l)
                val unregister: () -> Unit = { ev.unregisterEvBatteryStateOfCharge(l) }
                unregister
            },
        )
        watch(
            name = "odometerKm",
            set = { s, r -> s.copy(odometerKm = r) },
            check = { ok, fail -> odometer.checkCurrentDistanceDrivenCapabilities({ c -> ok(c) }, fail) },
            get = { ok, fail -> odometer.getCurrentDistanceDriven({ ok(it) }, fail) },
            register = { emit ->
                val l = object : DistanceDrivenListener {
                    override fun onDistanceDrivenUpdated(value: Float?) = emit(value)
                    override fun onFailed(e: Exception) = listenerFailed(e)
                }
                odometer.registerDistanceDriven(l)
                val unregister: () -> Unit = { odometer.unregisterDistanceDriven(l) }
                unregister
            },
        )
    }

    override fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        synchronized(unregisters) {
            unregisters.forEach { runCatching(it) }
            unregisters.clear()
        }
        started = false
    }

    /**
     * 항목 하나를 지원 확인 → 첫 값 읽기 → 변화 구독 순서로 처리한다.
     * 차량 서비스 연결이 아직 안 됐으면 잠시 뒤 다시 시도한다.
     */
    private fun <T> watch(
        name: String,
        set: (VehicleSnapshot, Reading<T>) -> VehicleSnapshot,
        check: (ok: (CapabilityConfig) -> Unit, fail: (Exception) -> Unit) -> Unit,
        get: (ok: (T?) -> Unit, fail: (Exception) -> Unit) -> Unit,
        register: (emit: (T?) -> Unit) -> () -> Unit,
        attempt: Int = 1,
    ) {
        fun publish(reading: Reading<T>) = _snapshot.update { set(it, reading) }
        fun publishValue(value: T?) = publish(if (value == null) Reading.Unavailable else Reading.Value(value))
        fun fail(step: String, e: Exception) {
            Log.w(TAG, "$name $step 실패: ${e.describe()}")
            if (e.isConnectionProblem() && attempt < MAX_ATTEMPTS && started) {
                mainHandler.postDelayed({ watch(name, set, check, get, register, attempt + 1) }, RETRY_DELAY_MS)
            } else {
                publish(if (e.isUnsupported()) Reading.Unsupported else Reading.Unavailable)
            }
        }

        guarded("check", ::fail) {
            check({ config ->
                if (config.accessType.canRead()) {
                    guarded("get", ::fail) { get(::publishValue) { e -> fail("get", e) } }
                    guarded("register", ::fail) {
                        val unregister = register(::publishValue)
                        synchronized(unregisters) { unregisters += unregister }
                    }
                } else {
                    publish(Reading.Unsupported)
                }
            }) { e -> fail("check", e) }
        }
    }

    /** SDK 가 콜백 대신 바로 예외를 던지는 경우(권한 등)도 같은 길로 보낸다. */
    private inline fun guarded(step: String, fail: (String, Exception) -> Unit, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail(step, e)
        }
    }

    private fun oilListener(emit: (FluidLevel?) -> Unit) = object : OilLevelStateListener {
        override fun onOilLevelUpdated(level: OilLevelState?) = emit(level?.toFluidLevel())
        override fun onFailed(e: Exception) = listenerFailed(e)
    }

    private companion object {
        const val TAG = "PleosVehicleStatus"
        const val MAX_ATTEMPTS = 5
        const val RETRY_DELAY_MS = 1_000L

        /** 구독 중 오류. 이미 읽은 값은 그대로 두고 기록만 한다. */
        fun listenerFailed(e: Exception) {
            Log.w(TAG, "구독 오류: ${e.describe()}")
        }

        fun AccessType.canRead() = this == AccessType.READ || this == AccessType.READ_WRITE

        fun OilLevelState.toFluidLevel(): FluidLevel = when (this) {
            OilLevelState.NORMAL -> FluidLevel.NORMAL
            OilLevelState.LOW -> FluidLevel.LOW
            OilLevelState.CRITICALLY_LOW -> FluidLevel.CRITICALLY_LOW
            OilLevelState.HIGH -> FluidLevel.HIGH
            OilLevelState.ERROR -> FluidLevel.ERROR
        }

        val UNSUPPORTED_CODES = setOf(
            ErrorCode.ERROR_NOT_SUPPORTED_PROPERTY,
            ErrorCode.ERROR_NOT_SUPPORTED_AREA,
            ErrorCode.ERROR_NOT_SUPPORTED_VALUE_TYPE,
            ErrorCode.ERROR_SECURITY_PERMISSION,
        )

        /**
         * 문서상 VehicleException.errorCode.
         * Vehicle 2.3.2 는 VehicleException 클래스를 컴파일 경로에 내보내지 않아서, 공개 getter 를 이름으로 부른다.
         * 그래도 못 얻으면 메시지에 들어 있는 코드 이름으로 찾는다.
         */
        fun Exception.vehicleErrorCode(): ErrorCode? =
            runCatching { javaClass.getMethod("getErrorCode").invoke(this) as? ErrorCode }.getOrNull()
                ?: ErrorCode.values().firstOrNull { message?.contains(it.name) == true }

        fun Exception.isUnsupported(): Boolean = this is SecurityException || vehicleErrorCode() in UNSUPPORTED_CODES

        fun Exception.isConnectionProblem(): Boolean = vehicleErrorCode() == ErrorCode.ERROR_SERVICE_CONNECTION_FAILURE

        fun Exception.describe(): String = "${javaClass.simpleName} ${vehicleErrorCode() ?: ""} ${message.orEmpty()}".trim()
    }
}
