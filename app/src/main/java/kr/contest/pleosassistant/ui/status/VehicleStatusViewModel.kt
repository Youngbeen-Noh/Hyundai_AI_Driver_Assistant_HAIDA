package kr.contest.pleosassistant.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kr.contest.pleosassistant.status.StatusReport
import kr.contest.pleosassistant.status.buildStatusReport
import kr.contest.pleosassistant.vehicle.VehicleStatusSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 차량 값 → 상태 카드 · 요약 문장. 화면은 [report] 만 그린다. */
class VehicleStatusViewModel(private val source: VehicleStatusSource) : ViewModel() {

    val report: StateFlow<StatusReport> = source.snapshot
        .map(::buildStatusReport)
        .stateIn(viewModelScope, SharingStarted.Eagerly, buildStatusReport(source.snapshot.value))

    init {
        source.start()
    }

    override fun onCleared() {
        source.stop()
    }
}
