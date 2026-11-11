package kr.contest.pleosassistant.music

// 기능 3 · 노래 자동 추천 (docs/기능계획서.pdf 5장)
// 날씨 · 시간대 · 교통 · 차량 신호(와이퍼 · 전조등)로 '분위기'를 먼저 정하고, 그 분위기에 맞는 곡 5개를 고른다.

/** 분위기 6종. 이름은 기능계획서 · 팀 UI 프로토타입과 같다. */
enum class Mood(val label: String) {
    FRESH("상쾌한 출발"),
    DRIVE("신나는 드라이브"),
    CRUISE("여유로운 크루징"),
    RAIN("촉촉한 빗길"),
    SUNSET("노을 감성"),
    NIGHT("잔잔한 밤길"),
}

/** 날씨 API 결과를 분위기 판단에 필요한 만큼만 줄인 것 */
enum class Weather { CLEAR, CLOUDY, RAIN, SNOW }

/**
 * 분위기를 정하는 신호. 모르는 값은 null 이다(차가 지원하지 않거나, 권한 · 통신이 없을 때).
 * @property hour 지금 시각(0~23)
 * @property etaDelayMin 내비 ETA 가 처음보다 늘어난 분. 0 이하면 막히지 않는 길
 */
data class MoodSignals(
    val hour: Int,
    val weather: Weather? = null,
    val wipersOn: Boolean? = null,
    val headlightsOn: Boolean? = null,
    val etaDelayMin: Int? = null,
)

/** 고른 분위기와 그 이유(화면 · 음성으로 그대로 말한다) */
data class MoodDecision(val mood: Mood, val reason: String)

/** ETA 가 이만큼(분) 늘면 정체로 본다 */
const val JAM_DELAY_MIN = 10

/**
 * 신호 → 분위기. 앞의 규칙이 이긴다.
 * 1. 와이퍼가 돌면 날씨 API 보다 먼저 빗길 (기능계획서: 차량 신호로 보정)
 * 2. 날씨가 비 · 눈이면 빗길
 * 3. 정체(ETA +10분 이상)면 차분한 곡: 밤이면 밤길, 아니면 크루징
 * 4. 전조등이 켜졌거나 밤(21~4시)이면 밤길
 * 5. 해 질 무렵(17~19시)이면 노을
 * 6. 아침(5~10시)이면 상쾌한 출발
 * 7. 길이 뚫려 있으면(ETA 늘지 않음) 신나는 드라이브
 * 8. 그 밖에는 여유로운 크루징
 */
fun decideMood(s: MoodSignals): MoodDecision {
    val night = s.hour >= 21 || s.hour < 5
    return when {
        s.wipersOn == true -> MoodDecision(Mood.RAIN, "와이퍼가 움직여서 골랐어요")
        s.weather == Weather.RAIN -> MoodDecision(Mood.RAIN, "비가 와서 골랐어요")
        s.weather == Weather.SNOW -> MoodDecision(Mood.RAIN, "눈이 와서 골랐어요")
        (s.etaDelayMin ?: 0) >= JAM_DELAY_MIN ->
            if (night || s.headlightsOn == true) MoodDecision(Mood.NIGHT, "길이 막혀서 골랐어요")
            else MoodDecision(Mood.CRUISE, "길이 막혀서 골랐어요")
        s.headlightsOn == true -> MoodDecision(Mood.NIGHT, "전조등을 켜서 골랐어요")
        night -> MoodDecision(Mood.NIGHT, "늦은 시간이라 골랐어요")
        s.hour in 17..19 -> MoodDecision(Mood.SUNSET, "해 질 무렵이라 골랐어요")
        s.hour in 5..10 -> MoodDecision(Mood.FRESH, "아침 출발이라 골랐어요")
        s.etaDelayMin != null && s.etaDelayMin <= 0 -> MoodDecision(Mood.DRIVE, "도로가 뻥 뚫려서 골랐어요")
        else -> MoodDecision(Mood.CRUISE, "막히지 않는 길이라 골랐어요")
    }
}
