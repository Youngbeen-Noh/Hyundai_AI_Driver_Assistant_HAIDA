package kr.contest.pleosassistant.briefing

/** 브리핑을 만들어 주는 곳. 브리핑 서버(Claude 웹 검색 + 대본)는 [ServerBriefingSource], 서버 설정이 없으면 [DemoBriefings] */
fun interface BriefingSource {
    /** [category] 의 새 브리핑. 만들 수 없으면 null. [seq] 는 같은 분야의 몇 번째 요청인지 */
    fun briefing(category: Category, seq: Int): Briefing?
}

/**
 * @property selected 고른 관심 분야. 큐는 이 순서로 돌아가며 채운다
 * @property keepPrepared 분야를 빼도 이미 준비된 브리핑은 듣기(기본은 큐에서도 바로 지운다)
 */
data class BriefingSettings(
    val selected: List<Category> = listOf(Category.IT, Category.TRAVEL, Category.GAME),
    val keepPrepared: Boolean = false,
)

/**
 * 지금 읽는 위치.
 * @property script 지금 소주제의 문장들. '더 자세히'를 누르면 지금 문장 뒤에 관련 기사 요약이 끼어든다
 */
data class Playback(
    val briefing: Briefing,
    val sub: Int,
    val line: Int,
    val script: List<String>,
    val moreAdded: Boolean = false,
) {
    val subtopic: Subtopic get() = briefing.subtopics[sub]
    val currentLine: String get() = script[line]
}

/** 큐에 미리 준비해 두는 브리핑 수(화면의 '다음 분야' 2개) */
const val QUEUE_SIZE = 2

/**
 * 브리핑 큐와 재생 위치. 안드로이드에 의존하지 않아 단위 테스트로 검증한다.
 * 큐는 분야를 돌아가며 채워 같은 분야가 연달아 나오지 않는다(게임 → IT·테크 → 여행 → 게임 → …).
 * @property rotation 다음에 만들 분야의 차례(selected 의 위치)
 * @property seq 분야별로 지금까지 만든 브리핑 수
 */
data class BriefingState(
    val settings: BriefingSettings = BriefingSettings(),
    val queue: List<Briefing> = emptyList(),
    val playback: Playback? = null,
    val rotation: Int = 0,
    val seq: Map<Category, Int> = emptyMap(),
) {
    val playing: Boolean get() = playback != null

    /** 큐를 [QUEUE_SIZE] 개까지 채운다. 만들지 못한 분야는 건너뛴다 */
    fun refill(source: BriefingSource): BriefingState {
        var s = this
        var attempts = 0
        val cats = settings.selected
        while (s.queue.size < QUEUE_SIZE && cats.isNotEmpty() && attempts < cats.size * QUEUE_SIZE) {
            val cat = cats[s.rotation % cats.size]
            val n = s.seq[cat] ?: 0
            val made = source.briefing(cat, n)?.fitToLimit()
            s = s.copy(
                queue = if (made != null) s.queue + made else s.queue,
                rotation = (s.rotation + 1) % cats.size,
                seq = s.seq + (cat to n + 1),
            )
            attempts++
        }
        return s
    }

    /** 큐를 채울 때 차례로 요청할 분야(미리 받아 두는 출처가 준비할 것) */
    fun upcomingCategories(): List<Category> {
        val cats = settings.selected
        if (cats.isEmpty()) return emptyList()
        return (0 until (QUEUE_SIZE - queue.size).coerceAtLeast(0)).map { cats[(rotation + it) % cats.size] }
    }

    /** 브리핑을 시작한다(이미 읽는 중이면 그대로) */
    fun start(source: BriefingSource): BriefingState {
        if (playing) return this
        return refill(source).popNext().refill(source)
    }

    /** '다음 브리핑': 지금 브리핑을 끝내고 큐의 다음 분야로. 큐가 비면 멈춘다 */
    fun nextBriefing(source: BriefingSource): BriefingState = popNext().refill(source)

    /** 브리핑을 멈춘다(큐는 그대로) */
    fun stop(): BriefingState = copy(playback = null)

    /** 지금 문장을 다 읽었다 → 다음 문장 · 다음 소주제 · 다음 브리핑 */
    fun advanceLine(source: BriefingSource): BriefingState {
        val p = playback ?: return this
        return when {
            p.line + 1 < p.script.size -> copy(playback = p.copy(line = p.line + 1))
            p.sub + 1 < p.briefing.subtopics.size -> {
                val sub = p.briefing.subtopics[p.sub + 1]
                copy(playback = Playback(p.briefing, p.sub + 1, 0, sub.lines))
            }
            else -> nextBriefing(source)
        }
    }

    /** '더 자세히': 지금 소주제의 관련 기사 요약을 지금 문장 바로 뒤에 넣는다. 넣었으면 true */
    fun more(): Pair<BriefingState, Boolean> {
        val p = playback ?: return this to false
        if (p.moreAdded || p.subtopic.related.isEmpty()) return this to false
        val script = p.script.take(p.line + 1) + p.subtopic.related + p.script.drop(p.line + 1)
        return copy(playback = p.copy(script = script, moreAdded = true)) to true
    }

    /** '용어 설명'으로 말할 문장 */
    fun termText(): String {
        val term = playback?.subtopic?.term ?: return NO_TERM
        return "${term.word}${josaEun(term.word)} ${term.meaning}"
    }

    /** 분야 고르기 · 빼기. 빼면 기본으로 그 분야의 준비된 브리핑도 큐에서 지운다 */
    fun toggleCategory(cat: Category, source: BriefingSource): BriefingState {
        val on = cat in settings.selected
        val selected = if (on) settings.selected - cat else settings.selected + cat
        val queue = if (on && !settings.keepPrepared) queue.filterNot { it.category == cat } else queue
        val rotation = if (selected.isEmpty()) 0 else rotation % selected.size
        return copy(settings = settings.copy(selected = selected), queue = queue, rotation = rotation).refill(source)
    }

    fun setKeepPrepared(keep: Boolean): BriefingState = copy(settings = settings.copy(keepPrepared = keep))

    private fun popNext(): BriefingState {
        val next = queue.firstOrNull() ?: return copy(playback = null)
        val first = next.subtopics.first()
        val opening = "${next.category.label} 브리핑이에요. 소식 ${next.subtopics.size}개를 들려 드릴게요."
        return copy(queue = queue.drop(1), playback = Playback(next, 0, 0, listOf(opening) + first.lines))
    }

    companion object {
        const val NO_TERM = "방금 나온 말 중엔 어려운 용어가 없었어요."
        const val MORE_INTRO = "관련 기사를 더 모아서 이어서 설명할게요."
        const val NO_MORE = "이 소식은 더 모은 기사가 없어요."

        /** 받침이 있으면 '은', 없으면 '는'. 한글로 끝나지 않으면(예: HBM) '은' */
        private fun josaEun(word: String): String {
            val ch = word.lastOrNull() ?: return "은"
            if (ch !in '가'..'힣') return if (ch.uppercaseChar() in "LMNR") "은" else "는"
            return if ((ch - '가') % 28 == 0) "는" else "은"
        }
    }
}
