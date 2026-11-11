package kr.contest.pleosassistant.navi

import ai.pleos.playground.navi.constants.BookmarkType
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kr.contest.pleosassistant.memory.NaviStatus

/**
 * Pleos NaviHelper 로 목적지 · 남은 거리 · 도착을 읽는다(권한 NAVI_ROUTE).
 * 목적지가 바뀌면 getDestinationInfo() 로 이름 · 남은 거리를 받고, 안내 중에는 5초마다 다시 묻는다.
 * 내비 즐겨찾기의 회사 · 집과 같은 곳이면 [NaviStatus.bookmark] 에 "회사" · "집"을 넣는다.
 */
class PleosNaviSource(context: Context) : NaviSource {

    private val navi = NaviHelper(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())

    private val _status = MutableStateFlow(NaviStatus())
    override val status: StateFlow<NaviStatus> = _status.asStateFlow()

    private val _arrivals = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val arrivals: SharedFlow<String> = _arrivals.asSharedFlow()

    /** 즐겨찾기 POI id → "회사" · "집" */
    private var bookmarks: Map<String, String> = emptyMap()
    private var started = false

    private val poll = object : Runnable {
        override fun run() {
            if (!started) return
            if (_status.value.destination != null) runCatching { navi.getDestinationInfo() }
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun start() {
        if (started) return
        started = true
        navi.initialize(
            onConnected = {
                Log.i(TAG, "내비 연결됨")
                navi.addListener(listener)
                runCatching { navi.getBookmarkInfo() }.onFailure { Log.w(TAG, "즐겨찾기 요청 실패: $it") }
                runCatching { navi.getDestinationInfo() }.onFailure { Log.w(TAG, "목적지 요청 실패: $it") }
                handler.postDelayed(poll, POLL_MS)
            },
            onFailed = { Log.w(TAG, "내비 연결 실패: $it") },
        )
    }

    override fun stop() {
        started = false
        handler.removeCallbacks(poll)
        runCatching { navi.removeListener(listener) }
        runCatching { navi.release() }
    }

    private fun clear() = _status.update { NaviStatus() }

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationChanged() {
            Log.i(TAG, "목적지 바뀜")
            runCatching { navi.getDestinationInfo() }
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            Log.i(TAG, "목적지: ${destinationInfo.name} · 남은 ${destinationInfo.remainDistance}m · ${destinationInfo.remainTime}s")
            if (destinationInfo.name.isBlank()) {
                clear()
                return
            }
            _status.update {
                it.copy(
                    destination = destinationInfo.name,
                    bookmark = bookmarks[destinationInfo.poiId],
                    remainMeters = destinationInfo.remainDistance,
                    remainSeconds = destinationInfo.remainTime,
                )
            }
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            // 목적지까지 남은 시간(초). 거리는 출발점 기준이라 쓰지 않는다
            _status.update { if (it.destination == null) it else it.copy(remainSeconds = drivingInfo.destination.duration) }
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            Log.i(TAG, "도착: ${destinationArrivedInfo.name}")
            _arrivals.tryEmit(destinationArrivedInfo.name)
        }

        override fun onRouteCancelled() {
            Log.i(TAG, "안내 취소")
            clear()
        }

        override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) {
            bookmarks = bookmarkInfoList.mapNotNull { info -> info.category.label()?.let { info.poiId to it } }.toMap()
            Log.i(TAG, "즐겨찾기 ${bookmarks.size}곳")
            _status.update { it.copy(bookmark = it.destination?.let { _ -> it.bookmark }) }
        }

        override fun onBookMarkChanged() {
            runCatching { navi.getBookmarkInfo() }
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "내비 오류: $errorCode")
        }
    }

    private fun BookmarkType.label(): String? = when (this) {
        BookmarkType.HOME -> "집"
        BookmarkType.OFFICE -> "회사"
        else -> null
    }

    private companion object {
        const val TAG = "PleosNavi"
        const val POLL_MS = 5_000L
    }
}
