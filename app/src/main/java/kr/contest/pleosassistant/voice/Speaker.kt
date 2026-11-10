package kr.contest.pleosassistant.voice

/** 비서의 말(음성 출력) */
interface Speaker {
    fun say(text: String)
    fun stop()
    fun release()
}
