package kr.contest.pleosassistant.music.spotify

import kr.contest.pleosassistant.music.Mood
import kr.contest.pleosassistant.music.MoodSignals
import kr.contest.pleosassistant.music.MusicState
import kr.contest.pleosassistant.music.PLAYLIST_SIZE
import kr.contest.pleosassistant.music.StreamingCatalog
import kr.contest.pleosassistant.music.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyModelTest {

    // ---- 로그인 ----

    @Test
    fun `PKCE challenge 는 RFC 7636 예시와 같다`() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        // 64바이트 무작위 값 → 86자(43~128자 규칙 안), URL 에 그대로 쓸 수 있는 글자만
        val v = pkceVerifier(ByteArray(64) { it.toByte() })
        assertEquals(86, v.length)
        assertTrue(v.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun `로그인 주소에 PKCE · 돌아올 주소 · 권한이 들어간다`() {
        val url = authorizeUrl("abc123", "CHAL", "st")
        assertTrue(url.startsWith("https://accounts.spotify.com/authorize?"))
        assertTrue("client_id=abc123" in url)
        assertTrue("code_challenge_method=S256" in url)
        assertTrue("code_challenge=CHAL" in url)
        assertTrue("redirect_uri=kr.contest.pleosassistant%3A%2F%2Fspotify-callback" in url)
        assertTrue("scope=user-modify-playback-state%20user-read-playback-state" in url)
    }

    @Test
    fun `돌아온 주소는 state 가 같고 오류가 없을 때만 code 를 쓴다`() {
        assertEquals("C", parseCallback(mapOf("code" to "C", "state" to "s"), "s"))
        assertNull(parseCallback(mapOf("code" to "C", "state" to "x"), "s"))
        assertNull(parseCallback(mapOf("error" to "access_denied", "state" to "s"), "s"))
    }

    @Test
    fun `토큰은 만료 1분 전부터 새로 받고 새로 고침 응답에 refresh 가 없으면 전 것을 쓴다`() {
        val t = parseTokens("""{"access_token":"A","refresh_token":"R","expires_in":3600}""", nowMs = 1_000)
        assertEquals(SpotifyTokens("A", "R", 3_601_000), t)
        assertFalse(t.expired(3_540_000))
        assertTrue(t.expired(3_541_000))
        val refreshed = parseTokens("""{"access_token":"B","expires_in":3600}""", nowMs = 0, previousRefresh = "R")
        assertEquals("R", refreshed.refreshToken)
    }

    // ---- 검색 ----

    private val searchJson = """
        {"tracks":{"items":[
          {"uri":"spotify:track:1","name":"우산 (Feat. 윤하)","duration_ms":200000,"artists":[{"name":"다른 가수"}]},
          {"uri":"spotify:track:2","name":"우산 (Feat. 윤하)","duration_ms":200000,"artists":[{"name":"Epik High"},{"name":"윤하"}]},
          {"uri":"spotify:track:3","name":"비가 와","duration_ms":180000,"artists":[{"name":"Epik High"}]}
        ]}}
    """.trimIndent()

    private val umbrella = Track("u", "우산", "에픽하이 (EPIK HIGH)", "힙합", mapOf(Mood.RAIN to 1f))

    @Test
    fun `검색 응답을 읽고 제목과 가수가 맞는 곡을 고른다`() {
        val results = parseSearch(searchJson)
        assertEquals(3, results.size)
        assertEquals(listOf("Epik High", "윤하"), results[1].artists)
        // 가수가 영문으로 와도 괄호 안 영문 표기와 맞춘다
        assertEquals("spotify:track:2", pickTrack(umbrella, results)?.uri)
    }

    @Test
    fun `제목이 맞는 곡이 없으면 고르지 않는다`() {
        val t = Track("x", "좋은 날", "아이유", "발라드", mapOf(Mood.FRESH to 1f))
        assertNull(pickTrack(t, parseSearch(searchJson)))
        assertNull(pickTrack(t, parseSearch("""{"tracks":{"items":[]}}""")))
        assertTrue(parseSearch("{}").isEmpty())
    }

    @Test
    fun `검색어는 필드 검색이고 가수 괄호는 뺀다`() {
        assertEquals("track:우산 artist:에픽하이", searchQuery(umbrella))
    }

    // ---- 기기 ----

    @Test
    fun `지금 쓰는 기기 → 휴대폰 → 아무거나, 원격 조종이 막힌 기기는 뺀다`() {
        val json = """{"devices":[
            {"id":"pc","name":"PC","type":"Computer","is_active":false,"is_restricted":false},
            {"id":"ph","name":"내 폰","type":"Smartphone","is_active":false,"is_restricted":false},
            {"id":"tv","name":"TV","type":"TV","is_active":true,"is_restricted":true},
            {"id":null,"name":"이상한 기기","type":"Speaker","is_active":false,"is_restricted":false}
        ]}"""
        val devices = parseDevices(json)
        assertEquals(listOf("pc", "ph", "tv"), devices.map { it.id })
        assertEquals("ph", chooseDevice(devices)?.id)
        assertEquals("pc", chooseDevice(devices.map { if (it.id == "pc") it.copy(active = true) else it })?.id)
        assertNull(chooseDevice(emptyList()))
    }

    // ---- 곡 끝 ----

    private fun snap(uri: String?, progress: Long, playing: Boolean = true, duration: Long = 200_000) =
        PlaybackSnapshot(uri, "t", "a", playing, progress, duration)

    @Test
    fun `요청한 곡이 끝 무렵에서 다른 곡으로 넘어가거나 처음으로 멈추면 끝난 것이다`() {
        val req = "spotify:track:1"
        assertTrue(trackFinished(snap(req, 197_000), snap("spotify:track:9", 1_000), req))
        assertTrue(trackFinished(snap(req, 197_000), snap(req, 0, playing = false), req))
        assertTrue(trackFinished(snap(req, 197_000), null, req))
    }

    @Test
    fun `중간에 다른 곡으로 바뀌거나 아직 재생 중이면 끝난 것이 아니다`() {
        val req = "spotify:track:1"
        // 사용자가 휴대폰에서 곡을 바꿈(끝 무렵이 아님)
        assertFalse(trackFinished(snap(req, 60_000), snap("spotify:track:9", 1_000), req))
        assertFalse(trackFinished(snap(req, 197_000), snap(req, 199_000), req))
        assertFalse(trackFinished(null, snap(req, 1_000), req))
        assertFalse(trackFinished(snap(req, 197_000), null, null))
    }

    @Test
    fun `재생 상태 응답을 읽는다`() {
        val s = parsePlayback("""{"is_playing":true,"progress_ms":5000,"item":{"uri":"spotify:track:1","name":"좋은 날","duration_ms":230000,"artists":[{"name":"IU"}]}}""")
        assertEquals(snap("spotify:track:1", 5000, duration = 230_000).copy(title = "좋은 날", artist = "IU"), s)
        assertNull(parsePlayback(""))
        assertNull(parsePlayback(null))
    }

    // ---- 오류 ----

    @Test
    fun `오류 코드를 화면 상태로 바꾼다`() {
        assertEquals(SpotifyProblem.NEEDS_LOGIN, problemOf(401, null))
        assertEquals(SpotifyProblem.NOT_PREMIUM, problemOf(403, """{"error":{"status":403,"reason":"PREMIUM_REQUIRED"}}"""))
        assertEquals(SpotifyProblem.OTHER, problemOf(403, "forbidden"))
        assertEquals(SpotifyProblem.NO_DEVICE, problemOf(404, """{"error":{"reason":"NO_ACTIVE_DEVICE"}}"""))
        assertEquals(SpotifyProblem.OTHER, problemOf(500, null))
    }
}

class StreamingCatalogTest {

    @Test
    fun `분위기마다 실제 곡이 4곡 있고 id 가 겹치지 않는다`() {
        val tracks = StreamingCatalog.tracks
        Mood.entries.forEach { mood -> assertEquals(mood.name, 4, tracks.count { it.fit(mood) == 1f }) }
        assertEquals(tracks.size, tracks.map { it.id }.toSet().size)
        assertTrue(tracks.all { it.assetPath == null && it.features == null && it.durationSec == 0 })
    }

    @Test
    fun `음악 앱 곡 목록으로도 한 번에 5곡을 추천한다`() {
        val s = MusicState.start(StreamingCatalog.tracks, MoodSignals(hour = 23))
        assertEquals(Mood.NIGHT, s.mood)
        assertEquals(PLAYLIST_SIZE, s.queue.size)
    }
}
