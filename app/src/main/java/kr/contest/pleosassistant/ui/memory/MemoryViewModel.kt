package kr.contest.pleosassistant.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kr.contest.pleosassistant.memory.MemoryReport
import kr.contest.pleosassistant.memory.MemoryState
import kr.contest.pleosassistant.memory.MemoryStore
import kr.contest.pleosassistant.memory.Seat
import kr.contest.pleosassistant.memory.addBelonging
import kr.contest.pleosassistant.memory.askAgain
import kr.contest.pleosassistant.memory.buildMemoryReport
import kr.contest.pleosassistant.memory.closeArrival
import kr.contest.pleosassistant.memory.collect
import kr.contest.pleosassistant.memory.confirmPassenger
import kr.contest.pleosassistant.memory.driving
import kr.contest.pleosassistant.memory.forgetAll
import kr.contest.pleosassistant.memory.namePassenger
import kr.contest.pleosassistant.memory.onGear
import kr.contest.pleosassistant.memory.onSeat
import kr.contest.pleosassistant.memory.skipPassenger
import kr.contest.pleosassistant.status.Reading
import kr.contest.pleosassistant.vehicle.CabinSource
import java.time.LocalDateTime

/** 화면에서 누르는 것들 */
interface MemoryActions {
    fun confirmPassenger(seat: Seat, isRight: Boolean)
    fun namePassenger(seat: Seat, name: String)
    fun skipPassenger(seat: Seat)
    fun askAgain(seat: Seat)

    /** 알아들었으면 true(입력칸을 비운다) */
    fun addBelonging(text: String): Boolean
    fun collect(id: Long)
    fun closeArrival(collected: Boolean)
    fun forgetAll()
}

/**
 * 기능 2 · 소지품 · 동승자 기억. 탭을 옮겨도 하차 알림이 뜨도록 Activity 범위로 둔다.
 * 좌석 · 변속 단 신호를 [MemoryState] 로 흘려 보내고, 기억(data)이 바뀔 때마다 기기 안에 저장한다.
 */
class MemoryViewModel(
    private val cabin: CabinSource,
    private val store: MemoryStore,
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
        viewModelScope.launch(Dispatchers.IO) {
            state.map { it.data }.distinctUntilChanged().drop(1).collect(store::save)
        }
        cabin.start()
    }

    override fun confirmPassenger(seat: Seat, isRight: Boolean) = state.update { it.confirmPassenger(seat, isRight) }
    override fun namePassenger(seat: Seat, name: String) = state.update { it.namePassenger(seat, name) }
    override fun skipPassenger(seat: Seat) = state.update { it.skipPassenger(seat) }
    override fun askAgain(seat: Seat) = state.update { it.askAgain(seat) }

    override fun addBelonging(text: String): Boolean {
        val next = state.updateAndGet { it.addBelonging(text, now()) }
        return next.inputError == null
    }

    override fun collect(id: Long) = state.update { it.collect(id) }
    override fun closeArrival(collected: Boolean) = state.update { it.closeArrival(collected) }
    override fun forgetAll() = state.update { it.forgetAll() }

    override fun onCleared() {
        cabin.stop()
    }
}
