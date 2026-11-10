package kr.contest.pleosassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kr.contest.pleosassistant.ui.HaidaApp
import kr.contest.pleosassistant.ui.common.HaidaTheme
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
            HaidaTheme {
                val report by statusViewModel.report.collectAsStateWithLifecycle()
                HaidaApp(report)
            }
        }
    }
}
