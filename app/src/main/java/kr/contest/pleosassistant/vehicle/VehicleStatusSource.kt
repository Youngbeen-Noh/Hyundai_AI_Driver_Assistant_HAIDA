package kr.contest.pleosassistant.vehicle

import kr.contest.pleosassistant.status.VehicleSnapshot
import kotlinx.coroutines.flow.StateFlow

/** 기능 1 이 읽는 차량 값의 출처. 실제 차량은 [PleosVehicleStatusSource], 테스트는 가짜 구현을 쓴다. */
interface VehicleStatusSource {
    val snapshot: StateFlow<VehicleSnapshot>

    /** 지원 여부를 확인하고 값을 읽은 뒤 변화를 구독한다. 여러 번 불러도 한 번만 시작한다. */
    fun start()

    /** 구독을 해제한다. */
    fun stop()
}
