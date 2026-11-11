package kr.contest.pleosassistant.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BelongingParserTest {

    private val today = LocalDate.of(2026, 11, 10)

    private fun parse(text: String) = parseBelonging(text, today)

    @Test
    fun `계획서 예시 - 트렁크에 우산 넣었어`() {
        assertEquals(ParsedBelonging("우산", "트렁크", null), parse("트렁크에 우산 넣었어"))
    }

    @Test
    fun `계획서 예시 - 내일 반납할 책 뒷자리에 뒀어`() {
        assertEquals(ParsedBelonging("반납할 책", "뒷자리", today.plusDays(1)), parse("내일 반납할 책 뒷자리에 뒀어"))
    }

    @Test
    fun `존댓말과 문장부호, 오늘`() {
        assertEquals(ParsedBelonging("택배", "트렁크", today), parse("오늘 택배 트렁크에 실었어요."))
    }

    @Test
    fun `목적격 조사를 뗀다`() {
        assertEquals(ParsedBelonging("노트북 가방", "조수석", null), parse("노트북 가방을 조수석에 놨어"))
    }

    @Test
    fun `같은 뜻의 위치는 한 이름으로 모은다`() {
        assertEquals("글로브박스", parse("글러브박스에 선글라스 넣어 놨어")?.place)
        assertEquals("뒷자리", parse("뒷좌석에 아이 신발 있어")?.place)
        assertEquals("콘솔", parse("센터콘솔에 주차권 넣었어")?.place)
    }

    @Test
    fun `끝의 동사가 여러 낱말이어도 뺀다`() {
        assertEquals("선글라스", parse("글러브박스에 선글라스 넣어 놨어")?.item)
    }

    @Test
    fun `위치 뒤에 안 이 붙고 날짜에 까지 가 붙어도 알아듣는다`() {
        assertEquals(ParsedBelonging("돌려줄 책", "트렁크", today.plusDays(2)), parse("모레까지 돌려줄 책 트렁크 안에 뒀어"))
    }

    @Test
    fun `위치를 말하지 않으면 위치는 비운다`() {
        assertEquals(ParsedBelonging("우산", null, null), parse("우산 챙겨"))
    }

    @Test
    fun `물건이 없으면 알아듣지 못한 것으로 본다`() {
        assertNull(parse("트렁크에 넣었어"))
        assertNull(parse("   "))
    }

    @Test
    fun `물건 이름의 앞글자가 동사와 비슷해도 지우지 않는다`() {
        assertEquals("실내화", parse("트렁크에 실내화")?.item)
    }
}
