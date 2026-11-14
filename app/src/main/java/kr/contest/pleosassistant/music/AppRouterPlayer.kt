package kr.contest.pleosassistant.music

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kr.contest.pleosassistant.music.spotify.SpotifyPlayer

/**
 * 고른 앱에 따라 재생을 나눈다: 스포티파이는 Web API([SpotifyPlayer]), 데모 곡 · 멜론 · 지니는 [MediaAppPlayer].
 * 화면은 지금 고른 쪽의 연결 상태만 본다.
 */
class AppRouterPlayer(private val media: MediaAppPlayer, val spotify: SpotifyPlayer) : MusicPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var app = MusicApp.DEMO

    private val _link = MutableStateFlow(media.link.value)
    override val link: StateFlow<PlayerLink> = _link.asStateFlow()

    override val events: SharedFlow<PlayerEvent> = merge(media.events, spotify.events)
        .shareIn(scope, SharingStarted.Eagerly)

    init {
        scope.launch { media.link.collect { if (app != MusicApp.SPOTIFY) _link.value = it } }
        scope.launch { spotify.link.collect { if (app == MusicApp.SPOTIFY) _link.value = it } }
    }

    private val active: MusicPlayer get() = if (app == MusicApp.SPOTIFY) spotify else media

    override fun use(app: MusicApp) {
        val before = active
        this.app = app
        if (app == MusicApp.SPOTIFY) {
            // 데모 곡 소리를 멈추고 스포티파이로
            media.use(MusicApp.DEMO)
            media.pause()
        } else {
            if (before === spotify) {
                spotify.pause()
                spotify.stop()
            }
        }
        active.use(app)
        _link.value = active.link.value
    }

    override fun play(track: Track, start: Boolean) = active.play(track, start)
    override fun pause() = active.pause()
    override fun resume() = active.resume()
    override fun positionMs(): Int? = active.positionMs()

    override fun release() {
        media.release()
        spotify.release()
        scope.cancel()
    }
}
