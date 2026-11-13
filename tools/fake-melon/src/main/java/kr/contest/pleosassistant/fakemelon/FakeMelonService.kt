package kr.contest.pleosassistant.fakemelon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat

/**
 * 동작 방식(adb 로 바꾼다, [ModeReceiver]):
 * - search : '검색해서 재생'을 지원한다(차량 미디어 앱 기본)
 * - browse : '검색해서 재생'을 지원하지 않는다고 알리고, 앱 안 검색과 mediaId 재생만 된다
 * - missing: 앱 안 검색을 해도 찾는 곡이 없다(엉뚱한 곡 하나만 나온다)
 */
enum class Mode { SEARCH, BROWSE, MISSING }

private const val TAG = "FakeMelon"
private const val PREFS = "fake-melon"

private fun Context.mode(): Mode =
    runCatching { Mode.valueOf(getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("mode", "SEARCH")!!) }
        .getOrDefault(Mode.SEARCH)

class ModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mode = Mode.entries.firstOrNull { it.name.equals(intent.getStringExtra("mode"), ignoreCase = true) } ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("mode", mode.name).apply()
        Log.i(TAG, "mode=$mode")
    }
}

/** 소리는 내지 않는 가짜 멜론. 받은 요청과 '지금 곡'만 바꾼다 */
class FakeMelonService : MediaBrowserServiceCompat() {

    private lateinit var session: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        session = MediaSessionCompat(this, TAG).apply {
            setCallback(callback)
            isActive = true
        }
        sessionToken = session.sessionToken
        setState(PlaybackStateCompat.STATE_NONE)
        Log.i(TAG, "시작 mode=${mode()}")
    }

    override fun onDestroy() {
        session.release()
        super.onDestroy()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        Log.i(TAG, "연결: $clientPackageName")
        return BrowserRoot("root", null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(mutableListOf())
    }

    override fun onSearch(query: String, extras: Bundle?, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val title = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE) ?: query
        val artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST).orEmpty()
        // 진짜 앱처럼 비슷한 다른 곡을 먼저 둔다. missing 이면 찾는 곡이 없다
        val items = mutableListOf(item("other", "다른 노래", artist))
        if (mode() != Mode.MISSING) items += item("song:$title|$artist", "$title (Live)", artist)
        Log.i(TAG, "앱 안 검색: '$query' → ${items.size}개")
        result.sendResult(items)
    }

    private fun item(id: String, title: String, subtitle: String) = MediaBrowserCompat.MediaItem(
        MediaDescriptionCompat.Builder().setMediaId(id).setTitle(title).setSubtitle(subtitle).build(),
        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE,
    )

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            Log.i(TAG, "검색해서 재생 요청: '$query' mode=${mode()}")
            if (mode() != Mode.SEARCH) {
                // 지원하지 않거나 못 찾으면 진짜 앱처럼 오류 상태로 둔다
                setState(PlaybackStateCompat.STATE_ERROR)
                return
            }
            val title = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE) ?: query.orEmpty()
            playNow(title, extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST).orEmpty())
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            Log.i(TAG, "mediaId 재생 요청: $mediaId")
            val parts = mediaId?.removePrefix("song:")?.split("|") ?: return
            playNow(parts[0], parts.getOrElse(1) { "" })
        }

        override fun onPlay() {
            Log.i(TAG, "재생")
            setState(PlaybackStateCompat.STATE_PLAYING)
        }

        override fun onPause() {
            Log.i(TAG, "일시정지")
            setState(PlaybackStateCompat.STATE_PAUSED)
        }
    }

    private fun playNow(title: String, artist: String) {
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .build(),
        )
        setState(PlaybackStateCompat.STATE_PLAYING)
        Log.i(TAG, "지금 곡: $title · $artist")
    }

    private fun setState(state: Int) {
        var actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
        if (mode() == Mode.SEARCH) actions = actions or PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
        session.setPlaybackState(PlaybackStateCompat.Builder().setActions(actions).setState(state, 0, 1f).build())
    }
}
