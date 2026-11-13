package kr.contest.pleosassistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kr.contest.pleosassistant.briefing.DemoBriefings
import kr.contest.pleosassistant.storage.FileDriveStore
import kr.contest.pleosassistant.storage.FileHazardStore
import kr.contest.pleosassistant.music.MediaAppPlayer
import kr.contest.pleosassistant.music.OpenMeteoWeatherSource
import kr.contest.pleosassistant.storage.FileBriefingStore
import kr.contest.pleosassistant.storage.FileMemoryStore
import kr.contest.pleosassistant.storage.FileMusicStore
import kr.contest.pleosassistant.ui.HaidaApp
import kr.contest.pleosassistant.ui.briefing.BriefingViewModel
import kr.contest.pleosassistant.ui.common.HaidaTheme
import kr.contest.pleosassistant.ui.hazard.HazardViewModel
import kr.contest.pleosassistant.ui.memory.MemoryViewModel
import kr.contest.pleosassistant.ui.music.MusicViewModel
import kr.contest.pleosassistant.ui.report.DriveViewModel
import kr.contest.pleosassistant.ui.status.VehicleStatusViewModel
import kr.contest.pleosassistant.vehicle.AndroidLocationSource
import kr.contest.pleosassistant.vehicle.CarExteriorSource
import kr.contest.pleosassistant.vehicle.CarSpeedSource
import kr.contest.pleosassistant.vehicle.PleosCabinSource
import kr.contest.pleosassistant.vehicle.PleosVehicleStatusSource
import kr.contest.pleosassistant.voice.PleosSpeaker
import java.io.File

class MainActivity : ComponentActivity() {

    private val haida get() = application as HaidaApplication

    // 좌석 · 변속 단 신호는 기능 2(기억)와 기능 6(위험 구간)이 함께 쓴다. 차량 연결을 한 번만 연다
    private val cabin by lazy { PleosCabinSource(haida.vehicle, haida) }

    private val statusViewModel: VehicleStatusViewModel by viewModels {
        viewModelFactory {
            initializer { VehicleStatusViewModel(PleosVehicleStatusSource(haida.vehicle)) }
        }
    }

    private val memoryViewModel: MemoryViewModel by viewModels {
        viewModelFactory {
            initializer {
                MemoryViewModel(
                    cabin = cabin,
                    store = FileMemoryStore(File(haida.noBackupFilesDir, "memory.json")),
                )
            }
        }
    }

    private val hazardViewModel: HazardViewModel by viewModels {
        viewModelFactory {
            initializer {
                HazardViewModel(
                    location = AndroidLocationSource(haida),
                    cabin = cabin,
                    store = FileHazardStore(File(haida.noBackupFilesDir, "hazard.json")),
                )
            }
        }
    }

    private val driveViewModel: DriveViewModel by viewModels {
        viewModelFactory {
            initializer {
                DriveViewModel(
                    speed = CarSpeedSource(haida),
                    cabin = cabin,
                    store = FileDriveStore(File(haida.noBackupFilesDir, "drive.json")),
                )
            }
        }
    }

    private val musicViewModel: MusicViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as HaidaApplication
                MusicViewModel(
                    exterior = CarExteriorSource(app),
                    weatherSource = OpenMeteoWeatherSource(app),
                    player = MediaAppPlayer(app),
                    store = FileMusicStore(File(app.noBackupFilesDir, "music.json")),
                    speaker = PleosSpeaker(app),
                )
            }
        }
    }

    private val briefingViewModel: BriefingViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as HaidaApplication
                BriefingViewModel(
                    source = DemoBriefings,
                    store = FileBriefingStore(File(app.noBackupFilesDir, "briefing.json")),
                    speaker = PleosSpeaker(app),
                )
            }
        }
    }

    /** 실행 중에 받아야 하는 권한: 위치(기능 6 위험 구간 · 기능 3 날씨), 차속(기능 5) */
    private val runtimePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hazardViewModel.onLocationPermissionResult()
        driveViewModel.onSpeedPermissionResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val missing = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, CarSpeedSource.PERMISSION)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) runtimePermissions.launch(missing.toTypedArray())

        setContent {
            HaidaTheme {
                val report by statusViewModel.report.collectAsStateWithLifecycle()
                val memory by memoryViewModel.report.collectAsStateWithLifecycle()
                val driving by memoryViewModel.driving.collectAsStateWithLifecycle()
                val music by musicViewModel.ui.collectAsStateWithLifecycle()
                val hazard by hazardViewModel.report.collectAsStateWithLifecycle()
                val drive by driveViewModel.report.collectAsStateWithLifecycle()
                val briefing by briefingViewModel.ui.collectAsStateWithLifecycle()
                HaidaApp(report, memory, driving, memoryViewModel, music, musicViewModel, hazard, hazardViewModel, drive, driveViewModel, briefing, briefingViewModel)
            }
        }
    }
}
