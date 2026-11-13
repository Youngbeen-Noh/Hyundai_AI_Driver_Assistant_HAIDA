package kr.contest.pleosassistant.briefing

// 기능 4 · 최근 이슈 브리핑 (docs/기능계획서.pdf 6장, 선택 기능)
// 목적지 없이 5분을 달리면, 고른 관심 분야를 번갈아 가며 브리핑(약 2~3분)을 하나씩 들려준다.

/** 관심 분야(대주제) */
enum class Category(val label: String) {
    IT("IT·테크"),
    GAME("게임"),
    TRAVEL("여행"),
    ECONOMY("경제"),
    SPORTS("스포츠"),
    CULTURE("문화"),
}

/** 쉬운 말 풀이가 필요한 용어 */
data class Term(val word: String, val meaning: String)

/**
 * 소주제 하나. 대본은 해요체 2~3문장이고, 첫 문장에 언론사 이름이 들어간다.
 * @property related '더 자세히'를 누르면 이어서 읽을 관련 기사 요약
 */
data class Subtopic(
    val title: String,
    val press: String,
    val publishedAt: String,
    val lines: List<String>,
    val term: Term? = null,
    val related: List<String> = emptyList(),
)

/** 브리핑 하나 = 대주제 하나 + 소주제 여러 개 */
data class Briefing(val id: String, val category: Category, val subtopics: List<Subtopic>) {
    val charCount: Int get() = subtopics.sumOf { s -> s.lines.sumOf { it.length } }
}

/**
 * 해요체 TTS 로 2~3분이 되게 하는 글자 수 상한. Pleos TTS(차 안 엔진)는 1초에 약 8글자를 읽어(에뮬레이터 측정)
 * 문장 사이 쉼까지 넣으면 3분 ≈ 1,300자. 여는 말이 붙으니 대본은 1,200자까지 담는다.
 */
const val SCRIPT_CHAR_LIMIT = 1200

/** Pleos TTS 가 1초에 읽는 글자 수(에뮬레이터에서 44~69자 문장으로 잰 값 7.7~8.2). 문장 길이로 읽는 시간을 짐작할 때 쓴다 */
const val TTS_CHARS_PER_SEC = 7.5

/** 상한을 넘기 전까지 소주제를 앞에서부터 담는다. 첫 소주제는 길어도 넣는다 */
fun Briefing.fitToLimit(limit: Int = SCRIPT_CHAR_LIMIT): Briefing {
    var total = 0
    val kept = subtopics.takeWhile { s ->
        total += s.lines.sumOf { it.length }
        total <= limit
    }.ifEmpty { subtopics.take(1) }
    return copy(subtopics = kept)
}

/** 문장 하나를 읽는 데 걸리는 시간(초, 짐작). 문장 사이 쉼 1초 포함 */
fun speakSeconds(line: String): Int = kotlin.math.ceil(line.length / TTS_CHARS_PER_SEC).toInt().coerceAtLeast(2) + 1

/** 목적지 없이 이만큼(분) 달리면 브리핑을 시작한다 */
const val START_AFTER_MIN = 5

/** 브리핑을 시작할 때인가: 켜져 있고, 분야를 하나 이상 골랐고, 목적지 없이 5분 이상 달렸다 */
fun shouldStartBriefing(enabled: Boolean, hasCategories: Boolean, drivingMinutes: Int, hasDestination: Boolean): Boolean =
    enabled && hasCategories && !hasDestination && drivingMinutes >= START_AFTER_MIN
