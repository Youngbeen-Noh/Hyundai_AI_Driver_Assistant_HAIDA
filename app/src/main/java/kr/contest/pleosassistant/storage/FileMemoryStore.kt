package kr.contest.pleosassistant.storage

import android.util.AtomicFile
import android.util.Log
import kr.contest.pleosassistant.memory.Belonging
import kr.contest.pleosassistant.memory.MemoryData
import kr.contest.pleosassistant.memory.MemoryStore
import kr.contest.pleosassistant.memory.Ride
import kr.contest.pleosassistant.memory.Seat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 기억을 기기 안 파일 하나(JSON)에 둔다. 서버로 보내지 않는다.
 * 앱의 noBackupFilesDir 에 두어 기기 백업으로도 밖에 나가지 않게 한다(매니페스트도 allowBackup=false).
 * 파일이 깨졌으면 빈 기억으로 시작한다.
 */
class FileMemoryStore(file: File) : MemoryStore {

    private val file = AtomicFile(file)

    override fun load(): MemoryData = runCatching {
        if (!file.baseFile.exists()) MemoryData() else decode(JSONObject(String(file.readFully(), Charsets.UTF_8)))
    }.getOrElse { e ->
        Log.w(TAG, "기억 파일을 읽지 못해 빈 기억으로 시작: $e")
        MemoryData()
    }

    @Synchronized
    override fun save(data: MemoryData) {
        val out = file.startWrite()
        try {
            out.write(encode(data).toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.w(TAG, "기억 저장 실패: $e")
        }
    }

    private fun encode(data: MemoryData) = JSONObject()
        .put("version", VERSION)
        .put("nextBelongingId", data.nextBelongingId)
        .put("rides", JSONArray(data.rides.map { JSONObject().put("name", it.name).put("seat", it.seat.name).put("at", it.at.toString()) }))
        .put(
            "belongings",
            JSONArray(
                data.belongings.map {
                    JSONObject()
                        .put("id", it.id)
                        .put("item", it.item)
                        .put("place", it.place ?: JSONObject.NULL)
                        .put("due", it.due?.toString() ?: JSONObject.NULL)
                        .put("addedAt", it.addedAt.toString())
                },
            ),
        )

    private fun decode(json: JSONObject): MemoryData {
        val rides = json.optJSONArray("rides").objects().mapNotNull { o ->
            val seat = Seat.entries.firstOrNull { it.name == o.optString("seat") } ?: return@mapNotNull null
            Ride(o.getString("name"), seat, LocalDateTime.parse(o.getString("at")))
        }
        val belongings = json.optJSONArray("belongings").objects().map { o ->
            Belonging(
                id = o.getLong("id"),
                item = o.getString("item"),
                place = o.optStringOrNull("place"),
                due = o.optStringOrNull("due")?.let(LocalDate::parse),
                addedAt = LocalDateTime.parse(o.getString("addedAt")),
            )
        }
        val nextId = maxOf(json.optLong("nextBelongingId", 1), (belongings.maxOfOrNull { it.id } ?: 0) + 1)
        return MemoryData(rides, belongings, nextId)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)

    private companion object {
        const val TAG = "FileMemoryStore"
        const val VERSION = 1
    }
}
