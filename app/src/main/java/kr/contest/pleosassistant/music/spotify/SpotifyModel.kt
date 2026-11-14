package kr.contest.pleosassistant.music.spotify

import kr.contest.pleosassistant.music.Track
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64

// 기능 3 · 스포티파이 Web API 연결에서 Android 에 기대지 않는 부분(단위 테스트로 확인한다).
// 로그인은 PKCE(앱에 비밀 값을 두지 않는 OAuth), 재생은 사용자의 스포티파이 기기(보통 휴대폰)에 Web API 로 요청한다.

/** 앱이 쓰는 권한: 재생 요청 · 일시정지, 기기 목록 · 지금 곡 읽기 */
val SPOTIFY_SCOPES = listOf("user-modify-playback-state", "user-read-playback-state")

/** 로그인 뒤 스포티파이가 돌려보내는 주소. 스포티파이 개발자 앱 설정(Redirect URIs)에 똑같이 넣는다 */
const val SPOTIFY_REDIRECT_URI = "kr.contest.pleosassistant://spotify-callback"

// ---- 로그인(PKCE) ----

/** code_verifier: 43~128자. [random] 은 32바이트 이상 무작위 값 */
fun pkceVerifier(random: ByteArray): String = base64Url(random)

/** code_challenge = BASE64URL(SHA-256(verifier)) (RFC 7636) */
fun pkceChallenge(verifier: String): String =
    base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

fun authorizeUrl(clientId: String, challenge: String, state: String): String {
    val params = linkedMapOf(
        "client_id" to clientId,
        "response_type" to "code",
        "redirect_uri" to SPOTIFY_REDIRECT_URI,
        "code_challenge_method" to "S256",
        "code_challenge" to challenge,
        "state" to state,
        "scope" to SPOTIFY_SCOPES.joinToString(" "),
    )
    return "https://accounts.spotify.com/authorize?" + formEncode(params)
}

fun formEncode(params: Map<String, String>): String =
    params.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

/** 로그인 결과(돌아온 주소)에서 code 를 꺼낸다. state 가 다르거나 오류면 null */
fun parseCallback(query: Map<String, String>, expectedState: String): String? {
    if (query["state"] != expectedState) return null
    if (query["error"] != null) return null
    return query["code"]?.takeIf { it.isNotBlank() }
}

/**
 * 받은 토큰.
 * @property expiresAtMs 이 시각(벽시계 ms)이 지나면 [refreshToken] 으로 새로 받는다
 */
data class SpotifyTokens(val accessToken: String, val refreshToken: String, val expiresAtMs: Long) {
    /** 만료 1분 전부터 새로 받는다 */
    fun expired(nowMs: Long): Boolean = nowMs >= expiresAtMs - 60_000
}

/**
 * 토큰 응답. 새로 고침 응답에는 refresh_token 이 없을 수 있어 [previousRefresh] 를 그대로 쓴다.
 */
fun parseTokens(json: String, nowMs: Long, previousRefresh: String? = null): SpotifyTokens {
    val o = JSONObject(json)
    val refresh = o.optString("refresh_token").ifBlank { previousRefresh.orEmpty() }
    return SpotifyTokens(
        accessToken = o.getString("access_token"),
        refreshToken = refresh,
        expiresAtMs = nowMs + o.optLong("expires_in", 3600) * 1000,
    )
}

// ---- 검색 · 기기 · 재생 상태 ----

/** 스포티파이 곡 하나 */
data class SpotifyTrack(val uri: String, val name: String, val artists: List<String>, val durationMs: Long)

/** GET /v1/search?type=track 응답 */
fun parseSearch(json: String): List<SpotifyTrack> {
    val items = JSONObject(json).optJSONObject("tracks")?.optJSONArray("items") ?: return emptyList()
    return (0 until items.length()).map { i ->
        val t = items.getJSONObject(i)
        val artists = t.optJSONArray("artists")
        SpotifyTrack(
            uri = t.getString("uri"),
            name = t.optString("name"),
            artists = (0 until (artists?.length() ?: 0)).map { artists!!.getJSONObject(it).optString("name") },
            durationMs = t.optLong("duration_ms"),
        )
    }
}

/** 검색어: 스포티파이 필드 검색(track: · artist:). 가수 이름의 괄호(영문 표기 등)는 뺀다 */
fun searchQuery(track: Track): String {
    val artist = track.artist.replace(Regex("""\s*\([^)]*\)"""), "").trim()
    return "track:${track.title} artist:$artist"
}

/**
 * 검색 결과에서 [track] 을 고른다. 제목이 맞아야 하고(괄호 · 기호 · 띄어쓰기 무시), 가수까지 맞으면 그것을 먼저.
 * 가수는 한글 · 영문 표기가 섞여 있어(예: "DAY6 (데이식스)") 괄호 안팎을 모두 비교한다. 제목이 맞는 곡이 없으면 null.
 */
fun pickTrack(track: Track, results: List<SpotifyTrack>): SpotifyTrack? {
    val title = track.title.normalized()
    val artistNames = artistVariants(track.artist)
    return results
        .map { r ->
            val t = r.name.normalized()
            val titleScore = when {
                t == title -> 3
                t.isNotEmpty() && (t.contains(title) || title.contains(t)) -> 2
                else -> 0
            }
            val artistScore = if (r.artists.any { a -> artistVariants(a).any { it in artistNames } }) 1 else 0
            r to titleScore + artistScore
        }
        .filter { it.second >= 2 }
        .maxByOrNull { it.second }
        ?.first
}

/** "DAY6 (데이식스)" → {day6, 데이식스} */
private fun artistVariants(name: String): Set<String> {
    val inside = Regex("""\(([^)]*)\)""").findAll(name).map { it.groupValues[1].normalized() }
    return (inside + name.normalized()).filter { it.isNotEmpty() }.toSet()
}

/** 비교용: 괄호 안(피처링 · 영문 표기) · 기호 · 띄어쓰기를 빼고 소문자로 */
internal fun String.normalized(): String =
    lowercase().replace(Regex("""\([^)]*\)|\[[^]]*]"""), "").filter { it.isLetterOrDigit() }

/** 스포티파이 Connect 기기(휴대폰 · 컴퓨터 · 스피커) */
data class SpotifyDevice(val id: String, val name: String, val type: String, val active: Boolean, val restricted: Boolean)

/** GET /v1/me/player/devices 응답 */
fun parseDevices(json: String): List<SpotifyDevice> {
    val arr = JSONObject(json).optJSONArray("devices") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val d = arr.getJSONObject(i)
        val id = d.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
        SpotifyDevice(id, d.optString("name"), d.optString("type"), d.optBoolean("is_active"), d.optBoolean("is_restricted"))
    }
}

/** 틀 기기: 지금 쓰는 기기 → 휴대폰 → 아무거나. 원격 조종이 막힌 기기는 뺀다 */
fun chooseDevice(devices: List<SpotifyDevice>): SpotifyDevice? {
    val usable = devices.filter { !it.restricted }
    return usable.firstOrNull { it.active }
        ?: usable.firstOrNull { it.type.equals("Smartphone", ignoreCase = true) }
        ?: usable.firstOrNull()
}

/** 지금 재생 상태 한 장(GET /v1/me/player). 아무것도 안 틀고 있으면 null */
data class PlaybackSnapshot(
    val trackUri: String?,
    val title: String?,
    val artist: String?,
    val playing: Boolean,
    val progressMs: Long,
    val durationMs: Long,
)

fun parsePlayback(json: String?): PlaybackSnapshot? {
    if (json.isNullOrBlank()) return null
    val o = JSONObject(json)
    val item = o.optJSONObject("item")
    val artists = item?.optJSONArray("artists")
    return PlaybackSnapshot(
        trackUri = item?.optString("uri"),
        title = item?.optString("name"),
        artist = if (artists != null && artists.length() > 0) artists.getJSONObject(0).optString("name") else null,
        playing = o.optBoolean("is_playing"),
        progressMs = o.optLong("progress_ms"),
        durationMs = item?.optLong("duration_ms") ?: 0,
    )
}

/**
 * 우리가 요청한 곡이 끝났는지. 앞 상태에서 그 곡이 끝 무렵([END_MARGIN_MS] 안)이었고,
 * 지금은 다른 곡으로 넘어갔거나 멈춰서 처음으로 돌아갔으면 끝난 것으로 본다(스포티파이가 이어서 다른 곡을 틀어도).
 */
fun trackFinished(prev: PlaybackSnapshot?, now: PlaybackSnapshot?, requestedUri: String?): Boolean {
    if (requestedUri == null || prev == null || prev.trackUri != requestedUri) return false
    val nearEnd = prev.durationMs > 0 && prev.progressMs >= prev.durationMs - END_MARGIN_MS
    if (!nearEnd) return false
    if (now == null) return true
    return now.trackUri != requestedUri || (!now.playing && now.progressMs < END_MARGIN_MS)
}

/** 재생 상태를 읽는 간격보다 조금 넉넉하게 */
const val END_MARGIN_MS = 6_000L

// ---- 오류 ----

/** Web API 오류를 화면에 보일 상태로 */
enum class SpotifyProblem {
    /** 로그인이 필요하다(토큰 없음 · 새로 고침 실패) */
    NEEDS_LOGIN,

    /** Premium 이 아니라 재생을 시킬 수 없다(403) */
    NOT_PREMIUM,

    /** 켜진 스포티파이 기기가 없다(404 · 기기 목록 비어 있음) */
    NO_DEVICE,

    /** 그 밖의 오류(네트워크 등) */
    OTHER,
}

fun problemOf(status: Int, body: String?): SpotifyProblem = when (status) {
    401 -> SpotifyProblem.NEEDS_LOGIN
    403 -> if (body?.contains("PREMIUM", ignoreCase = true) == true) SpotifyProblem.NOT_PREMIUM else SpotifyProblem.OTHER
    404 -> SpotifyProblem.NO_DEVICE
    else -> SpotifyProblem.OTHER
}
