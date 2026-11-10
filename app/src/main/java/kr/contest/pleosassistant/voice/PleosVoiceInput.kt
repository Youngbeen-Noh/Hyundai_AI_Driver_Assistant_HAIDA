package kr.contest.pleosassistant.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Pleos SpeechToText SDK 로 차량 마이크의 말을 글로 바꾼다(권한 STT_SERVICE).
 * 차 안 엔진(ON_DEVICE)만 써서 앱 등록 · 네트워크 없이 동작한다. 말이 끝나면(completed) 문장을 [results] 로 보낸다.
 */
class PleosVoiceInput(context: Context) : VoiceInput {

    private val stt = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
    private var ready = false

    private val _state = MutableStateFlow<ListenState>(ListenState.Idle)
    override val state: StateFlow<ListenState> = _state.asStateFlow()

    private val _results = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val results: SharedFlow<String> = _results.asSharedFlow()

    private val listener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() {
            Log.i(TAG, "듣기 시작")
            _state.value = ListenState.Listening("")
        }

        override fun onUpdated(stt: String, completed: Boolean) {
            Log.i(TAG, "들음(${if (completed) "끝" else "중간"}): $stt")
            if (completed) {
                if (stt.isNotBlank()) _results.tryEmit(stt)
                _state.value = ListenState.Idle
            } else {
                _state.value = ListenState.Listening(stt)
            }
        }

        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() {
            Log.i(TAG, "듣기 끝")
            if (_state.value is ListenState.Listening) _state.value = ListenState.Idle
        }

        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "듣기 오류: $errorType")
            _state.value = ListenState.Idle
        }
    }

    init {
        stt.initialize(
            object : OnInitializeListener {
                override fun onSuccess() {
                    Log.i(TAG, "음성 인식 준비됨")
                    stt.setLocale(Locale.KOREA)
                    stt.addListener(listener)
                    ready = true
                }

                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "음성 인식 초기화 실패: $errorType")
                    _state.value = ListenState.Unavailable("$errorType")
                }
            },
        )
    }

    override fun start() {
        if (!ready) {
            Log.w(TAG, "아직 준비 안 됨")
            return
        }
        _state.value = ListenState.Listening("")
        runCatching { stt.request() }.onFailure {
            Log.w(TAG, "듣기 요청 실패: $it")
            _state.value = ListenState.Idle
        }
    }

    override fun stop() {
        runCatching { stt.stop() }
        _state.value = ListenState.Idle
    }

    override fun release() {
        runCatching { stt.removeListener(listener) }
        runCatching { stt.release() }
    }

    private companion object {
        const val TAG = "PleosVoiceInput"
    }
}
