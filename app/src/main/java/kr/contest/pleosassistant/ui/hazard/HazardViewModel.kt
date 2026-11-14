package kr.contest.pleosassistant.ui.hazard

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
import kotlinx.coroutines.launch
import kr.contest.pleosassistant.hazard.AlertSchedule
import kr.contest.pleosassistant.hazard.HazardReport
import kr.contest.pleosassistant.hazard.HazardState
import kr.contest.pleosassistant.hazard.HazardStore
import kr.contest.pleosassistant.hazard.addZone
import kr.contest.pleosassistant.hazard.buildHazardReport
import kr.contest.pleosassistant.hazard.clearNotice
import kr.contest.pleosassistant.hazard.dismissAlert
import kr.contest.pleosassistant.hazard.onGear
import kr.contest.pleosassistant.hazard.onPosition
import kr.contest.pleosassistant.hazard.removeZone
import kr.contest.pleosassistant.hazard.setEnabled
import kr.contest.pleosassistant.hazard.setRadius
import kr.contest.pleosassistant.hazard.setSchedule
import kr.contest.pleosassistant.hazard.speechSince
import kr.contest.pleosassistant.vehicle.CabinSource
import kr.contest.pleosassistant.vehicle.LocationSource
import kr.contest.pleosassistant.voice.Speaker
import java.time.LocalDateTime

/** 화면에서 누르는 것들. 팀 UI 템플릿으로 바꿔도 이 목록은 그대로 쓴다. */
interface HazardActions {
    fun addZone(name: String, radiusM: Int, schedule: AlertSchedule)
    fun setEnabled(id: Long, enabled: Boolean)
    fun setRadius(id: Long, radiusM: Int)
    fun setSchedule(id: Long, schedule: AlertSchedule)
    fun removeZone(id: Long)
    fun dismissAlert()
    fun clearNotice()
}

/**
 * 기능 6 · 위험 구간. 경고가 어느 탭에서든 뜨도록 Activity 범위로 둔다.
 * 위치 · 변속 단 신호를 [HazardState] 로 흘려 보내고, 구간(data)이 바뀔 때마다 기기 안에 저장한다.
 * 새 경고는 [speaker] 로 소리 내어 말한다.
 */
class HazardViewModel(
    private val location: LocationSource,
    private val cabin: CabinSource,
    private val store: HazardStore,
    private val speaker: Speaker,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel(), HazardActions {

    private val state = MutableStateFlow(HazardState(data = store.load()))

    val report: StateFlow<HazardReport> = state
        .map { buildHazardReport(it, now()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, buildHazardReport(state.value, now()))

    init {
        viewModelScope.launch {
            location.position.collect { reading -> state.update { it.onPosition(reading, now()) } }
        }
        viewModelScope.launch {
            cabin.gear.collect { reading -> state.update { it.onGear(reading) } }
        }
        viewModelScope.launch(Dispatchers.IO) {
            state.map { it.data }.distinctUntilChanged().drop(1).collect(store::save)
        }
        // 새 경고를 소리 내어 말한다. 상태가 몰려 와도 마지막으로 본 상태와 비교하므로 놓치거나 두 번 말하지 않는다
        viewModelScope.launch {
            var last = state.value
            state.collect { next ->
                next.speechSince(last)?.let(speaker::say)
                last = next
            }
        }
        location.start()
        cabin.start()
    }

    /** 위치 권한을 받은 뒤 다시 시작한다 */
    fun onLocationPermissionResult() = location.start()

    override fun addZone(name: String, radiusM: Int, schedule: AlertSchedule) =
        state.update { it.addZone(name, radiusM, schedule, now()) }

    override fun setEnabled(id: Long, enabled: Boolean) = state.update { it.setEnabled(id, enabled) }
    override fun setRadius(id: Long, radiusM: Int) = state.update { it.setRadius(id, radiusM) }
    override fun setSchedule(id: Long, schedule: AlertSchedule) = state.update { it.setSchedule(id, schedule) }
    override fun removeZone(id: Long) = state.update { it.removeZone(id) }
    override fun dismissAlert() = state.update { it.dismissAlert() }
    override fun clearNotice() = state.update { it.clearNotice() }

    override fun onCleared() {
        location.stop()
        speaker.release()
    }
}
