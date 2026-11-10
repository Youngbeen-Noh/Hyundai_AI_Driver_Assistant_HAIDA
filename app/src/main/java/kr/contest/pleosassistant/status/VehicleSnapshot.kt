package kr.contest.pleosassistant.status

/**
 * 차량에서 읽은 값 하나. Pleos SDK 타입에 기대지 않아서 단위 테스트에서 그대로 만들 수 있다.
 */
sealed interface Reading<out T> {
    /** 아직 읽는 중 */
    data object Loading : Reading<Nothing>

    /** 이 차량이 지원하지 않거나, 앱에 권한이 없다 */
    data object Unsupported : Reading<Nothing>

    /** 지원은 하지만 값을 읽지 못했다 (오류, 값 없음) */
    data object Unavailable : Reading<Nothing>

    data class Value<T>(val value: T) : Reading<T>
}

/** 엔진오일 · 미션오일 · 브레이크액 수준 (Pleos OilLevelState 와 같은 단계) */
enum class FluidLevel { NORMAL, LOW, CRITICALLY_LOW, HIGH, ERROR }

/** 기능 1 차량 상태 요약에 쓰는 값 묶음 */
data class VehicleSnapshot(
    val engineOil: Reading<FluidLevel> = Reading.Loading,
    val transmissionOil: Reading<FluidLevel> = Reading.Loading,
    val brakeFluid: Reading<FluidLevel> = Reading.Loading,
    /** true 면 워셔액 부족 경고 */
    val washerFluidLow: Reading<Boolean> = Reading.Loading,
    /** 연료 잔량 (%) */
    val fuelPercent: Reading<Int> = Reading.Loading,
    /** EV 배터리 잔량 (%) */
    val evBatteryPercent: Reading<Int> = Reading.Loading,
    /** 누적 주행거리 (km) */
    val odometerKm: Reading<Float> = Reading.Loading,
)
