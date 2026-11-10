package kr.contest.pleosassistant.drive

import kr.contest.pleosassistant.memory.Gear
import kr.contest.pleosassistant.status.Reading
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 기능 5 · 주행 리포트. 변속 단 · 차속 신호를 받아 다음 상태를 돌려주는 순수 함수 모음.
 *
 * - D(또는 R · N)로 바뀌면 주행을 시작하고, P 로 돌아오면 끝내 점수를 낸다(하차 후 리포트, 기획서 7장).
 * - 속도 변화는 약 1초 간격([RATE_WINDOW_MS])으로 재서 급가속 · 급감속을 센다. 같은 종류는 [COOLDOWN_MS] 안에 한 번만.
 * - 이벤트를 10km 당 횟수로 바꿔 짧은 주행과 긴 주행을 같은 기준으로 비교한다.
 *   [MIN_SCORING_KM] 보다 짧으면 그 거리로 본다(1km 주행의 급감속 한 번이 40점을 깎는 식으로 지나치게 커지지 않게).
 */
data class DriveState(
    val data: DriveData = DriveData(),
    val gear: Reading<Gear> = Reading.Loading,
    val trip: Trip? = null,
    /** 막 끝난 주행. 하차 직후 한 번 띄운다 */
    val finished: TripSummary? = null,
)

/** 진행 중인 주행 */
data class Trip(
    val start: LocalDateTime,
    val distanceKm: Double = 0.0,
    val movingMs: Long = 0,
    val steadyMs: Long = 0,
    val events: List<DriveEvent> = emptyList(),
    /** 직전 속도 샘플 (거리 적분용) */
    val last: Sample? = null,
    /** 속도 변화율을 재는 기준 샘플 */
    val ref: Sample? = null,
    val lastEventAt: Map<DriveEventType, Long> = emptyMap(),
)

/** @property t 단조 증가 시계(ms). 벽시계가 아니라 elapsedRealtime 같은 것 */
data class Sample(val t: Long, val kmh: Double)

const val HARD_ACCEL_KMH_PER_S = 8.0
const val HARD_BRAKE_KMH_PER_S = 14.0
const val RATE_WINDOW_MS = 1_000L
const val COOLDOWN_MS = 3_000L
/** 이보다 짧은 주행(이동 거리 · 움직인 시간 모두)은 리포트를 내지 않는다 */
const val MIN_TRIP_KM = 0.05
const val MIN_TRIP_MOVING_MS = 30_000L
const val MAX_KEPT_TRIPS = 30
const val MIN_SCORING_KM = 5.0

private const val MOVING_KMH = 3.0
private const val STEADY_KMH_PER_S = 3.0
/** 샘플 사이가 이보다 벌어지면(신호 끊김) 그 구간은 적분하지 않는다 */
private const val MAX_GAP_MS = 5_000L

fun DriveState.onGear(reading: Reading<Gear>, now: LocalDateTime): DriveState {
    val gearValue = (reading as? Reading.Value)?.value
    return when {
        gearValue == Gear.PARK && trip != null -> endTrip(now).copy(gear = reading)
        gearValue != null && gearValue != Gear.PARK && trip == null -> copy(gear = reading, trip = Trip(start = now))
        else -> copy(gear = reading)
    }
}

fun DriveState.onSpeed(kmh: Double, t: Long, now: LocalDateTime): DriveState {
    val trip = trip ?: return this
    val sample = Sample(t, max(0.0, kmh))
    var next = trip.copy(last = sample)

    trip.last?.let { prev ->
        val dt = t - prev.t
        if (dt in 1..MAX_GAP_MS) {
            val avg = (prev.kmh + sample.kmh) / 2
            next = next.copy(
                distanceKm = trip.distanceKm + avg * dt / 3_600_000.0,
                movingMs = trip.movingMs + if (avg >= MOVING_KMH) dt else 0,
            )
        }
    }

    val ref = trip.ref
    if (ref == null || t - ref.t > MAX_GAP_MS) return copy(trip = next.copy(ref = sample))
    val window = t - ref.t
    if (window < RATE_WINDOW_MS) return copy(trip = next)

    val rate = (sample.kmh - ref.kmh) / (window / 1_000.0)
    val moving = (sample.kmh + ref.kmh) / 2 >= MOVING_KMH
    next = next.copy(ref = sample, steadyMs = next.steadyMs + if (moving && abs(rate) < STEADY_KMH_PER_S) window else 0)

    val type = when {
        rate >= HARD_ACCEL_KMH_PER_S -> DriveEventType.HARD_ACCEL
        rate <= -HARD_BRAKE_KMH_PER_S -> DriveEventType.HARD_BRAKE
        else -> null
    }
    if (type != null && t - (trip.lastEventAt[type] ?: Long.MIN_VALUE / 2) >= COOLDOWN_MS) {
        val event = DriveEvent(type, now, ref.kmh.roundToInt(), sample.kmh.roundToInt())
        next = next.copy(events = next.events + event, lastEventAt = next.lastEventAt + (type to t))
    }
    return copy(trip = next)
}

fun DriveState.dismissFinished(): DriveState = copy(finished = null)

private fun DriveState.endTrip(now: LocalDateTime): DriveState {
    val trip = trip ?: return this
    if (trip.distanceKm < MIN_TRIP_KM && trip.movingMs < MIN_TRIP_MOVING_MS) return copy(trip = null)
    val summary = summarize(trip, now)
    return copy(
        trip = null,
        finished = summary,
        data = data.copy(trips = (data.trips + summary).takeLast(MAX_KEPT_TRIPS)),
    )
}

/** 안전 · 연비 · 주행 점수를 계산한다 */
fun summarize(trip: Trip, end: LocalDateTime): TripSummary {
    val per10km = max(trip.distanceKm, MIN_SCORING_KM) / 10.0
    val deduction = trip.events.groupingBy { it.type }.eachCount()
        .entries.sumOf { (type, n) -> type.weight * n / per10km }
    val safety = (100 - deduction).roundToInt().coerceIn(0, 100)

    val steadyRatio = if (trip.movingMs > 0) (trip.steadyMs.toDouble() / trip.movingMs).coerceIn(0.0, 1.0) else 1.0
    val accelPer10 = trip.events.count { it.type == DriveEventType.HARD_ACCEL } / per10km
    // 연비 점수: 연료 · 전비 신호를 붙이기 전까지 정속 비율과 급가속으로 가늠한다(기획서 7장 '연비 운전' 항목 중 차속으로 되는 것)
    val eco = (50 + 50 * steadyRatio - 5 * accelPer10).roundToInt().coerceIn(0, 100)

    val score = (0.7 * safety + 0.3 * eco).roundToInt()
    return TripSummary(
        start = trip.start,
        end = end,
        distanceKm = trip.distanceKm,
        movingSec = (trip.movingMs / 1_000).toInt(),
        events = trip.events,
        steadyRatio = steadyRatio,
        safety = safety,
        eco = eco,
        score = score,
    )
}

// ---- 화면 · 멘트 ----

/** 점수를 가장 많이 깎은 장면(종류별로 묶어 하나). 깎인 게 없으면 null */
data class WorstScene(val type: DriveEventType, val count: Int, val minus: Int, val firstAt: LocalDateTime)

fun TripSummary.worstScene(): WorstScene? {
    val per10km = max(distanceKm, MIN_SCORING_KM) / 10.0
    return events.groupBy { it.type }
        .map { (type, list) -> WorstScene(type, list.size, (type.weight * list.size / per10km).roundToInt(), list.first().at) }
        .maxByOrNull { it.type.weight * it.count }
}

/** 하차 후 한 문장. 예: "오늘은 87점이에요. 급감속 두 번이 가장 아쉬웠어요." (기획서 7장 예시 멘트) */
fun TripSummary.message(previous: TripSummary?): String {
    val head = "오늘은 ${score}점이에요."
    val worst = worstScene()
    val body = if (worst == null) "급한 조작 없이 부드럽게 달렸어요." else "${worst.type.label} ${koreanCount(worst.count)}이 가장 아쉬웠어요."
    val diff = previous?.let { score - it.score }
    val tail = when {
        diff == null || diff == 0 -> ""
        diff > 0 -> " 지난번보다 ${diff}점 올랐어요."
        else -> " 지난번보다 ${-diff}점 내려갔어요."
    }
    return "$head $body$tail"
}

/** "한 번", "두 번" … 열 번까지는 우리말로, 그 위는 숫자로 */
internal fun koreanCount(n: Int): String {
    val words = listOf("한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열")
    return if (n in 1..10) "${words[n - 1]} 번" else "${n}번"
}

data class DriveReport(
    /** 주행 중이면 점수를 숨기고 이 문장만 보여 준다(기획서 7장 '상세 화면은 정차 중에만') */
    val driving: Boolean,
    val drivingText: String?,
    val last: TripSummary?,
    val lastMessage: String?,
    val worst: WorstScene?,
    /** 최근 주행 점수(오래된 것부터, 최대 7개) */
    val recentScores: List<Int>,
    val finished: TripSummary?,
    val finishedMessage: String?,
    /** 차속을 못 읽으면(권한 · 미지원) 안내 문장 */
    val speedProblem: String?,
)

fun buildDriveReport(state: DriveState, now: LocalDateTime, speed: Reading<Unit> = Reading.Value(Unit)): DriveReport {
    val trips = state.data.trips
    val last = trips.lastOrNull()
    val previous = trips.getOrNull(trips.size - 2)
    val driving = state.trip != null
    val drivingText = state.trip?.let {
        val minutes = Duration.between(it.start, now).toMinutes().coerceAtLeast(0)
        "주행 기록 중 · ${"%.1f".format(it.distanceKm)}km · ${minutes}분"
    }
    val finishedPrev = state.finished?.let { f -> trips.getOrNull(trips.indexOf(f) - 1) }
    return DriveReport(
        driving = driving,
        drivingText = drivingText,
        last = last,
        lastMessage = last?.message(previous),
        worst = last?.worstScene(),
        recentScores = trips.takeLast(7).map { it.score },
        finished = state.finished,
        finishedMessage = state.finished?.message(finishedPrev),
        speedProblem = when (speed) {
            Reading.Unsupported -> "차속을 읽을 권한이 없어요. 권한을 허용해야 점수를 낼 수 있어요"
            Reading.Unavailable -> "차속을 읽지 못하고 있어요"
            else -> null
        },
    )
}
