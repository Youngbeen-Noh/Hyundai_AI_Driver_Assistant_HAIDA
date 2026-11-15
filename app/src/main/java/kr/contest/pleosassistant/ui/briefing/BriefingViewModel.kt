package kr.contest.pleosassistant.ui.briefing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.contest.pleosassistant.briefing.BriefingSource
import kr.contest.pleosassistant.briefing.BriefingState
import kr.contest.pleosassistant.briefing.BriefingTestSignals
import kr.contest.pleosassistant.briefing.Category
import kr.contest.pleosassistant.briefing.DemoBriefings
import kr.contest.pleosassistant.briefing.NOTE_DEMO
import kr.contest.pleosassistant.briefing.PreparingBriefingSource
import kr.contest.pleosassistant.briefing.shouldStartBriefing
import kr.contest.pleosassistant.briefing.speakSeconds
import kr.contest.pleosassistant.storage.BriefingPrefs
import kr.contest.pleosassistant.storage.BriefingStore
import kr.contest.pleosassistant.voice.Speaker

/** 브리핑 화면이 그리는 값 */
data class BriefingUi(
    val state: BriefingState,
    val autoStart: Boolean,
    /** 지금 소주제에서 읽은 비율 0~1 (문장 수 기준) */
    val progress: Float,
    /** 방금 '용어 설명'으로 보여 줄 풀이(정차 중 화면) */
    val shownTerm: String?,
    /** 브리핑 출처 상태 한 줄(만드는 중 · 연결 실패 · 시연용). 없으면 null */
    val sourceNote: String? = null,
)

interface BriefingActions {
    fun more()
    fun explainTerm()
    fun nextBriefing()
    fun startNow()
    fun stop()
    fun toggleCategory(category: Category)
    fun setKeepPrepared(keep: Boolean)
    fun setAutoStart(on: Boolean)
}

/**
 * 시작 조건(목적지 없이 5분) → 큐에서 브리핑을 꺼내 문장마다 TTS 로 읽는다.
 * Pleos TTS 의 '다 읽음' 알림을 아직 쓰지 않아, 문장 길이로 읽는 시간을 짐작해 다음 문장으로 넘긴다.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class BriefingViewModel(
    private val source: BriefingSource,
    private val store: BriefingStore,
    private val speaker: Speaker,
) : ViewModel(), BriefingActions {

    private var prefs: BriefingPrefs = store.load()
    private val state = MutableStateFlow(BriefingState(prefs.settings).refill(source))
    private val autoStart = MutableStateFlow(prefs.autoStart)
    private val shownTerm = MutableStateFlow<String?>(null)
    private val preparing = source as? PreparingBriefingSource
    private val sourceNote: StateFlow<String?> = preparing?.note ?: MutableStateFlow(if (source === DemoBriefings) NOTE_DEMO else null)

    /** 시작하려 했는데 받아 둔 브리핑이 없었다 → 받으면 바로 시작 */
    private var pendingStart = false

    /** 이번 주행에서 이미 자동으로 시작했는지(정차하면 다시 false) */
    private var startedThisDrive = false
    private var lineJob: Job? = null

    val ui: StateFlow<BriefingUi> = combine(state, autoStart, shownTerm, sourceNote) { s, a, t, n -> BriefingUi(s, a, progressOf(s), t, n) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BriefingUi(state.value, autoStart.value, 0f, null, sourceNote.value))

    init {
        // 시작 조건
        viewModelScope.launch {
            combine(BriefingTestSignals.drivingMinutes, BriefingTestSignals.hasDestination, autoStart) { min, dest, auto ->
                Triple(min, dest, auto)
            }
                // 신호가 거의 같이 바뀌면(목적지 설정 + 주행 시간) 중간 상태로 시작하지 않게 잠깐 모은다
                .debounce(SIGNAL_SETTLE_MS)
                .collect { (min, dest, auto) ->
                if (min == null || min == 0) startedThisDrive = false
                val go = shouldStartBriefing(auto, state.value.settings.selected.isNotEmpty(), min ?: 0, dest)
                if (go && !startedThisDrive && !state.value.playing) {
                    startedThisDrive = true
                    begin()
                }
            }
        }
        // 미리 받아 두는 출처(브리핑 서버): 큐가 모자라면 다음 분야를 받아 채운다. 실패하면 점점 길게 쉬었다가 다시
        if (preparing != null) {
            viewModelScope.launch {
                state.map { it.upcomingCategories() }.distinctUntilChanged().collectLatest { needed ->
                    if (needed.isEmpty()) return@collectLatest
                    var wait = RETRY_FIRST_MS
                    while (!preparing.prepare(needed)) {
                        // 일부라도 받았으면 큐에 넣는다
                        refillAndMaybeStart()
                        delay(wait)
                        wait = (wait * 2).coerceAtMost(RETRY_MAX_MS)
                    }
                    refillAndMaybeStart()
                }
            }
        }
        // 문장이 바뀌면 읽고, 다 읽을 즈음 다음 문장으로
        viewModelScope.launch {
            state.map { p -> p.playback?.let { Triple(it.briefing.id, it.sub, it.line) to it.currentLine } }
                .distinctUntilChanged()
                .collect { current ->
                    lineJob?.cancel()
                    shownTerm.value = null
                    val line = current?.second ?: return@collect
                    speaker.say(line)
                    scheduleAdvance(speakSeconds(line))
                }
        }
    }

    private fun scheduleAdvance(seconds: Int) {
        lineJob?.cancel()
        lineJob = viewModelScope.launch {
            delay(seconds * 1_000L)
            state.update { it.advanceLine(source) }
        }
    }

    override fun more() {
        val (next, added) = state.value.more()
        state.value = next
        val text = if (added) BriefingState.MORE_INTRO else BriefingState.NO_MORE
        speaker.say(text)
        // 지금 문장은 끝난 것으로 보고, 안내 말을 마치면 관련 기사로 넘어간다
        if (next.playing) scheduleAdvance(speakSeconds(text))
    }

    override fun explainTerm() {
        val text = state.value.termText()
        shownTerm.value = text
        speaker.say(text)
        // 풀이를 다 들을 때까지 다음 문장으로 넘어가지 않는다
        if (state.value.playing) scheduleAdvance(speakSeconds(text))
    }

    override fun nextBriefing() {
        speaker.stop()
        state.update { it.nextBriefing(source) }
    }

    override fun startNow() = begin()

    private fun begin() {
        state.update { it.start(source) }
        // 받아 둔 브리핑이 없어 시작하지 못했으면, 받는 대로 시작한다
        pendingStart = !state.value.playing && state.value.settings.selected.isNotEmpty()
    }

    private fun refillAndMaybeStart() {
        state.update { it.refill(source) }
        if (pendingStart && !state.value.playing && state.value.queue.isNotEmpty()) {
            pendingStart = false
            state.update { it.start(source) }
        }
    }

    override fun stop() {
        pendingStart = false
        lineJob?.cancel()
        speaker.stop()
        state.update { it.stop() }
    }

    override fun toggleCategory(category: Category) {
        state.update { it.toggleCategory(category, source) }
        persist()
    }

    override fun setKeepPrepared(keep: Boolean) {
        state.update { it.setKeepPrepared(keep) }
        persist()
    }

    override fun setAutoStart(on: Boolean) {
        autoStart.value = on
        persist()
    }

    private fun persist() {
        prefs = BriefingPrefs(state.value.settings, autoStart.value)
        store.save(prefs)
    }

    override fun onCleared() {
        lineJob?.cancel()
        speaker.release()
    }

    private fun progressOf(s: BriefingState): Float {
        val p = s.playback ?: return 0f
        return (p.line + 1).toFloat() / p.script.size
    }
}

/** 시작 조건 신호가 바뀐 뒤 이만큼 더 바뀌지 않으면 판단한다 */
private const val SIGNAL_SETTLE_MS = 800L

/** 브리핑 서버에서 받지 못했을 때 다시 해 보기까지 기다리는 시간(처음 · 최대) */
private const val RETRY_FIRST_MS = 30_000L
private const val RETRY_MAX_MS = 10 * 60_000L
