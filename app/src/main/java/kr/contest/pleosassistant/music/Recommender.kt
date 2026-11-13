package kr.contest.pleosassistant.music

/**
 * 곡 하나. 분위기 적합도는 외부 API 에서 받을 수 없어(스포티파이 Audio Features 신규 앱 사용 불가)
 * 우리 쪽에서 붙인다: 앱에 든 데모 곡은 소리를 듣고 분석해서([LocalLibrary]), 음악 앱 곡은 나중에 서버 태그로.
 * @property moodFit 분위기별 적합도 0~1. 없는 분위기는 0
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val moodFit: Map<Mood, Float>,
    val durationSec: Int = 210,
    /** 앱에 든 데모 곡이면 assets 경로(재생용). 음악 앱으로 넘기는 곡은 null */
    val assetPath: String? = null,
    /** 소리를 듣고 잰 값(분위기 분석 결과). 없으면 태그로만 판단한 곡 */
    val features: kr.contest.pleosassistant.music.analysis.AudioFeatures? = null,
) {
    fun fit(mood: Mood): Float = moodFit[mood] ?: 0f

    /** 음악 앱에 '검색해서 재생'으로 넘길 검색어 */
    val query: String get() = "$title $artist"
}

/**
 * 취향. 차 안에만 저장한다.
 * @property genrePreference 장르별 선호도 0~1. 처음 보는 장르는 [DEFAULT_PREFERENCE]
 * @property saved '저장'한 곡 id
 * @property recent 최근에 튼 곡 id, 가장 최근이 앞. 최대 [RECENT_LIMIT]개
 */
data class Taste(
    val genrePreference: Map<String, Float> = emptyMap(),
    val saved: Set<String> = emptySet(),
    val recent: List<String> = emptyList(),
) {
    fun preference(genre: String): Float = genrePreference[genre] ?: DEFAULT_PREFERENCE

    /** 신선도 0~1: 최근 목록에 없으면 1, 방금 튼 곡은 0, 오래될수록 1 에 가깝다 */
    fun freshness(trackId: String): Float {
        val i = recent.indexOf(trackId)
        return if (i < 0) 1f else i.toFloat() / RECENT_LIMIT
    }

    /** 건너뛴 곡: 그 장르 선호도를 낮춘다 */
    fun skipped(track: Track): Taste = adjust(track.genre, -SKIP_PENALTY)

    /** 저장한 곡: 그 장르 선호도를 높이고 저장 목록에 넣는다. 다시 누르면 저장을 취소하고 올린 만큼 되돌린다 */
    fun toggleSaved(track: Track): Taste =
        if (track.id in saved) adjust(track.genre, -SAVE_BONUS).copy(saved = saved - track.id)
        else adjust(track.genre, SAVE_BONUS).copy(saved = saved + track.id)

    /** 재생을 시작한 곡을 최근 목록 맨 앞에 넣는다 */
    fun played(track: Track): Taste =
        copy(recent = (listOf(track.id) + recent.filterNot { it == track.id }).take(RECENT_LIMIT))

    private fun adjust(genre: String, delta: Float): Taste =
        copy(genrePreference = genrePreference + (genre to (preference(genre) + delta).coerceIn(0f, 1f)))

    companion object {
        const val DEFAULT_PREFERENCE = 0.5f
        const val SKIP_PENALTY = 0.1f
        const val SAVE_BONUS = 0.15f
        const val RECENT_LIMIT = 20
    }
}

/** 곡 점수 = 0.5 × 장르 선호도 + 0.3 × 분위기 적합도 + 0.2 × 신선도 (기능계획서 5장) */
fun scoreTrack(track: Track, mood: Mood, taste: Taste): Float =
    0.5f * taste.preference(track.genre) + 0.3f * track.fit(mood) + 0.2f * taste.freshness(track.id)

/** 재생목록 길이: 현재 곡 + 다음 4곡 */
const val PLAYLIST_SIZE = 5

/**
 * 분위기에 맞는 곡 [count]개를 점수 순으로 고른다. 그 분위기에 전혀 맞지 않는 곡(적합도 0)은 빼고,
 * 모자라면 나머지에서 채운다. 점수가 같으면 id 순이라 결과가 늘 같다.
 */
fun recommend(catalog: List<Track>, mood: Mood, taste: Taste, count: Int = PLAYLIST_SIZE): List<Track> {
    val order = compareByDescending<Track> { scoreTrack(it, mood, taste) }.thenBy { it.id }
    val (fitting, others) = catalog.partition { it.fit(mood) > 0f }
    return (fitting.sortedWith(order) + others.sortedWith(order)).take(count)
}
