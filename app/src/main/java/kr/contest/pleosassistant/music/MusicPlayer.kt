package kr.contest.pleosassistant.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.os.Bundle
import android.provider.MediaStore
import android.service.media.MediaBrowserService
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 재생을 넘길 차량 음악 앱 (기능계획서 5장 '재생 연동'). 설정에서 한 번 고른다 */
enum class MusicApp(val label: String, val packageName: String?) {
    /** 앱에 든 데모 곡을 직접 튼다(tools/music-gen 으로 만든 곡이라 저작권 문제 없음). 공모전 시연 · 음악 앱 연동 전 */
    DEMO("데모 곡", null),
    SPOTIFY("스포티파이", "com.spotify.music"),
    MELON("멜론", "com.iloen.melon"),
    GENIE("지니", "com.ktmusic.geniemusic"),
}

/** 음악 앱과의 연결 상태 */
sealed interface PlayerLink {
    /** 데모: 앱에 든 곡을 앱이 직접 튼다 */
    data object Local : PlayerLink
    data object Connecting : PlayerLink
    /** 연결됨. nowPlaying 은 음악 앱이 알려 준 지금 곡(제목 · 가수) */
    data class Connected(val nowPlaying: String? = null) : PlayerLink
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
 * 데모 곡은 앱이 MediaPlayer 로 직접 틀고(오디오 포커스를 받는다), 음악 앱을 고르면
 * 그 앱의 MediaBrowserService 에 붙어 '검색해서 재생'(playFromSearch, 제목 + 가수)을 요청한다.
 * 차량용 미디어 앱은 이 요청을 지원해야 하지만, 다른 앱의 연결을 받을지는 그 앱이 정한다(거절하면 [PlayerLink.Refused]).
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
    private var browser: MediaBrowser? = null
    private var controller: MediaController? = null
    /** 아직 음악 앱에 요청하지 않은 곡. [pendingStart] 면 연결되자마자, 아니면 '재생'을 누를 때 요청한다 */
    private var pending: Track? = null
    private var pendingStart = false

    override fun use(app: MusicApp) {
        if (app == this.app && browser != null) return
        disconnect()
        stopLocal()
        this.app = app
        val pkg = app.packageName ?: run {
            _link.value = PlayerLink.Local
            return
        }
        val service = context.packageManager
            .queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(pkg), 0)
            .firstOrNull()?.serviceInfo
        if (service == null) {
            Log.i(TAG, "${app.label}: 설치되지 않았거나 미디어 서비스가 없음")
            _link.value = PlayerLink.NotInstalled
            return
        }
        _link.value = PlayerLink.Connecting
        browser = MediaBrowser(context, ComponentName(service.packageName, service.name), connection, null).also {
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
        val extras = Bundle().apply {
            putString(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
            putString(MediaStore.EXTRA_MEDIA_TITLE, track.title)
            putString(MediaStore.EXTRA_MEDIA_ARTIST, track.artist)
        }
        Log.i(TAG, "${app.label} 에 재생 요청: ${track.query}")
        runCatching { c.transportControls.playFromSearch(track.query, extras) }
            .onFailure { Log.w(TAG, "재생 요청 실패: $it") }
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
        runCatching { controller?.unregisterCallback(controllerCallback) }
        runCatching { browser?.disconnect() }
        controller = null
        browser = null
    }

    private val connection = object : MediaBrowser.ConnectionCallback() {
        override fun onConnected() {
            val b = browser ?: return
            val c = MediaController(context, b.sessionToken)
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

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            _link.value = PlayerLink.Connected(metadata?.label())
        }
    }

    private fun MediaMetadata.label(): String? {
        val title = getString(MediaMetadata.METADATA_KEY_TITLE) ?: return null
        val artist = getString(MediaMetadata.METADATA_KEY_ARTIST)
        return if (artist.isNullOrBlank()) title else "$title · $artist"
    }

    private companion object {
        const val TAG = "MediaAppPlayer"
    }
}
