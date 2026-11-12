package kr.contest.pleosassistant.drive

import java.time.LocalDateTime

/**
 * 감점 이벤트. 감지 기준은 기능계획서 7장(교통안전공단 사업용 차량 기준 중 택시):
 * 급가속 초당 8km/h 이상, 급감속 초당 14km/h 이상, 급조향 진행 방향이 초당 10° 이상 바뀜.
 * @property weight 10km 당 1회에 깎는 점수. 시범 주행 데이터로 다시 조정한다(기획서 7장)
 */
enum class DriveEventType(val label: String, val weight: Double) {
    HARD_ACCEL("급가속", 3.0),
    HARD_BRAKE("급감속", 4.0),
    HARD_STEER("급조향", 4.0),
}

/** 감점 장면 하나. 화면과 리포트 멘트에 쓴다. 급조향은 그때 속도를 from · to 에 같이 넣는다 */
data class DriveEvent(val type: DriveEventType, val at: LocalDateTime, val fromKmh: Int, val toKmh: Int)

/**
 * 끝난 주행 한 번의 결과.
 * 주행 점수 = 0.7 × 안전 점수 + 0.3 × 연비 점수 (기획서 7장)
 */
data class TripSummary(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val distanceKm: Double,
    val movingSec: Int,
    val events: List<DriveEvent>,
    /** 움직인 시간 중 속도가 크게 변하지 않은 비율(0~1). 연비 점수에 쓴다 */
    val steadyRatio: Double,
    /** 움직이는 동안 운전석 안전벨트 미착용 경고가 켜져 있던 시간 */
    val unbeltedSec: Int = 0,
    val safety: Int,
    val eco: Int,
    val score: Int,
)

/** 차 안에만 저장하는 주행 기록. 서버로 보내지 않는다(랭킹은 동의한 사람만, 추후) */
data class DriveData(val trips: List<TripSummary> = emptyList())

/** 주행 기록을 저장하는 곳. 앱에서는 기기 내부 파일, 테스트에서는 메모리 */
interface DriveStore {
    fun load(): DriveData
    fun save(data: DriveData)
}
