package kr.contest.pleosassistant.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.contest.pleosassistant.music.DemoCatalog
import kr.contest.pleosassistant.music.Mood
import kr.contest.pleosassistant.music.MoodSignals
import kr.contest.pleosassistant.music.MusicApp
import kr.contest.pleosassistant.music.MusicPlayer
import kr.contest.pleosassistant.music.MusicState
import kr.contest.pleosassistant.music.MusicTestSignals
import kr.contest.pleosassistant.music.PlayerLink
import kr.contest.pleosassistant.music.Weather
import kr.contest.pleosassistant.music.WeatherSource
import kr.contest.pleosassistant.status.Reading
import kr.contest.pleosassistant.storage.MusicSettings
import kr.contest.pleosassistant.storage.MusicStore
import kr.contest.pleosassistant.vehicle.ExteriorSource
import kr.contest.pleosassistant.voice.Speaker
import java.time.LocalTime

/** 음악 화면이 그리는 값 */
data class MusicUi(
    val state: MusicState,
    /** 지금 곡을 들은 초(데모는 앱이 센다) */
    val elapsedSec: Int,
    val app: MusicApp,
    val link: PlayerLink,
)

/** 음악 화면의 버튼 */
interface MusicActions {
    fun togglePause()
    fun skip()
    fun toggleSave()
    fun chooseMood(mood: Mood?)
    fun chooseApp(app: MusicApp)
}

/**
 * 분위기 신호(시계 · 날씨 · 와이퍼 · 전조등 · 디버그 덮어쓰기) → [MusicState] → 음악 앱 재생 · 비서 음성.
 * 분위기가 바뀌면 비서가 한 번 말하고("비가 와서 촉촉한 빗길로 골랐어요."), 곡이 바뀌면 음악 앱에 재생을 요청한다.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class MusicViewModel(
    private val exterior: ExteriorSource,
    private val weatherSource: WeatherSource,
    private val player: MusicPlayer,
    private val store: MusicStore,
    private val speaker: Speaker,
    private val now: () -> LocalTime = LocalTime::now,
) : ViewModel(), MusicActions {

    private var settings: MusicSettings = store.load()
    private val hour = MutableStateFlow(now().hour)
    private val weather = MutableStateFlow<Weather?>(null)
    private val app = MutableStateFlow(settings.app)
    private val elapsed = MutableStateFlow(0)

    // 앱을 켤 때 고른 분위기는 말하지 않는다(탑승 직후에는 차량 상태 요약이 먼저). 화면에는 이유가 보인다
    private val state = MutableStateFlow(MusicState.start(DemoCatalog.tracks, currentSignals(), settings.taste).consumeAnnouncement())

    val ui: StateFlow<MusicUi> = combine(state, elapsed, app, player.link) { s, e, a, l -> MusicUi(s, e, a, l) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MusicUi(state.value, 0, app.value, player.link.value))

    init {
        exterior.start()
        player.use(settings.app)

        // 시계: 1분마다 시각을 다시 본다
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                hour.value = now().hour
            }
        }
        // 날씨: 15분마다
        viewModelScope.launch {
            while (true) {
                weatherSource.current()?.let { weather.value = it }
                delay(15 * 60_000L)
            }
        }
        // 신호가 바뀌면 분위기를 다시 정한다
        viewModelScope.launch {
            val vehicle = combine(exterior.wipersOn, exterior.headlightsOn) { w, h -> w to h }
            val overrides = combine(
                MusicTestSignals.hour, MusicTestSignals.weather, MusicTestSignals.wipersOn,
                MusicTestSignals.headlightsOn, MusicTestSignals.etaDelayMin,
            ) { h, w, wp, hl, eta -> listOf(h, w, wp, hl, eta) }
            // 신호 여러 개가 거의 같이 바뀌면(와이퍼 끄고 전조등 켬) 중간 분위기를 말하지 않게 잠깐 모은다
            combine(hour, weather, vehicle, overrides) { _, _, _, _ -> currentSignals() }
                .debounce(SIGNAL_SETTLE_MS)
                .distinctUntilChanged()
                .collect { signals -> state.update { it.withSignals(signals) } }
        }
        // 분위기가 바뀌면 한 번 말한다
        viewModelScope.launch {
            state.map { it.announcement }.distinctUntilChanged().collect { text ->
                if (text != null) {
                    speaker.say(text)
                    state.update { it.consumeAnnouncement() }
                }
            }
        }
        // 곡이 바뀌면 음악 앱에 재생을 요청하고, 취향을 저장한다
        viewModelScope.launch {
            state.map { it.current }.distinctUntilChanged().collect { track ->
                elapsed.value = 0
                track?.let(player::play)
            }
        }
        viewModelScope.launch {
            state.map { it.taste }.distinctUntilChanged().collect { taste -> persist(settings.copy(taste = taste)) }
        }
        // 데모(목록만): 재생 시간을 앱이 세고, 끝나면 다음 곡으로 넘긴다(선호도는 그대로)
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val s = state.value
                val track = s.current ?: continue
                if (s.paused || player.link.value !is PlayerLink.ListOnly) continue
                if (elapsed.value + 1 >= track.durationSec) state.update { it.finished() } else elapsed.update { it + 1 }
            }
        }
    }

    private fun currentSignals() = MoodSignals(
        hour = MusicTestSignals.hour.value ?: hour.value,
        weather = MusicTestSignals.weather.value ?: weather.value,
        wipersOn = MusicTestSignals.wipersOn.value ?: exterior.wipersOn.value.valueOrNull(),
        headlightsOn = MusicTestSignals.headlightsOn.value ?: exterior.headlightsOn.value.valueOrNull(),
        // 내비 ETA 증가분은 NaviSource(기능 2 PR)가 합쳐지면 연결한다
        etaDelayMin = MusicTestSignals.etaDelayMin.value,
    )

    override fun togglePause() {
        state.update { it.togglePause() }
        if (state.value.paused) player.pause() else player.resume()
    }

    override fun skip() = state.update { it.skip() }

    override fun toggleSave() = state.update { it.toggleSave() }

    override fun chooseMood(mood: Mood?) = state.update { it.chooseMood(mood) }

    override fun chooseApp(app: MusicApp) {
        this.app.value = app
        persist(settings.copy(app = app))
        player.use(app)
        state.value.current?.let(player::play)
    }

    private fun persist(next: MusicSettings) {
        settings = next
        store.save(next)
    }

    override fun onCleared() {
        exterior.stop()
        player.release()
        speaker.release()
    }
}

/** 신호가 바뀐 뒤 이만큼 더 바뀌지 않으면 분위기를 다시 정한다 */
private const val SIGNAL_SETTLE_MS = 800L

private fun <T> Reading<T>.valueOrNull(): T? = (this as? Reading.Value<T>)?.value
