package kr.contest.pleosassistant.memory

import java.time.LocalDate
import java.time.LocalDateTime

/** 동승자를 기억하는 좌석. 운전석은 사용자 본인이라 뺀다. */
enum class Seat(val label: String) {
    PASSENGER("조수석"),
    REAR_LEFT("뒷좌석 왼쪽"),
    REAR_RIGHT("뒷좌석 오른쪽"),
}

/** 변속 단. 하차(P단) 판단에만 쓴다. */
enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE }

/** 동승자가 한 번 탄 기록. 같은 요일 · 시간대에 다시 타면 이름을 먼저 짐작하는 데 쓴다. */
data class Ride(val name: String, val seat: Seat, val at: LocalDateTime)

/**
 * 이번에만 챙길 물건(임시 소지품). 챙겼다고 하면 지운다.
 * @property place "트렁크" 같은 위치. 말하지 않았으면 null
 * @property due 챙길 날. 말하지 않았으면 null(다음 하차 때 바로 알린다)
 * @property owner 동승자의 물건이면 그 사람 이름. 운전자(나)의 물건이면 null
 */
data class Belonging(
    val id: Long,
    val item: String,
    val place: String?,
    val due: LocalDate?,
    val addedAt: LocalDateTime,
    val owner: String? = null,
)

/**
 * 목적지마다 늘 챙길 것. 예: 회사 → 텀블러 · 사원증 · 지갑. 챙겼다고 해도 지우지 않는다.
 * @property place 내비 즐겨찾기 이름("회사" · "집")이거나 목적지 이름에 들어 있는 말("헬스장")
 */
data class Routine(val place: String, val items: List<String>)

/** 차 안에만 저장하는 기억 전체. 서버로 보내지 않는다(기능계획서 4장 원칙). */
data class MemoryData(
    val rides: List<Ride> = emptyList(),
    val belongings: List<Belonging> = emptyList(),
    val nextBelongingId: Long = 1,
    val routines: List<Routine> = emptyList(),
)

/** 기억을 저장하는 곳. 앱에서는 기기 내부 파일, 테스트에서는 메모리. */
interface MemoryStore {
    fun load(): MemoryData
    fun save(data: MemoryData)
}

/** 좌석 하나의 지금 상태 */
sealed interface SeatState {
    /** 좌석 센서를 아직 읽는 중 */
    data object Checking : SeatState

    /** 이 차량이 이 좌석의 착석 감지를 지원하지 않는다 */
    data object Unsupported : SeatState

    /** 지원하지만 값을 읽지 못했다 */
    data object Unknown : SeatState

    data object Empty : SeatState

    /** 사람이 앉았고 누구인지 묻는 중. suggestions 는 전에 탔던 사람 이름 */
    data class Asking(val since: LocalDateTime, val suggestions: List<String>) : SeatState

    /** 같은 요일 · 시간대에 탔던 사람이라 "오늘도 ○○님이죠?" 하고 확인하는 중 */
    data class Confirming(val since: LocalDateTime, val name: String) : SeatState

    data class Known(val since: LocalDateTime, val name: String) : SeatState

    /** 이름 알려 주기를 건너뛰었다 */
    data class Unnamed(val since: LocalDateTime) : SeatState
}

/** 사람이 앉아 있으면 앉은 시각, 아니면 null */
val SeatState.occupiedSince: LocalDateTime?
    get() = when (this) {
        is SeatState.Asking -> since
        is SeatState.Confirming -> since
        is SeatState.Known -> since
        is SeatState.Unnamed -> since
        else -> null
    }
