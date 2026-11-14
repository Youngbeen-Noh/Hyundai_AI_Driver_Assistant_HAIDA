package kr.contest.pleosassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kr.contest.pleosassistant.drive.DriveTestSignals

/**
 * 디버그 빌드 전용: adb 로 기능 5 의 운전석 안전벨트 미착용 경고를 넣어 본다.
 *
 *   adb shell am broadcast --user 10 -a kr.contest.pleosassistant.DEBUG_DRIVE -n kr.contest.pleosassistant/.DriveDebugReceiver --ez belt true
 *   … --ez belt false(경고 꺼짐) / --ez reset true(차량 값으로 돌아감)
 */
class DriveDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("reset", false)) DriveTestSignals.beltWarning.value = null
        if (intent.hasExtra("belt")) DriveTestSignals.beltWarning.value = intent.getBooleanExtra("belt", false)
        Log.i("DriveDebug", "안전벨트 경고 덮어쓰기: ${DriveTestSignals.beltWarning.value}")
    }
}
