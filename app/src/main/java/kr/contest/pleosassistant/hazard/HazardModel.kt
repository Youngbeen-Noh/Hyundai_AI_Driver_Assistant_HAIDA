package kr.contest.pleosassistant.hazard

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 위도 · 경도(도). Android Location 에 기대지 않아 단위 테스트에서 그대로 만든다. */
data class GeoPoint(val lat: Double, val lon: Double)

/** 두 지점 사이 거리(m). 하버사인 공식 */
fun GeoPoint.distanceTo(other: GeoPoint): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(other.lat - lat)
    val dLon = Math.toRadians(other.lon - lon)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) * sin(dLon / 2) * sin(dLon / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** 알림 반경. 들어서는 순간 "200m 앞 ○○예요" 하고 알린다(기능계획서 8장 · 예: 200m) */
val RADIUS_CHOICES = listOf(100, 200, 300)

/** 언제 알릴지. 경고 피로를 줄이려고 시간대를 고를 수 있다(기능계획서 8장 '과하지 않게') */
enum class AlertSchedule(val label: String) {
    ALWAYS("항상"),

    /** 아침 등교 07:30~09:30, 오후 하교 13:00~17:00 */
    SCHOOL_HOURS("등하교 시간만"),
    ;

    fun isActive(time: LocalTime): Boolean = when (this) {
        ALWAYS -> true
        SCHOOL_HOURS -> time in LocalTime.of(7, 30)..LocalTime.of(9, 30) || time in LocalTime.of(13, 0)..LocalTime.of(17, 0)
    }
}

/**
 * 사용자가 지정한 위험 구간 하나.
 * @property enabled false 면 다시 안내하지 않는다(기능계획서 8장 '사용자가 끈 구간은 다시 안내하지 않는다')
 */
data class HazardZone(
    val id: Long,
    val name: String,
    val center: GeoPoint,
    val radiusM: Int,
    val schedule: AlertSchedule,
    val enabled: Boolean,
    val createdAt: LocalDateTime,
)

/** 구간을 오늘 몇 번 지났는지. 두 번째부터는 안내를 짧게 줄인다 */
data class Pass(val date: LocalDate, val count: Int)

/** 차 안에만 저장하는 위험 구간 기억. 서버로 보내지 않는다(기능계획서 9장 원칙) */
data class HazardData(
    val zones: List<HazardZone> = emptyList(),
    val passes: Map<Long, Pass> = emptyMap(),
    val nextZoneId: Long = 1,
)

/** 위험 구간을 저장하는 곳. 앱에서는 기기 내부 파일, 테스트에서는 메모리 */
interface HazardStore {
    fun load(): HazardData
    fun save(data: HazardData)
}
