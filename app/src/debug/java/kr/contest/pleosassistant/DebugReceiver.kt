package kr.contest.pleosassistant

import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kr.contest.pleosassistant.memory.NaviStatus

/**
 * 에뮬레이터 확인용 수신기(디버그 빌드만). 앱이 켜져 있을 때 adb 로 보낸다.
 *
 * ```
 * A="adb shell am broadcast -n kr.contest.pleosassistant/.DebugReceiver -a kr.contest.pleosassistant.DEBUG"
 * $A --es voice "'나 오늘 보고서류 가져왔어 챙기라고 해줘'"     # 음성 인식 결과 대신
 * $A --es dest "'회사'" --es bookmark "'회사'" --ei meters 3000  # 내비 목적지 · 남은 거리(m)
 * $A --ei meters 300                                            # 남은 거리만 바꾸기(도착 직전)
 * $A --ez arrive true                                           # 내비 도착 알림
 * $A --ez clear true                                            # 안내 끝(목적지 없음)
 * $A --ez real true                                             # 덮어쓰기 끄고 실제 내비 값으로
 * $A --ez route true                                            # 실제 내비에 42dot 목적지 요청
 * ```
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("voice")?.let {
            Log.i(TAG, "말 넣기: $it")
            TestSignals.voice.tryEmit(it)
        }
        val dest = intent.getStringExtra("dest")
        val meters = intent.getIntExtra("meters", -1).takeIf { it >= 0 }
        if (dest != null || meters != null) {
            val before = TestSignals.navi.value ?: NaviStatus()
            TestSignals.navi.value = before.copy(
                destination = dest ?: before.destination,
                bookmark = intent.getStringExtra("bookmark") ?: if (dest != null) null else before.bookmark,
                remainMeters = meters ?: before.remainMeters,
                remainSeconds = meters?.let { it / 10 } ?: before.remainSeconds,
            )
            Log.i(TAG, "내비 넣기: ${TestSignals.navi.value}")
        }
        if (intent.getBooleanExtra("arrive", false)) TestSignals.arrivals.tryEmit(Unit)
        if (intent.getBooleanExtra("clear", false)) TestSignals.navi.value = NaviStatus()
        if (intent.getBooleanExtra("real", false)) TestSignals.navi.value = null
        if (intent.getBooleanExtra("route", false)) requestRoute(context)
    }

    private fun requestRoute(context: Context) {
        val helper = NaviHelper(context.applicationContext)
        helper.initialize(
            onConnected = {
                Log.i(TAG, "실제 내비에 경로 요청")
                helper.requestRoute(
                    RouteInfo(
                        latitude = 37.48544722,
                        longitude = 127.03636666,
                        displayLongitude = null,
                        displayLatitude = null,
                        poiName = "42dot",
                        poiId = "36khp37hh8gqcm5nqhe5",
                        poiSubId = "0",
                        address = "서울시 강남구 남부순환로 2621",
                    ),
                )
            },
            onFailed = { Log.w(TAG, "내비 연결 실패 $it") },
        )
    }

    private companion object {
        const val TAG = "DebugReceiver"
    }
}
