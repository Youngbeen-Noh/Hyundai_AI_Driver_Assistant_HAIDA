package kr.contest.pleosassistant.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.support.v4.media.MediaMetadataCompat
import android.media.MediaPlayer
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.service.media.MediaBrowserService
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 재생을 넘길 차량 음악 앱 (기능계획서 5장 '재생 연동'). 설정에서 한 번 고른다.
 * @property packageName 알려진 패키지(휴대폰 앱 기준). 차량용 앱은 다를 수 있어 [keywords] 로도 찾는다
 * @property keywords 앱 이름 · 패키지 이름에 이 말이 들어가면 그 앱으로 본다([findMediaService])
 */
enum class MusicApp(val label: String, val packageName: String?, val keywords: List<String> = emptyList()) {
    /** 앱에 든 데모 곡을 직접 튼다(tools/music-gen 으로 만든 곡이라 저작권 문제 없음). 공모전 시연 · 음악 앱 연동 전 */
    DEMO("데모 곡", null),
    SPOTIFY("스포티파이", "com.spotify.music", listOf("스포티파이", "spotify")),
    MELON("멜론", "com.iloen.melon", listOf("멜론", "melon")),
    GENIE("지니", "com.ktmusic.geniemusic", listOf("지니뮤직", "genie")),
}

/** 음악 앱과의 연결 상태 */
sealed interface PlayerLink {
    /** 데모: 앱에 든 곡을 앱이 직접 튼다 */
    data object Local : PlayerLink
    data object Connecting : PlayerLink
    /**
     * 연결됨. nowPlaying 은 음악 앱이 알려 준 지금 곡(제목 · 가수).
     * notFound 는 음악 앱에서 찾지 못한 곡 제목(검색해도 없을 때)
     */
    data class Connected(val nowPlaying: String? = null, val notFound: String? = null) : PlayerLink
    /** 앱이 없거나 미디어 서비스가 없다 */
    data object NotInstalled : PlayerLink
    /** 앱이 우리 앱의 연결을 받아 주지 않았다 → 곡 목록만 보여 주고 사용자가 음악 앱에서 고른다 */
    data object Refused : PlayerLink
}

/** 재생 쪽에서 앱에 알리는 일 */
enum class PlayerEvent {
    /** 곡이 끝까지 재생됐다 → 다음 곡 */
    COMPLETED,

    /** 다른 앱(전화 · 내비 안내 등)이 소리를 가져가 멈췄다 */
    FOCUS_LOST,
}

/** 추천은 우리 앱, 재생은 차량 음악 앱(또는 데모 곡은 앱이 직접) */
interface MusicPlayer {
    val link: StateFlow<PlayerLink>
    val events: SharedFlow<PlayerEvent>
    fun use(app: MusicApp)

    /** @param start false 면 곡만 준비하고 '재생'을 누를 때 튼다 */
    fun play(track: Track, start: Boolean = true)
    fun pause()
    fun resume()

    /** 지금 곡을 들은 시간(ms). 알 수 없으면 null(음악 앱이 재생할 때) */
    fun positionMs(): Int?
    fun release()
}

/**
 * 데모 곡은 앱이 MediaPlayer 로 직접 틀고(오디오 포커스를 받는다), 음악 앱(멜론 등)을 고르면
 * 그 앱의 MediaBrowserService 에 붙어 곡을 틀게 한다. 멜론은 공개 API 가 없어서 차량 미디어 앱 표준 방식만 쓴다.
 * 1) '검색해서 재생'(playFromSearch, 제목 + 가수)을 보낸다. 앱이 이 요청을 지원하지 않는다고 알렸으면 건너뛴다
 * 2) [WATCHDOG_MS] 안에 재생이 시작되지 않으면 음악 앱 안에서 검색(MediaBrowserCompat.search)해 제목이 맞는 곡을 playFromMediaId 로 튼다
 * 3) 그래도 없으면 [PlayerLink.Connected.notFound] 로 알린다(엉뚱한 곡을 틀지 않는다)
 * 다른 앱의 연결을 받을지는 그 앱이 정한다(거절하면 [PlayerLink.Refused]).
 * 스포티파이 Web API(1순위)는 OAuth · Premium 계정이 필요해 다음 PR 에서 붙인다.
 */
class MediaAppPlayer(private val context: Context) : MusicPlayer {

    private val _link = MutableStateFlow<PlayerLink>(PlayerLink.Local)
    override val link: StateFlow<PlayerLink> = _link.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    // 데모 곡 재생(앱이 직접)
    private var local: MediaPlayer? = null
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            // 잠깐 소리를 낮추는 경우(TTS · 내비 안내)는 시스템이 알아서 줄인다. 아주 가져가면 멈춘다
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                runCatching { local?.pause() }
                _events.tryEmit(PlayerEvent.FOCUS_LOST)
            }
        }
        .build()

    private var app = MusicApp.DEMO
    private var browser: MediaBrowserCompat? = null
    private var controller: MediaControllerCompat? = null
    /** 아직 음악 앱에 요청하지 않은 곡. [pendingStart] 면 연결되자마자, 아니면 '재생'을 누를 때 요청한다 */
    private var pending: Track? = null
    private var pendingStart = false

    /** 지금 음악 앱에 요청한 곡. 다른 곡을 요청하면 바뀌어 늦게 온 검색 결과 · 확인을 버린다 */
    private var requested: Track? = null
    private val main = Handler(Looper.getMainLooper())

    override fun use(app: MusicApp) {
        if (app == this.app && browser != null) return
        disconnect()
        stopLocal()
        this.app = app
        val pkg = app.packageName ?: run {
            _link.value = PlayerLink.Local
            return
        }
        val service = findMediaService(app, mediaServices(), context.packageName)
        if (service == null) {
            Log.i(TAG, "${app.label}: 설치되지 않았거나 미디어 서비스가 없음 (알려진 패키지 $pkg)")
            _link.value = PlayerLink.NotInstalled
            return
        }
        Log.i(TAG, "${app.label}: ${service.appLabel} (${service.packageName}/${service.className}) 에 연결")
        _link.value = PlayerLink.Connecting
        browser = MediaBrowserCompat(context, ComponentName(service.packageName, service.className), connection, null).also {
            runCatching { it.connect() }.onFailure { e ->
                Log.w(TAG, "${app.label} 연결 실패: $e")
                _link.value = PlayerLink.Refused
            }
        }
    }

    override fun play(track: Track, start: Boolean) {
        if (app == MusicApp.DEMO) {
            playLocal(track, start)
            return
        }
        if (!start) {
            // 음악 앱은 준비만 할 수 없어 '재생'을 누를 때 요청한다
            pending = track
            pendingStart = false
            return
        }
        val c = controller
        if (c == null) {
            pending = track
            pendingStart = true
            return
        }
        pending = null
        requested = track
        main.removeCallbacksAndMessages(null)
        if (!supportsPlayFromSearch(c.playbackState?.actions)) {
            Log.i(TAG, "${app.label} 은 '검색해서 재생'을 지원하지 않아 앱 안에서 찾음: ${track.query}")
            searchAndPlay(track)
            return
        }
        Log.i(TAG, "${app.label} 에 재생 요청: ${track.query}")
        runCatching { c.transportControls.playFromSearch(track.query, searchExtras(track)) }
            .onFailure { Log.w(TAG, "재생 요청 실패: $it") }
        // 음악 앱이 요청을 받고도 틀지 못하면(못 찾음 · 지원 안 함) 앱 안 검색으로 다시 찾는다
        main.postDelayed({
            if (requested != track) return@postDelayed
            val state = controller?.playbackState?.state
            if (needsSearchFallback(state)) {
                Log.i(TAG, "${app.label}: 재생이 시작되지 않음(state=$state) → 앱 안에서 검색")
                searchAndPlay(track)
            }
        }, WATCHDOG_MS)
    }

    /** 음악 앱 안에서 검색해 제목이 맞는 곡을 튼다 */
    private fun searchAndPlay(track: Track) {
        val b = browser ?: return
        runCatching {
            b.search(track.query, searchExtras(track), object : MediaBrowserCompat.SearchCallback() {
                override fun onSearchResult(query: String, extras: Bundle?, items: List<MediaBrowserCompat.MediaItem>) {
                    if (requested != track) return
                    val found = pickSearchResult(track, items.map { it.toSearchItem() })
                    if (found == null) {
                        Log.i(TAG, "${app.label} 검색 결과 ${items.size}개 중 '${track.title}' 없음")
                        markNotFound(track)
                        return
                    }
                    Log.i(TAG, "${app.label} 검색으로 찾음: ${found.title} · ${found.subtitle} (${found.mediaId})")
                    runCatching { controller?.transportControls?.playFromMediaId(found.mediaId, null) }
                        .onFailure { Log.w(TAG, "playFromMediaId 실패: $it") }
                }

                override fun onError(query: String, extras: Bundle?) {
                    if (requested != track) return
                    Log.i(TAG, "${app.label} 이 앱 안 검색을 지원하지 않음")
                    markNotFound(track)
                }
            })
        }.onFailure {
            Log.w(TAG, "앱 안 검색 실패: $it")
            markNotFound(track)
        }
    }

    private fun markNotFound(track: Track) {
        val now = (_link.value as? PlayerLink.Connected)?.nowPlaying
        _link.value = PlayerLink.Connected(nowPlaying = now, notFound = track.title)
    }

    private fun searchExtras(track: Track) = Bundle().apply {
        putString(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
        putString(MediaStore.EXTRA_MEDIA_TITLE, track.title)
        putString(MediaStore.EXTRA_MEDIA_ARTIST, track.artist)
    }

    private fun MediaBrowserCompat.MediaItem.toSearchItem() = SearchItem(
        mediaId = mediaId.orEmpty(),
        title = description.title?.toString().orEmpty(),
        subtitle = description.subtitle?.toString().orEmpty(),
        playable = isPlayable && mediaId != null,
    )

    /** 기기에 있는 미디어 앱(MediaBrowserService) 목록. 매니페스트 <queries> 로 보인다 */
    private fun mediaServices(): List<MediaServiceInfo> {
        val pm = context.packageManager
        return pm.queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE), 0).map { r ->
            val s = r.serviceInfo
            MediaServiceInfo(s.packageName, s.name, runCatching { s.applicationInfo.loadLabel(pm).toString() }.getOrDefault(""))
        }
    }

    override fun pause() {
        if (app == MusicApp.DEMO) {
            runCatching { local?.pause() }
            return
        }
        runCatching { controller?.transportControls?.pause() }
    }

    override fun resume() {
        if (app == MusicApp.DEMO) {
            if (requestFocus()) runCatching { local?.start() }
            return
        }
        val waiting = pending
        if (waiting != null && controller != null) play(waiting, start = true)
        else runCatching { controller?.transportControls?.play() }
    }

    override fun positionMs(): Int? =
        if (app == MusicApp.DEMO) runCatching { local?.currentPosition }.getOrNull() else null

    override fun release() {
        disconnect()
        stopLocal()
    }

    private fun playLocal(track: Track, start: Boolean) {
        stopLocal()
        val path = track.assetPath ?: run {
            Log.w(TAG, "${track.id}: 앱에 든 곡이 아니라 틀 수 없음")
            return
        }
        runCatching {
            val mp = MediaPlayer()
            context.assets.openFd(path).use { afd -> mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) }
            mp.setAudioAttributes(attributes)
            mp.setOnCompletionListener { _events.tryEmit(PlayerEvent.COMPLETED) }
            mp.prepare()
            local = mp
            if (start && requestFocus()) mp.start()
            Log.i(TAG, "데모 곡 ${if (start) "재생" else "준비"}: ${track.title}")
        }.onFailure { Log.w(TAG, "데모 곡 재생 실패: $it") }
    }

    private fun stopLocal() {
        runCatching { local?.release() }
        local = null
        runCatching { audio?.abandonAudioFocusRequest(focusRequest) }
    }

    private fun requestFocus(): Boolean =
        audio?.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    private fun disconnect() {
        main.removeCallbacksAndMessages(null)
        requested = null
        runCatching { controller?.unregisterCallback(controllerCallback) }
        runCatching { browser?.disconnect() }
        controller = null
        browser = null
    }

    private val connection = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val b = browser ?: return
            val c = MediaControllerCompat(context, b.sessionToken)
            c.registerCallback(controllerCallback)
            controller = c
            Log.i(TAG, "${app.label} 연결됨")
            _link.value = PlayerLink.Connected(c.metadata?.label())
            pending?.takeIf { pendingStart }?.let { play(it, start = true) }
        }

        override fun onConnectionFailed() {
            Log.i(TAG, "${app.label} 이 연결을 거절함")
            _link.value = PlayerLink.Refused
        }

        override fun onConnectionSuspended() {
            controller = null
            _link.value = PlayerLink.Connecting
        }
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            _link.value = PlayerLink.Connected(metadata?.label())
        }
    }

    private fun MediaMetadataCompat.label(): String? {
        val title = getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: return null
        val artist = getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
        return if (artist.isNullOrBlank()) title else "$title · $artist"
    }

    private companion object {
        const val TAG = "MediaAppPlayer"
    }
}
