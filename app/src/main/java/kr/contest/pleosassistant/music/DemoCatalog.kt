package kr.contest.pleosassistant.music

/**
 * 시연용 곡 목록(팀 UI 프로토타입과 같은 가상 곡 · 가수). 분위기마다 5곡이 주 분위기(적합도 1),
 * 가까운 분위기에 0.4 를 붙였다. 장르는 가수별로 하나라서 '건너뛰기 · 저장'이 다음 추천에 바로 드러난다.
 * 실제 곡과 분위기 태그는 서버(LLM 분류 + 사람 검수)가 생기면 그쪽에서 받는다.
 */
object DemoCatalog {

    private val genreOf = mapOf(
        "하루결" to "어쿠스틱",
        "소리결" to "인디",
        "Nova Lane" to "팝",
        "오렌지 아워" to "시티팝",
        "달빛 정원" to "발라드",
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
        Mood.FRESH to listOf("첫 햇살 한 모금" to "하루결", "창문을 열면" to "오렌지 아워", "출발선" to "Nova Lane", "가볍게 시동" to "소리결", "아침의 신호등" to "달빛 정원"),
        Mood.DRIVE to listOf("풀 스로틀" to "Nova Lane", "네온 하이웨이" to "오렌지 아워", "달려라 오늘" to "하루결", "코너를 지나" to "소리결", "라스트 랩" to "달빛 정원"),
        Mood.CRUISE to listOf("느린 오후" to "달빛 정원", "바람이 지나는 길" to "하루결", "해안도로" to "소리결", "천천히 가자" to "오렌지 아워", "긴 직선" to "Nova Lane"),
        Mood.RAIN to listOf("비 내리는 퇴근길" to "소리결", "와이퍼 박자" to "달빛 정원", "젖은 신호등" to "하루결", "우산 속 라디오" to "Nova Lane", "창가의 빗소리" to "오렌지 아워"),
        Mood.SUNSET to listOf("오렌지 하늘" to "오렌지 아워", "집으로 가는 노을" to "하루결", "저물녘 드라이브" to "소리결", "붉은 지평선" to "Nova Lane", "여운" to "달빛 정원"),
        Mood.NIGHT to listOf("가로등 사이로" to "달빛 정원", "조용한 터널" to "소리결", "밤의 끝에서" to "하루결", "별빛 차선" to "Nova Lane", "귀가" to "오렌지 아워"),
    )

    private val durations = listOf(220, 203, 247, 195, 231)

    val tracks: List<Track> = raw.flatMap { (mood, songs) ->
        songs.mapIndexed { i, (title, artist) ->
            Track(
                id = "${mood.name.lowercase()}-$i",
                title = title,
                artist = artist,
                genre = genreOf.getValue(artist),
                moodFit = mapOf(mood to 1f, near.getValue(mood) to 0.4f),
                durationSec = durations[i],
            )
        }
    }
}
