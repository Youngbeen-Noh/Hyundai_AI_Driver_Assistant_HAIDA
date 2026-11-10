package kr.contest.pleosassistant.ui.hazard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kr.contest.pleosassistant.hazard.AlertSchedule
import kr.contest.pleosassistant.hazard.HazardAlert
import kr.contest.pleosassistant.hazard.HazardReport
import kr.contest.pleosassistant.hazard.RADIUS_CHOICES
import kr.contest.pleosassistant.hazard.ZoneRow
import kr.contest.pleosassistant.ui.common.BigButton
import kr.contest.pleosassistant.ui.common.CardShape
import kr.contest.pleosassistant.ui.common.IconTile
import kr.contest.pleosassistant.ui.common.Palette
import kr.contest.pleosassistant.ui.common.ScreenHeader
import kr.contest.pleosassistant.ui.common.SummaryBanner

// 임시 디자인. 팀 UI 템플릿을 받으면 이 파일만 바꾼다(HazardReport · HazardActions 는 그대로).

@Composable
fun HazardScreen(report: HazardReport, actions: HazardActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.background)
            .padding(horizontal = 48.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        ScreenHeader(Icons.Filled.Place, "위험 구간", "가까이 가면 미리 알려 드려요 · 차 안에만 저장해요")
        val inside = report.zones.any { it.inside && it.enabled }
        SummaryBanner(
            icon = if (inside) Icons.Filled.WarningAmber else Icons.Filled.Place,
            color = if (inside) Palette.caution else Palette.accent,
            text = report.notice ?: report.summary,
        )
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            AddPanel(report, actions, Modifier.weight(1f).fillMaxHeight())
            ZoneListPanel(report, actions, Modifier.weight(1.2f).fillMaxHeight())
        }
    }

    // 저장 결과 안내는 잠깐 보여 주고 요약 문장으로 돌아간다
    report.notice?.let { notice ->
        LaunchedEffect(notice) {
            delay(3_000)
            actions.clearNotice()
        }
    }
}

/** 구간에 들어서면 어느 탭에 있든 앱 위에 뜬다. 주행 중이라 버튼은 하나만 둔다 */
@Composable
fun HazardAlertDialog(alert: HazardAlert, actions: HazardActions) {
    AlertDialog(
        onDismissRequest = actions::dismissAlert,
        containerColor = Palette.card,
        icon = { IconTile(Icons.Filled.WarningAmber, if (alert.repeat) Palette.accent else Palette.caution, tileSize = 80, iconSize = 48) },
        title = { Text(alert.title, color = Palette.text, fontSize = 34.sp, fontWeight = FontWeight.Bold) },
        text = { Text(alert.message, color = Palette.text, fontSize = 36.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold) },
        confirmButton = { BigButton("알겠어요", onClick = actions::dismissAlert, color = Palette.caution) },
    )
}

// ---- 새 구간 ----

@Composable
private fun AddPanel(report: HazardReport, actions: HazardActions, modifier: Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    var radius by rememberSaveable { mutableStateOf(200) }
    var schedule by rememberSaveable { mutableStateOf(AlertSchedule.ALWAYS) }
    val save = {
        actions.addZone(name, radius, schedule)
        name = ""
    }

    Panel(modifier) {
        PanelTitle(Icons.Filled.AddLocationAlt, "지금 위치를 저장", "예: 아파트 정문, 학원 앞 골목")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.MyLocation, contentDescription = null, tint = if (report.hasPosition) Palette.ok else Palette.textMuted, modifier = Modifier.size(28.dp))
            Text(report.positionText, color = Palette.textMuted, fontSize = 22.sp)
        }
        if (!report.canEdit) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Palette.caution, modifier = Modifier.size(28.dp))
                Text("주행 중에는 고칠 수 없어요. 정차(P)하면 열려요", color = Palette.caution, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(20) },
            enabled = report.canEdit,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("이름 (비우면 '위험 구간 N')", fontSize = 24.sp, color = Palette.textMuted) },
            textStyle = TextStyle(fontSize = 26.sp, color = Palette.text),
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (report.canEdit && report.hasPosition) save() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.accent,
                unfocusedBorderColor = Palette.cardBorder,
                cursorColor = Palette.accent,
                focusedTextColor = Palette.text,
                unfocusedTextColor = Palette.text,
            ),
        )
        Label("알림 반경")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RADIUS_CHOICES.forEach { r ->
                Choice("${r}m", selected = r == radius, enabled = report.canEdit, modifier = Modifier.weight(1f)) { radius = r }
            }
        }
        Label("알림 시간")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AlertSchedule.entries.forEach { s ->
                Choice(s.label, selected = s == schedule, enabled = report.canEdit, modifier = Modifier.weight(1f)) { schedule = s }
            }
        }
        BigButton(
            "이 위치를 위험 구간으로 저장",
            onClick = save,
            enabled = report.canEdit && report.hasPosition,
            icon = Icons.Filled.AddLocationAlt,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---- 저장한 구간 ----

@Composable
private fun ZoneListPanel(report: HazardReport, actions: HazardActions, modifier: Modifier) {
    Panel(modifier) {
        PanelTitle(Icons.Filled.Place, "저장한 구간 ${report.zones.size}곳", "끈 구간은 다시 알리지 않아요")
        if (report.zones.isEmpty()) {
            Text("아직 저장한 구간이 없어요", color = Palette.textMuted, fontSize = 26.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(report.zones, key = { it.id }) { ZoneItem(it, report.canEdit, actions) }
            }
        }
    }
}

@Composable
private fun ZoneItem(zone: ZoneRow, canEdit: Boolean, actions: HazardActions) {
    val tone = when {
        !zone.enabled -> Palette.neutral
        zone.inside -> Palette.caution
        else -> Palette.accent
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(tone.copy(alpha = 0.08f))
            .border(1.dp, tone.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(zone.name, color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                val status = listOfNotNull(
                    if (zone.inside && zone.enabled) "구간 안" else zone.distanceText,
                    if (zone.enabled && !zone.activeNow) "지금은 알림 시간 아님" else null,
                    if (!zone.enabled) "알림 끔" else null,
                ).joinToString(" · ")
                Text(status, color = if (zone.inside && zone.enabled) Palette.caution else Palette.textMuted, fontSize = 22.sp)
            }
            Switch(
                checked = zone.enabled,
                onCheckedChange = { actions.setEnabled(zone.id, it) },
                enabled = canEdit,
                // 주행 중엔 못 바꾸지만, 켜져 있으면 켜진 색으로 보여 준다
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Palette.accent,
                    disabledCheckedTrackColor = Palette.accent.copy(alpha = 0.45f),
                    disabledCheckedThumbColor = Palette.text.copy(alpha = 0.8f),
                ),
            )
            if (canEdit) {
                IconButton(onClick = { actions.removeZone(zone.id) }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "${zone.name} 지우기", tint = Palette.textMuted, modifier = Modifier.size(32.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RADIUS_CHOICES.forEach { r ->
                Choice("${r}m", selected = r == zone.radiusM, enabled = canEdit, small = true) { actions.setRadius(zone.id, r) }
            }
            AlertSchedule.entries.forEach { s ->
                Choice(s.label, selected = s == zone.schedule, enabled = canEdit, small = true) { actions.setSchedule(zone.id, s) }
            }
        }
    }
}

// ---- 공통 조각 ----

@Composable
private fun Panel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(Palette.card)
            .border(1.dp, Palette.cardBorder, CardShape)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        content = content,
    )
}

@Composable
private fun PanelTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconTile(icon, Palette.accent, tileSize = 56, iconSize = 32)
        Column {
            Text(title, color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Palette.textMuted, fontSize = 20.sp)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Palette.textMuted, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
}

/** 고르는 칩. 차 화면이라 높이 64dp 이상(작은 칩은 56dp) */
@Composable
private fun Choice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val color = if (selected) Palette.accent else Palette.textMuted
    Row(
        modifier = modifier
            .heightIn(min = if (small) 56.dp else 64.dp)
            .clip(shape)
            .background(if (selected) Palette.accent.copy(alpha = 0.16f) else Palette.background)
            .border(1.5.dp, if (selected) Palette.accent else Palette.cardBorder, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 18.dp else 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (enabled) color else color.copy(alpha = 0.5f),
            fontSize = if (small) 20.sp else 24.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
