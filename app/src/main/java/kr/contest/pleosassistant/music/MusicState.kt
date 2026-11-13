package kr.contest.pleosassistant.music

/**
 * 음악 화면의 상태. 차량 · 안드로이드에 의존하지 않아 단위 테스트로 검증한다.
 * 재생목록은 늘 '지금 곡 + 다음 4곡'([PLAYLIST_SIZE])이다. 곡이 넘어가면 맨 앞을 빼고,
 * 바뀐 취향으로 다시 점수를 매겨 한 곡을 뒤에 채운다.
 *
 * @property manualMood 사용자가 직접 고른 분위기. null 이면 신호로 정한다
 * @property announcement 이번 변화로 비서가 한 번 말할 문장(분위기가 바뀌었을 때). 화면이 읽은 뒤 [consumeAnnouncement]
 */
data class MusicState(
    val catalog: List<Track>,
    val signals: MoodSignals,
    val taste: Taste,
    val manualMood: Mood? = null,
    val decision: MoodDecision,
    val queue: List<Track>,
    val paused: Boolean = false,
    val announcement: String? = null,
) {
    val mood: Mood get() = decision.mood
    val current: Track? get() = queue.firstOrNull()
    val upcoming: List<Track> get() = queue.drop(1)
    val currentSaved: Boolean get() = current?.id in taste.saved

    /** 차량 · 날씨 신호가 바뀌었다. 직접 고른 분위기가 있으면 신호만 기억하고 곡은 그대로 둔다 */
    fun withSignals(next: MoodSignals): MusicState {
        if (next == signals) return this
        if (manualMood != null) return copy(signals = next)
        return redecide(copy(signals = next), decideMood(next))
    }

    /** 분위기를 직접 고른다. null 이면 다시 자동 */
    fun chooseMood(mood: Mood?): MusicState {
        val decision = if (mood == null) decideMood(signals) else MoodDecision(mood, "직접 고른 분위기")
        return redecide(copy(manualMood = mood), decision, force = true)
    }

    /** '다음' 버튼: 건너뛴 곡의 장르 선호도를 낮추고 다음 곡으로 */
    fun skip(): MusicState {
        val now = current ?: return this
        return advance(taste.skipped(now), rerank = true)
    }

    /** 곡이 끝까지 재생됐다(음악 앱이 다음 곡으로 넘어감). 선호도는 바꾸지 않는다 */
    fun finished(): MusicState = advance(taste)

    /** '저장' 버튼: 장르 선호도를 높인다. 다시 누르면 취소 */
    fun toggleSave(): MusicState {
        val now = current ?: return this
        return copy(taste = taste.toggleSaved(now))
    }

    fun togglePause(): MusicState = copy(paused = !paused)

    /** 곡 목록이 새로 생겼다(앱에 든 곡 분석이 끝남). 지금 분위기로 다시 고르고 말은 하지 않는다 */
    fun withCatalog(next: List<Track>): MusicState {
        val queue = recommend(next, mood, taste)
        return copy(
            catalog = next,
            queue = queue,
            taste = queue.firstOrNull()?.let(taste::played) ?: taste,
            announcement = null,
        )
    }

    fun consumeAnnouncement(): MusicState = copy(announcement = null)

    /**
     * @param rerank 건너뛰었을 때는 남은 곡을 바뀐 취향으로 다시 줄 세운다(건너뛴 장르가 바로 뒤로 밀린다).
     *               끝까지 들었을 때는 순서를 그대로 둔다
     */
    private fun advance(updated: Taste, rerank: Boolean = false): MusicState {
        val left = queue.drop(1)
        val rest = if (rerank) left.sortedByDescending { scoreTrack(it, mood, updated) } else left
        val refill = recommend(catalog.filterNot { t -> t.id in rest.map { it.id } || t.id == current?.id }, mood, updated, 1)
        val queue = rest + refill
        val played = queue.firstOrNull()?.let(updated::played) ?: updated
        return copy(taste = played, queue = queue, paused = false, announcement = null)
    }

    private fun redecide(base: MusicState, decision: MoodDecision, force: Boolean = false): MusicState {
        if (!force && decision.mood == base.mood) return base.copy(decision = decision)
        val queue = recommend(catalog, decision.mood, base.taste)
        return base.copy(
            decision = decision,
            queue = queue,
            taste = queue.firstOrNull()?.let(base.taste::played) ?: base.taste,
            paused = false,
            announcement = announce(decision),
        )
    }

    companion object {
        fun start(catalog: List<Track>, signals: MoodSignals, taste: Taste = Taste()): MusicState {
            val decision = decideMood(signals)
            val queue = recommend(catalog, decision.mood, taste)
            return MusicState(
                catalog = catalog,
                signals = signals,
                taste = queue.firstOrNull()?.let(taste::played) ?: taste,
                decision = decision,
                queue = queue,
                announcement = announce(decision),
            )
        }

        /** 예: "비가 와서 촉촉한 빗길로 골랐어요." */
        fun announce(d: MoodDecision): String =
            if (d.reason == "직접 고른 분위기") "${d.mood.label}${josaRo(d.mood.label)} 바꿨어요."
            else "${d.reason.removeSuffix(" 골랐어요")} ${d.mood.label}${josaRo(d.mood.label)} 골랐어요."

        /** 받침 없거나 ㄹ 받침이면 '로', 그 밖에는 '으로' */
        private fun josaRo(word: String): String {
            val ch = word.lastOrNull() ?: return "로"
            if (ch !in '가'..'힣') return "로"
            val jong = (ch - '가') % 28
            return if (jong == 0 || jong == 8) "로" else "으로"
        }
    }
}
