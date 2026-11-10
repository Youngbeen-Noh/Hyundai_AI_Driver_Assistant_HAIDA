package kr.contest.pleosassistant.memory

import java.time.LocalDate

/** "트렁크에 우산 넣었어" 를 나눈 결과 */
data class ParsedBelonging(val item: String, val place: String?, val due: LocalDate?)

/**
 * 말하듯 적은 문장에서 물건 · 위치 · 챙길 날을 뽑는다. 음성(STT)이 붙으면 인식된 문장을 그대로 여기에 넣는다.
 *
 * - 위치: 아래 [PLACES] 의 말(뒷좌석 → 뒷자리처럼 한 이름으로 모은다). 없으면 null
 * - 챙길 날: 오늘 · 내일 · 모레. 없으면 null
 * - 물건: 위치 · 날짜 · 끝의 동사("넣었어", "뒀어" …)를 뺀 나머지
 *
 * 물건이 남지 않으면 null(알아듣지 못함)을 돌려준다.
 */
fun parseBelonging(text: String, today: LocalDate): ParsedBelonging? {
    var rest = " " + text.replace(PUNCTUATION, " ") + " "

    var due: LocalDate? = null
    DUE_WORD.find(rest)?.let { match ->
        due = today.plusDays(DUE_OFFSETS.getValue(match.groupValues[1]))
        rest = rest.removeRange(match.range)
    }

    var place: String? = null
    for ((name, regex) in PLACE_PATTERNS) {
        val match = regex.find(rest) ?: continue
        place = name
        rest = rest.removeRange(match.range)
        break
    }

    val words = rest.split(WHITESPACE).filter { it.isNotEmpty() && it !in FILLER_WORDS }.toMutableList()
    while (words.isNotEmpty() && VERB.matches(words.last())) words.removeAt(words.lastIndex)
    if (words.isEmpty()) return null
    words[words.lastIndex] = words.last().removeObjectParticle()

    return ParsedBelonging(item = words.joinToString(" "), place = place, due = due)
}

/** 대표 위치 이름 → 같은 뜻으로 쓰는 말 */
private val PLACES: Map<String, List<String>> = linkedMapOf(
    "트렁크" to listOf("트렁크"),
    "프렁크" to listOf("프렁크"),
    "글로브박스" to listOf("글로브박스", "글러브박스", "글로브 박스", "글러브 박스"),
    "콘솔" to listOf("센터콘솔", "센터 콘솔", "콘솔박스", "콘솔", "팔걸이"),
    "컵홀더" to listOf("컵홀더"),
    "조수석" to listOf("조수석", "옆자리", "옆 좌석"),
    "운전석" to listOf("운전석"),
    "뒷자리" to listOf("뒷자리", "뒷좌석", "뒷시트", "뒤 좌석", "뒤에"),
)

/** 긴 말부터 맞춰 본다("센터콘솔"을 "콘솔"보다 먼저). 뒤에 "안에", "에다" 같은 말이 붙어도 함께 뺀다. */
private val PLACE_PATTERNS: List<Pair<String, Regex>> = PLACES
    .flatMap { (name, words) -> words.map { name to it } }
    .sortedByDescending { it.second.length }
    .map { (name, word) ->
        name to Regex("""(?<=\s)${Regex.escape(word)}(?:\s?(?:안쪽|안|속|밑|아래|위|옆|쪽))?(?:에다가|에다|에서|에|으로|로)?(?=\s)""")
    }

private val DUE_OFFSETS = mapOf("오늘" to 0L, "내일" to 1L, "모레" to 2L)
private val DUE_WORD = Regex("""(?<=\s)(오늘|내일|모레)(?:까지|에|은|는)?(?=\s)""")

/**
 * 문장 끝의 "넣었어", "뒀어요", "넣어 놨어", "있어" 같은 말과
 * 말로 부탁할 때 붙는 "가져왔어 챙기라고 해줘", "알려 줘", "기억해" 같은 말
 */
private val VERB = Regex("""^(?:(?:넣|뒀|둬|두었|놨|놔|놓|실었|실어|실음|있|보관|챙겼|챙겨|챙기|갖다|가져|들고|알려|말해|기억|잊지|부탁).*|해(?:줘|줘요|주세요|줄래)?|줘(?:요)?)$""")

/** 물건 이름이 아닌 말 */
private val FILLER_WORDS = setOf("나", "내가", "제가", "저", "방금", "아까", "지금", "좀", "꼭", "이거", "그거")

private val PUNCTUATION = Regex("""[.!?~,]""")
private val WHITESPACE = Regex("""\s+""")

private fun String.removeObjectParticle(): String =
    if (length >= 2 && (endsWith("을") || endsWith("를"))) dropLast(1) else this
