package kr.contest.pleosassistant.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodTest {

    @Test
    fun `와이퍼가 돌면 맑은 날씨 예보보다 빗길이 먼저다`() {
        val d = decideMood(MoodSignals(hour = 14, weather = Weather.CLEAR, wipersOn = true))
        assertEquals(Mood.RAIN, d.mood)
        assertEquals("와이퍼가 움직여서 골랐어요", d.reason)
    }

    @Test
    fun `비 예보면 빗길`() {
        assertEquals(Mood.RAIN, decideMood(MoodSignals(hour = 9, weather = Weather.RAIN)).mood)
    }

    @Test
    fun `전조등이 켜지면 낮이어도 밤길`() {
        assertEquals(Mood.NIGHT, decideMood(MoodSignals(hour = 15, headlightsOn = true)).mood)
    }

    @Test
    fun `정체 구간에서는 차분한 크루징`() {
        val d = decideMood(MoodSignals(hour = 8, etaDelayMin = 15))
        assertEquals(Mood.CRUISE, d.mood)
        assertEquals("길이 막혀서 골랐어요", d.reason)
    }

    @Test
    fun `시간대로 아침 · 노을 · 밤을 고른다`() {
        assertEquals(Mood.FRESH, decideMood(MoodSignals(hour = 7)).mood)
        assertEquals(Mood.SUNSET, decideMood(MoodSignals(hour = 18)).mood)
        assertEquals(Mood.NIGHT, decideMood(MoodSignals(hour = 23)).mood)
        assertEquals(Mood.NIGHT, decideMood(MoodSignals(hour = 2)).mood)
    }

    @Test
    fun `낮에 길이 뚫려 있으면 신나는 드라이브, 모르면 크루징`() {
        assertEquals(Mood.DRIVE, decideMood(MoodSignals(hour = 13, etaDelayMin = 0)).mood)
        assertEquals(Mood.CRUISE, decideMood(MoodSignals(hour = 13)).mood)
    }
}

class RecommenderTest {

    private val catalog = DemoCatalog.tracks

    @Test
    fun `점수는 0_5 선호도 + 0_3 적합도 + 0_2 신선도`() {
        val t = Track("a", "곡", "가수", "팝", mapOf(Mood.RAIN to 1f))
        val taste = Taste(genrePreference = mapOf("팝" to 0.8f))
        assertEquals(0.5f * 0.8f + 0.3f * 1f + 0.2f * 1f, scoreTrack(t, Mood.RAIN, taste), 1e-6f)
        // 방금 튼 곡은 신선도 0
        assertEquals(0.5f * 0.8f + 0.3f * 1f, scoreTrack(t, Mood.RAIN, taste.played(t)), 1e-6f)
    }

    @Test
    fun `처음에는 그 분위기 곡 5개를 고른다`() {
        val list = recommend(catalog, Mood.RAIN, Taste())
        assertEquals(5, list.size)
        assertTrue(list.all { it.fit(Mood.RAIN) == 1f })
    }

    @Test
    fun `좋아하는 장르가 앞에 온다`() {
        val taste = Taste(genrePreference = mapOf("발라드" to 1f))
        assertEquals("달빛 정원", recommend(catalog, Mood.RAIN, taste).first().artist)
    }

    @Test
    fun `건너뛰면 선호도가 내려가고 저장하면 올라간다`() {
        val track = catalog.first { it.genre == "팝" }
        assertEquals(0.4f, Taste().skipped(track).preference("팝"), 1e-6f)
        val saved = Taste().toggleSaved(track)
        assertEquals(0.65f, saved.preference("팝"), 1e-6f)
        assertTrue(track.id in saved.saved)
        // 다시 누르면 취소
        val unsaved = saved.toggleSaved(track)
        assertEquals(0.5f, unsaved.preference("팝"), 1e-6f)
        assertFalse(track.id in unsaved.saved)
    }

    @Test
    fun `선호도는 0~1 을 넘지 않고 최근 목록은 20곡까지`() {
        val track = catalog.first()
        var taste = Taste()
        repeat(10) { taste = taste.skipped(track) }
        assertEquals(0f, taste.preference(track.genre), 0f)
        catalog.forEach { taste = taste.played(it) }
        assertEquals(Taste.RECENT_LIMIT, taste.recent.size)
        assertEquals(catalog.last().id, taste.recent.first())
    }
}

class MusicStateTest {

    private val catalog = DemoCatalog.tracks

    @Test
    fun `시작하면 분위기를 말하고 지금 곡 + 다음 4곡을 만든다`() {
        val s = MusicState.start(catalog, MoodSignals(hour = 14, wipersOn = true))
        assertEquals(Mood.RAIN, s.mood)
        assertEquals(4, s.upcoming.size)
        assertEquals("와이퍼가 움직여서 촉촉한 빗길로 골랐어요.", s.announcement)
        assertEquals(s.current!!.id, s.taste.recent.first())
    }

    @Test
    fun `와이퍼가 켜지면 빗길로 바뀌고 한 번 말한다`() {
        val s = MusicState.start(catalog, MoodSignals(hour = 14, etaDelayMin = 0)).consumeAnnouncement()
        assertEquals(Mood.DRIVE, s.mood)
        val rain = s.withSignals(s.signals.copy(wipersOn = true))
        assertEquals(Mood.RAIN, rain.mood)
        assertTrue(rain.queue.all { it.fit(Mood.RAIN) == 1f })
        assertEquals("와이퍼가 움직여서 촉촉한 빗길로 골랐어요.", rain.announcement)
        // 와이퍼가 계속 돌면 같은 말을 다시 하지 않는다
        val again = rain.consumeAnnouncement().withSignals(rain.signals.copy(etaDelayMin = 1))
        assertNull(again.announcement)
        assertEquals(rain.queue, again.queue)
    }

    @Test
    fun `다음을 누르면 곡이 넘어가고 목록은 5곡을 유지하며 건너뛴 장르는 내려간다`() {
        val s = MusicState.start(catalog, MoodSignals(hour = 14, weather = Weather.RAIN))
        val first = s.current!!
        val next = s.skip()
        assertEquals(s.upcoming.first(), next.current)
        assertEquals(PLAYLIST_SIZE, next.queue.size)
        assertEquals(PLAYLIST_SIZE, next.queue.map { it.id }.toSet().size)
        assertTrue(next.queue.none { it.id == first.id })
        assertEquals(0.4f, next.taste.preference(first.genre), 1e-6f)
    }

    @Test
    fun `건너뛴 장르의 곡은 남은 목록에서도 바로 뒤로 밀린다`() {
        // 크루징: 신선도가 같으면 같은 점수라 id 순. 첫 곡과 같은 장르 곡을 다음 곡 자리에 둔다
        val s0 = MusicState.start(catalog, MoodSignals(hour = 13))
        val genre = s0.current!!.genre
        val sameGenre = catalog.first { it.genre == genre && it.id != s0.current!!.id }
        val s = s0.copy(queue = listOf(s0.current!!, sameGenre) + s0.upcoming.filterNot { it.genre == genre }.take(3))
        val next = s.skip()
        // 원래 바로 다음 곡이었지만, 남아 있던 4곡 중 맨 뒤로(그 뒤에는 새로 채운 곡 하나)
        assertEquals(3, next.queue.indexOf(sameGenre))
    }

    @Test
    fun `저장하면 다음 추천에서 그 장르가 앞선다`() {
        var s = MusicState.start(catalog, MoodSignals(hour = 14, weather = Weather.RAIN))
        val liked = s.upcoming.last()
        // 좋아하는 곡이 나올 때까지 끝까지 듣고(선호도 그대로) 저장
        while (s.current != liked) s = s.finished()
        s = s.toggleSave()
        assertTrue(s.currentSaved)
        val other = s.chooseMood(Mood.NIGHT)
        assertEquals(liked.genre, other.current!!.genre)
    }

    @Test
    fun `직접 고른 분위기는 신호가 바뀌어도 유지되고, 자동으로 돌리면 신호를 따른다`() {
        val s = MusicState.start(catalog, MoodSignals(hour = 14)).chooseMood(Mood.SUNSET)
        assertEquals(Mood.SUNSET, s.mood)
        assertEquals("노을 감성으로 바꿨어요.", s.announcement)
        val wet = s.withSignals(s.signals.copy(wipersOn = true))
        assertEquals(Mood.SUNSET, wet.mood)
        val auto = wet.chooseMood(null)
        assertEquals(Mood.RAIN, auto.mood)
    }

    @Test
    fun `곡 목록이 나중에 생기면 지금 분위기로 다시 고르고 말은 하지 않는다`() {
        val empty = MusicState.start(emptyList(), MoodSignals(hour = 14, wipersOn = true)).consumeAnnouncement().copy(paused = true)
        assertNull(empty.current)
        val filled = empty.withCatalog(catalog)
        assertEquals(Mood.RAIN, filled.mood)
        assertEquals(PLAYLIST_SIZE, filled.queue.size)
        assertTrue(filled.queue.all { it.fit(Mood.RAIN) == 1f })
        assertNull(filled.announcement)
        assertTrue(filled.paused)
        assertEquals(filled.current!!.id, filled.taste.recent.first())
    }

    @Test
    fun `일시정지는 토글되고 다음 곡으로 넘어가면 다시 재생된다`() {
        val s = MusicState.start(catalog, MoodSignals(hour = 14)).togglePause()
        assertTrue(s.paused)
        assertFalse(s.skip().paused)
        assertNotEquals(s.current, s.skip().current)
    }
}
