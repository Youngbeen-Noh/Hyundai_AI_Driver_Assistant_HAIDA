package kr.contest.pleosassistant.music

// 기능 3 · 음악 앱 연결에서 Android 에 기대지 않는 판단(단위 테스트로 확인한다).
// - 어느 미디어 서비스가 그 음악 앱인지(차량용 멜론은 휴대폰 앱과 패키지 이름이 다를 수 있다)
// - 음악 앱 안에서 검색한 결과 중 어느 항목이 우리가 고른 곡인지

/** 기기에 있는 미디어 앱 하나(MediaBrowserService 를 가진 앱) */
data class MediaServiceInfo(val packageName: String, val className: String, val appLabel: String)

/**
 * [app] 에 해당하는 미디어 서비스를 고른다.
 * 1) 알려진 패키지 이름이 같으면 그것 2) 아니면 앱 이름 · 패키지 이름에 [MusicApp.keywords] 가 들어간 것.
 * @param self 우리 앱 패키지(자기 자신에게는 붙지 않는다)
 */
fun findMediaService(app: MusicApp, services: List<MediaServiceInfo>, self: String): MediaServiceInfo? {
    val candidates = services.filter { it.packageName != self }
    app.packageName?.let { pkg -> candidates.firstOrNull { it.packageName == pkg }?.let { return it } }
    if (app.keywords.isEmpty()) return null
    return candidates.firstOrNull { s ->
        val label = s.appLabel.normalized()
        val pkg = s.packageName.lowercase()
        app.keywords.any { k -> label.contains(k.normalized()) || pkg.contains(k.lowercase()) }
    }
}

/** 음악 앱 안에서 검색해 받은 항목 하나 */
data class SearchItem(val mediaId: String, val title: String, val subtitle: String, val playable: Boolean)

/**
 * 검색 결과에서 [track] 을 고른다. 제목이 맞아야 하고(괄호 · 기호 · 띄어쓰기 무시), 가수까지 맞으면 그것을 먼저 고른다.
 * 제목이 맞는 항목이 없으면 null(엉뚱한 곡을 틀지 않는다).
 */
fun pickSearchResult(track: Track, items: List<SearchItem>): SearchItem? {
    val title = track.title.normalized()
    val artist = track.artist.normalized()
    return items.filter { it.playable }
        .map { item ->
            val t = item.title.normalized()
            val titleScore = when {
                t == title -> 3
                t.isNotEmpty() && (t.contains(title) || title.contains(t)) -> 2
                else -> 0
            }
            val sub = item.subtitle.normalized()
            val artistScore = if (sub.isNotEmpty() && (sub.contains(artist) || artist.contains(sub))) 1 else 0
            item to titleScore + artistScore
        }
        .filter { (item, score) -> score >= 2 && item.title.normalized().isNotEmpty() }
        .maxByOrNull { it.second }
        ?.first
}

/**
 * '검색해서 재생'을 보낸 뒤 [WATCHDOG_MS] 가 지났을 때의 재생 상태로, 음악 앱 안 검색으로 다시 찾아야 하는지 정한다.
 * @param state android.media.session.PlaybackState 의 state 값. 모르면 null
 */
fun needsSearchFallback(state: Int?): Boolean = state !in STARTED_STATES

/** 음악 앱이 '검색해서 재생'을 지원한다고 알렸는지. actions 를 모르면(null) 일단 보내 본다 */
fun supportsPlayFromSearch(actions: Long?): Boolean = actions == null || actions and ACTION_PLAY_FROM_SEARCH != 0L

const val WATCHDOG_MS = 4_000L

// android.media.session.PlaybackState 상수(단위 테스트에서 Android 없이 쓰려고 값을 옮겨 둔다)
internal const val ACTION_PLAY_FROM_SEARCH = 1L shl 11
private const val STATE_PLAYING = 3
private const val STATE_BUFFERING = 6
private const val STATE_CONNECTING = 8
private val STARTED_STATES = setOf(STATE_PLAYING, STATE_BUFFERING, STATE_CONNECTING)

/** 비교용: 괄호 안(피처링 등) · 기호 · 띄어쓰기를 빼고 소문자로 */
internal fun String.normalized(): String =
    lowercase()
        .replace(Regex("""\([^)]*\)|\[[^]]*]"""), "")
        .filter { it.isLetterOrDigit() }
