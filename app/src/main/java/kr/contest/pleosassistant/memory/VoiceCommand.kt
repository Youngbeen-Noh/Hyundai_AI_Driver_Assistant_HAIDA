package kr.contest.pleosassistant.memory

import java.time.LocalDate

/** 말(음성 인식 문장)로 한 부탁 */
sealed interface VoiceCommand {
    /** 이번에만 챙길 물건. 예: "나 오늘 보고서류 가져왔어 챙기라고 해줘", "민수 가방 뒷자리에 뒀어" */
    data class Remember(val belonging: ParsedBelonging, val owner: String?) : VoiceCommand

    /** 챙겼다. item 이 null 이면 "다 챙겼어" */
    data class Collected(val item: String?, val owner: String?) : VoiceCommand

    /** 목적지마다 늘 챙길 것. 예: "헬스장 갈 때 운동화 물 회원카드 챙기라고 해줘" */
    data class AddRoutine(val place: String, val items: List<String>) : VoiceCommand
}

/**
 * 말을 부탁으로 바꾼다. 알아듣지 못하면 null.
 * @param knownNames 탔던 사람 이름. 문장 맨 앞에 이름이 오면("민수 가방 …") 그 사람의 물건으로 본다.
 */
fun parseVoiceCommand(text: String, today: LocalDate, knownNames: Collection<String>): VoiceCommand? {
    val clean = text.replace(PUNCTUATION, " ").replace(SPACES, " ").trim()
    if (clean.isEmpty()) return null

    DAILY.find(clean)?.let { match -> return routine(EVERY_DAY, match.groupValues[1]) }
    ROUTINE.find(clean)?.let { match -> return routine(match.groupValues[1].trim().removeParticle(PLACE_PARTICLES).trim(), match.groupValues[2]) }

    val (owner, rest) = splitOwner(clean, knownNames)
    if (COLLECTED.containsMatchIn(rest) && !REQUEST.containsMatchIn(rest)) return collected(rest, owner)

    // 물건을 두었다 · 가져왔다 · 챙기라는 말이 있어야 기억한다(아무 말이나 물건으로 받지 않게)
    if (!REMEMBER_CUE.containsMatchIn(rest)) return null
    val parsed = parseBelonging(rest, today) ?: return null
    return VoiceCommand.Remember(parsed, owner)
}

/** "회사 갈 때 텀블러, 사원증, 지갑 챙기라고 해줘" · "매일 텀블러 챙기라고 해줘" */
private fun routine(place: String, list: String): VoiceCommand.AddRoutine? {
    val items = list
        .split(LIST_SEPARATOR)
        .map { it.trim().removeParticle(ITEM_PARTICLES) }
        .filter { it.isNotEmpty() && it !in LIST_FILLERS }
        .distinct()
    if (place.isEmpty() || items.isEmpty()) return null
    return VoiceCommand.AddRoutine(place, items)
}

/** "보고서류 챙겼어" · "다 챙겼어" */
private fun collected(text: String, owner: String?): VoiceCommand.Collected {
    val before = text.substring(0, COLLECTED.find(text)!!.range.first)
    val words = before.split(' ').map { it.trim() }.filter { it.isNotEmpty() && it !in FILLERS }
    val item = words.joinToString(" ").removeParticle(ITEM_PARTICLES).trim()
    return VoiceCommand.Collected(item.takeUnless { it.isEmpty() || it in ALL_WORDS }, owner)
}

/** 맨 앞 낱말이 탔던 사람 이름(님 · 이 · 가 · 의 · 는 · 씨 · 거 붙어도)이면 그 사람과 나머지 말로 나눈다 */
private fun splitOwner(text: String, knownNames: Collection<String>): Pair<String?, String> {
    val words = text.split(' ').toMutableList()
    val first = words.firstOrNull() ?: return null to text
    val bare = first.removeParticle(OWNER_PARTICLES)
    val name = knownNames.firstOrNull { it.equals(bare, ignoreCase = true) } ?: return null to text
    words.removeAt(0)
    if (words.firstOrNull() == "거") words.removeAt(0)
    return name to words.joinToString(" ")
}

private fun String.removeParticle(particles: List<String>): String {
    val p = particles.firstOrNull { length > it.length && endsWith(it) } ?: return this
    return dropLast(p.length)
}

/** "매일 · 항상 · 늘" + 물건들 + 부탁: 어디로 가든 챙길 것 */
private val DAILY = Regex("""^(?:매일|항상|늘|맨날|언제나|날마다)\s+(.+?)\s+(?:챙기|챙겨|가져|알려|잊지|기억)""")

/** 장소 + "갈 때(는)" · "가면" + 물건들 + 부탁 */
private val ROUTINE = Regex("""^(.+?)\s*(?:갈\s?때(?:는|마다)?|갈\s?땐|가면|갈\s?적에)\s+(.+?)\s+(?:챙기|챙겨|가져|알려|잊지|기억)""")
private val COLLECTED = Regex("""(챙겼|챙김|가져갔|가지고\s?내렸|들고\s?내렸)""")
private val REQUEST = Regex("""(챙기라|챙기게|챙겨\s?줘|챙겨\s?달|알려|잊지\s?않게|기억해)""")
private val REMEMBER_CUE = Regex("""(넣|뒀|둬|두었|놨|놔|놓|실었|실어|가져왔|가져와|가져옴|들고\s?왔|챙기라|챙기게|챙겨\s?줘|챙겨\s?달|알려|기억해|잊지)""")

private val LIST_SEPARATOR = Regex("""\s*(?:,|\s|그리고)\s*""")
private val PLACE_PARTICLES = listOf("으로", "에는", "에", "로")
private val ITEM_PARTICLES = listOf("이랑", "하고", "랑", "와", "과", "을", "를", "도", "은", "는")
private val OWNER_PARTICLES = listOf("님이", "님의", "님", "씨", "이가", "이의", "이는", "이", "가", "의", "는", "거")
private val LIST_FILLERS = setOf("꼭", "좀", "다", "그리고")
private val FILLERS = setOf("나", "내가", "제가", "저", "방금", "아까", "지금", "이제", "좀", "꼭")
private val ALL_WORDS = setOf("다", "전부", "모두", "다들", "모두다")

private val PUNCTUATION = Regex("""[.!?~]""")
private val SPACES = Regex("""\s+""")
