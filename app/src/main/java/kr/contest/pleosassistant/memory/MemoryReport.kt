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
    val belongings: List<BelongingRow>,
    val inputError: String?,
    /** 하차 알림 문장. 없으면 null */
    val arrivalMessage: String?,
    /** P단 신호를 읽을 수 있어 하차 알림이 동작하는지 */
    val parkAlertAvailable: Boolean,
    /** 하차 알림에서 챙길 물건(체크 목록). 알림이 없으면 빈 목록 */
    val arrivalItems: List<BelongingRow>,
    /** arrivalItems 중 챙겼다고 표시한 것 */
    val arrivalDone: Set<Long>,
    /** 동행 관리: 탄 적 있는 사람, 최근에 탄 사람부터 */
    val people: List<PersonRow>,
)

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
 * @property note 목록 둘째 줄. 예: "트렁크 · 도착 시 알림", "뒷자리 · 내일 챙기기"
 */
data class BelongingRow(
    val id: Long,
    val item: String,
    val place: String?,
    val dueLabel: String?,
    val remindNow: Boolean,
    val note: String,
)

object MemoryMessages {
    const val NOTHING = "기억해 둔 동승자와 물건이 없어요."
    const val NOT_UNDERSTOOD = "무엇을 어디에 뒀는지 알려 주세요. 예: 트렁크에 우산 넣었어"
    const val ALL_COLLECTED = "다 챙겼어요."
    val EXAMPLES = listOf("트렁크에 우산 넣었어", "내일 반납할 책 뒷자리에 뒀어")

    fun greeting(names: List<String>) = names.joinToString(", ") { "${it}님" } + " 타셨네요."
    fun ask(seat: Seat) = "${seat.label}에 누가 타셨어요?"
    fun confirm(name: String) = "오늘도 ${name}님이죠?"
    fun remembered(count: Int) = "물건 ${count}개를 기억하고 있어요."
    fun arrivalLeft(count: Int) = if (count > 0) "챙길 것 ${count}개 남았어요" else "다 챙겼어요"
    fun arrivalProgress(done: Int, total: Int) = "$done / $total 챙김"

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
    val arrivalItems = state.arrival.orEmpty().mapNotNull { id -> belongings.firstOrNull { it.id == id } }
    return MemoryReport(
        summary = summarize(state, today),
        seats = Seat.entries.map { seatCard(it, state.seats.getValue(it)) },
        question = Seat.entries.firstNotNullOfOrNull { question(it, state.seats.getValue(it)) },
        // 지금 챙길 것(날 없음 · 오늘 · 지난 날)이 위, 나중 날짜가 아래
        belongings = belongings.sortedWith(compareBy<Belonging>({ it.due ?: LocalDate.MIN }, { it.id })).map { it.toRow(today) },
        inputError = state.inputError,
        // 체크 목록에서 아직 안 챙긴 것만 말한다. 다 챙겼으면 그렇다고 말한다
        arrivalMessage = arrivalItems.takeIf { it.isNotEmpty() }?.let { all ->
            all.filterNot { it.id in state.arrivalDone }.takeIf { it.isNotEmpty() }?.let(::arrivalMessage) ?: MemoryMessages.ALL_COLLECTED
        },
        parkAlertAvailable = state.gear !is Reading.Unsupported,
        arrivalItems = arrivalItems.map { it.toRow(today) },
        arrivalDone = state.arrivalDone.filter { id -> arrivalItems.any { it.id == id } }.toSet(),
        people = people(state.data.rides),
    )
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
    return BelongingRow(id, item, place, label, remindNow, note = "${place ?: "위치 모름"} · $whenText")
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
