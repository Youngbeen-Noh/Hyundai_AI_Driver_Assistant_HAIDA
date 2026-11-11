package kr.contest.pleosassistant.storage

import android.util.Log
import kr.contest.pleosassistant.music.MusicApp
import kr.contest.pleosassistant.music.Taste
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 음악 취향과 고른 음악 앱. 차 안에만 저장한다(기능계획서 9장) */
data class MusicSettings(val taste: Taste = Taste(), val app: MusicApp = MusicApp.DEMO)

interface MusicStore {
    fun load(): MusicSettings
    fun save(settings: MusicSettings)
}

/** JSON 파일 하나에 저장한다. 임시 파일에 쓰고 바꿔 넣어 쓰다 끊겨도 깨지지 않는다 */
class FileMusicStore(private val file: File) : MusicStore {

    override fun load(): MusicSettings = runCatching {
        if (!file.exists()) return MusicSettings()
        decodeMusicSettings(file.readText())
    }.getOrElse {
        Log.w(TAG, "음악 설정을 읽지 못해 처음 상태로 시작: $it")
        MusicSettings()
    }

    override fun save(settings: MusicSettings) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(encodeMusicSettings(settings))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }.onFailure { Log.w(TAG, "음악 설정 저장 실패: $it") }
    }

    private companion object {
        const val TAG = "FileMusicStore"
    }
}

fun encodeMusicSettings(s: MusicSettings): String = JSONObject().apply {
    put("app", s.app.name)
    put("genres", JSONObject().apply { s.taste.genrePreference.forEach { (g, v) -> put(g, v.toDouble()) } })
    put("saved", JSONArray(s.taste.saved.sorted()))
    put("recent", JSONArray(s.taste.recent))
}.toString()

fun decodeMusicSettings(json: String): MusicSettings {
    val o = JSONObject(json)
    val genres = o.optJSONObject("genres")
    val prefs = genres?.keys()?.asSequence()?.associateWith { genres.getDouble(it).toFloat() }.orEmpty()
    fun JSONArray?.strings() = (0 until (this?.length() ?: 0)).map { this!!.getString(it) }
    return MusicSettings(
        taste = Taste(
            genrePreference = prefs,
            saved = o.optJSONArray("saved").strings().toSet(),
            recent = o.optJSONArray("recent").strings(),
        ),
        app = MusicApp.entries.firstOrNull { it.name == o.optString("app") } ?: MusicApp.DEMO,
    )
}
