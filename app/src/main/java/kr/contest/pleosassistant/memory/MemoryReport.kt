package kr.contest.pleosassistant.memory

import kr.contest.pleosassistant.status.Reading
import java.time.LocalDate

/** 화면이 그대로 그리는 기능 2 결과. 팀 UI 템플릿을 받아도 이 모양은 그대로 둔다. */
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
)

enum class SeatTone { KNOWN, ASKING, UNNAMED, EMPTY, OFF }

data class SeatCard(val seat: Seat, val status: String, val tone: SeatTone)

sealed interface Question {
    val seat: Seat
    val text: String

    data class Confirm(override val seat: Seat, val name: String, override val text: String) : Question
    data class AskName(override val seat: Seat, val suggestions: List<String>, override val text: String) : Question
}

/** @property remindNow 다음 하차 때 알릴 물건인지(챙길 날이 없거나 오늘 · 지난 날) */
data class BelongingRow(
    val id: Long,
    val item: String,
    val place: String?,
    val dueLabel: String?,
    val remindNow: Boolean,
)

object MemoryMessages {
    const val NOTHING = "기억해 둔 동승자와 물건이 없어요."
    const val NOT_UNDERSTOOD = "무엇을 어디에 뒀는지 알려 주세요. 예: 트렁크에 우산 넣었어"
    val EXAMPLES = listOf("트렁크에 우산 넣었어", "내일 반납할 책 뒷자리에 뒀어")

    fun greeting(names: List<String>) = names.joinToString(", ") { "${it}님" } + " 타셨네요."
    fun ask(seat: Seat) = "${seat.label}에 누가 타셨어요?"
    fun confirm(name: String) = "오늘도 ${name}님이죠?"
    fun remembered(count: Int) = "물건 ${count}개를 기억하고 있어요."
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
        arrivalMessage = arrivalItems.takeIf { it.isNotEmpty() }?.let(::arrivalMessage),
        parkAlertAvailable = state.gear !is Reading.Unsupported,
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

private fun Belonging.toRow(today: LocalDate) = BelongingRow(
    id = id,
    item = item,
    place = place,
    dueLabel = due?.let { dueLabel(it, today) },
    remindNow = due == null || !due.isAfter(today),
)

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
