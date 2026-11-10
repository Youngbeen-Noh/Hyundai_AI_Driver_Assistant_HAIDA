package kr.contest.pleosassistant.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kr.contest.pleosassistant.memory.MemoryReport
import kr.contest.pleosassistant.status.StatusReport
import kr.contest.pleosassistant.ui.common.AssistantPanel
import kr.contest.pleosassistant.ui.common.ButtonKind
import kr.contest.pleosassistant.ui.common.HaidaButton
import kr.contest.pleosassistant.ui.memory.MemoryActions
import kr.contest.pleosassistant.ui.memory.MemoryScreen
import kr.contest.pleosassistant.ui.common.Haida
import kr.contest.pleosassistant.ui.common.HaidaIcons
import kr.contest.pleosassistant.ui.common.HaidaType
import kr.contest.pleosassistant.ui.status.VehicleStatusScreen

// 앱 틀: 팀 UI 프로토타입의 왼쪽 메뉴(.dock, 너비 128)와 화면 영역(.main).
// 다른 기능 화면은 Destination 에 한 줄 추가하고 when 에 화면을 넣는다.

private enum class Destination(val label: String, val icon: ImageVector) {
    CAR("차량", HaidaIcons.Car),
    PEOPLE("동승", HaidaIcons.People),
}

/**
 * @param driving 주행 중(P 가 아님)이면 화면마다 입력 · 편집을 숨긴다(팀 UI 규칙: 정차 = P)
 */
@Composable
fun HaidaApp(status: StatusReport, memory: MemoryReport, driving: Boolean, memoryActions: MemoryActions) {
    var current by rememberSaveable { mutableStateOf(Destination.CAR) }
    val c = Haida.colors

    Row(modifier = Modifier.fillMaxSize().background(Haida.colors.l0)) {
        Dock(
            current = current,
            // 다른 화면에 있을 때 차에 주의할 것이 있거나, 동승 화면이 답을 기다리면(누가 탔는지 · 하차 확인) 점으로 알린다
            badge = {
                when (it) {
                    Destination.CAR -> status.overall.severity > 0
                    Destination.PEOPLE -> memory.question != null || memory.arrivalItems.isNotEmpty()
                }
            },
            onSelect = { current = it },
        )
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (current) {
                Destination.CAR -> VehicleStatusScreen(status, driving)
                Destination.PEOPLE -> MemoryScreen(memory, driving, memoryActions)
            }
            // 하차 알림: 동승 화면에서는 체크 목록으로, 다른 화면에서는 아래 비서 패널로 띄운다
            val arrival = memory.arrivalMessage
            if (arrival != null && current != Destination.PEOPLE) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Brush.verticalGradient(listOf(c.l0.copy(alpha = 0f), c.l0.copy(alpha = 0.55f)))),
                )
                AssistantPanel(
                    tag = "도착",
                    text = arrival,
                    tagIcon = HaidaIcons.Bag,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(start = 40.dp, end = 40.dp, bottom = 18.dp),
                ) {
                    HaidaButton("다 챙겼어요", onClick = { memoryActions.closeArrival(collected = true) }, kind = ButtonKind.PRIMARY)
                    HaidaButton("나중에", onClick = { memoryActions.closeArrival(collected = false) })
                }
            }
        }
    }
}

@Composable
private fun Dock(current: Destination, badge: (Destination) -> Boolean, onSelect: (Destination) -> Unit) {
    val c = Haida.colors
    val logo = remember(c) { HaidaIcons.logo(c.ac, c.onAc) }
    Column(
        modifier = Modifier
            .width(128.dp)
            .fillMaxHeight()
            .background(c.l1)
            .drawBehind { drawLine(c.line, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1.dp.toPx()) }
            .padding(top = 14.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(rememberVectorPainter(logo), contentDescription = "HAIDA", modifier = Modifier.padding(bottom = 10.dp).size(56.dp))
        Destination.entries.forEach { dest ->
            DockItem(dest, selected = dest == current, badge = dest != current && badge(dest), onClick = { onSelect(dest) })
        }
    }
}

@Composable
private fun DockItem(dest: Destination, selected: Boolean, badge: Boolean, onClick: () -> Unit) {
    val c = Haida.colors
    val color = if (selected) c.ac else c.t3
    Column(
        modifier = Modifier
            .width(100.dp)
            .height(92.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) c.acSoft else c.l1)
            .clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Box {
            Icon(dest.icon, contentDescription = null, tint = color, modifier = Modifier.size(38.dp))
            if (badge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-4).dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(c.l1)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(c.warnFill),
                )
            }
        }
        Text(dest.label, style = HaidaType.eyebrow, color = color)
    }
}
