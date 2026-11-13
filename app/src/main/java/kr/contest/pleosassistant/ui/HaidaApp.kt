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
import kr.contest.pleosassistant.drive.DriveReport
import kr.contest.pleosassistant.hazard.HazardReport
import kr.contest.pleosassistant.memory.MemoryReport
import kr.contest.pleosassistant.status.StatusReport
import kr.contest.pleosassistant.ui.common.AssistantPanel
import kr.contest.pleosassistant.ui.common.AssistantPanelHeight
import kr.contest.pleosassistant.ui.common.ButtonKind
import kr.contest.pleosassistant.ui.common.Haida
import kr.contest.pleosassistant.ui.common.HaidaButton
import kr.contest.pleosassistant.ui.common.HaidaIcons
import kr.contest.pleosassistant.ui.common.HaidaType
import kr.contest.pleosassistant.ui.briefing.BriefingActions
import kr.contest.pleosassistant.ui.briefing.BriefingScreen
import kr.contest.pleosassistant.ui.briefing.BriefingUi
import kr.contest.pleosassistant.ui.hazard.HazardActions
import kr.contest.pleosassistant.ui.hazard.HazardScreen
import kr.contest.pleosassistant.ui.memory.MemoryActions
import kr.contest.pleosassistant.ui.memory.MemoryScreen
import kr.contest.pleosassistant.ui.music.MusicActions
import kr.contest.pleosassistant.ui.music.MusicScreen
import kr.contest.pleosassistant.ui.music.MusicUi
import kr.contest.pleosassistant.ui.report.DriveActions
import kr.contest.pleosassistant.ui.report.ReportScreen
import kr.contest.pleosassistant.ui.status.VehicleStatusScreen

// 앱 틀: 팀 UI 프로토타입의 왼쪽 메뉴(.dock, 너비 128, 브랜드 연한 색)와 화면 영역(.main).
// 다른 기능 화면은 Destination 에 한 줄 추가하고 when 에 화면을 넣는다.

private enum class Destination(val label: String, val icon: ImageVector) {
    CAR("차량", HaidaIcons.Car),
    PEOPLE("동승", HaidaIcons.People),
    MUSIC("음악", HaidaIcons.Music),
    HAZARD("구간", HaidaIcons.Pin),
    REPORT("리포트", HaidaIcons.Gauge),
    NEWS("브리핑", HaidaIcons.News),
}

/**
 * @param driving 주행 중(P 가 아님)이면 화면마다 입력 · 편집을 숨긴다(팀 UI 규칙: 정차 = P)
 */
@Composable
fun HaidaApp(
    status: StatusReport,
    memory: MemoryReport,
    driving: Boolean,
    memoryActions: MemoryActions,
    music: MusicUi,
    musicActions: MusicActions,
    hazard: HazardReport,
    hazardActions: HazardActions,
    drive: DriveReport,
    driveActions: DriveActions,
    briefing: BriefingUi,
    briefingActions: BriefingActions,
) {
    var current by rememberSaveable { mutableStateOf(Destination.CAR) }
    // 하차 알림: 동승 화면에서는 체크 목록으로, 다른 화면에서는 아래 비서 패널로 띄운다
    val arrival = memory.arrivalMessage?.takeIf { current != Destination.PEOPLE }
    // 아래 비서 패널에는 한 번에 하나만: 위험 구간 경고 > 챙길 것 > 주행 점수(기능계획서 9장 '말하는 순서', 물건을 먼저 챙기게)
    val hazardAlert = hazard.alert
    val finished = drive.finished?.takeIf { current != Destination.REPORT }
    val panelShown = hazardAlert != null || arrival != null || finished != null

    Row(modifier = Modifier.fillMaxSize().background(Haida.colors.l0)) {
        Dock(
            current = current,
            // 다른 화면에 있을 때 차에 주의할 것이 있거나, 동승 화면이 답을 기다리면(누가 탔는지 · 하차 확인), 브리핑을 듣는 중이면 점으로 알린다
            badge = {
                when (it) {
                    Destination.CAR -> status.overall.severity > 0
                    Destination.PEOPLE -> memory.question != null || memory.arrivalItems.isNotEmpty()
                    Destination.MUSIC -> false
                    Destination.HAZARD -> hazard.zones.any { it.inside && it.enabled }
                    Destination.REPORT -> drive.finished != null
                    Destination.NEWS -> briefing.state.playing
                }
            },
            onSelect = { current = it },
        )
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // 비서 패널은 본문을 덮지 않는다: 패널이 뜨면 본문 아래에 패널 자리(높이 + 위아래 간격)를 비운다
            val bottom = if (panelShown) AssistantPanelHeight + 18.dp + 16.dp - 24.dp else 0.dp
            Box(Modifier.fillMaxSize().padding(bottom = bottom)) {
                when (current) {
                    Destination.CAR -> VehicleStatusScreen(status, driving)
                    Destination.PEOPLE -> MemoryScreen(memory, driving, memoryActions)
                    // 주행 중 화면(버튼 3개 · 다음 곡 하나)은 변속 단(기능 2 CabinSource)으로 켠다
                    Destination.MUSIC -> MusicScreen(music, driving, actions = musicActions)
                    Destination.HAZARD -> HazardScreen(hazard, hazardActions)
                    Destination.REPORT -> ReportScreen(drive, driveActions)
                    // 주행 중에는 자막을 숨기고 다음 분야만(기능 4)
                    Destination.NEWS -> BriefingScreen(briefing, driving, actions = briefingActions)
                }
            }
            if (hazardAlert != null) {
                AssistantPanel(
                    tag = if (hazardAlert.repeat) "다시 지나요" else "위험 구간",
                    text = hazardAlert.message,
                    tagIcon = HaidaIcons.Warn,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(start = 40.dp, end = 40.dp, bottom = 18.dp),
                ) {
                    HaidaButton("알겠어요", onClick = hazardActions::dismissAlert, kind = ButtonKind.PRIMARY, height = 76.dp)
                }
            } else if (arrival != null) {
                AssistantPanel(
                    tag = "도착",
                    text = arrival,
                    tagIcon = HaidaIcons.Bag,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(start = 40.dp, end = 40.dp, bottom = 18.dp),
                ) {
                    HaidaButton("다 챙겼어요", onClick = { memoryActions.closeArrival(collected = true) }, kind = ButtonKind.PRIMARY, height = 76.dp)
                    HaidaButton("나중에", onClick = { memoryActions.closeArrival(collected = false) }, height = 76.dp)
                }
            } else if (finished != null) {
                AssistantPanel(
                    tag = "${finished.score}점",
                    text = drive.finishedMessage.orEmpty(),
                    tagIcon = HaidaIcons.Gauge,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(start = 40.dp, end = 40.dp, bottom = 18.dp),
                ) {
                    HaidaButton("자세히", onClick = { current = Destination.REPORT }, height = 76.dp)
                    HaidaButton("확인", onClick = driveActions::dismissFinished, kind = ButtonKind.PRIMARY, height = 76.dp)
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
