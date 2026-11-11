package kr.contest.pleosassistant.voice

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 듣기(음성 인식) 상태 */
sealed interface ListenState {
    data object Idle : ListenState

    /** 듣는 중. partial 은 지금까지 알아들은 말 */
    data class Listening(val partial: String) : ListenState

    /** 이 차에서 음성 인식을 쓸 수 없음 */
    data class Unavailable(val reason: String) : ListenState
}

/** 사용자 말 듣기(음성 인식). 끝까지 알아들은 문장은 [results] 로 나온다. */
interface VoiceInput {
    val state: StateFlow<ListenState>
    val results: SharedFlow<String>
    fun start()
    fun stop()
    fun release()
}
