package kr.contest.pleosassistant.drive

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 에뮬레이터 확인용으로 운전석 안전벨트 미착용 경고를 덮어쓴다. 디버그 빌드의 DriveDebugReceiver(adb broadcast)만 값을 넣는다.
 * null 이면 차량 값(Safety.registerSeatBeltNotBuckledWarning)을 쓴다.
 * Pleos 에뮬레이터는 Virtual Console 에 벨트 스위치가 없고, 차량 서비스가 전원 상태 때문에 경고를 내지 않아서 둔다(2026-11-12 확인).
 */
object DriveTestSignals {
    val beltWarning = MutableStateFlow<Boolean?>(null)
}
