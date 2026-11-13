package kr.contest.pleosassistant.storage

import android.util.Log
import kr.contest.pleosassistant.briefing.BriefingSettings
import kr.contest.pleosassistant.briefing.Category
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 브리핑 설정. 기능 4 는 선택 기능이라 끌 수 있다 */
data class BriefingPrefs(val settings: BriefingSettings = BriefingSettings(), val autoStart: Boolean = true)

interface BriefingStore {
    fun load(): BriefingPrefs
    fun save(prefs: BriefingPrefs)
}

class FileBriefingStore(private val file: File) : BriefingStore {

    override fun load(): BriefingPrefs = runCatching {
        if (!file.exists()) return BriefingPrefs()
        decodeBriefingPrefs(file.readText())
    }.getOrElse {
        Log.w(TAG, "브리핑 설정을 읽지 못해 기본값으로 시작: $it")
        BriefingPrefs()
    }

    override fun save(prefs: BriefingPrefs) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(encodeBriefingPrefs(prefs))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }.onFailure { Log.w(TAG, "브리핑 설정 저장 실패: $it") }
    }

    private companion object {
        const val TAG = "FileBriefingStore"
    }
}

fun encodeBriefingPrefs(p: BriefingPrefs): String = JSONObject().apply {
    put("selected", JSONArray(p.settings.selected.map { it.name }))
    put("keepPrepared", p.settings.keepPrepared)
    put("autoStart", p.autoStart)
}.toString()

fun decodeBriefingPrefs(json: String): BriefingPrefs {
    val o = JSONObject(json)
    val arr = o.optJSONArray("selected")
    val selected = (0 until (arr?.length() ?: 0)).mapNotNull { i -> Category.entries.firstOrNull { it.name == arr!!.getString(i) } }
    return BriefingPrefs(
        settings = BriefingSettings(selected = selected, keepPrepared = o.optBoolean("keepPrepared", false)),
        autoStart = o.optBoolean("autoStart", true),
    )
}
