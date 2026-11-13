package kr.contest.pleosassistant.storage

import android.util.AtomicFile
import android.util.Log
import kr.contest.pleosassistant.hazard.AlertSchedule
import kr.contest.pleosassistant.hazard.GeoPoint
import kr.contest.pleosassistant.hazard.HazardData
import kr.contest.pleosassistant.hazard.HazardStore
import kr.contest.pleosassistant.hazard.HazardZone
import kr.contest.pleosassistant.hazard.Pass
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 위험 구간을 기기 안 파일 하나(JSON)에 둔다. 서버로 보내지 않는다.
 * [FileMemoryStore] 와 같은 방식: noBackupFilesDir, 깨졌으면 빈 값으로 시작.
 */
class FileHazardStore(file: File) : HazardStore {

    private val file = AtomicFile(file)

    override fun load(): HazardData = runCatching {
        if (!file.baseFile.exists()) HazardData() else decode(JSONObject(String(file.readFully(), Charsets.UTF_8)))
    }.getOrElse { e ->
        Log.w(TAG, "위험 구간 파일을 읽지 못해 빈 값으로 시작: $e")
        HazardData()
    }

    @Synchronized
    override fun save(data: HazardData) {
        val out = file.startWrite()
        try {
            out.write(encode(data).toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.w(TAG, "위험 구간 저장 실패: $e")
        }
    }

    private fun encode(data: HazardData) = JSONObject()
        .put("version", VERSION)
        .put("nextZoneId", data.nextZoneId)
        .put(
            "zones",
            JSONArray(
                data.zones.map {
                    JSONObject()
                        .put("id", it.id)
                        .put("name", it.name)
                        .put("lat", it.center.lat)
                        .put("lon", it.center.lon)
                        .put("radiusM", it.radiusM)
                        .put("schedule", it.schedule.name)
                        .put("enabled", it.enabled)
                        .put("createdAt", it.createdAt.toString())
                },
            ),
        )
        .put(
            "passes",
            JSONArray(data.passes.map { (id, p) -> JSONObject().put("zoneId", id).put("date", p.date.toString()).put("count", p.count) }),
        )

    private fun decode(json: JSONObject): HazardData {
        val zones = json.optJSONArray("zones").objects().map { o ->
            HazardZone(
                id = o.getLong("id"),
                name = o.getString("name"),
                center = GeoPoint(o.getDouble("lat"), o.getDouble("lon")),
                radiusM = o.getInt("radiusM"),
                schedule = AlertSchedule.entries.firstOrNull { it.name == o.optString("schedule") } ?: AlertSchedule.ALWAYS,
                enabled = o.optBoolean("enabled", true),
                createdAt = LocalDateTime.parse(o.getString("createdAt")),
            )
        }
        val passes = json.optJSONArray("passes").objects().associate { o ->
            o.getLong("zoneId") to Pass(LocalDate.parse(o.getString("date")), o.getInt("count"))
        }
        val nextId = maxOf(json.optLong("nextZoneId", 1), (zones.maxOfOrNull { it.id } ?: 0) + 1)
        return HazardData(zones, passes, nextId)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private companion object {
        const val TAG = "FileHazardStore"
        const val VERSION = 1
    }
}
