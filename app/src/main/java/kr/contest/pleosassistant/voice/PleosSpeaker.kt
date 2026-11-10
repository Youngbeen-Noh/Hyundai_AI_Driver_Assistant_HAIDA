package kr.contest.pleosassistant.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import java.util.Locale

/**
 * Pleos TextToSpeech SDK 로 말한다(권한 TTS_SERVICE).
 * 차 안 엔진(ON_DEVICE)만 써서 앱 등록 · 네트워크 없이 동작한다. 초기화 전에 들어온 말은 모아 두었다가 말한다.
 */
class PleosSpeaker(context: Context) : Speaker {

    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private val pending = ArrayDeque<String>()
    private var ready = false

    private val events = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() {
            Log.i(TAG, "말하기 시작")
        }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() {
            Log.i(TAG, "말하기 끝")
        }
        override fun onStop() = Unit
        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "말하기 오류: $errorType")
        }
    }

    init {
        tts.initialize(
            object : OnInitializeListener {
                override fun onSuccess() {
                    Log.i(TAG, "음성 출력 준비됨")
                    tts.setLocale(Locale.KOREA)
                    tts.addEventListener(events)
                    ready = true
                    while (pending.isNotEmpty()) speakNow(pending.removeFirst())
                }

                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "음성 출력 초기화 실패: $errorType")
                }
            },
        )
    }

    override fun say(text: String) {
        if (ready) speakNow(text) else pending.addLast(text)
    }

    private fun speakNow(text: String) {
        Log.i(TAG, "말함: $text")
        runCatching { tts.speak(text) }.onFailure { Log.w(TAG, "말하기 실패: $it") }
    }

    override fun stop() {
        runCatching { tts.stop() }
    }

    override fun release() {
        runCatching { tts.removeEventListener(events) }
        runCatching { tts.release() }
    }

    private companion object {
        const val TAG = "PleosSpeaker"
    }
}
