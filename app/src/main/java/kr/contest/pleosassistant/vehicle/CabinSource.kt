package kr.contest.pleosassistant.vehicle

import kotlinx.coroutines.flow.StateFlow
import kr.contest.pleosassistant.memory.Gear
import kr.contest.pleosassistant.memory.Seat
import kr.contest.pleosassistant.status.Reading

/** 기능 2 가 쓰는 실내 신호: 좌석별 착석 여부와 변속 단. */
interface CabinSource {
    val seats: StateFlow<Map<Seat, Reading<Boolean>>>
    val gear: StateFlow<Reading<Gear>>
    fun start()
    fun stop()
}
