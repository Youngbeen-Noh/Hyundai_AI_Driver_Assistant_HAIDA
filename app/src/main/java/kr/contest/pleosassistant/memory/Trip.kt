package kr.contest.pleosassistant.memory

import java.time.LocalDateTime

/**
 * 내비 안내 상태. destination 이 null 이면 안내 중이 아니다.
 * @property bookmark 내비 즐겨찾기의 "회사" · "집"과 같은 곳이면 그 이름
 */
data class NaviStatus(
    val destination: String? = null,
    val bookmark: String? = null,
    val remainMeters: Int? = null,
    val remainSeconds: Int? = null,
)

/** 정한 값(계획서에 숫자가 없어 정함) */
object TripRules {
    /** 도착 직전: 남은 거리가 이 값(m) 이하이거나 */
    const val APPROACH_METERS = 500

    /** 남은 시간이 이 값(초) 이하 */
    const val APPROACH_SECONDS = 60
}

/** 한 번의 안내에서 말하는 때 */
enum class TripMoment { DEPARTED, APPROACHING, ARRIVED }

/** 지금 내비로 가는 길. said 는 이미 말한 때(한 번씩만 말한다) */
data class Trip(
    val destination: String,
    val bookmark: String? = null,
    val remainMeters: Int? = null,
    val remainSeconds: Int? = null,
    val said: Set<TripMoment> = emptySet(),
) {
    val approaching: Boolean
        get() = (remainMeters != null && remainMeters <= TripRules.APPROACH_METERS) ||
            (remainSeconds != null && remainSeconds <= TripRules.APPROACH_SECONDS)
}

/**
 * 챙길 것 체크 목록 한 줄.
 * @property belongingId 있으면 이번에만 챙길 물건(챙기면 지운다), 없으면 목적지마다 늘 챙길 것
 */
data class CheckItem(val key: String, val item: String, val owner: String?, val place: String?, val belongingId: Long?)

/** 도착 · 동승자 하차 때 띄우는 체크 목록. title 은 머리 문장("회사에 도착했어요", "민수님이 내려요") */
data class Arrival(val title: String, val items: List<CheckItem>, val done: Set<String> = emptySet())

/** 비서가 할 말(화면 아래 패널 + 음성). seq 가 바뀔 때마다 한 번 말한다 */
data class Announcement(val seq: Long, val tag: String, val text: String, val dismissed: Boolean = false)

// ---- 목적지마다 늘 챙길 것 ----

private fun String.key() = lowercase().replace(" ", "")

/** 어디로 가든 챙길 것의 장소 이름("매일 텀블러 챙기라고 해줘") */
const val EVERY_DAY = "매일"

/** 이 목적지의 늘 챙길 것: 즐겨찾기 이름("회사")이 같거나, 목적지 이름에 장소 말("헬스장")이 들어 있으면 */
fun routineFor(routines: List<Routine>, destination: String, bookmark: String?): Routine? {
    val places = routines.filterNot { it.place == EVERY_DAY }
    return places.firstOrNull { bookmark != null && it.place.key() == bookmark.key() }
        ?: places.firstOrNull { destination.key().contains(it.place.key()) }
}

/** 매일 챙길 것 */
fun dailyItems(routines: List<Routine>): List<String> = routines.firstOrNull { it.place == EVERY_DAY }?.items.orEmpty()

/** 이번 길에 늘 챙길 것: 매일 챙길 것 + 이 목적지의 것 */
fun tripItems(routines: List<Routine>, destination: String, bookmark: String?): List<String> =
    (dailyItems(routines) + routineFor(routines, destination, bookmark)?.items.orEmpty()).distinct()

/** 같은 장소면 물건을 더한다 */
fun MemoryState.addRoutine(place: String, items: List<String>): MemoryState {
    val name = place.trim()
    val clean = items.map { it.trim() }.filter { it.isNotEmpty() }
    if (name.isEmpty() || clean.isEmpty()) return this
    val exists = data.routines.any { it.place.key() == name.key() }
    val routines = if (!exists) {
        data.routines + Routine(name, clean.distinct())
    } else {
        data.routines.map { if (it.place.key() == name.key()) it.copy(items = (it.items + clean).distinct()) else it }
    }
    return copy(data = data.copy(routines = routines))
}

fun MemoryState.removeRoutineItem(place: String, item: String): MemoryState = copy(
    data = data.copy(
        routines = data.routines.mapNotNull { r ->
            if (r.place.key() != place.key()) r else r.copy(items = r.items - item).takeIf { it.items.isNotEmpty() }
        },
    ),
)

fun MemoryState.removeRoutine(place: String): MemoryState =
    copy(data = data.copy(routines = data.routines.filterNot { it.place.key() == place.key() }))

// ---- 내비 ----

/** 지금 가는 곳 이름: 맞는 늘 챙길 것의 장소 → 즐겨찾기 이름 → 목적지 이름 */
fun MemoryState.tripPlace(): String? = trip?.let { t ->
    routineFor(data.routines, t.destination, t.bookmark)?.place ?: t.bookmark ?: t.destination
}

/** 내비 상태가 바뀜. 목적지가 새로 정해지면 늘 챙길 것을 불러오고, 도착 직전이 되면 말한다 */
fun MemoryState.onNavi(status: NaviStatus, now: LocalDateTime): MemoryState {
    val destination = status.destination ?: return copy(trip = null)
    val same = trip?.destination == destination
    val nextTrip = if (same) {
        trip!!.copy(
            bookmark = status.bookmark ?: trip.bookmark,
            remainMeters = status.remainMeters,
            remainSeconds = status.remainSeconds,
        )
    } else {
        Trip(destination, status.bookmark, status.remainMeters, status.remainSeconds)
    }
    var next = copy(trip = nextTrip)
    // 달리는 중에 목적지를 정했으면 그때가 출발
    if (!same && next.driving) next = next.departed()
    if (next.driving && nextTrip.approaching && TripMoment.APPROACHING !in nextTrip.said) next = next.approach(now)
    return next
}

/** 내비가 목적지 도착을 알림 */
fun MemoryState.onNaviArrived(now: LocalDateTime): MemoryState =
    if (trip != null && TripMoment.ARRIVED !in trip.said) arrive(now) else this

/** P 에서 출발: 늘 챙길 것을 확인한다("회사 가시네요. 텀블러, 사원증, 지갑 챙기셨나요?") */
internal fun MemoryState.departed(): MemoryState {
    val t = trip ?: return this
    if (TripMoment.DEPARTED in t.said) return this
    val marked = copy(trip = t.copy(said = t.said + TripMoment.DEPARTED))
    val items = tripItems(data.routines, t.destination, t.bookmark)
    if (items.isEmpty()) return marked
    return marked.announce(TAG_DEPART, MemoryMessages.departure(tripPlace()!!, items))
}

/** 도착 직전: 늘 챙길 것 + 이번에 챙길 물건을 말한다 */
private fun MemoryState.approach(now: LocalDateTime): MemoryState {
    val t = trip ?: return this
    val marked = copy(trip = t.copy(said = t.said + TripMoment.APPROACHING))
    val items = marked.checklist(now)
    if (items.isEmpty()) return marked
    return marked.announce(TAG_APPROACH, MemoryMessages.approach(tripPlace()!!, items))
}

/** 도착 직후: 말하고 체크 목록을 띄운다 */
internal fun MemoryState.arrive(now: LocalDateTime): MemoryState {
    val t = trip ?: return this
    val marked = copy(trip = t.copy(said = t.said + TripMoment.ARRIVED))
    val items = marked.checklist(now)
    if (items.isEmpty()) return marked
    val place = tripPlace()!!
    return marked.copy(arrival = Arrival(MemoryMessages.arrivedAt(place), items)).announce(TAG_ARRIVE, MemoryMessages.arrived(place, items))
}

/** 이번 길에 챙길 것: 이 목적지의 늘 챙길 것 + 오늘 챙길 물건(동승자 것 포함) */
internal fun MemoryState.checklist(now: LocalDateTime): List<CheckItem> {
    val always = trip?.let { tripItems(data.routines, it.destination, it.bookmark) } ?: dailyItems(data.routines)
    val fixed = always.map { CheckItem("r:$it", it, owner = null, place = null, belongingId = null) }
    val temporary = dueBelongings(data.belongings, now.toLocalDate()).map { it.toCheckItem() }
    return fixed + temporary
}

internal fun Belonging.toCheckItem() = CheckItem("b:$id", item, owner, place, id)

internal fun MemoryState.announce(tag: String, text: String): MemoryState =
    copy(announcement = Announcement((announcement?.seq ?: 0) + 1, tag, text))

/** 패널의 알림을 닫는다(말은 이미 했다) */
fun MemoryState.dismissAnnouncement(): MemoryState = copy(announcement = announcement?.copy(dismissed = true))

internal const val TAG_DEPART = "출발"
internal const val TAG_APPROACH = "도착 전"
internal const val TAG_ARRIVE = "도착"
internal const val TAG_LEAVE = "하차"
internal const val TAG_REPLY = "하이다"
