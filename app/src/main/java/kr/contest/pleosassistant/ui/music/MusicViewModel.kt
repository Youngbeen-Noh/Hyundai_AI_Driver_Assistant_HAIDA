package kr.contest.pleosassistant.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
import kr.contest.pleosassistant.music.PlayerEvent
import kr.contest.pleosassistant.music.PlayerLink
import kr.contest.pleosassistant.music.StreamingCatalog
import kr.contest.pleosassistant.music.Track
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
    /** 지금 곡을 들은 초(데모 곡은 플레이어 위치) */
    val elapsedSec: Int,
    val app: MusicApp,
    val link: PlayerLink,
    /** 앱에 든 곡을 분석하는 중이면 (분석한 곡 수, 전체 곡 수) */
    val analyzing: Pair<Int, Int>? = null,
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
 * 분위기 신호(시계 · 날씨 · 와이퍼 · 전조등 · 디버그 덮어쓰기) → [MusicState] → 재생 · 비서 음성.
 * 분위기가 바뀌면 비서가 한 번 말하고("비가 와서 촉촉한 빗길로 골랐어요."), 곡이 바뀌면 재생을 요청한다.
 * 곡 목록은 [loadTracks](앱에 든 곡을 듣고 분위기 분석)로 받는다. 분석이 끝날 때까지는 '분석 중'.
 * 앱을 켜면 멈춘 상태로 기다리다가, 처음 출발(P 가 아님)할 때 저절로 튼다. 사용자가 먼저 누르면 그대로 따른다.
 *
 * @param loadTracks 곡 목록 읽기 · 분석. 인자는 진행 알림(분석한 곡 수, 전체)
 * @param driving 주행 중 여부(기능 2 변속 단)
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class MusicViewModel(
    private val exterior: ExteriorSource,
    private val weatherSource: WeatherSource,
    private val player: MusicPlayer,
    private val store: MusicStore,
    private val speaker: Speaker,
    private val loadTracks: suspend ((Int, Int) -> Unit) -> List<Track>,
    private val driving: Flow<Boolean>,
    private val now: () -> LocalTime = LocalTime::now,
) : ViewModel(), MusicActions {

    private var settings: MusicSettings = store.load()
    private val hour = MutableStateFlow(now().hour)
    private val weather = MutableStateFlow<Weather?>(null)
    private val app = MutableStateFlow(settings.app)
    private val elapsed = MutableStateFlow(0)
    private val analyzing = MutableStateFlow<Pair<Int, Int>?>(0 to 0)

    /** 앱에 든 데모 곡(분석 결과 포함). 분석이 끝나야 채워진다 */
    private var localTracks: List<Track>? = null

    /** 사용자가 재생 · 일시정지를 직접 눌렀으면 출발할 때 저절로 틀지 않는다 */
    private var userControlled = false
    private var autoStarted = false

    // 앱을 켤 때 고른 분위기는 말하지 않는다(탑승 직후에는 차량 상태 요약이 먼저). 화면에는 이유가 보인다
    // 곡은 분석이 끝나면 채운다. 소리는 출발하거나 '재생'을 누를 때 난다
    private val state = MutableStateFlow(
        MusicState.start(emptyList(), currentSignals(), settings.taste).consumeAnnouncement().copy(paused = true),
    )

    val ui: StateFlow<MusicUi> = combine(state, elapsed, app, player.link, analyzing) { s, e, a, l, an -> MusicUi(s, e, a, l, an) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MusicUi(state.value, 0, app.value, player.link.value, analyzing.value))

    init {
        exterior.start()
        player.use(settings.app)

        // 앱에 든 곡을 듣고 분위기를 분석한다(처음 한 번, 다음부터는 저장해 둔 결과)
        viewModelScope.launch {
            val tracks = loadTracks { done, total -> analyzing.value = done to total }
            // 곡을 못 읽으면 소리 없는 시연 목록이라도 보여 준다
            localTracks = tracks.ifEmpty { DemoCatalog.tracks }
            state.update { it.withCatalog(catalogFor(app.value)) }
            analyzing.value = null
        }
        // 처음 출발할 때 저절로 튼다
        viewModelScope.launch {
            driving.collect { moving ->
                if (moving && !autoStarted && !userControlled && state.value.paused && state.value.current != null) {
                    autoStarted = true
                    state.update { it.copy(paused = false) }
                    player.resume()
                }
            }
        }
        // 곡이 끝나면 다음 곡, 다른 앱이 소리를 가져가면 멈춤 표시
        viewModelScope.launch {
            player.events.collect { e ->
                when (e) {
                    PlayerEvent.COMPLETED -> state.update { it.finished() }
                    PlayerEvent.FOCUS_LOST -> state.update { it.copy(paused = true) }
                }
            }
        }

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
                track?.let { player.play(it, start = !state.value.paused) }
            }
        }
        viewModelScope.launch {
            state.map { it.taste }.distinctUntilChanged().collect { taste -> persist(settings.copy(taste = taste)) }
        }
        // 데모 곡: 진행 막대는 플레이어 위치를 따른다(곡 끝은 PlayerEvent.COMPLETED)
        viewModelScope.launch {
            while (true) {
                delay(500)
                if (player.link.value is PlayerLink.Local) player.positionMs()?.let { elapsed.value = it / 1000 }
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
        userControlled = true
        state.update { it.togglePause() }
        if (state.value.paused) player.pause() else player.resume()
    }

    override fun skip() = state.update { it.skip() }

    override fun toggleSave() = state.update { it.toggleSave() }

    override fun chooseMood(mood: Mood?) = state.update { it.chooseMood(mood) }

    override fun chooseApp(app: MusicApp) {
        val before = catalogFor(this.app.value)
        this.app.value = app
        persist(settings.copy(app = app))
        player.use(app)
        val next = catalogFor(app)
        // 데모 곡 ↔ 음악 앱이면 곡 목록이 바뀐다(곡이 바뀌면 위의 수집기가 재생을 요청한다). 음악 앱끼리면 지금 곡을 새 앱에 요청한다
        if (localTracks != null && next != before) state.update { it.withCatalog(next) }
        else state.value.current?.let { player.play(it, start = !state.value.paused) }
    }

    /** 데모 곡은 앱에 든 곡, 음악 앱은 실제 곡 목록(데모 곡은 음악 앱에서 검색해도 없다) */
    private fun catalogFor(app: MusicApp): List<Track> =
        if (app == MusicApp.DEMO) localTracks.orEmpty() else StreamingCatalog.tracks

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
