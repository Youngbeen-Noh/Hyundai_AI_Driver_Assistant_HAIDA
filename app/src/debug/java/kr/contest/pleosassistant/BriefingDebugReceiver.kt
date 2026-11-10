package kr.contest.pleosassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kr.contest.pleosassistant.briefing.BriefingTestSignals

/**
 * 디버그 빌드 전용: adb 로 기능 4 의 시작 조건(목적지 없이 달린 시간)을 넣어 본다.
 *
 *   adb shell am broadcast -a kr.contest.pleosassistant.DEBUG_BRIEFING -n kr.contest.pleosassistant/.BriefingDebugReceiver --ei driving_min 5
 *   … --ez destination true (목적지 있음) / --ei driving_min 0 (정차)
 */
class BriefingDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 목적지를 먼저 넣는다(시간을 먼저 넣으면 잠깐 '목적지 없음 + 10분'이 되어 시작해 버린다)
        if (intent.hasExtra("destination")) BriefingTestSignals.hasDestination.value = intent.getBooleanExtra("destination", false)
        if (intent.hasExtra("driving_min")) BriefingTestSignals.drivingMinutes.value = intent.getIntExtra("driving_min", 0)
        Log.i(
            "BriefingDebug",
            "주행 ${BriefingTestSignals.drivingMinutes.value}분 · 목적지 ${BriefingTestSignals.hasDestination.value}",
        )
    }
}
