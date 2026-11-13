package kr.contest.pleosassistant.music.spotify

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kr.contest.pleosassistant.music.MusicApp
import kr.contest.pleosassistant.music.MusicPlayer
import kr.contest.pleosassistant.music.PlayerEvent
import kr.contest.pleosassistant.music.PlayerLink
import kr.contest.pleosassistant.music.Track

/**
 * 스포티파이 Web API 로 재생한다(기능계획서 5장 '재생 연동' 1순위).
 * 차에 스포티파이 앱이 없어도 되도록, 사용자의 스포티파이 기기(보통 블루투스로 차에 연결된 휴대폰)에 '이 곡을 틀어'를 보낸다.
 * 1) 검색(track: · artist:) → 제목이 맞는 곡 2) 기기 목록 → 지금 쓰는 기기 · 휴대폰 3) 그 기기에서 재생
 * 재생 중에는 [POLL_MS] 마다 재생 상태를 읽어 지금 곡을 보여 주고, 곡이 끝나면 [PlayerEvent.COMPLETED] 로 다음 곡을 부른다.
 * 스포티파이 Premium 이 있어야 재생을 시킬 수 있다(Web API 규칙).
 */
class SpotifyPlayer(private val client: SpotifyClient) : MusicPlayer {

    private val _link = MutableStateFlow<PlayerLink>(PlayerLink.Connecting)
    override val link: StateFlow<PlayerLink> = _link.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var requestJob: Job? = null
    private var pollJob: Job? = null

    /** '재생'을 누르면 요청할 곡(아직 스포티파이에 보내지 않음) */
    @Volatile private var pending: Track? = null
    /** 스포티파이에 틀어 달라고 보낸 곡 */
    @Volatile private var requestedUri: String? = null
    @Volatile private var last: PlaybackSnapshot? = null

    override fun use(app: MusicApp) {
        check(app == MusicApp.SPOTIFY)
        _link.value = when {
            !client.configured -> PlayerLink.NotConfigured
            !client.loggedIn -> PlayerLink.NeedsLogin
            else -> PlayerLink.Connected()
        }
        startPolling()
    }

    /** 로그인 화면에서 돌아왔다 */
    fun onLoginChanged() {
        if (!client.configured) return
        _link.value = if (client.loggedIn) PlayerLink.Connected() else PlayerLink.NeedsLogin
        if (client.loggedIn) pending?.let { play(it, start = true) }
    }

    override fun play(track: Track, start: Boolean) {
        pending = track
        requestedUri = null
        if (!start || !client.loggedIn) return
        requestJob?.cancel()
        requestJob = scope.launch { request(track) }
    }

    private fun request(track: Track) {
        val search = client.search(searchQuery(track))
        if (!search.ok) return fail(search.problem)
        val found = pickTrack(track, parseSearch(search.body.orEmpty()))
        if (found == null) {
            Log.i(TAG, "스포티파이에 '${track.title} · ${track.artist}' 없음")
            _link.value = PlayerLink.Connected(notFound = track.title)
            return
        }
        val devices = client.devices()
        if (!devices.ok) return fail(devices.problem)
        val device = chooseDevice(parseDevices(devices.body.orEmpty()))
        if (device == null) {
            Log.i(TAG, "켜진 스포티파이 기기 없음")
            _link.value = PlayerLink.NoDevice
            return
        }
        val res = client.play(device.id, found.uri)
        if (!res.ok) return fail(res.problem)
        Log.i(TAG, "${device.name}(${device.type}) 에서 재생: ${found.name} · ${found.artists.joinToString()}")
        pending = null
        requestedUri = found.uri
        last = null
        _link.value = PlayerLink.Connected(nowPlaying = "${found.name} · ${found.artists.firstOrNull().orEmpty()}", device = device.name)
    }

    private fun fail(problem: SpotifyProblem?) {
        _link.value = when (problem) {
            SpotifyProblem.NEEDS_LOGIN -> PlayerLink.NeedsLogin
            SpotifyProblem.NOT_PREMIUM -> PlayerLink.NotPremium
            SpotifyProblem.NO_DEVICE -> PlayerLink.NoDevice
            else -> PlayerLink.Offline
        }
    }

    override fun pause() {
        if (requestedUri == null) return
        scope.launch { client.pause().problem?.let(::fail) }
    }

    override fun resume() {
        val waiting = pending
        if (waiting != null) play(waiting, start = true)
        else scope.launch { client.resume().problem?.let(::fail) }
    }

    /** 스포티파이가 알려 준 지금 곡의 위치(우리가 요청한 곡일 때만) */
    override fun positionMs(): Int? = last?.takeIf { it.trackUri == requestedUri }?.progressMs?.toInt()

    /** 스포티파이가 알려 준 지금 곡의 길이 */
    fun durationMs(): Long? = last?.takeIf { it.trackUri == requestedUri }?.durationMs

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                delay(POLL_MS)
                val uri = requestedUri ?: continue
                val res = client.playback()
                if (!res.ok) continue
                val now = parsePlayback(res.body)
                if (trackFinished(last, now, uri)) {
                    Log.i(TAG, "곡 끝 → 다음 곡")
                    requestedUri = null
                    last = null
                    _events.tryEmit(PlayerEvent.COMPLETED)
                    continue
                }
                last = now
                val current = _link.value
                if (now?.trackUri == uri && current is PlayerLink.Connected && now.title != null) {
                    _link.value = current.copy(nowPlaying = "${now.title} · ${now.artist.orEmpty()}")
                }
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        requestJob?.cancel()
    }

    override fun release() = scope.cancel()

    private companion object {
        const val TAG = "SpotifyPlayer"
        const val POLL_MS = 3_000L
    }
}
