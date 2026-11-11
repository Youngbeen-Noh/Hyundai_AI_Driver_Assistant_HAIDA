package kr.contest.pleosassistant.storage

import android.util.AtomicFile
import android.util.Log
import kr.contest.pleosassistant.drive.DriveData
import kr.contest.pleosassistant.drive.DriveEvent
import kr.contest.pleosassistant.drive.DriveEventType
import kr.contest.pleosassistant.drive.DriveStore
import kr.contest.pleosassistant.drive.TripSummary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime

/** 주행 기록을 기기 안 파일 하나(JSON)에 둔다. 경로는 저장하지 않고 점수 · 이벤트만 둔다 */
class FileDriveStore(file: File) : DriveStore {

    private val file = AtomicFile(file)

    override fun load(): DriveData = runCatching {
        if (!file.baseFile.exists()) DriveData() else decode(JSONObject(String(file.readFully(), Charsets.UTF_8)))
    }.getOrElse { e ->
        Log.w(TAG, "주행 기록 파일을 읽지 못해 빈 값으로 시작: $e")
        DriveData()
    }

    @Synchronized
    override fun save(data: DriveData) {
        var out: java.io.FileOutputStream? = null
        try {
            out = file.startWrite()
            out.write(encode(data).toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            out?.let(file::failWrite)
            Log.w(TAG, "주행 기록 저장 실패: $e")
        }
    }

    private fun encode(data: DriveData) = JSONObject()
        .put("version", VERSION)
        .put(
            "trips",
            JSONArray(
                data.trips.map { t ->
                    JSONObject()
                        .put("start", t.start.toString())
                        .put("end", t.end.toString())
                        .put("distanceKm", t.distanceKm)
                        .put("movingSec", t.movingSec)
                        .put("steadyRatio", t.steadyRatio)
                        .put("safety", t.safety)
                        .put("eco", t.eco)
                        .put("score", t.score)
                        .put(
                            "events",
                            JSONArray(
                                t.events.map { e ->
                                    JSONObject().put("type", e.type.name).put("at", e.at.toString()).put("from", e.fromKmh).put("to", e.toKmh)
                                },
                            ),
                        )
                },
            ),
        )

    private fun decode(json: JSONObject): DriveData {
        val trips = json.optJSONArray("trips").objects().map { o ->
            TripSummary(
                start = LocalDateTime.parse(o.getString("start")),
                end = LocalDateTime.parse(o.getString("end")),
                distanceKm = o.getDouble("distanceKm"),
                movingSec = o.getInt("movingSec"),
                events = o.optJSONArray("events").objects().mapNotNull { e ->
                    val type = DriveEventType.entries.firstOrNull { it.name == e.optString("type") } ?: return@mapNotNull null
                    DriveEvent(type, LocalDateTime.parse(e.getString("at")), e.getInt("from"), e.getInt("to"))
                },
                steadyRatio = o.getDouble("steadyRatio"),
                safety = o.getInt("safety"),
                eco = o.getInt("eco"),
                score = o.getInt("score"),
            )
        }
        return DriveData(trips)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private companion object {
        const val TAG = "FileDriveStore"
        const val VERSION = 1
    }
}
