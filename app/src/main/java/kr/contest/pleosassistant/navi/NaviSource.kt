package kr.contest.pleosassistant.navi

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kr.contest.pleosassistant.memory.NaviStatus

/** 내비 안내 상태(목적지 · 남은 거리)와 도착 알림 */
interface NaviSource {
    val status: StateFlow<NaviStatus>

    /** 목적지에 도착하면 그 이름 */
    val arrivals: SharedFlow<String>
    fun start()
    fun stop()
}
