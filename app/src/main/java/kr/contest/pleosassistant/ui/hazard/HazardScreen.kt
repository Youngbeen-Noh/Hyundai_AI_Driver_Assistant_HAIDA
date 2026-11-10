package kr.contest.pleosassistant.ui.hazard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kr.contest.pleosassistant.hazard.AlertSchedule
import kr.contest.pleosassistant.hazard.HazardReport
import kr.contest.pleosassistant.hazard.RADIUS_CHOICES
import kr.contest.pleosassistant.hazard.ZoneRow
import kr.contest.pleosassistant.ui.common.ButtonKind
import kr.contest.pleosassistant.ui.common.Eyebrow
import kr.contest.pleosassistant.ui.common.Haida
import kr.contest.pleosassistant.ui.common.HaidaButton
import kr.contest.pleosassistant.ui.common.HaidaCard
import kr.contest.pleosassistant.ui.common.HaidaChip
import kr.contest.pleosassistant.ui.common.HaidaIcons
import kr.contest.pleosassistant.ui.common.HaidaTextField
import kr.contest.pleosassistant.ui.common.HaidaType
import kr.contest.pleosassistant.ui.common.HeaderSub
import kr.contest.pleosassistant.ui.common.IconTile
import kr.contest.pleosassistant.ui.common.PillShape
import kr.contest.pleosassistant.ui.common.PillTone
import kr.contest.pleosassistant.ui.common.ScreenHeader
import kr.contest.pleosassistant.ui.common.Separator
import kr.contest.pleosassistant.ui.common.StatusPill
import kr.contest.pleosassistant.ui.common.TileTone

// 팀 UI 프로토타입의 위험 구간 화면(.hz-edit · .hz-idle)을 따른다. 지도 SDK 가 없어 지도 대신 '지금 위치 저장'과 구간 목록을 둔다.
// 정차 중: 왼쪽 저장 카드 + 오른쪽 구간 목록(편집). 주행 중: 큰 한 문장 + 구간 목록(읽기만, 기획서 9장 '주행 중 편집 입력 없음').

@Composable
fun HazardScreen(report: HazardReport, actions: HazardActions, modifier: Modifier = Modifier) {
    val watching = report.zones.count { it.enabled }
    val off = report.zones.size - watching
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            "위험 구간",
            sub = {
                when {
                    report.notice != null -> StatusPill(report.notice, PillTone.OK)
                    report.zones.isNotEmpty() -> HeaderSub("지나는 구간 ${report.zones.size}곳" + if (off > 0) " · ${off}곳 알림 끔" else "")
                }
            },
        )
        Row(
            modifier = Modifier.weight(1f).padding(start = 40.dp, end = 40.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (report.canEdit) AddCard(report, actions, Modifier.width(580.dp).fillMaxHeight())
            ZoneCard(report, actions, Modifier.weight(1f).fillMaxHeight())
        }
    }

    // 저장 결과 안내는 잠깐 보여 주고 지운다
    report.notice?.let { notice ->
        LaunchedEffect(notice) {
            delay(3_000)
            actions.clearNotice()
        }
    }
}

// ---- 왼쪽: 지금 위치 저장 (정차 중에만) ----

@Composable
private fun AddCard(report: HazardReport, actions: HazardActions, modifier: Modifier) {
    val c = Haida.colors
    var name by rememberSaveable { mutableStateOf("") }
    var radius by rememberSaveable { mutableIntStateOf(200) }
    var schedule by rememberSaveable { mutableStateOf(AlertSchedule.ALWAYS) }
    val save = {
        if (report.hasPosition) {
            actions.addZone(name, radius, schedule)
            name = ""
        }
    }

    HaidaCard(modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Eyebrow("알림 받을 곳 · 정차 중에만 수정")
            Text("지금 위치를 저장", style = HaidaType.title, color = c.t1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(HaidaIcons.Pin, contentDescription = null, tint = if (report.hasPosition) c.ac else c.t3, modifier = Modifier.size(28.dp))
                Text(report.positionText, style = HaidaType.small, color = c.t3)
            }
            HaidaTextField(name, onValueChange = { name = it.take(20) }, placeholder = "이름 (예: 아파트 정문)", modifier = Modifier.fillMaxWidth(), onDone = save)

            Eyebrow("반경", modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RADIUS_CHOICES.forEach { r -> HaidaChip("${r}m", onClick = { radius = r }, selected = r == radius, modifier = Modifier.weight(1f)) }
            }
            Eyebrow("알림 시간", modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AlertSchedule.entries.forEach { s -> HaidaChip(s.label, onClick = { schedule = s }, selected = s == schedule, modifier = Modifier.weight(1f)) }
            }
            Text("구간 ${radius}m 앞에서 알려 드려요", style = HaidaType.small, color = c.t3)
            Spacer(Modifier.weight(1f))
            HaidaButton(
                "이 위치를 위험 구간으로 저장",
                onClick = save,
                kind = ButtonKind.PRIMARY,
                enabled = report.hasPosition,
                height = 96.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---- 오른쪽: 저장한 구간 ----

@Composable
private fun ZoneCard(report: HazardReport, actions: HazardActions, modifier: Modifier) {
    val c = Haida.colors
    HaidaCard(modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Eyebrow(if (report.canEdit) "저장한 구간" else "주행 중 · 정차하면 고칠 수 있어요")
            Text(report.summary, style = if (report.canEdit) HaidaType.title else HaidaType.sentence, color = c.t1)
            if (report.zones.isNotEmpty()) {
                Separator()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.weight(1f)) {
                    items(report.zones, key = { it.id }) { zone ->
                        ZoneItem(zone, report.canEdit, actions)
                        Separator()
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(HaidaIcons.Warn, contentDescription = null, tint = c.t3, modifier = Modifier.size(28.dp))
                    Text("같은 날 다시 지나면 짧게만 알려요 · 끈 구간은 알리지 않아요", style = HaidaType.small, color = c.t3)
                }
            }
        }
    }
}

@Composable
private fun ZoneItem(zone: ZoneRow, canEdit: Boolean, actions: HazardActions) {
    val c = Haida.colors
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            IconTile(HaidaIcons.Pin, tone = if (zone.inside && zone.enabled) TileTone.WARN else if (zone.enabled) TileTone.ACCENT else TileTone.NEUTRAL)
            Column(modifier = Modifier.weight(1f).alpha(if (zone.enabled) 1f else 0.6f)) {
                Text(zone.name, style = HaidaType.bodySemi, color = c.t1)
                val detail = listOfNotNull(
                    "반경 ${zone.radiusM}m",
                    zone.schedule.label,
                    zone.distanceText,
                    if (zone.enabled && !zone.activeNow) "지금은 알림 시간 아님" else null,
                ).joinToString(" · ")
                Text(detail, style = HaidaType.small, color = c.t3)
            }
            when {
                zone.inside && zone.enabled -> StatusPill("지나는 중", PillTone.WARN, icon = HaidaIcons.Pin)
                !zone.enabled -> StatusPill("알림 끔", PillTone.NEUTRAL)
            }
            HaidaSwitch(
                checked = zone.enabled,
                enabled = canEdit,
                label = "${zone.name} 알림",
                onChange = { actions.setEnabled(zone.id, it) },
            )
        }
        if (canEdit) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RADIUS_CHOICES.forEach { r ->
                    HaidaChip("${r}m", onClick = { actions.setRadius(zone.id, r) }, selected = r == zone.radiusM, small = true, height = 64.dp)
                }
                // 알림 시간은 눌러서 바꾼다(항상 ↔ 등하교 시간만)
                val next = AlertSchedule.entries[(zone.schedule.ordinal + 1) % AlertSchedule.entries.size]
                HaidaChip(zone.schedule.label, onClick = { actions.setSchedule(zone.id, next) }, small = true, icon = HaidaIcons.Clock, height = 64.dp)
                Spacer(Modifier.weight(1f))
                HaidaChip("지우기", onClick = { actions.removeZone(zone.id) }, small = true, danger = true, icon = HaidaIcons.Close, height = 64.dp)
            }
        }
    }
}

/** 켜기/끄기 스위치(.sw): 76 × 44. 주행 중에는 못 바꾸지만 켜진 상태는 그대로 보여 준다 */
@Composable
private fun HaidaSwitch(checked: Boolean, enabled: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val c = Haida.colors
    Box(
        modifier = Modifier
            .width(76.dp)
            .height(44.dp)
            .alpha(if (enabled) 1f else 0.55f)
            .clip(PillShape)
            .background(if (checked) c.ac else c.l3)
            .border(1.dp, if (checked) c.ac else c.line2, PillShape)
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .semantics { contentDescription = label },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = if (checked) 36.dp else 4.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(if (checked) c.onAc else c.l1),
        )
    }
}
