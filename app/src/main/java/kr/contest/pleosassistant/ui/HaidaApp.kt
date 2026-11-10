package kr.contest.pleosassistant.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import kr.contest.pleosassistant.status.StatusReport
import kr.contest.pleosassistant.ui.common.Haida
import kr.contest.pleosassistant.ui.common.HaidaIcons
import kr.contest.pleosassistant.ui.common.HaidaType
import kr.contest.pleosassistant.ui.music.MusicActions
import kr.contest.pleosassistant.ui.music.MusicScreen
import kr.contest.pleosassistant.ui.music.MusicUi
import kr.contest.pleosassistant.ui.status.VehicleStatusScreen

// 앱 틀: 팀 UI 프로토타입의 왼쪽 메뉴(.dock, 너비 128, 브랜드 연한 색)와 화면 영역(.main).
// 다른 기능 화면은 Destination 에 한 줄 추가하고 when 에 화면을 넣는다.

private enum class Destination(val label: String, val icon: ImageVector) {
    CAR("차량", HaidaIcons.Car),
    MUSIC("음악", HaidaIcons.Music),
}

@Composable
fun HaidaApp(status: StatusReport, music: MusicUi, musicActions: MusicActions) {
    var current by rememberSaveable { mutableStateOf(Destination.CAR) }

    Row(modifier = Modifier.fillMaxSize().background(Haida.colors.l0)) {
        Dock(
            current = current,
            // 다른 화면에 있을 때 차에 주의할 것이 있으면 점으로 알린다
            badge = { it == Destination.CAR && status.overall.severity > 0 },
            onSelect = { current = it },
        )
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (current) {
                // 변속 단(주행 중 화면)은 기능 2 PR 에서 CabinSource 로 연결한다
                Destination.CAR -> VehicleStatusScreen(status, driving = false)
                // 주행 중 화면(버튼 3개 · 다음 곡 하나)도 변속 단이 연결되면 driving 으로 켠다
                Destination.MUSIC -> MusicScreen(music, driving = false, actions = musicActions)
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
            .background(c.acSoft)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(rememberVectorPainter(logo), contentDescription = "HAIDA", modifier = Modifier.size(56.dp))
        // 메뉴는 세로 가운데에 모은다
        Spacer(Modifier.weight(1f))
        Destination.entries.forEach { dest ->
            DockItem(dest, selected = dest == current, badge = dest != current && badge(dest), onClick = { onSelect(dest) })
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun DockItem(dest: Destination, selected: Boolean, badge: Boolean, onClick: () -> Unit) {
    val c = Haida.colors
    val color = if (selected) c.ac else c.t2
    Column(
        modifier = Modifier
            .width(104.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.l1 else c.acSoft)
            .clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
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
                        .background(c.acSoft)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(c.warnFill),
                )
            }
        }
        Text(dest.label, style = if (selected) HaidaType.smallSemi else HaidaType.eyebrow, color = color)
    }
}
