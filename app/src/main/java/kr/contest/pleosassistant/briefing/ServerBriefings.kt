package kr.contest.pleosassistant.briefing

import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

// 기능 4 · 실제 뉴스 브리핑: 브리핑 서버(server/, Claude 웹 검색)가 만든 대본을 미리 받아 기기에 둔다.
// 여기는 안드로이드에 의존하지 않는 부분(서버 응답 읽기 · 받아 둔 브리핑 관리). 네트워크는 ServerBriefingSource.

/**
 * 시간이 걸려 미리 받아 두는 브리핑 출처. [BriefingSource.briefing] 은 받아 둔 것만 바로 돌려주고,
 * 모자라면 화면 쪽이 [prepare] 를 불러 채운다.
 */
interface PreparingBriefingSource : BriefingSource {
    /** 화면에 보일 상태 한 줄(만드는 중 · 연결 실패). 문제없으면 null */
    val note: StateFlow<String?>

    /** [needed] 의 분야마다(같은 분야가 두 번이면 두 개) 받아 둔 브리핑이 있게 한다. 다 갖췄으면 true */
    suspend fun prepare(needed: List<Category>): Boolean
}

/** 서버 응답(JSON) → 브리핑. 소주제가 하나도 없으면 null */
fun decodeServerBriefing(json: String, id: String): Briefing? = runCatching {
    val o = JSONObject(json)
    val category = Category.entries.firstOrNull { it.name == o.optString("category") } ?: return null
    decodeSubtopics(o.optJSONArray("subtopics"))
        .takeIf { it.isNotEmpty() }
        ?.let { Briefing(id, category, it) }
}.getOrNull()

private fun decodeSubtopics(arr: JSONArray?): List<Subtopic> = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
    val s = arr!!.optJSONObject(i) ?: return@mapNotNull null
    val lines = s.optJSONArray("lines").strings()
    val title = s.optString("title")
    if (lines.isEmpty() || title.isBlank()) return@mapNotNull null
    val term = s.optJSONObject("term")?.let { t ->
        val word = t.optString("word")
        val meaning = t.optString("meaning")
        if (word.isNotBlank() && meaning.isNotBlank()) Term(word, meaning) else null
    }
    Subtopic(title, s.optString("press"), s.optString("publishedAt"), lines, term, s.optJSONArray("related").strings())
}

private fun JSONArray?.strings(): List<String> =
    (0 until (this?.length() ?: 0)).map { this!!.optString(it) }.filter { it.isNotBlank() }

/** 브리핑 → 저장용 JSON(서버 응답과 같은 모양 + id) */
fun encodeBriefing(b: Briefing): JSONObject = JSONObject().apply {
    put("id", b.id)
    put("category", b.category.name)
    put(
        "subtopics",
        JSONArray(
            b.subtopics.map { s ->
                JSONObject().apply {
                    put("title", s.title)
                    put("press", s.press)
                    put("publishedAt", s.publishedAt)
                    put("lines", JSONArray(s.lines))
                    put("term", s.term?.let { JSONObject().put("word", it.word).put("meaning", it.meaning) } ?: JSONObject.NULL)
                    put("related", JSONArray(s.related))
                }
            },
        ),
    )
}

/** 받아 둔 브리핑 하나 */
data class Prepared(val briefing: Briefing, val fetchedAtMs: Long)

/**
 * 받아 둔 브리핑과 이미 꺼내 준 소주제 제목. 터널에서도 끊기지 않게 파일로 둔다(기능계획서 6장).
 * 오래된 뉴스는 들려주지 않는다([MAX_AGE_MS]).
 */
data class BriefingCache(val items: List<Prepared> = emptyList(), val heard: List<String> = emptyList()) {

    fun fresh(nowMs: Long): BriefingCache = copy(items = items.filter { nowMs - it.fetchedAtMs < MAX_AGE_MS })

    fun count(category: Category, nowMs: Long): Int = fresh(nowMs).items.count { it.briefing.category == category }

    /** [category] 의 가장 먼저 받은 브리핑을 꺼낸다. 꺼낸 소주제 제목은 [heard] 에 남겨 서버가 다시 고르지 않게 한다 */
    fun take(category: Category, nowMs: Long): Pair<BriefingCache, Briefing?> {
        val live = fresh(nowMs)
        val hit = live.items.firstOrNull { it.briefing.category == category } ?: return live to null
        val heard = (live.heard + hit.briefing.subtopics.map { it.title }).takeLast(MAX_HEARD)
        return live.copy(items = live.items - hit, heard = heard) to hit.briefing
    }

    fun add(briefing: Briefing, nowMs: Long): BriefingCache = copy(items = items + Prepared(briefing, nowMs))

    /** [needed] 를 채우려면 분야마다 더 받아야 하는 수 */
    fun missing(needed: List<Category>, nowMs: Long): Map<Category, Int> =
        needed.groupingBy { it }.eachCount()
            .mapValues { (cat, want) -> want - count(cat, nowMs) }
            .filterValues { it > 0 }

    fun encode(): String = JSONObject().apply {
        put("items", JSONArray(items.map { JSONObject().put("fetchedAt", it.fetchedAtMs).put("briefing", encodeBriefing(it.briefing)) }))
        put("heard", JSONArray(heard))
    }.toString()

    companion object {
        /** 이보다 오래 받아 둔 브리핑은 버린다 */
        const val MAX_AGE_MS = 12 * 60 * 60 * 1000L
        /** 서버에 '이미 들려준 소주제'로 보내는 제목 수 */
        const val MAX_HEARD = 30

        fun decode(json: String): BriefingCache = runCatching {
            val o = JSONObject(json)
            val arr = o.optJSONArray("items")
            val items = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                val it = arr!!.getJSONObject(i)
                val b = it.getJSONObject("briefing")
                decodeServerBriefing(b.toString(), b.optString("id"))?.let { briefing -> Prepared(briefing, it.getLong("fetchedAt")) }
            }
            BriefingCache(items, o.optJSONArray("heard").strings())
        }.getOrDefault(BriefingCache())
    }
}
