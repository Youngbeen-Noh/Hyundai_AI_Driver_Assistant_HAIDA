package kr.contest.pleosassistant.briefing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefingTest {

    private val source = DemoBriefings
    private val three = BriefingSettings(selected = listOf(Category.GAME, Category.IT, Category.TRAVEL))

    @Test
    fun `목적지 없이 5분을 달려야 시작한다`() {
        assertFalse(shouldStartBriefing(enabled = true, hasCategories = true, drivingMinutes = 4, hasDestination = false))
        assertTrue(shouldStartBriefing(enabled = true, hasCategories = true, drivingMinutes = 5, hasDestination = false))
        assertFalse(shouldStartBriefing(enabled = true, hasCategories = true, drivingMinutes = 30, hasDestination = true))
        assertFalse(shouldStartBriefing(enabled = true, hasCategories = false, drivingMinutes = 30, hasDestination = false))
        assertFalse(shouldStartBriefing(enabled = false, hasCategories = true, drivingMinutes = 30, hasDestination = false))
    }

    @Test
    fun `분야를 돌아가며 재생해 같은 분야가 연달아 나오지 않는다`() {
        var s = BriefingState(three).start(source)
        val order = mutableListOf(s.playback!!.briefing.category)
        repeat(5) {
            s = s.nextBriefing(source)
            order += s.playback!!.briefing.category
        }
        assertEquals(
            listOf(Category.GAME, Category.IT, Category.TRAVEL, Category.GAME, Category.IT, Category.TRAVEL),
            order,
        )
        assertEquals(QUEUE_SIZE, s.queue.size)
    }

    @Test
    fun `분야를 빼면 기본은 큐에서도 바로 지운다`() {
        val s = BriefingState(three).start(source)
        assertEquals(listOf(Category.IT, Category.TRAVEL), s.queue.map { it.category })
        val off = s.toggleCategory(Category.IT, source)
        assertTrue(off.queue.none { it.category == Category.IT })
        assertEquals(QUEUE_SIZE, off.queue.size)
        // 지금 듣는 것은 끊지 않는다
        assertEquals(s.playback, off.playback)
    }

    @Test
    fun `이미 준비된 브리핑은 듣기를 켜면 큐에 남기고 새로 만들지만 않는다`() {
        val s = BriefingState(three.copy(keepPrepared = true)).start(source)
        val off = s.toggleCategory(Category.IT, source)
        assertEquals(listOf(Category.IT, Category.TRAVEL), off.queue.map { it.category })
        var after = off
        repeat(4) { after = after.nextBriefing(source) }
        assertTrue(after.queue.none { it.category == Category.IT })
        assertTrue(after.playback!!.briefing.category != Category.IT)
    }

    @Test
    fun `문장 → 소주제 → 다음 브리핑 순으로 넘어가고 여는 말에 분야를 말한다`() {
        var s = BriefingState(three).start(source)
        val first = s.playback!!
        assertEquals("게임 브리핑이에요. 소식 3개를 들려 드릴게요.", first.currentLine)
        // 첫 소주제: 여는 말 + 대본
        repeat(first.script.size) { s = s.advanceLine(source) }
        assertEquals(1, s.playback!!.sub)
        assertEquals(0, s.playback!!.line)
        // 남은 소주제를 다 읽으면 다음 분야
        while (s.playback!!.briefing.category == Category.GAME) s = s.advanceLine(source)
        assertEquals(Category.IT, s.playback!!.briefing.category)
    }

    @Test
    fun `소주제 첫 문장에 언론사 이름이 들어간다`() {
        DemoBriefings.briefing(Category.IT, 0)!!.subtopics.forEach { assertTrue(it.lines.first().contains(it.press)) }
    }

    @Test
    fun `더 자세히는 관련 기사를 지금 문장 바로 뒤에 한 번만 넣는다`() {
        var s = BriefingState(BriefingSettings(listOf(Category.IT))).start(source)
        s = s.advanceLine(source) // 첫 소주제 첫 문장
        val (more, added) = s.more()
        assertTrue(added)
        val p = more.playback!!
        assertEquals(p.subtopic.related.first(), p.script[p.line + 1])
        assertFalse(more.more().second)
    }

    @Test
    fun `용어 설명은 지금 소주제의 용어를 풀어 주고 없으면 없다고 말한다`() {
        var s = BriefingState(BriefingSettings(listOf(Category.IT))).start(source)
        assertEquals(BriefingState.NO_TERM, s.termText())
        while (s.playback!!.sub == 0) s = s.advanceLine(source)
        assertTrue(s.termText().startsWith("온디바이스 AI는 서버를 거치지 않고"))
    }

    @Test
    fun `대본은 글자 수 상한 안에서 소주제를 담는다`() {
        val long = Subtopic("t", "p", "", listOf("가".repeat(500)))
        val b = Briefing("x", Category.IT, listOf(long, long, long)).fitToLimit()
        assertEquals(1, b.subtopics.size)
        DemoBriefings.briefing(Category.IT, 0)!!.let { assertEquals(it, it.fitToLimit()) }
        assertTrue(speakSeconds("가".repeat(50)) in 10..12)
    }

    @Test
    fun `설정을 저장하고 다시 읽는다`() {
        val prefs = kr.contest.pleosassistant.storage.BriefingPrefs(
            BriefingSettings(listOf(Category.SPORTS, Category.IT), keepPrepared = true),
            autoStart = false,
        )
        val json = kr.contest.pleosassistant.storage.encodeBriefingPrefs(prefs)
        assertEquals(prefs, kr.contest.pleosassistant.storage.decodeBriefingPrefs(json))
    }

    @Test
    fun `분야를 모두 빼면 큐가 비고 다음 브리핑에서 멈춘다`() {
        var s = BriefingState(BriefingSettings(listOf(Category.IT))).start(source)
        s = s.toggleCategory(Category.IT, source)
        assertTrue(s.queue.isEmpty())
        assertNull(s.nextBriefing(source).playback)
    }
}
