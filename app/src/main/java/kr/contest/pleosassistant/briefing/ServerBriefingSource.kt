package kr.contest.pleosassistant.briefing

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 브리핑 서버(server/, Claude 웹 검색 + 요약)에서 브리핑을 받아 기기에 둔다.
 * 서버가 기사 검색 · 해요체 대본 · 용어 풀이 · 관련 기사까지 만들어 주고, 앱은 받아 둔 것을 차례로 읽는다.
 * @param baseUrl 예: https://haida-briefing.<계정>.workers.dev (디버그 빌드에서 에뮬레이터는 http://localhost:8787 + adb reverse)
 * @param token 서버의 APP_TOKEN 과 같은 값
 */
class ServerBriefingSource(
    private val baseUrl: String,
    private val token: String,
    private val cacheFile: File,
    private val now: () -> Long = System::currentTimeMillis,
) : PreparingBriefingSource {

    private var cache: BriefingCache = load()
    private val _note = MutableStateFlow<String?>(null)
    override val note: StateFlow<String?> = _note.asStateFlow()

    @Synchronized
    override fun briefing(category: Category, seq: Int): Briefing? {
        val (next, b) = cache.take(category, now())
        cache = next
        if (b != null) save()
        return b
    }

    override suspend fun prepare(needed: List<Category>): Boolean {
        val missing = synchronized(this) { cache.missing(needed, now()) }
        if (missing.isEmpty()) return true
        _note.value = NOTE_MAKING
        val problems = withContext(Dispatchers.IO) {
            coroutineScope {
                missing.flatMap { (cat, n) -> List(n) { cat } }
                    .map { cat -> async { fetch(cat) } }
                    .awaitAll()
                    .filterNotNull()
            }
        }
        _note.value = problems.firstOrNull()
        return problems.isEmpty()
    }

    /** 분야 하나를 받아 둔다. 실패하면 화면에 보일 문장 */
    private fun fetch(category: Category): String? {
        val avoid = synchronized(this) { cache.heard + cache.items.flatMap { p -> p.briefing.subtopics.map { it.title } } }
        val body = JSONObject().put("category", category.name).put("avoid", JSONArray(avoid.takeLast(BriefingCache.MAX_HEARD))).toString()
        val started = now()
        val (status, text) = post("$baseUrl/v1/briefing", body)
        if (status != 200 || text == null) {
            Log.w(TAG, "${category.name} 브리핑 실패: $status ${text?.take(200)}")
            return problemText(status, text)
        }
        val briefing = decodeServerBriefing(text, "srv-${category.name}-$started")
            ?: return "브리핑 서버 응답을 읽지 못했어요".also { Log.w(TAG, "응답 형식이 맞지 않음") }
        Log.i(TAG, "${category.name} 브리핑 받음: 소주제 ${briefing.subtopics.size}개, ${(now() - started) / 1000}초")
        synchronized(this) {
            cache = cache.add(briefing, now())
            save()
        }
        return null
    }

    private fun post(url: String, body: String): Pair<Int, String?> = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 10_000
        // 서버가 웹 검색 · 요약을 하는 데 1분 안팎 걸린다
        c.readTimeout = 180_000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        c.setRequestProperty("x-haida-token", token)
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val status = c.responseCode
        val text = (if (status >= 400) c.errorStream else c.inputStream)?.use { it.readBytes().toString(Charsets.UTF_8) }
        c.disconnect()
        status to text
    }.getOrElse { e ->
        Log.w(TAG, "브리핑 서버 연결 실패: $e")
        0 to null
    }

    private fun load(): BriefingCache = runCatching {
        if (cacheFile.exists()) BriefingCache.decode(cacheFile.readText()) else BriefingCache()
    }.getOrDefault(BriefingCache())

    private fun save() {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            tmp.writeText(cache.encode())
            if (!tmp.renameTo(cacheFile)) {
                cacheFile.delete()
                tmp.renameTo(cacheFile)
            }
        }.onFailure { Log.w(TAG, "받아 둔 브리핑 저장 실패: $it") }
    }

    private companion object {
        const val TAG = "ServerBriefingSource"
    }
}

const val NOTE_MAKING = "최근 기사를 찾아 새 브리핑을 만드는 중이에요 · 1분쯤 걸려요"
const val NOTE_DEMO = "시연용 브리핑이에요 · 가상의 언론사와 기사예요"

/** 서버 응답 코드 → 화면 문장 */
fun problemText(status: Int, body: String?): String {
    val reason = body?.let { runCatching { JSONObject(it).optString("error") }.getOrNull() }?.takeIf { it.isNotBlank() }
    return when (status) {
        0 -> "브리핑 서버에 연결하지 못했어요 · 잠시 뒤 다시 해 볼게요"
        401 -> "브리핑 서버 설정(앱 토큰)이 맞지 않아요"
        else -> "브리핑을 만들지 못했어요" + (reason?.let { " · $it" } ?: "")
    }
}
