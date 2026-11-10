package kr.contest.pleosassistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.contest.pleosassistant.hazard.HazardReport
import kr.contest.pleosassistant.memory.MemoryReport
import kr.contest.pleosassistant.status.StatusReport
import kr.contest.pleosassistant.ui.common.Palette
import kr.contest.pleosassistant.ui.hazard.HazardActions
import kr.contest.pleosassistant.ui.hazard.HazardAlertDialog
import kr.contest.pleosassistant.ui.hazard.HazardScreen
import kr.contest.pleosassistant.ui.memory.ArrivalDialog
import kr.contest.pleosassistant.ui.memory.MemoryActions
import kr.contest.pleosassistant.ui.memory.MemoryScreen
import kr.contest.pleosassistant.ui.status.VehicleStatusScreen

// 임시 디자인. 팀 UI 템플릿을 받으면 화면 이동 방식과 함께 바꾼다.

private enum class Destination(val label: String, val icon: ImageVector) {
    STATUS("차량 요약", Icons.Filled.DirectionsCar),
    MEMORY("기억", Icons.Filled.People),
    HAZARD("위험 구간", Icons.Filled.Place),
}

/** 왼쪽 메뉴로 기능 화면을 오간다. 하차 알림은 어느 화면에 있든 위에 뜬다. */
@Composable
fun HaidaApp(
    status: StatusReport,
    memory: MemoryReport,
    memoryActions: MemoryActions,
    hazard: HazardReport,
    hazardActions: HazardActions,
) {
    var current by rememberSaveable { mutableStateOf(Destination.STATUS) }

    Row(modifier = Modifier.fillMaxSize().background(Palette.background)) {
        Column(
            modifier = Modifier
                .width(132.dp)
                .fillMaxHeight()
                .background(Palette.card)
                .padding(vertical = 40.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Destination.entries.forEach { dest ->
                RailItem(
                    dest = dest,
                    selected = dest == current,
                    // 기억 화면이 답을 기다리면(누가 탔는지 등) 점으로 알린다
                    badge = dest == Destination.MEMORY && memory.question != null && current != Destination.MEMORY,
                    onClick = { current = dest },
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (current) {
                Destination.STATUS -> VehicleStatusScreen(status)
                Destination.MEMORY -> MemoryScreen(memory, memoryActions)
                Destination.HAZARD -> HazardScreen(hazard, hazardActions)
            }
        }
    }

    memory.arrivalMessage?.let { ArrivalDialog(it, memoryActions) }
    // 위험 구간 경고가 하차 알림보다 먼저다(기능계획서 9장 '말하는 순서')
    hazard.alert?.let { HazardAlertDialog(it, hazardActions) }
}

@Composable
private fun RailItem(dest: Destination, selected: Boolean, badge: Boolean, onClick: () -> Unit) {
    val color = if (selected) Palette.accent else Palette.textMuted
    Column(
        modifier = Modifier
            .width(108.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) Palette.accent.copy(alpha = 0.14f) else Palette.card)
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            Icon(dest.icon, contentDescription = null, tint = color, modifier = Modifier.size(44.dp))
            if (badge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-4).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Palette.caution),
                )
            }
        }
        Text(dest.label, color = color, fontSize = 20.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}
