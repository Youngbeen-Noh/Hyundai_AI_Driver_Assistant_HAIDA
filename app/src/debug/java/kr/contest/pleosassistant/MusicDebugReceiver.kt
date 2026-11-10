package kr.contest.pleosassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kr.contest.pleosassistant.music.MusicTestSignals
import kr.contest.pleosassistant.music.Weather

/**
 * 디버그 빌드 전용: adb 로 기능 3 의 분위기 신호를 넣어 본다.
 *
 *   adb shell am broadcast -a kr.contest.pleosassistant.DEBUG_MUSIC -n kr.contest.pleosassistant/.MusicDebugReceiver --es weather RAIN
 *   … --ei hour 18 / --ez wipers true / --ez headlights true / --ei eta_delay 15 / --ez reset true
 */
class MusicDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("reset", false)) MusicTestSignals.reset()
        if (intent.hasExtra("hour")) MusicTestSignals.hour.value = intent.getIntExtra("hour", 12)
        intent.getStringExtra("weather")?.let { w ->
            MusicTestSignals.weather.value = Weather.entries.firstOrNull { it.name == w.uppercase() }
        }
        if (intent.hasExtra("wipers")) MusicTestSignals.wipersOn.value = intent.getBooleanExtra("wipers", false)
        if (intent.hasExtra("headlights")) MusicTestSignals.headlightsOn.value = intent.getBooleanExtra("headlights", false)
        if (intent.hasExtra("eta_delay")) MusicTestSignals.etaDelayMin.value = intent.getIntExtra("eta_delay", 0)
        Log.i(
            "MusicDebug",
            "신호 덮어쓰기: hour=${MusicTestSignals.hour.value} weather=${MusicTestSignals.weather.value} " +
                "wipers=${MusicTestSignals.wipersOn.value} headlights=${MusicTestSignals.headlightsOn.value} " +
                "eta=${MusicTestSignals.etaDelayMin.value}",
        )
    }
}
