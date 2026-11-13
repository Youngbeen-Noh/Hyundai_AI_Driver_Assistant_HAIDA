package kr.contest.pleosassistant.briefing

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 에뮬레이터 확인용 주행 신호. 디버그 빌드의 BriefingDebugReceiver(adb broadcast)만 값을 넣는다.
 * '목적지 없이 달린 시간'은 변속 단(기능 2) · 내비 목적지(기능 2) 신호가 main 에 합쳐지면 그것으로 센다.
 */
object BriefingTestSignals {
    /** 목적지 없이 달린 분. null 이면 아직 모름(실제 신호 연결 전) */
    val drivingMinutes = MutableStateFlow<Int?>(null)
    val hasDestination = MutableStateFlow(false)
}
