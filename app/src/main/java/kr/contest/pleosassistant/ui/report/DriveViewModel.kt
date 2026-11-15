package kr.contest.pleosassistant.ui.report

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.contest.pleosassistant.BuildConfig
import kr.contest.pleosassistant.drive.DriveReport
import kr.contest.pleosassistant.drive.DriveState
import kr.contest.pleosassistant.drive.DriveStore
import kr.contest.pleosassistant.drive.DriveTestSignals
import kr.contest.pleosassistant.drive.Trip
import kr.contest.pleosassistant.drive.buildDriveReport
import kr.contest.pleosassistant.drive.dismissFinished
import kr.contest.pleosassistant.drive.onBeltWarning
import kr.contest.pleosassistant.drive.onGear
import kr.contest.pleosassistant.drive.onSteering
import kr.contest.pleosassistant.status.Reading
import kr.contest.pleosassistant.vehicle.DriverSignalSource
import kr.contest.pleosassistant.drive.onSpeed
import kr.contest.pleosassistant.vehicle.CabinSource
import kr.contest.pleosassistant.vehicle.SpeedSample
import kr.contest.pleosassistant.vehicle.SpeedSource
import java.time.LocalDateTime

/** 화면에서 누르는 것들 */
interface DriveActions {
    fun dismissFinished()
}

/**
 * 기능 5 · 주행 리포트. 하차 리포트가 어느 탭에서든 뜨도록 Activity 범위로 둔다.
 * 변속 단 · 차속 · 운전대 각도 · 안전벨트 신호를 [DriveState] 로 흘려 보내고, 주행 기록(data)이 바뀔 때마다 기기 안에 저장한다.
 */
class DriveViewModel(
    private val speed: SpeedSource,
    private val cabin: CabinSource,
    private val driver: DriverSignalSource,
    private val store: DriveStore,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel(), DriveActions {

    private val state = MutableStateFlow(DriveState(data = store.load()))

    /** 주행 중 '○분' 표시가 흐르도록 30초마다 다시 만든다 */
    private val tick = flow {
        while (true) {
            emit(Unit)
            delay(30_000)
        }
    }

    val report: StateFlow<DriveReport> = combine(state, tick, speed.status) { s, _, sp -> buildDriveReport(s, now(), sp) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, buildDriveReport(state.value, now(), speed.status.value))

    init {
        viewModelScope.launch {
            cabin.gear.collect { reading -> state.update { it.onGear(reading, now()) } }
        }
        viewModelScope.launch {
            speed.samples.collect { s ->
                val before = state.value.trip
                state.update { it.onSpeed(s.kmh, s.t, now()) }
                if (BuildConfig.DEBUG) logSample(s, before, state.value)
            }
        }
        viewModelScope.launch {
            driver.steering.collect { s -> state.update { it.onSteering(s.deg, now()) } }
        }
        viewModelScope.launch {
            combine(driver.beltWarning, DriveTestSignals.beltWarning) { r, test -> test ?: ((r as? Reading.Value)?.value == true) }
                .collect { on -> state.update { it.onBeltWarning(on) } }
        }
        viewModelScope.launch(Dispatchers.IO) {
            state.map { it.data }.distinctUntilChanged().drop(1).collect(store::save)
        }
        speed.start()
        cabin.start()
        driver.start()
    }

    /** 차속 권한을 받은 뒤 다시 시작한다 */
    fun onSpeedPermissionResult() = speed.start()

    override fun dismissFinished() = state.update { it.dismissFinished() }

    /**
     * 주행 중 차속 샘플마다 움직인 시간 · 미착용 시간이 얼마나 늘었는지 남긴다(디버그 빌드만, `adb logcat -s DriveSample`).
     * 정차 중인데 움직인 시간이 늘던 현상(#10, 한 번 나오고 재현 안 됨)을 다시 만나면 원인을 보려고 둔다.
     * 지연 = 이 샘플을 처리한 시각 − 샘플 시각. 길면 안전벨트 경고가 다른 시점의 샘플에 붙을 수 있다.
     */
    private fun logSample(s: SpeedSample, before: Trip?, after: DriveState) {
        val trip = after.trip ?: return
        val dt = before?.last?.let { s.t - it.t }
        val lag = SystemClock.elapsedRealtime() - s.t
        val moved = trip.movingMs - (before?.movingMs ?: 0)
        val unbelted = trip.unbeltedMs - (before?.unbeltedMs ?: 0)
        Log.d(
            SAMPLE_TAG,
            "%.1fkm/h t=%d 지연=%dms dt=%s 움직임+%d 미착용+%d 벨트경고=%s 합계 움직임=%d 미착용=%d"
                .format(s.kmh, s.t, lag, dt, moved, unbelted, after.beltWarning, trip.movingMs, trip.unbeltedMs),
        )
    }

    override fun onCleared() {
        speed.stop()
        driver.stop()
    }

    private companion object {
        const val SAMPLE_TAG = "DriveSample"
    }
}
