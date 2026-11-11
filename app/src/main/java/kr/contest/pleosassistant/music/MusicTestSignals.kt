package kr.contest.pleosassistant.music

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 에뮬레이터 확인용으로 분위기 신호를 덮어쓴다. 디버그 빌드의 MusicDebugReceiver(adb broadcast)만 값을 넣는다.
 * null 이면 실제 값(차량 · 날씨 · 시계)을 쓴다. 날씨 · 정체는 에뮬레이터에서 바꿀 방법이 없어서 둔다.
 */
object MusicTestSignals {
    val hour = MutableStateFlow<Int?>(null)
    val weather = MutableStateFlow<Weather?>(null)
    val wipersOn = MutableStateFlow<Boolean?>(null)
    val headlightsOn = MutableStateFlow<Boolean?>(null)
    val etaDelayMin = MutableStateFlow<Int?>(null)

    fun reset() {
        hour.value = null
        weather.value = null
        wipersOn.value = null
        headlightsOn.value = null
        etaDelayMin.value = null
    }
}
