package kr.contest.pleosassistant.status

import java.text.NumberFormat
import java.util.Locale

/** 카드 하나의 상태. severity 가 클수록 먼저 말한다. */
enum class StatusLevel(val label: String, val severity: Int) {
    URGENT("즉시 조치", 2),
    CAUTION("주의", 1),
    OK("정상", 0),
    INFO("정보", 0),
    UNKNOWN("확인 불가", 0),
    UNSUPPORTED("지원 안 함", 0),
    LOADING("확인 중", 0),
}

enum class StatusItemId { ENGINE_OIL, TRANSMISSION_OIL, BRAKE_FLUID, WASHER_FLUID, FUEL, EV_BATTERY, ENERGY, ODOMETER }

/**
 * 화면 카드 하나.
 * @param detail 카드에 보이는 짧은 값 ("부족", "15%")
 * @param advice 주의 · 즉시 조치일 때 말할 문장. 요약 문장에 그대로 들어간다.
 */
data class StatusItem(
    val id: StatusItemId,
    val title: String,
    val level: StatusLevel,
    val detail: String,
    val advice: String? = null,
)

/** 화면이 그리는 전부. UI 템플릿이 바뀌어도 이 형태는 유지한다. */
data class StatusReport(
    val summary: String,
    val items: List<StatusItem>,
)

/** 기능 1 분류 기준 (docs/기능계획서.pdf 3장). 시범 주행 후 조정할 수 있게 한곳에 둔다. */
object StatusRules {
    /** 이 값 이하면 즉시 조치 */
    const val ENERGY_URGENT_PERCENT = 10

    /** 이 값 이하면 주의 */
    const val ENERGY_CAUTION_PERCENT = 20

    /** 요약 문장에서 자세히 말하는 문제 개수. 나머지는 개수만 말한다. */
    const val SUMMARY_MAX_PROBLEMS = 2
}

object StatusMessages {
    const val ALL_GOOD = "오늘 차 상태 좋아요."
    const val CHECKING = "차 상태를 확인하고 있어요."
    const val NO_DATA = "차 상태를 읽지 못했어요."
    const val REST_OK = "나머지는 모두 정상이에요."
    fun more(count: Int) = "그 밖에 점검할 곳이 ${count}곳 더 있어요."
}

fun buildStatusReport(snapshot: VehicleSnapshot): StatusReport {
    val items = buildList {
        add(fluidItem(StatusItemId.ENGINE_OIL, "엔진오일", snapshot.engineOil, lowIsUrgent = false))
        add(fluidItem(StatusItemId.TRANSMISSION_OIL, "미션오일", snapshot.transmissionOil, lowIsUrgent = false))
        add(fluidItem(StatusItemId.BRAKE_FLUID, "브레이크액", snapshot.brakeFluid, lowIsUrgent = true))
        add(washerItem(snapshot.washerFluidLow))
        addAll(energyItems(snapshot.fuelPercent, snapshot.evBatteryPercent))
        add(odometerItem(snapshot.odometerKm))
    }
    return StatusReport(summary = summarize(items), items = items)
}

internal fun summarize(items: List<StatusItem>): String {
    val readable = items.filter { it.level != StatusLevel.UNSUPPORTED }
    if (readable.isEmpty() || readable.all { it.level == StatusLevel.UNKNOWN }) return StatusMessages.NO_DATA
    if (readable.all { it.level == StatusLevel.LOADING }) return StatusMessages.CHECKING

    // sortedByDescending 은 안정 정렬이라 같은 단계끼리는 카드 순서를 지킨다
    val problems = readable.filter { it.level.severity > 0 }.sortedByDescending { it.level.severity }
    if (problems.isEmpty()) {
        return if (readable.any { it.level == StatusLevel.LOADING }) StatusMessages.CHECKING else StatusMessages.ALL_GOOD
    }

    val parts = problems.take(StatusRules.SUMMARY_MAX_PROBLEMS).mapNotNull { it.advice }.toMutableList()
    val hidden = problems.size - StatusRules.SUMMARY_MAX_PROBLEMS
    if (hidden > 0) {
        parts += StatusMessages.more(hidden)
    } else {
        val othersAllOk = readable
            .filter { it.level.severity == 0 && it.level != StatusLevel.INFO }
            .let { others -> others.isNotEmpty() && others.all { it.level == StatusLevel.OK } }
        if (othersAllOk) parts += StatusMessages.REST_OK
    }
    return parts.joinToString(" ")
}

private fun <T> notReadyItem(id: StatusItemId, title: String, reading: Reading<T>): StatusItem? = when (reading) {
    Reading.Loading -> StatusItem(id, title, StatusLevel.LOADING, "확인 중")
    Reading.Unsupported -> StatusItem(id, title, StatusLevel.UNSUPPORTED, "이 차량에서 지원 안 함")
    Reading.Unavailable -> StatusItem(id, title, StatusLevel.UNKNOWN, "값을 읽지 못했어요")
    is Reading.Value -> null
}

/** title 뒤에 붙는 조사는 받침이 있는 이름(엔진오일 · 미션오일 · 브레이크액)만 쓰므로 "이"로 고정한다. */
private fun fluidItem(id: StatusItemId, title: String, reading: Reading<FluidLevel>, lowIsUrgent: Boolean): StatusItem {
    notReadyItem(id, title, reading)?.let { return it }
    return when ((reading as Reading.Value).value) {
        FluidLevel.NORMAL -> StatusItem(id, title, StatusLevel.OK, "정상")
        FluidLevel.LOW -> if (lowIsUrgent) {
            StatusItem(id, title, StatusLevel.URGENT, "부족", "${title}이 부족해요. 운행 전에 바로 정비소에서 점검받으세요.")
        } else {
            StatusItem(id, title, StatusLevel.CAUTION, "부족", "${title}이 부족해요. 가까운 정비소에서 보충하세요.")
        }
        FluidLevel.CRITICALLY_LOW ->
            StatusItem(id, title, StatusLevel.URGENT, "매우 부족", "${title}이 매우 부족해요. 운행 전에 바로 보충하세요.")
        FluidLevel.HIGH ->
            StatusItem(id, title, StatusLevel.CAUTION, "너무 많음", "${title}이 너무 많아요. 정비소에서 점검받으세요.")
        FluidLevel.ERROR ->
            StatusItem(id, title, StatusLevel.CAUTION, "센서 이상", "${title} 상태를 확인할 수 없어요. 정비소에서 점검받으세요.")
    }
}

private fun washerItem(reading: Reading<Boolean>): StatusItem {
    val id = StatusItemId.WASHER_FLUID
    val title = "워셔액"
    notReadyItem(id, title, reading)?.let { return it }
    return if ((reading as Reading.Value).value) {
        StatusItem(id, title, StatusLevel.CAUTION, "부족", "워셔액이 부족해요. 주유소나 마트에서 보충하세요.")
    } else {
        StatusItem(id, title, StatusLevel.OK, "충분")
    }
}

private fun energyItems(fuel: Reading<Int>, ev: Reading<Int>): List<StatusItem> {
    val unsupported = Reading.Unsupported
    // 둘 다 모르면 카드 하나로 보여 준다
    if (fuel == unsupported && ev == unsupported) {
        return listOf(StatusItem(StatusItemId.ENERGY, "연료·배터리", StatusLevel.UNSUPPORTED, "이 차량에서 지원 안 함"))
    }
    if (fuel == Reading.Loading && ev == Reading.Loading) {
        return listOf(StatusItem(StatusItemId.ENERGY, "연료·배터리", StatusLevel.LOADING, "확인 중"))
    }
    return buildList {
        if (fuel != unsupported) add(energyItem(StatusItemId.FUEL, "연료", fuel, refill = "주유"))
        if (ev != unsupported) add(energyItem(StatusItemId.EV_BATTERY, "배터리", ev, refill = "충전"))
    }
}

/** 연료 · 배터리 모두 받침이 없어 조사 "가"를 쓴다. */
private fun energyItem(id: StatusItemId, title: String, reading: Reading<Int>, refill: String): StatusItem {
    notReadyItem(id, title, reading)?.let { return it }
    val percent = (reading as Reading.Value).value
    val detail = "$percent%"
    val place = if (refill == "주유") "주유소" else "충전소"
    return when {
        percent <= StatusRules.ENERGY_URGENT_PERCENT ->
            StatusItem(id, title, StatusLevel.URGENT, detail, "${title}가 ${percent}% 남았어요. 바로 ${refill}하세요.")
        percent <= StatusRules.ENERGY_CAUTION_PERCENT ->
            StatusItem(id, title, StatusLevel.CAUTION, detail, "${title}가 ${percent}% 남았어요. 가까운 ${place}에 들르세요.")
        else -> StatusItem(id, title, StatusLevel.OK, detail)
    }
}

private fun odometerItem(reading: Reading<Float>): StatusItem {
    val id = StatusItemId.ODOMETER
    val title = "누적 주행거리"
    notReadyItem(id, title, reading)?.let { return it }
    val km = (reading as Reading.Value).value
    return StatusItem(id, title, StatusLevel.INFO, formatKm(km))
}

internal fun formatKm(km: Float): String =
    NumberFormat.getIntegerInstance(Locale.KOREA).format(km.toLong()) + " km"
