package kr.contest.pleosassistant.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAppMatchTest {

    private val self = "kr.contest.pleosassistant"
    private val radio = MediaServiceInfo("com.android.music", "com.android.music.MediaPlaybackService", "Music")

    @Test
    fun `알려진 패키지가 있으면 그것에 붙는다`() {
        val melon = MediaServiceInfo("com.iloen.melon", "com.iloen.melon.MediaService", "Melon")
        val other = MediaServiceInfo("com.example.melonfan", "x.Service", "멜론 팬")
        assertEquals(melon, findMediaService(MusicApp.MELON, listOf(other, radio, melon), self))
    }

    @Test
    fun `차량용 앱은 패키지가 달라도 앱 이름으로 찾는다`() {
        val carMelon = MediaServiceInfo("com.kakaoent.melon.car", "a.b.CarMediaService", "멜론")
        assertEquals(carMelon, findMediaService(MusicApp.MELON, listOf(radio, carMelon), self))
        // 영문 이름 · 띄어쓰기 · 대소문자
        val english = MediaServiceInfo("com.vendor.player", "a.S", "MELON for Car")
        assertEquals(english, findMediaService(MusicApp.MELON, listOf(radio, english), self))
    }

    @Test
    fun `해당 앱이 없으면 null 이고 우리 앱 자신에게는 붙지 않는다`() {
        assertNull(findMediaService(MusicApp.MELON, listOf(radio), self))
        val mine = MediaServiceInfo(self, "x.S", "멜론 흉내")
        assertNull(findMediaService(MusicApp.MELON, listOf(mine), self))
        // 데모 곡은 음악 앱을 찾지 않는다
        assertNull(findMediaService(MusicApp.DEMO, listOf(radio), self))
    }

    private val umbrella = Track("u", "우산", "에픽하이 (EPIK HIGH)", "힙합", mapOf(Mood.RAIN to 1f))

    @Test
    fun `검색 결과에서 제목과 가수가 맞는 곡을 고른다`() {
        val items = listOf(
            SearchItem("1", "우산 (Feat. 윤하)", "다른 가수", true),
            SearchItem("2", "우산 (Feat. 윤하)", "에픽하이 (EPIK HIGH)", true),
            SearchItem("3", "비가 와", "에픽하이 (EPIK HIGH)", true),
        )
        assertEquals("2", pickSearchResult(umbrella, items)?.mediaId)
    }

    @Test
    fun `제목이 맞는 곡이 없거나 틀 수 없는 항목뿐이면 고르지 않는다`() {
        assertNull(pickSearchResult(umbrella, listOf(SearchItem("3", "비가 와", "에픽하이", true))))
        assertNull(pickSearchResult(umbrella, listOf(SearchItem("a", "우산", "에픽하이", playable = false))))
        assertNull(pickSearchResult(umbrella, emptyList()))
    }

    @Test
    fun `기호와 띄어쓰기는 무시한다`() {
        val t = Track("s", "봄이 좋냐??", "10CM", "인디", mapOf(Mood.CRUISE to 1f))
        assertEquals("x", pickSearchResult(t, listOf(SearchItem("x", "봄이좋냐", "10cm", true)))?.mediaId)
    }

    @Test
    fun `재생이 시작되지 않았으면 앱 안 검색으로 다시 찾는다`() {
        assertFalse(needsSearchFallback(3)) // PLAYING
        assertFalse(needsSearchFallback(6)) // BUFFERING
        assertTrue(needsSearchFallback(7)) // ERROR
        assertTrue(needsSearchFallback(2)) // PAUSED
        assertTrue(needsSearchFallback(null))
    }

    @Test
    fun `검색해서 재생 지원 여부는 앱이 알린 actions 로 본다`() {
        assertTrue(supportsPlayFromSearch(null))
        assertTrue(supportsPlayFromSearch(ACTION_PLAY_FROM_SEARCH or 4L))
        assertFalse(supportsPlayFromSearch(4L or 2L))
    }
}

class StreamingCatalogTest {

    @Test
    fun `분위기마다 실제 곡이 4곡 있고 id 가 겹치지 않는다`() {
        val tracks = StreamingCatalog.tracks
        Mood.entries.forEach { mood -> assertEquals(mood.name, 4, tracks.count { it.fit(mood) == 1f }) }
        assertEquals(tracks.size, tracks.map { it.id }.toSet().size)
        assertTrue(tracks.all { it.assetPath == null && it.features == null })
    }

    @Test
    fun `음악 앱 곡 목록으로도 한 번에 5곡을 추천한다`() {
        val s = MusicState.start(StreamingCatalog.tracks, MoodSignals(hour = 23))
        assertEquals(Mood.NIGHT, s.mood)
        assertEquals(PLAYLIST_SIZE, s.queue.size)
    }
}
