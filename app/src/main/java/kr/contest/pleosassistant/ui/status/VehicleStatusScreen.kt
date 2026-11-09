package kr.contest.pleosassistant.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kr.contest.pleosassistant.status.StatusItem
import kr.contest.pleosassistant.status.StatusLevel
import kr.contest.pleosassistant.status.StatusReport

// 임시 화면. 팀 UI 템플릿을 받으면 이 파일만 바꾼다(StatusReport 는 그대로).

@Composable
fun VehicleStatusScreen(report: StatusReport, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(text = report.summary, style = MaterialTheme.typography.headlineMedium)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 260.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(report.items, key = { it.id }) { StatusCard(it) }
        }
    }
}

@Composable
private fun StatusCard(item: StatusItem) {
    Card {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            Text(text = item.level.label, color = item.level.color(), style = MaterialTheme.typography.labelLarge)
            Text(text = item.detail, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

private fun StatusLevel.color(): Color = when (this) {
    StatusLevel.URGENT -> Color(0xFFFF5A52)
    StatusLevel.CAUTION -> Color(0xFFFFB020)
    StatusLevel.OK -> Color(0xFF3DD68C)
    else -> Color(0xFF9AA4B2)
}
