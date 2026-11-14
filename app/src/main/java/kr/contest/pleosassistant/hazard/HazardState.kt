package kr.contest.pleosassistant.hazard

import kr.contest.pleosassistant.memory.Gear
import kr.contest.pleosassistant.status.Reading
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * 기능 6 · 위험 구간. 위치 · 변속 단 신호와 사용자 조작을 받아 다음 상태를 돌려주는 순수 함수 모음.
 * Android 에 기대지 않아 단위 테스트로 확인한다.
 *
 * - 구간 반경 안에 들어서면 한 번 알린다. 경계에서 흔들려도 다시 울리지 않게 반경 + [EXIT_MARGIN_M] 밖으로 나가야 '나감'으로 본다.
 * - 같은 구간을 같은 날 두 번째부터 지나면 짧게 알린다.
 * - 구간 추가 · 수정은 정차(P) 중에만 한다(기능계획서 9장 '주행 중 편집 입력 없음').
 * - 새 경고는 음성으로도 한 번 말한다([speechSince], 기능계획서 8장 '들어가기 직전에 짧게 안내한다').
 */
data class HazardState(
    val data: HazardData = HazardData(),
    val position: Reading<GeoPoint> = Reading.Loading,
    val gear: Reading<Gear> = Reading.Loading,
    /** 지금 안에 있는 구간 id */
    val inside: Set<Long> = emptySet(),
    val alert: HazardAlert? = null,
    /** 지금까지 낸 경고 수. 같은 문장의 경고가 다시 나와도 새 경고인지 가린다 */
    val alertCount: Int = 0,
    /** 저장 결과 같은 짧은 안내 */
    val notice: String? = null,
)

/** 구간에 들어설 때 띄우는 경고 */
data class HazardAlert(val zoneId: Long, val title: String, val message: String, val repeat: Boolean)

const val EXIT_MARGIN_M = 30

/** 차가 움직일 수 있는 단이면 편집을 막는다. 변속 단을 못 읽는 차는 막지 않는다 */
val HazardState.canEdit: Boolean
    get() = when (val g = gear) {
        is Reading.Value -> g.value == Gear.PARK
        else -> true
    }

/** 경고를 울릴 상황. 변속 단을 못 읽는 차는 움직이는 것으로 본다 */
val HazardState.isMoving: Boolean
    get() = when (val g = gear) {
        is Reading.Value -> g.value != Gear.PARK
        Reading.Loading -> false
        else -> true
    }

fun HazardState.onGear(reading: Reading<Gear>): HazardState = copy(gear = reading)

fun HazardState.onPosition(reading: Reading<GeoPoint>, now: LocalDateTime): HazardState {
    val point = (reading as? Reading.Value)?.value ?: return copy(position = reading)
    val time = now.toLocalTime()
    val nowInside = data.zones.filter { it.enabled && it.schedule.isActive(time) }.mapNotNull { zone ->
        val d = point.distanceTo(zone.center)
        when {
            d <= zone.radiusM -> zone.id
            d <= zone.radiusM + EXIT_MARGIN_M && zone.id in inside -> zone.id
            else -> null
        }
    }.toSet()

    // 정차(P) 중이거나 변속 단을 아직 읽는 중이면 조용히 '안'으로만 둔다. 앱을 켰는데 이미 구간 안이라고 울리지 않게
    if (!isMoving) return copy(position = reading, inside = nowInside)

    val entered = data.zones
        .filter { it.id in nowInside && it.id !in inside }
        .minByOrNull { point.distanceTo(it.center) }
    if (entered == null) return copy(position = reading, inside = nowInside)

    val today = now.toLocalDate()
    val before = data.passes[entered.id]?.takeIf { it.date == today }?.count ?: 0
    val repeat = before >= 1
    val alert = HazardAlert(
        zoneId = entered.id,
        title = if (repeat) "다시 지나요" else "위험 구간이에요",
        message = if (repeat) {
            "다시 ${entered.name}${entered.name.copula()}."
        } else {
            "${entered.radiusM}m 앞 ${entered.name}${entered.name.copula()}. 보행자 조심하세요."
        },
        repeat = repeat,
    )
    return copy(
        position = reading,
        inside = nowInside,
        alert = alert,
        alertCount = alertCount + 1,
        data = data.copy(passes = data.passes + (entered.id to Pass(today, before + 1))),
    )
}

/**
 * [before] 뒤에 새로 낸 경고가 있으면 소리 내어 말할 문장. 없으면 null.
 * 화면 경고와 같은 문장을 쓰고, 같은 경고는 한 번만 말한다(경고 창이 떠 있는 동안 위치가 바뀌어도 다시 말하지 않는다).
 */
fun HazardState.speechSince(before: HazardState): String? =
    alert?.takeIf { alertCount != before.alertCount }?.message

fun HazardState.addZone(name: String, radiusM: Int, schedule: AlertSchedule, now: LocalDateTime): HazardState {
    if (!canEdit) return copy(notice = "정차(P)하면 구간을 저장할 수 있어요")
    val point = (position as? Reading.Value)?.value ?: return copy(notice = "현재 위치를 아직 몰라요")
    val id = data.nextZoneId
    val zone = HazardZone(
        id = id,
        name = name.trim().ifEmpty { "위험 구간 $id" },
        center = point,
        radiusM = radiusM,
        schedule = schedule,
        enabled = true,
        createdAt = now,
    )
    // 지금 서 있는 곳을 저장했으니 이미 '안'으로 둔다. 저장하자마자 경고가 울리지 않게
    return copy(
        data = data.copy(zones = data.zones + zone, nextZoneId = id + 1),
        inside = inside + id,
        notice = "${zone.name}${zone.name.objectParticle()} 저장했어요",
    )
}

fun HazardState.setEnabled(id: Long, enabled: Boolean): HazardState = editZone(id) { it.copy(enabled = enabled) }
    .let { if (!enabled) it.copy(inside = it.inside - id) else it }

fun HazardState.setRadius(id: Long, radiusM: Int): HazardState = editZone(id) { it.copy(radiusM = radiusM) }

fun HazardState.setSchedule(id: Long, schedule: AlertSchedule): HazardState = editZone(id) { it.copy(schedule = schedule) }

fun HazardState.removeZone(id: Long): HazardState {
    if (!canEdit) return copy(notice = "정차(P)하면 고칠 수 있어요")
    val zone = data.zones.firstOrNull { it.id == id } ?: return this
    return copy(
        data = data.copy(zones = data.zones - zone, passes = data.passes - id),
        inside = inside - id,
        alert = alert?.takeIf { it.zoneId != id },
        notice = "${zone.name}${zone.name.objectParticle()} 지웠어요",
    )
}

fun HazardState.dismissAlert(): HazardState = copy(alert = null)

fun HazardState.clearNotice(): HazardState = copy(notice = null)

private inline fun HazardState.editZone(id: Long, change: (HazardZone) -> HazardZone): HazardState {
    if (!canEdit) return copy(notice = "정차(P)하면 고칠 수 있어요")
    return copy(data = data.copy(zones = data.zones.map { if (it.id == id) change(it) else it }))
}

// ---- 화면에 넘기는 값 ----

data class ZoneRow(
    val id: Long,
    val name: String,
    val radiusM: Int,
    val schedule: AlertSchedule,
    val enabled: Boolean,
    /** "지금 350m" 같은 현재 거리. 위치를 모르면 null */
    val distanceText: String?,
    val inside: Boolean,
    /** 시간대 설정상 지금 알리는 시간인지 */
    val activeNow: Boolean,
)

data class HazardReport(
    val summary: String,
    val canEdit: Boolean,
    val hasPosition: Boolean,
    val positionText: String,
    val zones: List<ZoneRow>,
    val alert: HazardAlert?,
    val notice: String?,
)

fun buildHazardReport(state: HazardState, now: LocalDateTime): HazardReport {
    val point = (state.position as? Reading.Value)?.value
    val time = now.toLocalTime()
    val rows = state.data.zones.map { z ->
        ZoneRow(
            id = z.id,
            name = z.name,
            radiusM = z.radiusM,
            schedule = z.schedule,
            enabled = z.enabled,
            distanceText = point?.let { "지금 ${formatDistance(it.distanceTo(z.center))}" },
            inside = z.id in state.inside,
            activeNow = z.schedule.isActive(time),
        )
    }
    val insideZone = rows.firstOrNull { it.inside && it.enabled }
    val watching = rows.count { it.enabled }
    val summary = when {
        insideZone != null -> "${insideZone.name} 구간 안이에요. 천천히 지나가요"
        rows.isEmpty() -> "정차 중에 지금 위치를 위험 구간으로 저장해 두면, 다음에 가까이 갈 때 알려 드려요"
        watching == 0 -> "모든 구간의 알림을 껐어요"
        else -> "위험 구간 ${watching}곳을 지켜보고 있어요" + if (watching < rows.size) " · ${rows.size - watching}곳은 알림 끔" else ""
    }
    val positionText = when (val p = state.position) {
        is Reading.Value -> "현재 위치 %.5f, %.5f".format(p.value.lat, p.value.lon)
        Reading.Loading -> "현재 위치를 찾는 중이에요"
        Reading.Unsupported -> "위치 권한이 없어요. 설정에서 위치를 허용해 주세요"
        Reading.Unavailable -> "현재 위치를 읽지 못했어요"
    }
    return HazardReport(
        summary = summary,
        canEdit = state.canEdit,
        hasPosition = point != null,
        positionText = positionText,
        zones = rows,
        alert = state.alert,
        notice = state.notice,
    )
}

internal fun formatDistance(m: Double): String =
    if (m < 1_000) "${(m / 10).roundToInt() * 10}m" else "%.1fkm".format(m / 1_000)

// ---- 한국어 조사 ----

/** 받침이 있으면 true. 숫자는 읽는 소리(영 · 일 · 삼 · 육 · 칠 · 팔은 받침 있음)로, 영문은 받침 없음으로 본다 */
private fun String.endsWithBatchim(): Boolean {
    val c = trim().lastOrNull() ?: return false
    if (c.isDigit()) return c in "013678"
    if (c !in '가'..'힣') return false
    return (c - '가') % 28 != 0
}

/** "정문이에요" / "학교예요" */
internal fun String.copula(): String = if (endsWithBatchim()) "이에요" else "예요"

/** "정문을" / "학교를" */
internal fun String.objectParticle(): String = if (endsWithBatchim()) "을" else "를"
