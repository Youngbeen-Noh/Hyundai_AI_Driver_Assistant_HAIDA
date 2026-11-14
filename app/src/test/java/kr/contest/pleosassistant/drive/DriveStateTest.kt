package kr.contest.pleosassistant.drive

import kr.contest.pleosassistant.memory.Gear
import kr.contest.pleosassistant.status.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DriveStateTest {

    private val t0 = LocalDateTime.of(2026, 11, 10, 8, 0)

    private fun start() = DriveState().onGear(Reading.Value(Gear.PARK), t0).onGear(Reading.Value(Gear.DRIVE), t0)

    /** 1초 간격으로 속도를 넣는다. 반환: 다음 시각(ms) */
    private fun DriveState.feed(speeds: List<Double>, fromMs: Long = 0): DriveState =
        speeds.foldIndexed(this) { i, s, v -> s.onSpeed(v, fromMs + i * 1_000L, t0.plusSeconds((fromMs / 1_000) + i.toLong())) }

    /** 60km/h 로 n 초 */
    private fun cruise(n: Int) = List(n) { 60.0 }

    private fun park(s: DriveState, sec: Long = 600) = s.onGear(Reading.Value(Gear.PARK), t0.plusSeconds(sec))

    @Test
    fun `D 로 바꾸면 주행을 시작하고 P 로 돌아오면 점수를 낸다`() {
        val driving = start().feed(List(11) { it * 6.0 } + cruise(300)) // 초당 6km/h 로 부드럽게 출발
        assertNotNull(driving.trip)
        val done = park(driving)
        assertNull(done.trip)
        val f = done.finished!!
        assertEquals(1, done.data.trips.size)
        assertEquals(5.05, f.distanceKm, 0.1) // 60km/h × 300초 ≈ 5km + 출발 구간
        assertEquals(100, f.safety)
    }

    @Test
    fun `초당 14km_h 이상 줄면 급감속 한 번`() {
        val s = start().feed(cruise(60) + listOf(40.0, 20.0, 20.0) + cruise(60))
        val events = s.trip!!.events
        assertEquals(1, events.count { it.type == DriveEventType.HARD_BRAKE })
        assertEquals(60, events.first { it.type == DriveEventType.HARD_BRAKE }.fromKmh)
    }

    @Test
    fun `초당 13km_h 감속은 급감속이 아니다`() {
        val s = start().feed(cruise(10) + listOf(47.0, 34.0, 21.0))
        assertTrue(s.trip!!.events.none { it.type == DriveEventType.HARD_BRAKE })
    }

    @Test
    fun `초당 8km_h 이상 늘면 급가속`() {
        val s = start().feed(listOf(0.0, 9.0, 18.0, 27.0, 30.0))
        assertEquals(1, s.trip!!.events.count { it.type == DriveEventType.HARD_ACCEL }) // 3초 안이라 한 번만 센다
    }

    @Test
    fun `같은 종류는 3초 지나야 다시 센다`() {
        val s = start().feed(listOf(0.0, 9.0, 18.0, 27.0, 36.0, 45.0))
        assertEquals(2, s.trip!!.events.count { it.type == DriveEventType.HARD_ACCEL })
    }

    @Test
    fun `점수 = 0점7 안전 + 0점3 연비, 이벤트는 10km 당 횟수`() {
        // 10km 를 달리며 급감속 2번: 안전 = 100 - 4 × 2 / 1 = 92
        val trip = Trip(
            start = t0,
            distanceKm = 10.0,
            movingMs = 600_000,
            steadyMs = 480_000,
            events = listOf(
                DriveEvent(DriveEventType.HARD_BRAKE, t0, 60, 40),
                DriveEvent(DriveEventType.HARD_BRAKE, t0.plusMinutes(3), 50, 30),
            ),
        )
        val s = summarize(trip, t0.plusMinutes(10))
        assertEquals(92, s.safety)
        assertEquals(90, s.eco) // 50 + 50 × 0.8
        assertEquals(91, s.score) // 0.7 × 92 + 0.3 × 90 = 91.4
    }

    @Test
    fun `5km 보다 짧은 주행은 5km 로 보고 계산한다`() {
        val trip = Trip(start = t0, distanceKm = 0.2, movingMs = 60_000, steadyMs = 60_000, events = listOf(DriveEvent(DriveEventType.HARD_BRAKE, t0, 30, 10)))
        assertEquals(92, summarize(trip, t0).safety) // 100 - 4 × 1 / 0.5
    }

    @Test
    fun `거의 움직이지 않은 주행은 리포트를 내지 않는다`() {
        val s = park(start().feed(listOf(0.0, 0.0, 2.0, 0.0)), 10)
        assertNull(s.finished)
        assertTrue(s.data.trips.isEmpty())
    }

    @Test
    fun `하차 멘트는 기획서 예시 형식이다`() {
        val trip = Trip(
            start = t0, distanceKm = 10.0, movingMs = 600_000, steadyMs = 420_000,
            events = listOf(DriveEvent(DriveEventType.HARD_BRAKE, t0, 60, 40), DriveEvent(DriveEventType.HARD_BRAKE, t0, 50, 30)),
        )
        val s = summarize(trip, t0)
        assertEquals("오늘은 ${s.score}점이에요. 급감속 두 번이 가장 아쉬웠어요.", s.message(previous = null))
        val prev = s.copy(score = s.score - 3)
        assertTrue(s.message(prev).endsWith("지난번보다 3점 올랐어요."))
    }

    @Test
    fun `깎인 게 없으면 부드럽게 달렸다고 말한다`() {
        val s = summarize(Trip(start = t0, distanceKm = 5.0, movingMs = 300_000, steadyMs = 300_000), t0)
        assertEquals(100, s.score)
        assertEquals("오늘은 100점이에요. 급한 조작 없이 부드럽게 달렸어요.", s.message(null))
    }

    @Test
    fun `주행 중에는 점수를 숨기고 기록 중 문장만 준다`() {
        val r = buildDriveReport(start().feed(cruise(120)), t0.plusMinutes(2))
        assertTrue(r.driving)
        assertEquals("주행 기록 중 · 2.0km · 2분", r.drivingText)
    }

    @Test
    fun `신호가 5초 넘게 끊기면 그 구간은 거리에 넣지 않는다`() {
        val s = start().onSpeed(60.0, 0, t0).onSpeed(60.0, 60_000, t0.plusMinutes(1))
        assertEquals(0.0, s.trip!!.distanceKm, 0.001)
    }

    @Test
    fun `가장 크게 깎인 장면은 가중치 × 횟수가 큰 것`() {
        val trip = Trip(
            start = t0, distanceKm = 10.0, movingMs = 1, steadyMs = 0,
            events = listOf(
                DriveEvent(DriveEventType.HARD_ACCEL, t0, 0, 20),
                DriveEvent(DriveEventType.HARD_BRAKE, t0.plusMinutes(1), 50, 30),
            ),
        )
        val w = summarize(trip, t0).worstScene()!!
        assertEquals("급감속", w.label)
        assertEquals("1회", w.amount)
        assertEquals(4, w.minus)
    }

    @Test
    fun `점수 창을 닫지 않고 다시 출발하면 주행 중에는 점수를 숨긴다`() {
        val parked = park(start().feed(List(11) { it * 6.0 } + cruise(120)))
        assertNotNull(parked.finished)
        assertNotNull(buildDriveReport(parked, t0.plusMinutes(10)).finished)

        val again = parked.onGear(Reading.Value(Gear.DRIVE), t0.plusMinutes(11))
        assertNull(again.finished)
        val r = buildDriveReport(again, t0.plusMinutes(11))
        assertTrue(r.driving)
        assertNull(r.finished)
        assertNull(r.finishedMessage)
        assertNotNull(r.last) // 지난 기록은 남아 있고, 정차하면 리포트 탭에서 다시 본다
    }

    // ---- 급조향 ----

    @Test
    fun `진행 방향 빠르기는 차속과 운전대 각도로 가늠한다`() {
        assertEquals(12.8, headingRateDegPerSec(60.0, 30.0), 0.1)
        assertEquals(6.4, headingRateDegPerSec(60.0, 15.0), 0.1)
        assertEquals(0.0, headingRateDegPerSec(0.0, 90.0), 0.001)
    }

    @Test
    fun `60km_h 에서 운전대를 30도 넘게 꺾으면 급조향 한 번, 꺾은 채로는 더 세지 않는다`() {
        var s = start().feed(cruise(10))
        s = s.onSteering(-40.0, t0.plusSeconds(10)).feed(cruise(5), fromMs = 10_000)
        assertEquals(1, s.trip!!.events.count { it.type == DriveEventType.HARD_STEER })
        assertEquals(60, s.trip!!.events.first { it.type == DriveEventType.HARD_STEER }.fromKmh)
        // 운전대를 풀었다가 다시 꺾으면 또 센다
        s = s.onSteering(0.0, t0.plusSeconds(16)).onSteering(40.0, t0.plusSeconds(18))
        assertEquals(2, s.trip!!.events.count { it.type == DriveEventType.HARD_STEER })
    }

    @Test
    fun `완만한 조향이나 느린 회전은 급조향이 아니다`() {
        val gentle = start().feed(cruise(5)).onSteering(15.0, t0.plusSeconds(5))
        assertTrue(gentle.trip!!.events.none { it.type == DriveEventType.HARD_STEER })
        // 20km/h 로 교차로를 크게 도는 것(30km/h 아래)
        val slow = start().feed(List(5) { 20.0 }).onSteering(200.0, t0.plusSeconds(5)).feed(List(5) { 20.0 }, fromMs = 5_000)
        assertTrue(slow.trip!!.events.none { it.type == DriveEventType.HARD_STEER })
    }

    @Test
    fun `운전대를 꺾은 채로 30km_h 를 넘기면 그때 센다`() {
        val s = start().onSteering(40.0, t0).feed(listOf(10.0, 18.0, 25.0, 32.0, 40.0))
        assertEquals(1, s.trip!!.events.count { it.type == DriveEventType.HARD_STEER })
    }

    // ---- 안전벨트 ----

    @Test
    fun `움직이는 동안 켜진 안전벨트 경고 시간만 모은다`() {
        var s = start().onBeltWarning(true).feed(List(31) { 0.0 }) // 정차 중 30초: 세지 않는다
        assertEquals(0L, s.trip!!.unbeltedMs)
        s = s.feed(List(10) { (it + 1) * 6.0 } + cruise(110), fromMs = 31_000) // 부드럽게 출발해 움직인 120초
        assertEquals(120_000L, s.trip!!.unbeltedMs)
        s = s.onBeltWarning(false).feed(cruise(60), fromMs = 151_000)
        assertEquals(120_000L, s.trip!!.unbeltedMs)
        val f = park(s).finished!!
        assertEquals(120, f.unbeltedSec)
        assertEquals(90, f.safety) // 2분 × 5점
    }

    @Test
    fun `안전벨트 감점은 짧은 경고는 넘기고 1분마다 5점, 30점까지`() {
        assertEquals(0.0, beltMinus(9), 0.0)
        assertEquals(5.0, beltMinus(10), 0.0)
        assertEquals(10.0, beltMinus(61), 0.0)
        assertEquals(30.0, beltMinus(20 * 60), 0.0)
    }

    @Test
    fun `안전벨트 감점이 가장 크면 그 장면을 말한다`() {
        val trip = Trip(
            start = t0, distanceKm = 10.0, movingMs = 600_000, steadyMs = 600_000,
            events = listOf(DriveEvent(DriveEventType.HARD_BRAKE, t0, 50, 30)),
            unbeltedMs = 150_000,
        )
        val f = summarize(trip, t0.plusMinutes(10))
        assertEquals(81, f.safety) // 100 - 4 - 15
        val w = f.worstScene()!!
        assertEquals("안전벨트 미착용", w.label)
        assertEquals("2분 30초", w.amount)
        assertEquals(15, w.minus) // 시작한 1분마다: 3분 × 5점
        assertNull(w.firstAt)
        assertEquals("오늘은 87점이에요. 안전벨트를 매지 않은 2분 30초가 가장 아쉬웠어요.", f.message(null))
    }

    @Test
    fun `시간은 분 초로 쓴다`() {
        assertEquals("40초", durationText(40))
        assertEquals("1분 19초", durationText(79))
        assertEquals("3분", durationText(180))
    }
}
