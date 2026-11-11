package kr.contest.pleosassistant.memory

import kr.contest.pleosassistant.status.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MemoryStateTest {

    /** 2026-11-10 은 화요일 */
    private val tue0830 = LocalDateTime.of(2026, 11, 10, 8, 30)

    private fun ready() = Seat.entries.fold(MemoryState()) { s, seat -> s.onSeat(seat, Reading.Value(false), tue0830) }

    private fun MemoryState.sit(seat: Seat, at: LocalDateTime) = onSeat(seat, Reading.Value(true), at)
    private fun MemoryState.leave(seat: Seat) = onSeat(seat, Reading.Value(false), tue0830)

    // ---- 동승자 ----

    @Test
    fun `처음 보는 사람이 앉으면 이름을 묻고, 답하면 기억한다`() {
        val asked = ready().sit(Seat.PASSENGER, tue0830)
        assertEquals(SeatState.Asking(tue0830, emptyList()), asked.seats[Seat.PASSENGER])

        val named = asked.namePassenger(Seat.PASSENGER, " 민수님 ")
        assertEquals(SeatState.Known(tue0830, "민수"), named.seats[Seat.PASSENGER])
        assertEquals(listOf(Ride("민수", Seat.PASSENGER, tue0830)), named.data.rides)
    }

    @Test
    fun `다음 주 같은 요일 비슷한 시간에 앉으면 확인만 한다`() {
        val nextTue0910 = tue0830.plusWeeks(1).withHour(9).withMinute(10)
        val state = ready().sit(Seat.PASSENGER, tue0830).namePassenger(Seat.PASSENGER, "민수")
            .leave(Seat.PASSENGER)
            .sit(Seat.PASSENGER, nextTue0910)
        assertEquals(SeatState.Confirming(nextTue0910, "민수"), state.seats[Seat.PASSENGER])

        val confirmed = state.confirmPassenger(Seat.PASSENGER, isRight = true)
        assertEquals(SeatState.Known(nextTue0910, "민수"), confirmed.seats[Seat.PASSENGER])
        assertEquals(2, confirmed.data.rides.size)
    }

    @Test
    fun `요일이 다르거나 시간이 1시간 넘게 다르면 다시 묻고, 전에 탄 사람을 추천한다`() {
        val known = ready().sit(Seat.PASSENGER, tue0830).namePassenger(Seat.PASSENGER, "민수").leave(Seat.PASSENGER)

        val wednesday = tue0830.plusDays(1)
        assertEquals(SeatState.Asking(wednesday, listOf("민수")), known.sit(Seat.PASSENGER, wednesday).seats[Seat.PASSENGER])

        val tue0931 = tue0830.plusMinutes(61)
        assertEquals(SeatState.Asking(tue0931, listOf("민수")), known.sit(Seat.PASSENGER, tue0931).seats[Seat.PASSENGER])
    }

    @Test
    fun `아니라고 하면 그 사람을 빼고 이름을 묻는다`() {
        val state = ready().sit(Seat.PASSENGER, tue0830).namePassenger(Seat.PASSENGER, "민수")
            .leave(Seat.PASSENGER)
            .sit(Seat.PASSENGER, tue0830.plusMinutes(10))
            .confirmPassenger(Seat.PASSENGER, isRight = false)
        assertEquals(SeatState.Asking(tue0830.plusMinutes(10), emptyList()), state.seats[Seat.PASSENGER])

        val named = state.namePassenger(Seat.PASSENGER, "지은")
        assertEquals(SeatState.Known(tue0830.plusMinutes(10), "지은"), named.seats[Seat.PASSENGER])
    }

    @Test
    fun `같은 시간대에 여러 명이 탔으면 더 자주 탄 사람을 짐작한다`() {
        val rides = listOf(
            Ride("지은", Seat.PASSENGER, tue0830.minusWeeks(1)),
            Ride("민수", Seat.PASSENGER, tue0830.minusWeeks(2)),
            Ride("민수", Seat.PASSENGER, tue0830.minusWeeks(3)),
        )
        assertEquals("민수", guessPassenger(rides, Seat.PASSENGER, tue0830))
        assertEquals("지은", guessPassenger(rides, Seat.PASSENGER, tue0830, exclude = setOf("민수")))
        assertNull(guessPassenger(rides, Seat.REAR_LEFT, tue0830))
    }

    @Test
    fun `다른 좌석에 이미 앉은 사람은 짐작하지 않는다`() {
        val rides = listOf(Ride("민수", Seat.REAR_LEFT, tue0830.minusWeeks(1)))
        val state = ready().copy(data = MemoryData(rides = rides))
            .sit(Seat.PASSENGER, tue0830).namePassenger(Seat.PASSENGER, "민수")
            .sit(Seat.REAR_LEFT, tue0830)
        assertEquals(SeatState.Asking(tue0830, emptyList()), state.seats[Seat.REAR_LEFT])
    }

    @Test
    fun `앉아 있는 동안 같은 값이 다시 와도 질문을 처음으로 되돌리지 않는다`() {
        val named = ready().sit(Seat.PASSENGER, tue0830).namePassenger(Seat.PASSENGER, "민수")
        assertEquals(named, named.sit(Seat.PASSENGER, tue0830.plusMinutes(5)))
    }

    @Test
    fun `건너뛰면 이름 없이 두고, 나중에 다시 물을 수 있다`() {
        val skipped = ready().sit(Seat.PASSENGER, tue0830).skipPassenger(Seat.PASSENGER)
        assertEquals(SeatState.Unnamed(tue0830), skipped.seats[Seat.PASSENGER])
        assertTrue(skipped.data.rides.isEmpty())
        assertEquals(SeatState.Asking(tue0830, emptyList()), skipped.askAgain(Seat.PASSENGER).seats[Seat.PASSENGER])
    }

    @Test
    fun `빈 이름이나 너무 긴 이름은 저장하지 않는다`() {
        val asked = ready().sit(Seat.PASSENGER, tue0830)
        assertEquals(asked, asked.namePassenger(Seat.PASSENGER, "  님 "))
        assertEquals(asked, asked.namePassenger(Seat.PASSENGER, "가".repeat(MemoryRules.MAX_NAME_LENGTH + 1)))
    }

    @Test
    fun `좌석 센서가 없으면 지원 안 함으로 둔다`() {
        val state = MemoryState().onSeat(Seat.REAR_RIGHT, Reading.Unsupported, tue0830)
        assertEquals(SeatState.Unsupported, state.seats[Seat.REAR_RIGHT])
    }

    // ---- 소지품 · 하차 알림 ----

    private fun MemoryState.drive() = onGear(Reading.Value(Gear.DRIVE), tue0830)
    private fun MemoryState.park() = onGear(Reading.Value(Gear.PARK), tue0830)

    @Test
    fun `물건을 기억하고, 주행하다 P단에 놓으면 알린다`() {
        val state = ready().park()
            .addBelonging("트렁크에 우산 넣었어", tue0830)
        assertEquals(listOf(Belonging(1, "우산", "트렁크", null, tue0830)), state.data.belongings)
        assertNull("처음부터 P 인 것은 하차가 아니다", state.arrival)

        val arrived = state.drive().park()
        assertEquals(listOf(1L), arrived.arrival?.items?.map { it.belongingId })
    }

    @Test
    fun `챙길 날이 내일인 물건은 오늘 하차 때 알리지 않는다`() {
        val state = ready().drive()
            .addBelonging("내일 반납할 책 뒷자리에 뒀어", tue0830)
            .park()
        assertNull(state.arrival)

        val tomorrow = tue0830.plusDays(1)
        val nextDay = state.onGear(Reading.Value(Gear.DRIVE), tomorrow).onGear(Reading.Value(Gear.PARK), tomorrow)
        assertEquals(listOf(1L), nextDay.arrival?.items?.map { it.belongingId })
    }

    @Test
    fun `다 챙겼어요 하면 알린 물건을 지우고, 나중에 하면 남긴다`() {
        val arrived = ready().drive()
            .addBelonging("트렁크에 우산 넣었어", tue0830)
            .addBelonging("내일 반납할 책 뒷자리에 뒀어", tue0830)
            .park()

        val collected = arrived.closeArrival(collected = true)
        assertNull(collected.arrival)
        assertEquals(listOf("반납할 책"), collected.data.belongings.map { it.item })

        val later = arrived.closeArrival(collected = false)
        assertNull(later.arrival)
        assertEquals(2, later.data.belongings.size)
    }

    @Test
    fun `하차 알림에 답하지 않고 출발하면 나중에로 닫고, 다음 하차 때 다시 알린다`() {
        val arrived = ready().drive()
            .addBelonging("트렁크에 우산 넣었어", tue0830)
            .addBelonging("가방 뒷자리에 뒀어", tue0830)
            .park()
            .collect(1)
        assertEquals(listOf(1L, 2L), arrived.arrival?.items?.map { it.belongingId })

        val departed = arrived.drive()
        assertNull("주행 중에는 체크 목록을 띄우지 않는다", departed.arrival)
        val report = buildMemoryReport(departed, tue0830.toLocalDate())
        assertNull(report.checklist)
        assertFalse(report.panel?.checklist == true)
        assertEquals("챙긴 우산은 바로 지웠고, 가방은 남긴다", listOf("가방"), departed.data.belongings.map { it.item })

        assertEquals(listOf(2L), departed.park().arrival?.items?.map { it.belongingId })
        assertNull("후진으로 나가도 닫는다", arrived.onGear(Reading.Value(Gear.REVERSE), tue0830).arrival)
    }

    @Test
    fun `알아듣지 못한 문장은 저장하지 않고 안내한다`() {
        val state = ready().addBelonging("트렁크에 넣었어", tue0830)
        assertTrue(state.data.belongings.isEmpty())
        assertEquals(MemoryMessages.NOT_UNDERSTOOD, state.inputError)
        assertNull(state.addBelonging("트렁크에 우산 넣었어", tue0830).inputError)
    }

    @Test
    fun `기어 값을 잠깐 못 읽어도 마지막 단을 기억해 하차를 놓치지 않는다`() {
        val state = ready().addBelonging("우산 챙겨", tue0830).drive()
            .onGear(Reading.Unavailable, tue0830)
            .park()
        assertEquals(listOf(1L), state.arrival?.items?.map { it.belongingId })
    }

    // ---- 다 잊어줘 ----

    @Test
    fun `다 잊어줘 는 모두 지우고, 앉아 있는 사람에게 처음부터 다시 묻는다`() {
        val state = ready().sit(Seat.PASSENGER, tue0830).namePassenger(Seat.PASSENGER, "민수")
            .addBelonging("트렁크에 우산 넣었어", tue0830)
            .forgetAll()
        assertEquals(MemoryData(), state.data)
        assertEquals(SeatState.Asking(tue0830, emptyList()), state.seats[Seat.PASSENGER])
        assertEquals(SeatState.Empty, state.seats[Seat.REAR_LEFT])
    }

    @Test
    fun `P 가 아니면 주행 중이고, 단을 모르면 정차로 본다`() {
        assertTrue(ready().onGear(Reading.Value(Gear.DRIVE), tue0830).driving)
        assertTrue(ready().onGear(Reading.Value(Gear.REVERSE), tue0830).driving)
        assertFalse(ready().onGear(Reading.Value(Gear.PARK), tue0830).driving)
        assertFalse(ready().driving)
        assertFalse(ready().onGear(Reading.Unsupported, tue0830).driving)
        // 달리다가 값을 잠깐 못 읽어도 주행 중으로 둔다
        assertTrue(ready().onGear(Reading.Value(Gear.DRIVE), tue0830).onGear(Reading.Unavailable, tue0830).driving)
    }
}
