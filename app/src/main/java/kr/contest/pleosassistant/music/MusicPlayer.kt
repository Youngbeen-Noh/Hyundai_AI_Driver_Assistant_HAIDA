package kr.contest.pleosassistant.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.os.Bundle
import android.provider.MediaStore
import android.service.media.MediaBrowserService
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 재생을 넘길 차량 음악 앱 (기능계획서 5장 '재생 연동'). 설정에서 한 번 고른다 */
enum class MusicApp(val label: String, val packageName: String?) {
    /** 음악 앱 없이 곡 목록과 진행만 보여 준다(공모전 시연 · 연동이 안 될 때) */
    DEMO("데모(목록만)", null),
    SPOTIFY("스포티파이", "com.spotify.music"),
    MELON("멜론", "com.iloen.melon"),
    GENIE("지니", "com.ktmusic.geniemusic"),
}

/** 음악 앱과의 연결 상태 */
sealed interface PlayerLink {
    /** 데모: 재생은 하지 않고 목록만 */
    data object ListOnly : PlayerLink
    data object Connecting : PlayerLink
    /** 연결됨. nowPlaying 은 음악 앱이 알려 준 지금 곡(제목 · 가수) */
    data class Connected(val nowPlaying: String? = null) : PlayerLink
    /** 앱이 없거나 미디어 서비스가 없다 */
    data object NotInstalled : PlayerLink
    /** 앱이 우리 앱의 연결을 받아 주지 않았다 → 곡 목록만 보여 주고 사용자가 음악 앱에서 고른다 */
    data object Refused : PlayerLink
}

/** 추천은 우리 앱, 재생은 차량 음악 앱 */
interface MusicPlayer {
    val link: StateFlow<PlayerLink>
    fun use(app: MusicApp)
    fun play(track: Track)
    fun pause()
    fun resume()
    fun release()
}

/**
 * 고른 음악 앱의 MediaBrowserService 에 붙어 '검색해서 재생'(playFromSearch, 제목 + 가수)을 요청한다.
 * 차량용 미디어 앱은 이 요청을 지원해야 하지만, 다른 앱의 연결을 받을지는 그 앱이 정한다(거절하면 [PlayerLink.Refused]).
 * 스포티파이 Web API(1순위)는 OAuth · Premium 계정이 필요해 다음 PR 에서 붙인다.
 */
class MediaAppPlayer(private val context: Context) : MusicPlayer {

    private val _link = MutableStateFlow<PlayerLink>(PlayerLink.ListOnly)
    override val link: StateFlow<PlayerLink> = _link.asStateFlow()

    private var app = MusicApp.DEMO
    private var browser: MediaBrowser? = null
    private var controller: MediaController? = null
    /** 연결되기 전에 들어온 곡. 연결되면 바로 튼다 */
    private var pending: Track? = null

    override fun use(app: MusicApp) {
        if (app == this.app && browser != null) return
        disconnect()
        this.app = app
        val pkg = app.packageName ?: run {
            _link.value = PlayerLink.ListOnly
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

    override fun play(track: Track) {
        val c = controller
        if (c == null) {
            pending = track
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
        runCatching { controller?.transportControls?.pause() }
    }

    override fun resume() {
        runCatching { controller?.transportControls?.play() }
    }

    override fun release() = disconnect()

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
            pending?.let(::play)
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
