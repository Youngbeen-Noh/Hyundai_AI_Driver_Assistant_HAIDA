package kr.contest.pleosassistant.briefing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerBriefingsTest {

    private val serverJson = """
        {"category":"IT","generatedAt":"2026-11-14T01:00:00Z","subtopics":[
          {"title":"AI 요약 확산","press":"한빛일보","publishedAt":"11월 13일",
           "lines":["한빛일보 소식이에요. AI 요약이 늘어요.","원문도 함께 보여 준대요."],
           "term":{"word":"생성형 AI","meaning":"새 글을 만들어 내는 AI 예요."},
           "related":["관련 기사로는, 메신저에도 들어온대요."]},
          {"title":"보안 업데이트","press":"누리신문","publishedAt":"11월 12일","lines":["누리신문 소식이에요. 업데이트가 나왔어요."],"term":null,"related":[]},
          {"title":"빈 소주제","press":"x","publishedAt":"","lines":[],"term":null,"related":[]}
        ]}
    """.trimIndent()

    @Test
    fun `서버 응답을 브리핑으로 읽고 빈 소주제는 뺀다`() {
        val b = decodeServerBriefing(serverJson, "srv-1")!!
        assertEquals(Category.IT, b.category)
        assertEquals(listOf("AI 요약 확산", "보안 업데이트"), b.subtopics.map { it.title })
        assertEquals(Term("생성형 AI", "새 글을 만들어 내는 AI 예요."), b.subtopics[0].term)
        assertNull(b.subtopics[1].term)
        assertEquals(1, b.subtopics[0].related.size)
    }

    @Test
    fun `분야를 모르거나 소주제가 없거나 JSON 이 아니면 null`() {
        assertNull(decodeServerBriefing("""{"category":"NEWS","subtopics":[]}""", "x"))
        assertNull(decodeServerBriefing("""{"category":"IT","subtopics":[]}""", "x"))
        assertNull(decodeServerBriefing("오류", "x"))
    }

    @Test
    fun `받아 둔 브리핑은 저장했다 다시 읽어도 같다`() {
        val b = decodeServerBriefing(serverJson, "srv-1")!!
        val cache = BriefingCache().add(b, 1_000).copy(heard = listOf("예전 제목"))
        assertEquals(cache, BriefingCache.decode(cache.encode()))
        assertEquals(BriefingCache(), BriefingCache.decode("망가진 파일"))
    }

    @Test
    fun `꺼내면 큐에서 빠지고 제목은 들은 목록에 남는다 · 오래된 것은 버린다`() {
        val b = decodeServerBriefing(serverJson, "srv-1")!!
        val cache = BriefingCache().add(b, 0)
        val (after, got) = cache.take(Category.IT, 1_000)
        assertEquals(b, got)
        assertEquals(0, after.count(Category.IT, 1_000))
        assertEquals(listOf("AI 요약 확산", "보안 업데이트"), after.heard)
        assertNull(cache.take(Category.GAME, 1_000).second)
        assertNull(cache.take(Category.IT, BriefingCache.MAX_AGE_MS + 1).second)
    }

    @Test
    fun `모자란 분야만 더 받는다`() {
        val b = decodeServerBriefing(serverJson, "srv-1")!!
        val cache = BriefingCache().add(b, 0)
        assertEquals(mapOf(Category.GAME to 1), cache.missing(listOf(Category.IT, Category.GAME), 0))
        assertEquals(mapOf(Category.IT to 1), cache.missing(listOf(Category.IT, Category.IT), 0))
    }

    @Test
    fun `큐를 채울 때 요청할 분야를 돌아가는 순서대로 알려 준다`() {
        val s = BriefingState(BriefingSettings(selected = listOf(Category.GAME, Category.IT, Category.TRAVEL)), rotation = 2)
        assertEquals(listOf(Category.TRAVEL, Category.GAME), s.upcomingCategories())
        assertTrue(s.copy(settings = BriefingSettings(selected = emptyList())).upcomingCategories().isEmpty())
        // 받아 둔 것만 큐에 넣고, 없는 분야는 건너뛴다
        var prepared = listOf(decodeServerBriefing(serverJson, "g")!!.copy(category = Category.GAME))
        val only = BriefingSource { cat, _ ->
            prepared.firstOrNull { it.category == cat }?.also { prepared = prepared - it }
        }
        val filled = s.refill(only)
        assertEquals(listOf(Category.GAME), filled.queue.map { it.category })
    }

    @Test
    fun `서버 오류를 화면 문장으로 바꾼다`() {
        assertTrue(problemText(0, null).contains("연결하지 못했어요"))
        assertTrue(problemText(401, null).contains("앱 토큰"))
        assertEquals("브리핑을 만들지 못했어요 · 쓸 만한 기사를 찾지 못했어요", problemText(502, """{"error":"쓸 만한 기사를 찾지 못했어요"}"""))
    }
}
