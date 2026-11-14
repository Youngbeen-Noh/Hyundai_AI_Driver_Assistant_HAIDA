package kr.contest.pleosassistant.drive

import kr.contest.pleosassistant.memory.Gear
import kr.contest.pleosassistant.status.Reading
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * 기능 5 · 주행 리포트. 변속 단 · 차속 · 운전대 각도 · 안전벨트 신호를 받아 다음 상태를 돌려주는 순수 함수 모음.
 *
 * - D(또는 R · N)로 바뀌면 주행을 시작하고, P 로 돌아오면 끝내 점수를 낸다(하차 후 리포트, 기획서 7장).
 * - 속도 변화는 약 1초 간격([RATE_WINDOW_MS])으로 재서 급가속 · 급감속을 센다. 같은 종류는 [COOLDOWN_MS] 안에 한 번만.
 * - 이벤트를 10km 당 횟수로 바꿔 짧은 주행과 긴 주행을 같은 기준으로 비교한다.
 *   [MIN_SCORING_KM] 보다 짧으면 그 거리로 본다(1km 주행의 급감속 한 번이 40점을 깎는 식으로 지나치게 커지지 않게).
 * - 급조향: 운전대 각도와 차속으로 진행 방향이 바뀌는 빠르기(°/s)를 가늠해([headingRateDegPerSec]),
 *   [HARD_STEER_MIN_KMH] 이상에서 [HARD_STEER_DEG_PER_S] 를 넘는 순간 한 번 센다(다시 그 아래로 내려와야 다음을 센다).
 * - 안전벨트: 움직이는 동안 미착용 경고가 켜져 있던 시간을 모아, 1분마다 [BELT_MINUS_PER_MIN] 점을 깎는다(거리와 상관없이).
 */
data class DriveState(
    val data: DriveData = DriveData(),
    val gear: Reading<Gear> = Reading.Loading,
    val trip: Trip? = null,
    /** 막 끝난 주행. 하차 직후 한 번 띄운다 */
    val finished: TripSummary? = null,
    /** 지금 운전대 각도(°). 주행 사이에도 유지한다 */
    val steerDeg: Double = 0.0,
    /** 운전석 안전벨트 미착용 경고가 켜져 있는지 */
    val beltWarning: Boolean = false,
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
    /** 움직이는 동안 안전벨트 경고가 켜져 있던 시간 */
    val unbeltedMs: Long = 0,
    /** 급조향 기준을 넘은 채로 있는지. 넘는 순간만 센다 */
    val steering: Boolean = false,
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
const val HARD_STEER_DEG_PER_S = 10.0
/** 이보다 느리면 급조향을 세지 않는다(교차로 회전 · 주차는 원래 크게 돈다) */
const val HARD_STEER_MIN_KMH = 30.0
/** 운전대 각도 → 바퀴 각도. 승용차 보통 값. 시범 주행으로 다시 맞춘다 */
const val STEERING_RATIO = 14.0
const val WHEELBASE_M = 2.8
const val BELT_MINUS_PER_MIN = 5.0
const val BELT_MAX_MINUS = 30.0
/** 이보다 짧게 켜졌다 꺼진 경고(출발하며 매는 중)는 깎지 않는다 */
const val BELT_GRACE_SEC = 10

private const val MOVING_KMH = 3.0
private const val STEADY_KMH_PER_S = 3.0
/** 샘플 사이가 이보다 벌어지면(신호 끊김) 그 구간은 적분하지 않는다 */
private const val MAX_GAP_MS = 5_000L

fun DriveState.onGear(reading: Reading<Gear>, now: LocalDateTime): DriveState {
    val gearValue = (reading as? Reading.Value)?.value
    return when {
        gearValue == Gear.PARK && trip != null -> endTrip(now).copy(gear = reading)
        // 새로 출발하면 닫지 않은 점수 창도 닫는다(주행 중 점수 숨김)
        gearValue != null && gearValue != Gear.PARK && trip == null -> copy(gear = reading, trip = Trip(start = now), finished = null)
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
            val moving = avg >= MOVING_KMH
            next = next.copy(
                distanceKm = trip.distanceKm + avg * dt / 3_600_000.0,
                movingMs = trip.movingMs + if (moving) dt else 0,
                unbeltedMs = trip.unbeltedMs + if (moving && beltWarning) dt else 0,
            )
        }
    }
    next = next.checkSteering(steerDeg, sample.kmh, now)

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

fun DriveState.onSteering(deg: Double, now: LocalDateTime): DriveState {
    val trip = trip ?: return copy(steerDeg = deg)
    val kmh = trip.last?.kmh ?: 0.0
    return copy(steerDeg = deg, trip = trip.checkSteering(deg, kmh, now))
}

fun DriveState.onBeltWarning(on: Boolean): DriveState = copy(beltWarning = on)

/**
 * 운전대 각도 · 차속으로 진행 방향이 바뀌는 빠르기(°/s)를 가늠한다(자전거 모형: 요 각속도 = 차속 × tan(바퀴 각도) / 축간 거리).
 * 예: 60km/h 에서 운전대 30° 면 약 13°/s, 15° 면 약 6°/s.
 */
fun headingRateDegPerSec(kmh: Double, steerDeg: Double): Double {
    val wheel = Math.toRadians(steerDeg / STEERING_RATIO)
    return Math.toDegrees(kmh / 3.6 * tan(wheel) / WHEELBASE_M)
}

private fun Trip.checkSteering(deg: Double, kmh: Double, now: LocalDateTime): Trip {
    val hard = kmh >= HARD_STEER_MIN_KMH && abs(headingRateDegPerSec(kmh, deg)) >= HARD_STEER_DEG_PER_S
    if (hard == steering) return this
    if (!hard) return copy(steering = false)
    val event = DriveEvent(DriveEventType.HARD_STEER, now, kmh.roundToInt(), kmh.roundToInt())
    return copy(steering = true, events = events + event)
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
    val unbeltedSec = (trip.unbeltedMs / 1_000).toInt()
    val safety = (100 - deduction - beltMinus(unbeltedSec)).roundToInt().coerceIn(0, 100)

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
        unbeltedSec = unbeltedSec,
        safety = safety,
        eco = eco,
        score = score,
    )
}

/** 안전벨트 미착용으로 깎는 점수. 짧은 경고는 넘기고, 시작한 1분도 1분으로 센다 */
fun beltMinus(unbeltedSec: Int): Double {
    if (unbeltedSec < BELT_GRACE_SEC) return 0.0
    val minutes = (unbeltedSec + 59) / 60
    return min(BELT_MAX_MINUS, BELT_MINUS_PER_MIN * minutes)
}

// ---- 화면 · 멘트 ----

/** "40초", "1분 19초", "3분" */
fun durationText(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return when {
        m == 0 -> "${s}초"
        s == 0 -> "${m}분"
        else -> "${m}분 ${s}초"
    }
}

/**
 * 점수를 가장 많이 깎은 장면(이벤트는 종류별로 묶어 하나, 안전벨트는 미착용 시간 하나). 깎인 게 없으면 null
 * @property amount 화면에 쓰는 양("2회", "1분 19초")
 * @property spoken 멘트에서 '가장 아쉬웠어요' 앞에 오는 말("급감속 두 번이", "안전벨트를 매지 않은 1분 19초가")
 */
data class WorstScene(
    val label: String,
    val amount: String,
    val spoken: String,
    val minus: Int,
    val firstAt: LocalDateTime?,
    /** 어떻게 깎았는지 한 줄 */
    val basis: String,
)

fun TripSummary.worstScene(): WorstScene? {
    val per10km = max(distanceKm, MIN_SCORING_KM) / 10.0
    val scenes = events.groupBy { it.type }.map { (type, list) ->
        val minus = type.weight * list.size / per10km
        WorstScene(type.label, "${list.size}회", "${type.label} ${koreanCount(list.size)}이", minus.roundToInt(), list.first().at, "10km 당 횟수로 비교해요") to minus
    }
    val belt = beltMinus(unbeltedSec).takeIf { it > 0 }?.let { minus ->
        val time = durationText(unbeltedSec)
        // '초'로 끝나면 '가', '분'으로 끝나면 '이'
        val spoken = "안전벨트를 매지 않은 $time${if (time.endsWith("초")) "가" else "이"}"
        WorstScene("안전벨트 미착용", time, spoken, minus.roundToInt(), null, "움직이는 동안 경고가 켜진 시간 · 시작한 1분마다 5점") to minus
    }
    return (scenes + listOfNotNull(belt)).maxByOrNull { it.second }?.first
}

/** 하차 후 한 문장. 예: "오늘은 87점이에요. 급감속 두 번이 가장 아쉬웠어요." (기획서 7장 예시 멘트) */
fun TripSummary.message(previous: TripSummary?): String {
    val head = "오늘은 ${score}점이에요."
    val worst = worstScene()
    val body = if (worst == null) "급한 조작 없이 부드럽게 달렸어요." else "${worst.spoken} 가장 아쉬웠어요."
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
        // 주행 중에는 점수를 보여 주지 않는다
        finished = state.finished.takeIf { !driving },
        finishedMessage = state.finished?.takeIf { !driving }?.message(finishedPrev),
        speedProblem = when (speed) {
            Reading.Unsupported -> "차속을 읽을 권한이 없어요. 권한을 허용해야 점수를 낼 수 있어요"
            Reading.Unavailable -> "차속을 읽지 못하고 있어요"
            else -> null
        },
    )
}
