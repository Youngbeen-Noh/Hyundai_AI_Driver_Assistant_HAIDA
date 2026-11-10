package kr.contest.pleosassistant.memory

import kr.contest.pleosassistant.status.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MemoryReportTest {

    private val now = LocalDateTime.of(2026, 11, 10, 8, 30)
    private val today = now.toLocalDate()

    private fun empty() = Seat.entries.fold(MemoryState()) { s, seat -> s.onSeat(seat, Reading.Value(false), now) }
    private fun MemoryState.report() = buildMemoryReport(this, today)
    private fun MemoryState.withMinsu() = onSeat(Seat.PASSENGER, Reading.Value(true), now).namePassenger(Seat.PASSENGER, "민수")

    @Test
    fun `아무것도 없으면 없다고 말한다`() {
        assertEquals(MemoryMessages.NOTHING, empty().report().summary)
    }

    @Test
    fun `계획서 예시 멘트 - 동승자 인사 뒤에 챙길 물건`() {
        val state = empty().withMinsu().addBelonging("오늘 택배 트렁크에 실었어", now)
        assertEquals("민수님 타셨네요. 트렁크에 둔 택배도 잊지 마세요.", state.report().summary)
    }

    @Test
    fun `동승자만, 물건만, 물건 여러 개`() {
        assertEquals("민수님 타셨네요.", empty().withMinsu().report().summary)

        val two = empty().addBelonging("트렁크에 우산 넣었어", now).addBelonging("조수석에 지갑 뒀어", now)
        assertEquals("트렁크에 둔 우산 외 1개 잊지 마세요.", two.report().summary)
    }

    @Test
    fun `나중에 챙길 물건만 있으면 개수만 말한다`() {
        val state = empty().addBelonging("내일 반납할 책 뒷자리에 뒀어", now)
        assertEquals("물건 1개를 기억하고 있어요.", state.report().summary)
    }

    @Test
    fun `좌석 카드와 질문`() {
        val asked = empty().onSeat(Seat.PASSENGER, Reading.Value(true), now)
        val report = asked.report()
        assertEquals(SeatCard(Seat.PASSENGER, "누구세요?", SeatTone.ASKING), report.seats.first())
        assertEquals(Question.AskName(Seat.PASSENGER, emptyList(), "조수석에 누가 타셨어요?"), report.question)

        val known = asked.namePassenger(Seat.PASSENGER, "민수")
        assertEquals(SeatCard(Seat.PASSENGER, "민수님", SeatTone.KNOWN), known.report().seats.first())
        assertNull(known.report().question)

        val again = known.onSeat(Seat.PASSENGER, Reading.Value(false), now)
            .onSeat(Seat.PASSENGER, Reading.Value(true), now.plusMinutes(5))
        assertEquals(Question.Confirm(Seat.PASSENGER, "민수", "오늘도 민수님이죠?"), again.report().question)
    }

    @Test
    fun `하차 알림 문장`() {
        val items = listOf(
            Belonging(1, "우산", "트렁크", null, now),
            Belonging(2, "책", "뒷자리", null, now),
            Belonging(3, "지갑", null, null, now),
        )
        assertEquals("트렁크에 둔 우산 챙기세요.", arrivalMessage(items.take(1)))
        assertEquals("트렁크에 둔 우산, 뒷자리에 둔 책 챙기세요.", arrivalMessage(items.take(2)))
        assertEquals("트렁크에 둔 우산, 뒷자리에 둔 책 외 1개 챙기세요.", arrivalMessage(items))
        assertEquals("지갑 챙기세요.", arrivalMessage(items.drop(2)))
    }

    @Test
    fun `소지품 목록 - 지금 챙길 것이 위, 날짜 표시`() {
        val state = empty()
            .addBelonging("내일 반납할 책 뒷자리에 뒀어", now)
            .addBelonging("트렁크에 우산 넣었어", now)
        val rows = state.report().belongings
        assertEquals(listOf("우산", "반납할 책"), rows.map { it.item })
        assertTrue(rows[0].remindNow)
        assertNull(rows[0].dueLabel)
        assertFalse(rows[1].remindNow)
        assertEquals("내일", rows[1].dueLabel)
    }

    @Test
    fun `P단 신호가 없는 차는 하차 알림이 안 된다고 알린다`() {
        assertFalse(empty().onGear(Reading.Unsupported, now).report().parkAlertAvailable)
        assertTrue(empty().onGear(Reading.Value(Gear.PARK), now).report().parkAlertAvailable)
    }
}
