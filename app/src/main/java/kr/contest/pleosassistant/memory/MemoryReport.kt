package kr.contest.pleosassistant.memory

import kr.contest.pleosassistant.status.Reading
import java.time.LocalDate

/** 화면이 그대로 그리는 기능 2 결과 */
data class MemoryReport(
    /** 맨 위 한 문장. 예: "민수님 타셨네요. 트렁크에 둔 택배도 잊지 마세요." */
    val summary: String,
    val seats: List<SeatCard>,
    /** 지금 답을 기다리는 질문(한 번에 하나). 없으면 null */
    val question: Question?,
    /** 이번에만 챙길 물건 */
    val belongings: List<BelongingRow>,
    val inputError: String?,
    /** P단 신호를 읽을 수 있어 하차 알림이 동작하는지 */
    val parkAlertAvailable: Boolean,
    /** 동행 관리: 탄 적 있는 사람, 최근에 탄 사람부터 */
    val people: List<PersonRow>,
    /** 내비로 가는 중이면 그 길과 늘 챙길 것 */
    val trip: TripCard?,
    /** 도착 · 동승자 하차 때의 체크 목록 */
    val checklist: Checklist?,
    /** 화면 아래 비서 패널에 띄울 말. 없으면 null */
    val panel: PanelMessage?,
    /** 목적지마다 늘 챙길 것 */
    val routines: List<Routine>,
    /** 지금 탄 동승자 이름(물건 주인 고르기) */
    val passengers: List<String>,
)

/** @property remain 남은 거리 · 시간. 예: "1.2km · 4분" */
data class TripCard(val place: String, val destination: String, val remain: String?, val routineItems: List<String>)

data class Checklist(val title: String, val rows: List<CheckRow>, val done: Int, val total: Int)

/** @property fixed 목적지마다 늘 챙길 것(챙겨도 지우지 않음) */
data class CheckRow(val key: String, val item: String, val note: String, val done: Boolean, val fixed: Boolean)

/** @property checklist 체크 목록 알림이면 true(버튼: 다 챙겼어요 · 나중에) */
data class PanelMessage(val seq: Long, val tag: String, val text: String, val checklist: Boolean)

/** @property detail 자주 탄 요일 · 시간대와 횟수. 예: "월·금 오전 · 3번 탔어요" */
data class PersonRow(val name: String, val detail: String)

enum class SeatTone { KNOWN, ASKING, UNNAMED, EMPTY, OFF }

data class SeatCard(val seat: Seat, val status: String, val tone: SeatTone)

sealed interface Question {
    val seat: Seat
    val text: String

    data class Confirm(override val seat: Seat, val name: String, override val text: String) : Question
    data class AskName(override val seat: Seat, val suggestions: List<String>, override val text: String) : Question
}

/**
 * @property remindNow 다음 하차 때 알릴 물건인지(챙길 날이 없거나 오늘 · 지난 날)
 * @property note 목록 둘째 줄. 예: "트렁크 · 도착 시 알림", "Minsu님 · 뒷자리 · 내일 챙기기"
 * @property owner 동승자의 물건이면 그 사람 이름
 */
data class BelongingRow(
    val id: Long,
    val item: String,
    val place: String?,
    val dueLabel: String?,
    val remindNow: Boolean,
    val note: String,
    val owner: String? = null,
)

object MemoryMessages {
    const val NOTHING = "기억해 둔 동승자와 물건이 없어요."
    const val NOT_UNDERSTOOD = "무엇을 어디에 뒀는지 알려 주세요. 예: 트렁크에 우산 넣었어"
    const val VOICE_NOT_UNDERSTOOD = "잘 못 알아들었어요. 예: 오늘 보고서류 챙기라고 해줘"
    const val VOICE_TIMEOUT = "말씀을 듣지 못했어요. 다시 말하기를 눌러 주세요."
    const val ALL_COLLECTED = "다 챙겼어요."
    const val ALL_COLLECTED_SPOKEN = "다 챙기셨네요."
    const val NOTHING_TO_COLLECT = "챙길 물건이 없어요."
    const val ARRIVED_TITLE = "도착했어요"
    val EXAMPLES = listOf("트렁크에 우산 넣었어", "오늘 보고서류 챙기라고 해줘", "회사 갈 때 텀블러 사원증 지갑 챙기라고 해줘")

    fun greeting(names: List<String>) = names.joinToString(", ") { "${it}님" } + " 타셨네요."
    fun ask(seat: Seat) = "${seat.label}에 누가 타셨어요?"
    fun confirm(name: String) = "오늘도 ${name}님이죠?"
    fun remembered(count: Int) = "물건 ${count}개를 기억하고 있어요."
    fun arrivalLeft(count: Int) = if (count > 0) "챙길 것 ${count}개 남았어요" else "다 챙겼어요"
    fun arrivalProgress(done: Int, total: Int) = "$done / $total 챙김"
    fun arrivedAt(place: String) = "${place}에 도착했어요"
    fun passengerLeaving(name: String) = "${name}님이 내려요"

    /** 출발 직후: "회사 가시네요. 텀블러, 사원증, 지갑 챙기셨나요?" */
    fun departure(place: String, items: List<String>) = "$place 가시네요. ${items.joinToString(", ")} 챙기셨나요?"

    /** 도착 직전: "곧 회사에 도착해요. 텀블러, 보고서류 챙기세요. 민수님은 가방 챙기세요." */
    fun approach(place: String, items: List<CheckItem>) = "곧 ${place}에 도착해요. " + itemsSentence(items, "챙기세요.")

    /** 도착 직후: "회사에 도착했어요. 텀블러, 보고서류 잊지 마세요." */
    fun arrived(place: String, items: List<CheckItem>) = "${place}에 도착했어요. " + itemsSentence(items, "잊지 마세요.")

    fun passengerReminder(name: String, items: List<String>) = "${name}님, ${items.joinToString(", ")} 챙기세요."

    /** 말로 물건을 맡겼을 때의 대답 */
    fun rememberedItem(item: String, owner: String?, place: String?, later: Boolean): String {
        val whose = owner?.let { "${it}님 " }.orEmpty()
        val whenText = when {
            later -> "그날 내릴 때 알려 드릴게요."
            place != null -> "$place 도착 전에 알려 드릴게요."
            else -> "내릴 때 알려 드릴게요."
        }
        return "$whose$item 기억할게요. $whenText"
    }

    fun collectedItem(item: String) = "$item 챙기셨네요."
    fun notRemembered(item: String) = "$item, 기억해 둔 물건이 아니에요."
    fun routineAdded(place: String, items: List<String>) =
        if (place == EVERY_DAY) "매일 ${items.joinToString(", ")} 챙기라고 알려 드릴게요." else "$place 갈 때 ${items.joinToString(", ")} 챙기라고 알려 드릴게요."

    /** 내 물건은 그대로, 동승자 물건은 "민수님은 가방 …"으로 나눠 말한다 */
    fun itemsSentence(items: List<CheckItem>, verb: String): String {
        val mine = items.filter { it.owner == null }.map { it.item }
        val others = items.filter { it.owner != null }.groupBy({ it.owner!! }, { it.item })
        val parts = buildList {
            if (mine.isNotEmpty()) add("${mine.joinToString(", ")} $verb")
            others.forEach { (name, list) -> add("${name}님은 ${list.joinToString(", ")} $verb") }
        }
        return parts.joinToString(" ")
    }

    /** 동행 관리의 지우기 확인. 받침에 맞춰 조사를 고른다(명 → 과 · 을, 개 → 를). */
    fun forgetQuestion(people: Int, items: Int): String = when {
        people > 0 && items > 0 -> "동행 ${people}명과 소지품 ${items}개를 지울까요?"
        people > 0 -> "동행 ${people}명을 지울까요?"
        items > 0 -> "소지품 ${items}개를 지울까요?"
        else -> "지울 기억이 없어요."
    }
}

fun buildMemoryReport(state: MemoryState, today: LocalDate): MemoryReport {
    val belongings = state.data.belongings
    val checklist = state.arrival?.let { open ->
        val rows = open.items.map { it.toRow(open.done) }
        Checklist(open.title, rows, done = rows.count { it.done }, total = rows.size)
    }
    return MemoryReport(
        summary = summarize(state, today),
        seats = Seat.entries.map { seatCard(it, state.seats.getValue(it)) },
        question = Seat.entries.firstNotNullOfOrNull { question(it, state.seats.getValue(it)) },
        // 지금 챙길 것(날 없음 · 오늘 · 지난 날)이 위, 나중 날짜가 아래
        belongings = belongings.sortedWith(compareBy<Belonging>({ it.due ?: LocalDate.MIN }, { it.id })).map { it.toRow(today) },
        inputError = state.inputError,
        parkAlertAvailable = state.gear !is Reading.Unsupported,
        people = people(state.data.rides),
        trip = state.trip?.let { t ->
            TripCard(
                place = state.tripPlace()!!,
                destination = t.destination,
                remain = remainLabel(t.remainMeters, t.remainSeconds),
                routineItems = tripItems(state.data.routines, t.destination, t.bookmark),
            )
        },
        checklist = checklist,
        panel = panel(state, checklist),
        routines = state.data.routines,
        passengers = Seat.entries.mapNotNull { (state.seats.getValue(it) as? SeatState.Known)?.name },
    )
}

/** 체크 목록이 떠 있으면 남은 것을, 아니면 마지막으로 한 말을 띄운다 */
private fun panel(state: MemoryState, checklist: Checklist?): PanelMessage? {
    val said = state.announcement
    val open = state.arrival
    if (checklist != null && open != null) {
        val left = open.items.filterNot { it.key in open.done }
        val text = if (left.isEmpty()) MemoryMessages.ALL_COLLECTED else MemoryMessages.itemsSentence(left, "챙기세요.")
        return PanelMessage(said?.seq ?: 0, said?.tag ?: TAG_ARRIVE, text, checklist = true)
    }
    return said?.takeUnless { it.dismissed }?.let { PanelMessage(it.seq, it.tag, it.text, checklist = false) }
}

/** "1.2km · 4분", "350m · 1분" */
internal fun remainLabel(meters: Int?, seconds: Int?): String? {
    val distance = meters?.let { if (it >= 1000) "%.1fkm".format(it / 1000.0) else "${it}m" }
    val time = seconds?.let { "${maxOf(1, (it + 59) / 60)}분" }
    return listOfNotNull(distance, time).joinToString(" · ").ifEmpty { null }
}

private fun CheckItem.toRow(done: Set<String>): CheckRow {
    val note = if (belongingId == null) {
        "늘 챙기는 것"
    } else {
        listOfNotNull(owner?.let { "${it}님" }, place).joinToString(" · ").ifEmpty { "이번에만" }
    }
    return CheckRow(key, item, note, done = key in done, fixed = belongingId == null)
}

/** "트렁크에 둔 우산 챙기세요." 셋 이상이면 "…, … 외 1개 챙기세요." */
fun arrivalMessage(items: List<Belonging>): String {
    val named = items.take(2).joinToString(", ") { it.describe() }
    val more = if (items.size > 2) " 외 ${items.size - 2}개" else ""
    return "$named$more 챙기세요."
}

/** 계획서 예시처럼 동승자 인사 → 챙길 물건 순서. 예: "민수님 타셨네요. 트렁크에 둔 택배도 잊지 마세요." */
internal fun summarize(state: MemoryState, today: LocalDate): String {
    val names = Seat.entries.mapNotNull { (state.seats.getValue(it) as? SeatState.Known)?.name }
    val due = dueBelongings(state.data.belongings, today)
    val greeting = names.takeIf { it.isNotEmpty() }?.let(MemoryMessages::greeting)
    val reminder = due.firstOrNull()?.let { first ->
        val what = first.describe() + if (due.size > 1) " 외 ${due.size - 1}개" else ""
        if (greeting != null) "${what}도 잊지 마세요." else "$what 잊지 마세요."
    }
    return when {
        greeting != null && reminder != null -> "$greeting $reminder"
        greeting != null -> greeting
        reminder != null -> reminder
        state.data.belongings.isNotEmpty() -> MemoryMessages.remembered(state.data.belongings.size)
        else -> MemoryMessages.NOTHING
    }
}

private fun Belonging.describe(): String = place?.let { "${it}에 둔 $item" } ?: item

private fun Belonging.toRow(today: LocalDate): BelongingRow {
    val label = due?.let { dueLabel(it, today) }
    val remindNow = due == null || !due.isAfter(today)
    val whenText = when {
        label == null -> "도착 시 알림"
        remindNow -> "$label · 도착 시 알림"
        else -> "$label 챙기기"
    }
    val whose = owner?.let { "${it}님 · " }.orEmpty()
    return BelongingRow(id, item, place, label, remindNow, note = "$whose${place ?: "위치 모름"} · $whenText", owner = owner)
}

/** 동행 관리 목록: 이름마다 자주 탄 요일 · 시간대와 횟수. 최근에 탄 사람부터 */
internal fun people(rides: List<Ride>): List<PersonRow> = rides
    .groupBy { it.name }
    .entries
    .sortedByDescending { (_, list) -> list.maxOf { it.at } }
    .map { (name, list) -> PersonRow(name, personDetail(list)) }

private val WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")

private fun personDetail(rides: List<Ride>): String {
    // 많이 탄 요일 두 개를 월 → 일 순서로
    val days = rides.groupingBy { it.at.dayOfWeek }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<java.time.DayOfWeek, Int>> { it.value }.thenBy { it.key })
        .take(2)
        .map { it.key }
        .sorted()
        .joinToString("·") { WEEKDAYS[it.value - 1] }
    val time = rides.groupingBy { timeOfDay(it.at.hour) }.eachCount().entries.maxBy { it.value }.key
    return "$days $time · ${rides.size}번 탔어요"
}

private fun timeOfDay(hour: Int): String = when (hour) {
    in 5..11 -> "오전"
    in 12..17 -> "오후"
    in 18..22 -> "저녁"
    else -> "밤"
}

private fun dueLabel(due: LocalDate, today: LocalDate): String = when {
    due == today -> "오늘"
    due == today.plusDays(1) -> "내일"
    due.isBefore(today) -> "${due.monthValue}월 ${due.dayOfMonth}일 지남"
    else -> "${due.monthValue}월 ${due.dayOfMonth}일"
}

private fun seatCard(seat: Seat, state: SeatState): SeatCard = when (state) {
    SeatState.Checking -> SeatCard(seat, "확인 중", SeatTone.OFF)
    SeatState.Unsupported -> SeatCard(seat, "감지 안 됨", SeatTone.OFF)
    SeatState.Unknown -> SeatCard(seat, "확인 불가", SeatTone.OFF)
    SeatState.Empty -> SeatCard(seat, "비어 있음", SeatTone.EMPTY)
    is SeatState.Asking -> SeatCard(seat, "누구세요?", SeatTone.ASKING)
    is SeatState.Confirming -> SeatCard(seat, "${state.name}님?", SeatTone.ASKING)
    is SeatState.Known -> SeatCard(seat, "${state.name}님", SeatTone.KNOWN)
    is SeatState.Unnamed -> SeatCard(seat, "동승자", SeatTone.UNNAMED)
}

private fun question(seat: Seat, state: SeatState): Question? = when (state) {
    is SeatState.Confirming -> Question.Confirm(seat, state.name, MemoryMessages.confirm(state.name))
    is SeatState.Asking -> Question.AskName(seat, state.suggestions, MemoryMessages.ask(seat))
    else -> null
}
