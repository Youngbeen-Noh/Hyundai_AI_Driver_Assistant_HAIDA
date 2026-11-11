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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import kr.contest.pleosassistant.memory.MemoryMessages
import kr.contest.pleosassistant.memory.MemoryReport
import kr.contest.pleosassistant.status.StatusReport
import kr.contest.pleosassistant.ui.common.AssistantPanel
import kr.contest.pleosassistant.ui.common.AssistantPanelHeight
import kr.contest.pleosassistant.ui.common.ButtonKind
import kr.contest.pleosassistant.ui.common.Haida
import kr.contest.pleosassistant.ui.common.HaidaButton
import kr.contest.pleosassistant.ui.common.HaidaIcons
import kr.contest.pleosassistant.ui.common.HaidaType
import kr.contest.pleosassistant.ui.memory.MemoryActions
import kr.contest.pleosassistant.ui.memory.MemoryScreen
import kr.contest.pleosassistant.ui.music.MusicActions
import kr.contest.pleosassistant.ui.music.MusicScreen
import kr.contest.pleosassistant.ui.music.MusicUi
import kr.contest.pleosassistant.ui.status.VehicleStatusScreen
import kr.contest.pleosassistant.voice.ListenState

// 앱 틀: 팀 UI 프로토타입의 왼쪽 메뉴(.dock, 너비 128, 브랜드 연한 색)와 화면 영역(.main).
// 다른 기능 화면은 Destination 에 한 줄 추가하고 when 에 화면을 넣는다.

private enum class Destination(val label: String, val icon: ImageVector) {
    CAR("차량", HaidaIcons.Car),
    PEOPLE("동승", HaidaIcons.People),
    MUSIC("음악", HaidaIcons.Music),
}


/**
 * @param driving 주행 중(P 가 아님)이면 화면마다 입력 · 편집을 숨긴다(팀 UI 규칙: 정차 = P)
 * @param listening 말로 하기(음성 인식) 상태. 듣는 중이면 아래 패널에 알아들은 말을 보인다
 */
@Composable
fun HaidaApp(
    status: StatusReport,
    memory: MemoryReport,
    driving: Boolean,
    listening: ListenState,
    memoryActions: MemoryActions,
    music: MusicUi,
    musicActions: MusicActions,
) {
    var current by rememberSaveable { mutableStateOf(Destination.CAR) }
    // 체크 목록 알림은 동승 화면에서는 화면 안에 보이므로 패널로 띄우지 않는다
    val panel = memory.panel?.takeUnless { it.checklist && current == Destination.PEOPLE }
    val hearing = listening as? ListenState.Listening

    // 패널의 말은 잠시 보이고 사라진다(체크 목록 알림은 답할 때까지 둔다)
    LaunchedEffect(panel?.seq, panel?.checklist) {
        if (panel != null && !panel.checklist) {
            delay(PANEL_MILLIS)
            memoryActions.dismissPanel()
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(Haida.colors.l0)) {
        Dock(
            current = current,
            // 다른 화면에 있을 때 차에 주의할 것이 있거나, 동승 화면이 답을 기다리면(누가 탔는지 · 하차 확인) 점으로 알린다
            badge = {
                when (it) {
                    Destination.CAR -> status.overall.severity > 0
                    Destination.PEOPLE -> memory.question != null || memory.checklist != null
                    Destination.MUSIC -> false
                }
            },
            onSelect = { current = it },
            listening = hearing != null,
            voiceAvailable = listening !is ListenState.Unavailable,
            onMic = { if (hearing != null) memoryActions.stopListening() else memoryActions.startListening() },
        )
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // 비서 패널은 본문을 덮지 않는다: 패널이 뜨면 본문 아래에 패널 자리(높이 + 위아래 간격)를 비운다
            val showPanel = hearing != null || panel != null
            val bottom = if (showPanel) AssistantPanelHeight + 18.dp + 16.dp - 24.dp else 0.dp
            Box(Modifier.fillMaxSize().padding(bottom = bottom)) {
                when (current) {
                    Destination.CAR -> VehicleStatusScreen(status, driving)
                    Destination.PEOPLE -> MemoryScreen(memory, driving, memoryActions)
                    // 주행 중 화면(버튼 3개 · 다음 곡 하나)은 변속 단(기능 2 CabinSource)으로 켠다
                    Destination.MUSIC -> MusicScreen(music, driving, actions = musicActions)
                }
            }
            val panelModifier = Modifier.align(Alignment.BottomCenter).padding(start = 40.dp, end = 40.dp, bottom = 18.dp)
            when {
                hearing != null -> AssistantPanel(
                    tag = "듣고 있어요",
                    text = hearing.partial.ifEmpty { "말씀하세요. 예: ${MemoryMessages.EXAMPLES[1]}" },
                    tagIcon = HaidaIcons.Mic,
                    modifier = panelModifier,
                ) {
                    HaidaButton("그만", onClick = memoryActions::stopListening, height = 76.dp)
                }
                panel != null && panel.checklist -> AssistantPanel(panel.tag, panel.text, panelModifier, tagIcon = HaidaIcons.Bag) {
                    HaidaButton("다 챙겼어요", onClick = { memoryActions.closeArrival(collected = true) }, kind = ButtonKind.PRIMARY, height = 76.dp)
                    HaidaButton("나중에", onClick = { memoryActions.closeArrival(collected = false) }, height = 76.dp)
                }
                panel != null -> AssistantPanel(panel.tag, panel.text, panelModifier, tagIcon = HaidaIcons.Bag) {
                    HaidaButton("알겠어요", onClick = memoryActions::dismissPanel, height = 76.dp)
                }
            }
        }
    }
}

/** 비서의 말을 패널에 두는 시간 */
private const val PANEL_MILLIS = 12_000L

@Composable
private fun Dock(
    current: Destination,
    badge: (Destination) -> Boolean,
    onSelect: (Destination) -> Unit,
    listening: Boolean,
    voiceAvailable: Boolean,
    onMic: () -> Unit,
) {
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
            DockItem(dest.label, dest.icon, selected = dest == current, badge = dest != current && badge(dest), onClick = { onSelect(dest) })
        }
        Spacer(Modifier.weight(1f))
        // 말로 하기: 어느 화면에서나, 주행 중에도
        if (voiceAvailable) MicItem(listening, onMic)
    }
}

@Composable
private fun MicItem(listening: Boolean, onClick: () -> Unit) {
    val c = Haida.colors
    Column(
        modifier = Modifier
            .width(104.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (listening) c.pri else c.l1)
            .clickable(role = Role.Button, onClickLabel = if (listening) "그만 듣기" else "말로 하기", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Icon(HaidaIcons.Mic, contentDescription = null, tint = if (listening) c.onPri else c.ac, modifier = Modifier.size(38.dp))
        Text(if (listening) "듣는 중" else "말하기", style = HaidaType.smallSemi, color = if (listening) c.onPri else c.ac)
    }
}

@Composable
private fun DockItem(label: String, icon: ImageVector, selected: Boolean, badge: Boolean, onClick: () -> Unit) {
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
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(38.dp))
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
        Text(label, style = if (selected) HaidaType.smallSemi else HaidaType.eyebrow, color = color)
    }
}
