package kr.contest.pleosassistant.memory

import kr.contest.pleosassistant.status.Reading
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.min

/** 정한 값. 계획서에 숫자가 없어서 정했다(PR 본문 '범위 밖'에 적음). */
object MemoryRules {
    /** "같은 시간대": 전에 탄 시각과 이만큼(분) 안쪽이면 같은 패턴으로 본다 */
    const val MATCH_WINDOW_MINUTES = 60

    /** 탑승 기록은 최근 것만 남긴다 */
    const val MAX_RIDES = 500

    /** 이름 묻기에서 보여 줄 전에 탔던 사람 수 */
    const val MAX_SUGGESTIONS = 4

    const val MAX_NAME_LENGTH = 12
}

/**
 * 기능 2 의 상태 전체. 함수마다 새 상태를 돌려준다(Android 와 Pleos SDK 에 기대지 않아 단위 테스트로 흐름을 확인한다).
 * @property trip 지금 내비로 가는 길. 안내 중이 아니면 null
 * @property arrival 도착 · 동승자 하차 때 띄운 체크 목록. null 이면 없음
 * @property announcement 비서가 마지막으로 한 말(화면 아래 패널 + 음성)
 * @property inputError 소지품 문장을 알아듣지 못했을 때 안내
 */
data class MemoryState(
    val data: MemoryData = MemoryData(),
    val seats: Map<Seat, SeatState> = Seat.entries.associateWith { SeatState.Checking },
    val gear: Reading<Gear> = Reading.Loading,
    val trip: Trip? = null,
    val arrival: Arrival? = null,
    val announcement: Announcement? = null,
    val inputError: String? = null,
)

// ---- 동승자 ----

/**
 * 좌석 센서 값이 바뀜. 비어 있던 좌석에 사람이 앉으면 누구인지 짐작하거나 묻는다.
 * 이름을 아는 동승자가 내리면(좌석이 비면) 그 사람의 물건을 챙기라고 말한다.
 */
fun MemoryState.onSeat(seat: Seat, reading: Reading<Boolean>, now: LocalDateTime): MemoryState {
    val current = seats.getValue(seat)
    val next = when (reading) {
        Reading.Loading -> SeatState.Checking
        Reading.Unsupported -> SeatState.Unsupported
        Reading.Unavailable -> SeatState.Unknown
        is Reading.Value -> when {
            !reading.value -> SeatState.Empty
            current.occupiedSince != null -> current
            else -> boarding(seat, now)
        }
    }
    val leaving = (current as? SeatState.Known)?.name?.takeIf { next == SeatState.Empty }
    val seated = withSeat(seat, next)
    return if (leaving != null) seated.passengerLeft(leaving, now) else seated
}

/** 동승자가 내림: 그 사람의 물건이 있으면 체크 목록에 더하고 "민수님, 가방 챙기세요." */
private fun MemoryState.passengerLeft(name: String, now: LocalDateTime): MemoryState {
    val theirs = dueBelongings(data.belongings, now.toLocalDate()).filter { it.owner == name }
    if (theirs.isEmpty()) return this
    val items = theirs.map { it.toCheckItem() }
    val open = arrival
    val merged = if (open == null) {
        Arrival(MemoryMessages.passengerLeaving(name), items)
    } else {
        open.copy(items = open.items + items.filterNot { item -> open.items.any { it.key == item.key } })
    }
    return copy(arrival = merged).announce(TAG_LEAVE, MemoryMessages.passengerReminder(name, theirs.map { it.item }))
}

/** "오늘도 ○○님이죠?" 에 대한 답 */
fun MemoryState.confirmPassenger(seat: Seat, isRight: Boolean): MemoryState {
    val current = seats.getValue(seat) as? SeatState.Confirming ?: return this
    if (isRight) return remember(seat, current.name, current.since)
    val suggestions = nameSuggestions(data.rides, seat, exclude = namesOnOtherSeats(seat) + current.name)
    return withSeat(seat, SeatState.Asking(current.since, suggestions))
}

/** "조수석에 누가 타셨어요?" 에 대한 답. 이름이 비었거나 너무 길면 그대로 둔다. */
fun MemoryState.namePassenger(seat: Seat, rawName: String): MemoryState {
    val current = seats.getValue(seat) as? SeatState.Asking ?: return this
    val name = normalizeName(rawName) ?: return this
    return remember(seat, name, current.since)
}

fun MemoryState.skipPassenger(seat: Seat): MemoryState {
    val current = seats.getValue(seat) as? SeatState.Asking ?: return this
    return withSeat(seat, SeatState.Unnamed(current.since))
}

/** 건너뛴 좌석에 이름을 다시 묻는다 */
fun MemoryState.askAgain(seat: Seat): MemoryState {
    val current = seats.getValue(seat) as? SeatState.Unnamed ?: return this
    return withSeat(seat, SeatState.Asking(current.since, nameSuggestions(data.rides, seat, exclude = namesOnOtherSeats(seat))))
}

/** 앞뒤 공백과 끝의 "님"을 뗀다. 비었거나 [MemoryRules.MAX_NAME_LENGTH] 보다 길면 null */
fun normalizeName(raw: String): String? {
    val name = raw.trim().removeSuffix("님").trim()
    return name.takeIf { it.isNotEmpty() && it.length <= MemoryRules.MAX_NAME_LENGTH }
}

/**
 * 이 좌석에 같은 요일, 앉은 시각 ±[MemoryRules.MATCH_WINDOW_MINUTES]분 안에 탔던 사람.
 * 여럿이면 더 자주 탄 사람, 같으면 최근에 탄 사람. 없으면 null.
 */
fun guessPassenger(rides: List<Ride>, seat: Seat, at: LocalDateTime, exclude: Set<String> = emptySet()): String? =
    rides
        .filter {
            it.seat == seat && it.name !in exclude &&
                it.at.dayOfWeek == at.dayOfWeek && minutesApart(it.at, at) <= MemoryRules.MATCH_WINDOW_MINUTES
        }
        .groupBy { it.name }
        .maxWithOrNull(compareBy<Map.Entry<String, List<Ride>>>({ it.value.size }, { it.value.maxOf { ride -> ride.at } }))
        ?.key

/** 이름 묻기 화면에 보여 줄 전에 탔던 사람. 이 좌석에 자주 탄 사람 → 전체로 자주 탄 사람 → 최근 순 */
fun nameSuggestions(rides: List<Ride>, seat: Seat, exclude: Set<String> = emptySet()): List<String> =
    rides
        .filter { it.name !in exclude }
        .groupBy { it.name }
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String, List<Ride>>> { e -> e.value.count { it.seat == seat } }
                .thenByDescending { it.value.size }
                .thenByDescending { e -> e.value.maxOf { it.at } },
        )
        .map { it.key }
        .take(MemoryRules.MAX_SUGGESTIONS)

/** 하루 중 시각 차이(분). 자정을 넘는 쪽이 더 가까우면 그쪽으로 잰다. */
internal fun minutesApart(a: LocalDateTime, b: LocalDateTime): Int {
    val diff = abs(a.toLocalTime().toSecondOfDay() - b.toLocalTime().toSecondOfDay()) / 60
    return min(diff, 24 * 60 - diff)
}

private fun MemoryState.boarding(seat: Seat, at: LocalDateTime): SeatState {
    val taken = namesOnOtherSeats(seat)
    val guess = guessPassenger(data.rides, seat, at, exclude = taken)
    return if (guess != null) {
        SeatState.Confirming(at, guess)
    } else {
        SeatState.Asking(at, nameSuggestions(data.rides, seat, exclude = taken))
    }
}

/** 한 사람이 두 좌석에 동시에 앉을 수는 없으니, 다른 좌석에서 이미 정해진 이름은 짐작 · 추천에서 뺀다 */
private fun MemoryState.namesOnOtherSeats(seat: Seat): Set<String> =
    seats.filterKeys { it != seat }.values.mapNotNull {
        when (it) {
            is SeatState.Known -> it.name
            is SeatState.Confirming -> it.name
            else -> null
        }
    }.toSet()

private fun MemoryState.remember(seat: Seat, name: String, since: LocalDateTime): MemoryState = copy(
    data = data.copy(rides = (data.rides + Ride(name, seat, since)).takeLast(MemoryRules.MAX_RIDES)),
    seats = seats + (seat to SeatState.Known(since, name)),
)

private fun MemoryState.withSeat(seat: Seat, state: SeatState) = copy(seats = seats + (seat to state))

// ---- 소지품 ----

/**
 * 글이나 말로 한 부탁을 처리한다. 화면 입력칸과 음성 인식이 같은 길로 들어온다.
 * - "트렁크에 우산 넣었어", "나 오늘 보고서류 가져왔어 챙기라고 해줘" → 이번에만 챙길 물건
 * - "보고서류 챙겼어", "다 챙겼어" → 바로 지운다
 * - "회사 갈 때 텀블러 사원증 지갑 챙기라고 해줘" → 목적지마다 늘 챙길 것
 * @param owner 화면에서 고른 주인(동승자 이름). 말 맨 앞의 이름이 먼저다
 * @param spoken 말로 했으면 true: 결과를 비서가 말로 답한다. 글이면 알아듣지 못했을 때 [MemoryState.inputError] 를 채운다
 */
fun MemoryState.submit(text: String, now: LocalDateTime, owner: String? = null, spoken: Boolean = false): MemoryState {
    val names = (data.rides.map { it.name } + data.belongings.mapNotNull { it.owner }).distinct()
    val command = parseVoiceCommand(text, now.toLocalDate(), names)
        // 글로는 "트렁크 우산"처럼 부탁하는 말 없이도 적는다
        ?: if (!spoken) parseBelonging(text, now.toLocalDate())?.let { VoiceCommand.Remember(it, null) } else null
    val next = when (command) {
        null -> return if (spoken) announce(TAG_REPLY, MemoryMessages.VOICE_NOT_UNDERSTOOD) else copy(inputError = MemoryMessages.NOT_UNDERSTOOD)
        is VoiceCommand.Remember -> rememberBelonging(command.belonging, command.owner ?: owner, now)
        is VoiceCommand.Collected -> collectByName(command.item, command.owner)
        is VoiceCommand.AddRoutine -> addRoutine(command.place, command.items)
            .announce(TAG_REPLY, MemoryMessages.routineAdded(command.place, command.items))
    }
    return next.copy(inputError = null)
}

/** 예전 이름(글 입력). [submit] 과 같다 */
fun MemoryState.addBelonging(text: String, now: LocalDateTime, owner: String? = null): MemoryState = submit(text, now, owner)

private fun MemoryState.rememberBelonging(parsed: ParsedBelonging, owner: String?, now: LocalDateTime): MemoryState {
    val belonging = Belonging(data.nextBelongingId, parsed.item, parsed.place, parsed.due, now, owner)
    val added = copy(data = data.copy(belongings = data.belongings + belonging, nextBelongingId = data.nextBelongingId + 1))
    // 도착 체크 목록이 떠 있으면 거기에도 더한다
    val withArrival = added.arrival?.let { added.copy(arrival = it.copy(items = it.items + belonging.toCheckItem())) } ?: added
    val later = parsed.due?.isAfter(now.toLocalDate()) == true
    return withArrival.announce(TAG_REPLY, MemoryMessages.rememberedItem(belonging.item, owner, tripPlace(), later))
}

/** "보고서류 챙겼어" · "다 챙겼어"(item 이 null). 이번에만 챙길 물건은 바로 지운다 */
private fun MemoryState.collectByName(item: String?, owner: String?): MemoryState {
    if (item == null) {
        val open = arrival
        if (open != null) return closeArrival(collected = true).announce(TAG_REPLY, MemoryMessages.ALL_COLLECTED_SPOKEN)
        val gone = data.belongings.filter { owner == null || it.owner == owner }
        if (gone.isEmpty()) return announce(TAG_REPLY, MemoryMessages.NOTHING_TO_COLLECT)
        return copy(data = data.copy(belongings = data.belongings - gone.toSet())).announce(TAG_REPLY, MemoryMessages.ALL_COLLECTED_SPOKEN)
    }
    val key = item.replace(" ", "")
    val belonging = data.belongings
        .filter { owner == null || it.owner == owner }
        .firstOrNull { it.item.replace(" ", "").let { name -> name == key || name.contains(key) || key.contains(name) } }
    if (belonging != null) return collect(belonging.id).announce(TAG_REPLY, MemoryMessages.collectedItem(belonging.item))
    // 늘 챙길 것은 체크 목록에서만 챙김 표시
    val fixed = arrival?.items?.firstOrNull { it.belongingId == null && it.item.replace(" ", "") == key }
    if (fixed != null) return check(fixed.key).announce(TAG_REPLY, MemoryMessages.collectedItem(fixed.item))
    return announce(TAG_REPLY, MemoryMessages.notRemembered(item))
}

/** "챙겼어요"(목록의 체크 칸). 이번에만 챙길 물건은 바로 지우고, 떠 있는 체크 목록에는 챙김으로 남긴다 */
fun MemoryState.collect(id: Long): MemoryState {
    val removed = copy(data = data.copy(belongings = data.belongings.filterNot { it.id == id }))
    val open = arrival ?: return removed
    val key = "b:$id"
    return if (open.items.any { it.key == key }) removed.copy(arrival = open.copy(done = open.done + key)) else removed
}

/** 체크 목록 한 줄을 누름. 이번에만 챙길 물건은 지우고(되돌리지 않음), 늘 챙길 것은 챙김 표시를 켜고 끈다 */
fun MemoryState.check(key: String): MemoryState {
    val open = arrival ?: return this
    val item = open.items.firstOrNull { it.key == key } ?: return this
    if (item.belongingId != null) return if (key in open.done) this else collect(item.belongingId)
    return copy(arrival = open.copy(done = if (key in open.done) open.done - key else open.done + key))
}

/** 지금 챙겨야 할 물건: 챙길 날이 없거나, 오늘이거나, 이미 지난 것 */
fun dueBelongings(belongings: List<Belonging>, today: LocalDate): List<Belonging> =
    belongings.filter { it.due == null || !it.due.isAfter(today) }

/**
 * 변속 단이 바뀜.
 * - P 에서 출발하면: 내비 목적지가 있으면 늘 챙길 것을 확인한다
 * - 주행(D · R · N)하다가 P 에 놓으면: 내비로 가는 중이면 도착 직전 이후일 때만 도착으로 보고(가는 길에 잠깐 선 것은 아님),
 *   내비 없이 다니면 하차로 보고 챙길 물건을 알린다. 이때 체크 목록이 이미 열려 있으면(달리는 중에 온 내비 도착 알림 뒤
 *   내비가 목적지를 지운 경우, D 에서 동승자를 내려 준 경우) 새로 만들지 않고 목록에 없는 것만 더해 그것만 말한다
 * 앱을 켤 때 이미 P 였던 것은 하차로 치지 않는다. 잠깐 값을 못 읽어도 마지막 단을 기억한다.
 * 체크 목록에 답하지 않고 P 에서 출발하면 "나중에"로 닫는다(주행 중에는 체크 목록을 띄우지 않는다).
 * 챙긴 물건은 누를 때 이미 지웠고, 남은 물건은 다음 하차 때 다시 알린다.
 */
fun MemoryState.onGear(reading: Reading<Gear>, now: LocalDateTime): MemoryState {
    val before = (gear as? Reading.Value)?.value
    val after = (reading as? Reading.Value)?.value
    val kept = if (after == null && before != null) gear else reading
    var next = copy(gear = kept)
    // P 에서 출발: 체크 목록에 답하지 않았으면 "나중에"로 닫고(주행 중에는 띄우지 않는다), 늘 챙길 것을 확인한다
    if (before == Gear.PARK && after != null && after != Gear.PARK) next = next.closeArrival(collected = false).departed()
    val parked = before != null && before != Gear.PARK && after == Gear.PARK
    if (!parked) return next
    val t = next.trip
    if (t != null) {
        val nearby = TripMoment.APPROACHING in t.said || t.approaching
        return if (nearby && TripMoment.ARRIVED !in t.said) next.arrive(now) else next
    }
    // 내비 없이 다닐 때(또는 내비가 도착 뒤 목적지를 지웠을 때): 매일 챙길 것 + 오늘 챙길 물건.
    // 이미 열린 체크 목록(달리는 중에 온 내비 도착 알림, D 에서 내려 준 동승자)이 있으면 그 목록에 없는 것만 더하고 그것만 말한다.
    val open = next.arrival
    val fresh = next.checklist(now).filterNot { item -> open?.items?.any { it.key == item.key } == true }
    if (fresh.isEmpty()) return next
    val daily = fresh.filter { it.belongingId == null }.map { it.item }
        .takeIf { it.isNotEmpty() }?.let { "${it.joinToString(", ")} 챙기세요." }
    val due = dueBelongings(data.belongings, now.toLocalDate()).filter { b -> fresh.any { it.belongingId == b.id } }
        .takeIf { it.isNotEmpty() }?.let(::arrivalMessage)
    val arrival = open?.copy(items = open.items + fresh) ?: Arrival(MemoryMessages.ARRIVED_TITLE, fresh)
    return next.copy(arrival = arrival).announce(TAG_ARRIVE, listOfNotNull(daily, due).joinToString(" "))
}

/**
 * 주행 중인지. 팀 UI 규칙: 정차 = P 단(신호 대기도 주행으로 친다). 주행 중이면 입력 · 편집을 숨긴다.
 * 단을 읽지 못하는 차는 정차로 보고 화면을 모두 보여 준다(값을 잠깐 못 읽으면 onGear 가 마지막 단을 기억한다).
 */
val MemoryState.driving: Boolean
    get() = (gear as? Reading.Value)?.value.let { it != null && it != Gear.PARK }

/**
 * 체크 목록을 닫는다. collected("다 챙겼어요")면 목록의 이번에만 챙길 물건을 모두 지우고,
 * 아니면("나중에") 챙기지 않은 물건은 남긴다(챙긴 것은 이미 지웠다).
 */
fun MemoryState.closeArrival(collected: Boolean): MemoryState {
    val open = arrival ?: return this
    val gone = if (collected) open.items.mapNotNull { it.belongingId }.toSet() else emptySet()
    return copy(
        data = data.copy(belongings = data.belongings.filterNot { it.id in gone }),
        arrival = null,
        announcement = announcement?.copy(dismissed = true),
    )
}

// ---- 다 잊어줘 ----

/** 동승자 이름 · 탑승 기록 · 소지품 · 목적지별 챙길 것을 모두 지운다. 지금 앉아 있는 사람에게는 이름을 처음부터 다시 묻는다. */
fun MemoryState.forgetAll(): MemoryState = copy(
    data = MemoryData(),
    seats = seats.mapValues { (_, state) -> state.occupiedSince?.let { SeatState.Asking(it, emptyList()) } ?: state },
    arrival = null,
    inputError = null,
)
