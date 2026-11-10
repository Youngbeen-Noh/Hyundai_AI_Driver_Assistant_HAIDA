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
        assertEquals(DriveEventType.HARD_BRAKE, w.type)
        assertEquals(4, w.minus)
    }
}
