package kr.contest.pleosassistant

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kr.contest.pleosassistant.storage.FileHazardStore
import kr.contest.pleosassistant.storage.FileMemoryStore
import kr.contest.pleosassistant.ui.HaidaApp
import kr.contest.pleosassistant.ui.hazard.HazardViewModel
import kr.contest.pleosassistant.ui.memory.MemoryViewModel
import kr.contest.pleosassistant.ui.status.VehicleStatusViewModel
import kr.contest.pleosassistant.vehicle.AndroidLocationSource
import kr.contest.pleosassistant.vehicle.PleosCabinSource
import kr.contest.pleosassistant.vehicle.PleosVehicleStatusSource
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

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        hazardViewModel.onLocationPermissionResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface {
                    val report by statusViewModel.report.collectAsStateWithLifecycle()
                    val memory by memoryViewModel.report.collectAsStateWithLifecycle()
                    val hazard by hazardViewModel.report.collectAsStateWithLifecycle()
                    HaidaApp(report, memory, memoryViewModel, hazard, hazardViewModel)
                }
            }
        }
    }
}
