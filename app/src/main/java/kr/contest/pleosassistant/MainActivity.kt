package kr.contest.pleosassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kr.contest.pleosassistant.navi.PleosNaviSource
import kr.contest.pleosassistant.storage.FileMemoryStore
import kr.contest.pleosassistant.ui.HaidaApp
import kr.contest.pleosassistant.ui.common.HaidaTheme
import kr.contest.pleosassistant.ui.memory.MemoryViewModel
import kr.contest.pleosassistant.ui.status.VehicleStatusViewModel
import kr.contest.pleosassistant.vehicle.PleosCabinSource
import kr.contest.pleosassistant.vehicle.PleosVehicleStatusSource
import kr.contest.pleosassistant.voice.PleosSpeaker
import kr.contest.pleosassistant.voice.PleosVoiceInput
import java.io.File

class MainActivity : ComponentActivity() {

    private val haida get() = application as HaidaApplication

    private val statusViewModel: VehicleStatusViewModel by viewModels {
        viewModelFactory {
            initializer { VehicleStatusViewModel(PleosVehicleStatusSource(haida.vehicle)) }
        }
    }

    private val memoryViewModel: MemoryViewModel by viewModels {
        viewModelFactory {
            initializer {
                MemoryViewModel(
                    cabin = PleosCabinSource(haida.vehicle, haida),
                    store = FileMemoryStore(File(haida.noBackupFilesDir, "memory.json")),
                    navi = PleosNaviSource(haida),
                    speaker = PleosSpeaker(haida),
                    voice = PleosVoiceInput(haida),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HaidaTheme {
                val report by statusViewModel.report.collectAsStateWithLifecycle()
                val memory by memoryViewModel.report.collectAsStateWithLifecycle()
                val driving by memoryViewModel.driving.collectAsStateWithLifecycle()
                val listening by memoryViewModel.listening.collectAsStateWithLifecycle()
                HaidaApp(report, memory, driving, listening, memoryViewModel)
            }
        }
    }
}
