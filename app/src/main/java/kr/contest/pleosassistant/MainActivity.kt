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
import kr.contest.pleosassistant.music.MediaAppPlayer
import kr.contest.pleosassistant.music.OpenMeteoWeatherSource
import kr.contest.pleosassistant.storage.FileBriefingStore
import kr.contest.pleosassistant.storage.FileMusicStore
import kr.contest.pleosassistant.ui.HaidaApp
import kr.contest.pleosassistant.ui.common.HaidaTheme
import kr.contest.pleosassistant.ui.briefing.BriefingViewModel
import kr.contest.pleosassistant.ui.music.MusicViewModel
import kr.contest.pleosassistant.ui.status.VehicleStatusViewModel
import kr.contest.pleosassistant.vehicle.CarExteriorSource
import kr.contest.pleosassistant.vehicle.PleosVehicleStatusSource
import kr.contest.pleosassistant.voice.PleosSpeaker
import java.io.File

class MainActivity : ComponentActivity() {

    private val statusViewModel: VehicleStatusViewModel by viewModels {
        viewModelFactory {
            initializer { VehicleStatusViewModel(PleosVehicleStatusSource((application as HaidaApplication).vehicle)) }
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

    /** 실행 중에 받아야 하는 권한: 대략 위치(기능 3 날씨) */
    private val runtimePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val missing = listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) runtimePermissions.launch(missing.toTypedArray())

        setContent {
            HaidaTheme {
                val report by statusViewModel.report.collectAsStateWithLifecycle()
                val music by musicViewModel.ui.collectAsStateWithLifecycle()
                val briefing by briefingViewModel.ui.collectAsStateWithLifecycle()
                HaidaApp(report, music, musicViewModel, briefing, briefingViewModel)
            }
        }
    }
}
