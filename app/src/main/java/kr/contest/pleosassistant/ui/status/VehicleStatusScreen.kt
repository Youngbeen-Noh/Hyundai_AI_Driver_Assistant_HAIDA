package kr.contest.pleosassistant.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.OilBarrel
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.contest.pleosassistant.status.StatusItem
import kr.contest.pleosassistant.status.StatusItemId
import kr.contest.pleosassistant.status.StatusLevel
import kr.contest.pleosassistant.status.StatusReport
import kr.contest.pleosassistant.ui.common.CardShape
import kr.contest.pleosassistant.ui.common.IconTile
import kr.contest.pleosassistant.ui.common.Palette
import kr.contest.pleosassistant.ui.common.ScreenHeader
import kr.contest.pleosassistant.ui.common.SummaryBanner

// 임시 디자인. 팀 UI 템플릿을 받으면 이 파일(과 ui/common)만 바꾼다(StatusReport 는 그대로).

@Composable
fun VehicleStatusScreen(report: StatusReport, modifier: Modifier = Modifier) {
    // 값이 있는 항목은 큰 카드로(문제 있는 것부터), 지원하지 않는 항목은 맨 아래 한 줄로 모은다
    val shown = report.items
        .filter { it.level != StatusLevel.UNSUPPORTED }
        .sortedByDescending { it.level.severity }
    val unsupported = report.items.filter { it.level == StatusLevel.UNSUPPORTED }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.background)
            .padding(horizontal = 48.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        ScreenHeader(Icons.Filled.DirectionsCar, "차량 요약", "탑승할 때 차 상태를 한눈에 확인해요")
        StatusBanner(report)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            items(shown, key = { it.id }) { StatusCard(it) }
            if (unsupported.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { UnsupportedRow(unsupported) }
            }
        }
    }
}

@Composable
private fun StatusBanner(report: StatusReport) {
    val level = report.overall
    val icon = when (level) {
        StatusLevel.URGENT -> Icons.Filled.Error
        StatusLevel.CAUTION -> Icons.Filled.Warning
        StatusLevel.LOADING -> Icons.Filled.HourglassTop
        StatusLevel.UNKNOWN -> Icons.Filled.Info
        else -> Icons.Filled.CheckCircle
    }
    SummaryBanner(icon, level.color(), report.summary, contentDescription = level.label)
}

@Composable
private fun StatusCard(item: StatusItem) {
    val color = item.level.color()
    val isProblem = item.level == StatusLevel.URGENT || item.level == StatusLevel.CAUTION
    val hasValue = item.level != StatusLevel.UNKNOWN && item.level != StatusLevel.LOADING
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(336.dp)
            .clip(CardShape)
            .background(if (isProblem) color.copy(alpha = 0.07f) else Palette.card)
            .border(if (isProblem) 2.5.dp else 1.dp, if (isProblem) color.copy(alpha = 0.8f) else Palette.cardBorder, CardShape)
            .padding(32.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            IconTile(item.icon(), color, tileSize = 80, iconSize = 46)
            LevelChip(item.level)
        }
        Spacer(Modifier.weight(1f))
        Text(item.title, color = Palette.textMuted, fontSize = 24.sp)
        Text(
            item.detail,
            color = Palette.text,
            fontSize = if (hasValue) 52.sp else 30.sp,
            fontWeight = if (hasValue) FontWeight.Bold else FontWeight.Medium,
            lineHeight = if (hasValue) 60.sp else 38.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.gauge?.let { Gauge(it, color) }
        item.advice?.let {
            // 요약 문장의 두 번째 문장(무엇을 할지)만 카드에 보인다
            Text(
                it.substringAfter(". ", it),
                color = color,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnsupportedRow(items: List<StatusItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.dp, Palette.cardBorder, CardShape)
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("이 차량에서 지원하지 않는 항목", color = Palette.textMuted, fontSize = 22.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Palette.card)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(item.icon(), contentDescription = null, tint = Palette.neutral, modifier = Modifier.size(26.dp))
                    Text(item.title, color = Palette.textMuted, fontSize = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun LevelChip(level: StatusLevel) {
    val color = level.color()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Text(level.label, color = color, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Gauge(value: Float, color: Color) {
    Box(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(50))
            .background(Palette.cardBorder),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(value.coerceIn(0.02f, 1f))
                .height(12.dp)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}

private fun StatusLevel.color(): Color = when (this) {
    StatusLevel.URGENT -> Palette.urgent
    StatusLevel.CAUTION -> Palette.caution
    StatusLevel.OK -> Palette.ok
    StatusLevel.INFO -> Palette.accent
    else -> Palette.neutral
}

private fun StatusItem.icon(): ImageVector = when (id) {
    StatusItemId.ENGINE_OIL -> Icons.Filled.OilBarrel
    StatusItemId.TRANSMISSION_OIL -> Icons.Filled.Settings
    StatusItemId.BRAKE_FLUID -> BrakeWarningIcon
    StatusItemId.WASHER_FLUID -> Icons.Filled.WaterDrop
    StatusItemId.FUEL -> Icons.Filled.LocalGasStation
    StatusItemId.EV_BATTERY -> batteryIcon(gauge)
    StatusItemId.ENERGY -> Icons.Filled.EvStation
    StatusItemId.ODOMETER -> Icons.Filled.Speed
}

private fun batteryIcon(gauge: Float?): ImageVector = when {
    gauge == null -> Icons.Filled.BatteryStd
    gauge >= 0.9f -> Icons.Filled.BatteryFull
    gauge >= 0.6f -> Icons.Filled.Battery6Bar
    gauge >= 0.35f -> Icons.Filled.Battery4Bar
    gauge > 0.1f -> Icons.Filled.Battery2Bar
    else -> Icons.Filled.Battery0Bar
}

/** 계기판의 브레이크 경고등 모양: ( ! ) */
private val BrakeWarningIcon: ImageVector by lazy {
    ImageVector.Builder("BrakeWarning", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            // 가운데 원
            moveTo(18.5f, 12f)
            arcTo(6.5f, 6.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 5.5f, y1 = 12f)
            arcTo(6.5f, 6.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 18.5f, y1 = 12f)
            // 양옆 괄호
            moveTo(4.2f, 6.5f)
            arcTo(9.6f, 9.6f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 4.2f, y1 = 17.5f)
            moveTo(19.8f, 6.5f)
            arcTo(9.6f, 9.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 19.8f, y1 = 17.5f)
            // 느낌표 막대
            moveTo(12f, 8.6f)
            lineTo(12f, 12.4f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(13.1f, 15.2f)
            arcTo(1.1f, 1.1f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 10.9f, y1 = 15.2f)
            arcTo(1.1f, 1.1f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 13.1f, y1 = 15.2f)
        }
    }.build()
}
