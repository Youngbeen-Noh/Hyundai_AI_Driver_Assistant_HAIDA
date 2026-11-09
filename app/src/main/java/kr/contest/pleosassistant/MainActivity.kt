package kr.contest.pleosassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kr.contest.pleosassistant.ui.status.VehicleStatusScreen
import kr.contest.pleosassistant.ui.status.VehicleStatusViewModel
import kr.contest.pleosassistant.vehicle.PleosVehicleStatusSource

class MainActivity : ComponentActivity() {

    private val statusViewModel: VehicleStatusViewModel by viewModels {
        viewModelFactory {
            initializer { VehicleStatusViewModel(PleosVehicleStatusSource((application as HaidaApplication).vehicle)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface {
                    val report by statusViewModel.report.collectAsStateWithLifecycle()
                    VehicleStatusScreen(report)
                }
            }
        }
    }
}
