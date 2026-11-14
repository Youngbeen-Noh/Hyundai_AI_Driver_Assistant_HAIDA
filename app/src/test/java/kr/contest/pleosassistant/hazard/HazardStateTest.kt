package kr.contest.pleosassistant.hazard

import kr.contest.pleosassistant.memory.Gear
import kr.contest.pleosassistant.status.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class HazardStateTest {

    private val home = GeoPoint(37.5665, 126.9780)

    /** home 에서 북쪽으로 m 미터 떨어진 지점 (위도 1도 ≈ 111,195m) */
    private fun north(m: Double) = GeoPoint(home.lat + m / 111_195.0, home.lon)

    private val morning = LocalDateTime.of(2026, 11, 10, 8, 0)
    private val night = LocalDateTime.of(2026, 11, 10, 22, 0)

    private fun parkedAt(point: GeoPoint) = HazardState()
        .onGear(Reading.Value(Gear.PARK))
        .onPosition(Reading.Value(point), morning)

    /** home 에 반경 200m '아파트 정문'을 저장하고, 멀리 떨어져 출발한 상태 */
    private fun withZone(schedule: AlertSchedule = AlertSchedule.ALWAYS): HazardState =
        parkedAt(home)
            .addZone("아파트 정문", 200, schedule, morning)
            .onGear(Reading.Value(Gear.DRIVE))
            .onPosition(Reading.Value(north(1_000.0)), morning)
            .clearNotice()

    @Test
    fun `거리 계산은 하버사인으로 1km 안팎이 맞다`() {
        assertEquals(1_000.0, home.distanceTo(north(1_000.0)), 2.0)
    }

    @Test
    fun `정차 중 지금 위치를 저장하면 구간이 생기고 바로 경고하지 않는다`() {
        val s = parkedAt(home).addZone("아파트 정문", 200, AlertSchedule.ALWAYS, morning)
        assertEquals(1, s.data.zones.size)
        assertEquals(home, s.data.zones[0].center)
        assertNull(s.alert)
        assertEquals("아파트 정문을 저장했어요", s.notice)
    }

    @Test
    fun `이름을 비우면 위험 구간 번호로 저장한다`() {
        val s = parkedAt(home).addZone("  ", 100, AlertSchedule.ALWAYS, morning)
        assertEquals("위험 구간 1", s.data.zones[0].name)
    }

    @Test
    fun `주행 중에는 저장하지 않는다`() {
        val s = parkedAt(home).onGear(Reading.Value(Gear.DRIVE)).addZone("정문", 200, AlertSchedule.ALWAYS, morning)
        assertTrue(s.data.zones.isEmpty())
        assertEquals("정차(P)하면 구간을 저장할 수 있어요", s.notice)
    }

    @Test
    fun `위치를 모르면 저장하지 않는다`() {
        val s = HazardState().onGear(Reading.Value(Gear.PARK)).addZone("정문", 200, AlertSchedule.ALWAYS, morning)
        assertTrue(s.data.zones.isEmpty())
        assertEquals("현재 위치를 아직 몰라요", s.notice)
    }

    @Test
    fun `반경 안으로 들어서면 기획서 문장으로 경고한다`() {
        val s = withZone().onPosition(Reading.Value(north(150.0)), morning)
        assertNotNull(s.alert)
        val alert = s.alert!!
        assertEquals("200m 앞 아파트 정문이에요. 보행자 조심하세요.", alert.message)
        assertFalse(alert.repeat)
        assertTrue(s.inside.contains(1L))
    }

    @Test
    fun `정차 중에 구간 안에서 앱을 켜면 울리지 않고, 출발해 나갔다 들어오면 울린다`() {
        val saved = withZone().onGear(Reading.Value(Gear.PARK)).onPosition(Reading.Value(north(800.0)), morning)
        val restarted = HazardState(data = saved.data).onGear(Reading.Value(Gear.PARK)).onPosition(Reading.Value(north(50.0)), morning)
        assertNull(restarted.alert)
        val driven = restarted.onGear(Reading.Value(Gear.DRIVE))
            .onPosition(Reading.Value(north(900.0)), morning)
            .onPosition(Reading.Value(north(50.0)), morning)
        assertNotNull(driven.alert)
    }

    @Test
    fun `반경 밖이면 경고하지 않는다`() {
        val s = withZone().onPosition(Reading.Value(north(260.0)), morning)
        assertNull(s.alert)
    }

    @Test
    fun `경계에서 흔들려도 한 번만 경고한다`() {
        val s = withZone()
            .onPosition(Reading.Value(north(190.0)), morning).dismissAlert()
            .onPosition(Reading.Value(north(215.0)), morning) // 반경 + 30m 안이라 아직 '안'
            .onPosition(Reading.Value(north(195.0)), morning)
        assertNull(s.alert)
        assertEquals(1, s.data.passes[1L]?.count)
    }

    @Test
    fun `같은 날 두 번째로 지나면 짧게 알린다`() {
        val s = withZone()
            .onPosition(Reading.Value(north(100.0)), morning).dismissAlert()
            .onPosition(Reading.Value(north(800.0)), morning)
            .onPosition(Reading.Value(north(100.0)), morning.plusHours(1))
        val alert = s.alert!!
        assertTrue(alert.repeat)
        assertEquals("다시 아파트 정문이에요.", alert.message)
        assertEquals(2, s.data.passes[1L]?.count)
    }

    @Test
    fun `다음 날에는 다시 처음처럼 알린다`() {
        val s = withZone()
            .onPosition(Reading.Value(north(100.0)), morning).dismissAlert()
            .onPosition(Reading.Value(north(800.0)), morning)
            .onPosition(Reading.Value(north(100.0)), morning.plusDays(1))
        assertFalse(s.alert!!.repeat)
    }

    @Test
    fun `알림을 끈 구간은 들어가도 알리지 않는다`() {
        val s = withZone()
            .onGear(Reading.Value(Gear.PARK)).setEnabled(1L, false).onGear(Reading.Value(Gear.DRIVE))
            .onPosition(Reading.Value(north(50.0)), morning)
        assertNull(s.alert)
    }

    @Test
    fun `등하교 시간만 고른 구간은 밤에는 알리지 않는다`() {
        val s = withZone(AlertSchedule.SCHOOL_HOURS).onPosition(Reading.Value(north(50.0)), night)
        assertNull(s.alert)
        val day = withZone(AlertSchedule.SCHOOL_HOURS).onPosition(Reading.Value(north(50.0)), morning)
        assertNotNull(day.alert)
    }

    @Test
    fun `주행 중에는 구간을 고치거나 지울 수 없다`() {
        val s = withZone().setRadius(1L, 300).removeZone(1L)
        assertEquals(200, s.data.zones.single().radiusM)
    }

    @Test
    fun `변속 단을 못 읽는 차는 편집을 막지 않는다`() {
        assertTrue(HazardState(gear = Reading.Unsupported).canEdit)
        assertFalse(HazardState(gear = Reading.Value(Gear.DRIVE)).canEdit)
    }

    @Test
    fun `받침에 따라 조사를 고른다`() {
        assertEquals("이에요", "정문".copula())
        assertEquals("예요", "학교".copula())
        assertEquals("를", "학원 앞 골목길 학교".objectParticle())
        assertEquals("을", "위험 구간 1".objectParticle())
        assertEquals("이에요", "위험 구간 1".copula())
        assertEquals("예요", "위험 구간 2".copula())
    }

    @Test
    fun `화면 요약은 구간 안이면 그 구간을 말한다`() {
        val s = withZone().onPosition(Reading.Value(north(100.0)), morning)
        val r = buildHazardReport(s, morning)
        assertEquals("아파트 정문 구간 안이에요. 천천히 지나가요", r.summary)
        assertEquals("지금 100m", r.zones.single().distanceText)
    }

    // ---- 음성 안내 ----

    @Test
    fun `들어서면 경고 문장을 한 번만 말한다`() {
        val before = withZone()
        val entered = before.onPosition(Reading.Value(north(150.0)), morning)
        assertEquals("200m 앞 아파트 정문이에요. 보행자 조심하세요.", entered.speechSince(before))
        // 경고 창이 떠 있는 채로 구간 안에서 움직이면 다시 말하지 않는다
        val moved = entered.onPosition(Reading.Value(north(80.0)), morning)
        assertNull(moved.speechSince(entered))
    }

    @Test
    fun `경고 창을 닫지 않고 같은 날 다시 지나도 짧게 다시 말한다`() {
        val first = withZone().onPosition(Reading.Value(north(100.0)), morning)
        val out = first.onPosition(Reading.Value(north(800.0)), morning)
        assertNull(out.speechSince(first))
        val again = out.onPosition(Reading.Value(north(100.0)), morning.plusHours(1))
        assertEquals("다시 아파트 정문이에요.", again.speechSince(out))
    }

    @Test
    fun `정차 중 · 알림 끈 구간 · 반경 밖에서는 말하지 않는다`() {
        val parked = withZone().onGear(Reading.Value(Gear.PARK))
        assertNull(parked.onPosition(Reading.Value(north(50.0)), morning).speechSince(parked))

        val off = withZone().onGear(Reading.Value(Gear.PARK)).setEnabled(1L, false).onGear(Reading.Value(Gear.DRIVE))
        assertNull(off.onPosition(Reading.Value(north(50.0)), morning).speechSince(off))

        val driving = withZone()
        assertNull(driving.onPosition(Reading.Value(north(260.0)), morning).speechSince(driving))
    }

    @Test
    fun `경고 창을 닫거나 구간을 지워도 말하지 않는다`() {
        val entered = withZone().onPosition(Reading.Value(north(100.0)), morning)
        assertNull(entered.dismissAlert().speechSince(entered))
        val parked = entered.onGear(Reading.Value(Gear.PARK))
        assertNull(parked.removeZone(1L).speechSince(parked))
    }

    @Test
    fun `구간이 없으면 저장 방법을 안내한다`() {
        val r = buildHazardReport(HazardState(), morning)
        assertTrue(r.summary.startsWith("정차 중에 지금 위치를"))
        assertFalse(r.hasPosition)
    }
}
