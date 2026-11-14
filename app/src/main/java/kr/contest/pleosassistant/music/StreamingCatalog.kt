package kr.contest.pleosassistant.music

/**
 * 음악 앱(멜론 등)으로 넘길 때 쓰는 실제 곡 목록. 제목 · 가수만 갖고, 소리는 음악 앱이 튼다.
 * 데모 곡(앱에 든 직접 만든 곡)은 음악 앱에서 검색해도 없어서, 음악 앱을 고르면 이 목록으로 추천한다.
 *
 * 분위기 태그는 곡을 아는 사람이 붙였다(분위기마다 4곡이 주 분위기 1, 가까운 분위기 0.4).
 * 기능계획서 5장의 '서버가 LLM 으로 분류 + 사람 검수'가 생기면 그 결과로 바꾼다.
 * 음원 파일은 없어서 소리 분석([kr.contest.pleosassistant.music.analysis.AudioAnalyzer])은 하지 않는다.
 */
object StreamingCatalog {

    private val genreOf = mapOf(
        "아이유" to "발라드",
        "볼빨간사춘기" to "인디",
        "버스커 버스커" to "인디",
        "DAY6 (데이식스)" to "밴드",
        "쿨" to "댄스",
        "클론" to "댄스",
        "방탄소년단" to "팝",
        "싸이" to "댄스",
        "성시경" to "발라드",
        "폴킴" to "발라드",
        "10CM" to "인디",
        "잔나비" to "밴드",
        "에픽하이 (EPIK HIGH)" to "힙합",
        "헤이즈 (Heize)" to "R&B",
        "김현식" to "발라드",
        "검정치마" to "인디",
        "혁오 (HYUKOH)" to "밴드",
        "자이언티" to "R&B",
        "크러쉬" to "R&B",
    )

    /** 주 분위기와 가까운 분위기 */
    private val near = mapOf(
        Mood.FRESH to Mood.DRIVE,
        Mood.DRIVE to Mood.FRESH,
        Mood.CRUISE to Mood.SUNSET,
        Mood.RAIN to Mood.NIGHT,
        Mood.SUNSET to Mood.CRUISE,
        Mood.NIGHT to Mood.RAIN,
    )

    private val raw = mapOf(
        Mood.FRESH to listOf(
            "좋은 날" to "아이유",
            "우주를 줄게" to "볼빨간사춘기",
            "벚꽃 엔딩" to "버스커 버스커",
            "한 페이지가 될 수 있게" to "DAY6 (데이식스)",
        ),
        Mood.DRIVE to listOf(
            "해변의 여인" to "쿨",
            "쿵따리 샤바라" to "클론",
            "Dynamite" to "방탄소년단",
            "강남스타일" to "싸이",
        ),
        Mood.CRUISE to listOf(
            "거리에서" to "성시경",
            "모든 날, 모든 순간" to "폴킴",
            "봄이 좋냐??" to "10CM",
            "주저하는 연인들을 위해" to "잔나비",
        ),
        Mood.RAIN to listOf(
            "우산" to "에픽하이 (EPIK HIGH)",
            "비도 오고 그래서" to "헤이즈 (Heize)",
            "비처럼 음악처럼" to "김현식",
            "사랑이 잘" to "아이유",
        ),
        Mood.SUNSET to listOf(
            "Everything" to "검정치마",
            "TOMBOY" to "혁오 (HYUKOH)",
            "뜨거운 여름밤은 가고 남은 건 볼품없지만" to "잔나비",
            "너를 만나" to "폴킴",
        ),
        Mood.NIGHT to listOf(
            "밤편지" to "아이유",
            "여수 밤바다" to "버스커 버스커",
            "양화대교" to "자이언티",
            "잊어버리지마" to "크러쉬",
        ),
    )

    val tracks: List<Track> = raw.flatMap { (mood, songs) ->
        songs.mapIndexed { i, (title, artist) ->
            Track(
                id = "app-${mood.name.lowercase()}-$i",
                title = title,
                artist = artist,
                genre = genreOf.getValue(artist),
                moodFit = mapOf(mood to 1f, near.getValue(mood) to 0.4f),
                // 곡 길이는 음악 앱이 안다. 0 이면 화면에 적지 않는다
                durationSec = 0,
            )
        }
    }
}
