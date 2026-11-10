package kr.contest.pleosassistant

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kr.contest.pleosassistant.memory.NaviStatus

/**
 * 에뮬레이터 확인용 입력. 디버그 빌드의 DebugReceiver(adb broadcast)만 값을 넣는다(릴리스에는 넣는 곳이 없다).
 * 에뮬레이터 내비는 차가 움직이지 않아 남은 거리가 줄지 않고, adb 로는 한글을 말할 수 없어서 쓴다.
 */
object TestSignals {
    /** 음성 인식 결과 대신 넣는 문장 */
    val voice = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** 내비 상태를 덮어쓴다. null 이면 실제 내비 값 */
    val navi = MutableStateFlow<NaviStatus?>(null)

    /** 내비 도착 알림 */
    val arrivals = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
}
