package kr.contest.pleosassistant.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kr.contest.pleosassistant.TestSignals
import kr.contest.pleosassistant.memory.MemoryMessages
import kr.contest.pleosassistant.memory.MemoryReport
import kr.contest.pleosassistant.memory.TAG_REPLY
import kr.contest.pleosassistant.memory.announce
import kr.contest.pleosassistant.memory.MemoryState
import kr.contest.pleosassistant.memory.MemoryStore
import kr.contest.pleosassistant.memory.Seat
import kr.contest.pleosassistant.memory.addRoutine
import kr.contest.pleosassistant.memory.askAgain
import kr.contest.pleosassistant.memory.buildMemoryReport
import kr.contest.pleosassistant.memory.check
import kr.contest.pleosassistant.memory.closeArrival
import kr.contest.pleosassistant.memory.collect
import kr.contest.pleosassistant.memory.confirmPassenger
import kr.contest.pleosassistant.memory.dismissAnnouncement
import kr.contest.pleosassistant.memory.driving
import kr.contest.pleosassistant.memory.forgetAll
import kr.contest.pleosassistant.memory.namePassenger
import kr.contest.pleosassistant.memory.onGear
import kr.contest.pleosassistant.memory.onNavi
import kr.contest.pleosassistant.memory.onNaviArrived
import kr.contest.pleosassistant.memory.onSeat
import kr.contest.pleosassistant.memory.removeRoutine
import kr.contest.pleosassistant.memory.removeRoutineItem
import kr.contest.pleosassistant.memory.skipPassenger
import kr.contest.pleosassistant.memory.submit
import kr.contest.pleosassistant.navi.NaviSource
import kr.contest.pleosassistant.status.Reading
import kr.contest.pleosassistant.vehicle.CabinSource
import kr.contest.pleosassistant.voice.ListenState
import kr.contest.pleosassistant.voice.Speaker
import kr.contest.pleosassistant.voice.VoiceInput
import java.time.LocalDateTime

/** 화면에서 누르는 것들 */
interface MemoryActions {
    fun confirmPassenger(seat: Seat, isRight: Boolean)
    fun namePassenger(seat: Seat, name: String)
    fun skipPassenger(seat: Seat)
    fun askAgain(seat: Seat)

    /** 글로 적은 부탁(물건 · 챙겼어 · 목적지별 챙길 것). 알아들었으면 true(입력칸을 비운다). owner 는 고른 동승자 */
    fun addBelonging(text: String, owner: String? = null): Boolean

    /** 이번에만 챙길 물건을 챙겼다(바로 지운다) */
    fun collect(id: Long)

    /** 체크 목록 한 줄을 누름 */
    fun check(key: String)
    fun closeArrival(collected: Boolean)
    fun dismissPanel()
    fun forgetAll()
    fun addRoutine(place: String, items: List<String>)
    fun removeRoutineItem(place: String, item: String)
    fun removeRoutine(place: String)

    /** 말로 하기(음성 인식) 시작 · 멈춤 */
    fun startListening()
    fun stopListening()
}

/**
 * 기능 2 · 소지품 · 동승자 기억. 탭을 옮겨도 하차 알림이 뜨도록 Activity 범위로 둔다.
 * 좌석 · 변속 단 · 내비 신호와 음성 인식 결과를 [MemoryState] 로 흘려 보내고, 비서가 할 말이 생기면 말한다.
 * 기억(data)이 바뀔 때마다 기기 안에 저장한다.
 */
class MemoryViewModel(
    private val cabin: CabinSource,
    private val store: MemoryStore,
    private val navi: NaviSource,
    private val speaker: Speaker,
    private val voice: VoiceInput,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel(), MemoryActions {

    private val state = MutableStateFlow(MemoryState(data = store.load()))

    val report: StateFlow<MemoryReport> = state
        .map { buildMemoryReport(it, now().toLocalDate()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, buildMemoryReport(state.value, now().toLocalDate()))

    /** 주행 중(P 가 아님)이면 true. 앱 전체 화면이 입력 · 편집을 숨기는 데 쓴다. */
    val driving: StateFlow<Boolean> = state
        .map { it.driving }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, state.value.driving)

    /** 듣는 중인지 · 지금까지 알아들은 말 */
    val listening: StateFlow<ListenState> = voice.state

    init {
        Seat.entries.forEach { seat ->
            viewModelScope.launch {
                cabin.seats
                    .map { it[seat] ?: Reading.Loading }
                    .distinctUntilChanged()
                    .collect { reading -> state.update { it.onSeat(seat, reading, now()) } }
            }
        }
        viewModelScope.launch {
            cabin.gear.collect { reading -> state.update { it.onGear(reading, now()) } }
        }
        viewModelScope.launch {
            combine(navi.status, TestSignals.navi) { real, test -> test ?: real }
                .distinctUntilChanged()
                .collect { status -> state.update { it.onNavi(status, now()) } }
        }
        viewModelScope.launch {
            merge(navi.arrivals.map { }, TestSignals.arrivals).collect { state.update { it.onNaviArrived(now()) } }
        }
        viewModelScope.launch {
            merge(voice.results, TestSignals.voice).collect { text ->
                listenLimit?.cancel()
                state.update { it.submit(text, now(), spoken = true) }
            }
        }
        // 새로 할 말이 생기면 한 번 말한다(닫기만 바뀐 것은 다시 말하지 않는다)
        viewModelScope.launch {
            state.map { it.announcement }.filterNotNull().distinctUntilChangedBy { it.seq }.collect { speaker.say(it.text) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            state.map { it.data }.distinctUntilChanged().drop(1).collect(store::save)
        }
        cabin.start()
        navi.start()
    }

    override fun confirmPassenger(seat: Seat, isRight: Boolean) = state.update { it.confirmPassenger(seat, isRight) }
    override fun namePassenger(seat: Seat, name: String) = state.update { it.namePassenger(seat, name) }
    override fun skipPassenger(seat: Seat) = state.update { it.skipPassenger(seat) }
    override fun askAgain(seat: Seat) = state.update { it.askAgain(seat) }

    override fun addBelonging(text: String, owner: String?): Boolean {
        val next = state.updateAndGet { it.submit(text, now(), owner) }
        return next.inputError == null
    }

    override fun collect(id: Long) = state.update { it.collect(id) }
    override fun check(key: String) = state.update { it.check(key) }
    override fun closeArrival(collected: Boolean) = state.update { it.closeArrival(collected) }
    override fun dismissPanel() = state.update { it.dismissAnnouncement() }
    override fun forgetAll() = state.update { it.forgetAll() }
    override fun addRoutine(place: String, items: List<String>) = state.update { it.addRoutine(place, items) }
    override fun removeRoutineItem(place: String, item: String) = state.update { it.removeRoutineItem(place, item) }
    override fun removeRoutine(place: String) = state.update { it.removeRoutine(place) }

    /** 말이 없으면 [LISTEN_LIMIT_MS] 뒤에 듣기를 멈춘다(마이크를 계속 켜 두지 않게) */
    private var listenLimit: Job? = null

    override fun startListening() {
        speaker.stop()
        voice.start()
        listenLimit?.cancel()
        listenLimit = viewModelScope.launch {
            delay(LISTEN_LIMIT_MS)
            if (voice.state.value is ListenState.Listening) {
                voice.stop()
                state.update { it.announce(TAG_REPLY, MemoryMessages.VOICE_TIMEOUT) }
            }
        }
    }

    override fun stopListening() {
        listenLimit?.cancel()
        voice.stop()
    }

    private companion object {
        const val LISTEN_LIMIT_MS = 12_000L
    }

    override fun onCleared() {
        cabin.stop()
        navi.stop()
        speaker.release()
        voice.release()
    }
}
