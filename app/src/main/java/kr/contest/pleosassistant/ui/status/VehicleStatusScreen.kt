package kr.contest.pleosassistant.ui.status

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.contest.pleosassistant.R
import kr.contest.pleosassistant.status.StatusItem
import kr.contest.pleosassistant.status.StatusItemId
import kr.contest.pleosassistant.status.StatusLevel
import kr.contest.pleosassistant.status.StatusReport
import kr.contest.pleosassistant.ui.common.Haida
import kr.contest.pleosassistant.ui.common.HaidaCard
import kr.contest.pleosassistant.ui.common.HaidaIcons
import kr.contest.pleosassistant.ui.common.HaidaType
import kr.contest.pleosassistant.ui.common.IconTile
import kr.contest.pleosassistant.ui.common.LevelBar
import kr.contest.pleosassistant.ui.common.PillTone
import kr.contest.pleosassistant.ui.common.ScreenHeader
import kr.contest.pleosassistant.ui.common.Separator
import kr.contest.pleosassistant.ui.common.StatusPill
import kr.contest.pleosassistant.ui.common.TileTone

// 팀 UI 프로토타입의 '차량 상태' 화면: 상태 알약 + 한 문장, 왼쪽 차량 그림 카드, 오른쪽 항목 목록 카드.
// 목록에는 문제 · 잔량 · 주행거리 · 못 읽은 항목만 두고, 정상 · 지원 안 함 항목은 접어 둔다.

@Composable
fun VehicleStatusScreen(report: StatusReport, modifier: Modifier = Modifier) {
    val sorted = report.items.sortedForList()
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader("차량 상태", headerSub(report))
        Column(
            modifier = Modifier.weight(1f).padding(start = 40.dp, end = 40.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Sentence(report)
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                CarStage(primaryProblem(report), Modifier.weight(640f).fillMaxHeight())
                ItemList(sorted, expanded, onToggle = { expanded = !expanded }, Modifier.weight(568f).fillMaxHeight())
            }
        }
    }
}

/** 목록 카드에 늘 보이는 항목 / 접어 두는 항목 */
private data class SortedItems(val shown: List<StatusItem>, val folded: List<StatusItem>)

private val ENERGY = setOf(StatusItemId.FUEL, StatusItemId.EV_BATTERY, StatusItemId.ENERGY)

private fun List<StatusItem>.sortedForList(): SortedItems {
    val problems = filter { it.level.severity > 0 }.sortedByDescending { it.level.severity }
    val energy = filter { it.id in ENERGY && it.level == StatusLevel.OK }
    val pending = filter { it.level == StatusLevel.UNKNOWN || it.level == StatusLevel.LOADING }
    val info = filter { it.level == StatusLevel.INFO }
    val shown = problems + energy + pending + info
    return SortedItems(shown, filterNot { it in shown })
}

private fun primaryProblem(report: StatusReport): StatusItem? =
    report.items.filter { it.level.severity > 0 }.maxByOrNull { it.level.severity }

private fun headerSub(report: StatusReport): String {
    val readable = report.items.filter { it.level != StatusLevel.UNSUPPORTED }
    if (readable.any { it.level == StatusLevel.LOADING }) return "확인하는 중"
    val unknown = readable.count { it.level == StatusLevel.UNKNOWN }
    return if (unknown > 0) "실시간 점검 · ${unknown}개 확인 불가" else "실시간 점검"
}

/** 맨 위: 전체 상태 알약 + 요약 한 문장(.car-sentence) */
@Composable
private fun Sentence(report: StatusReport) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val (text, tone, icon) = when (report.overall) {
            StatusLevel.URGENT -> Triple("즉시 조치", PillTone.CRIT, HaidaIcons.Crit)
            StatusLevel.CAUTION -> Triple("주의", PillTone.WARN, HaidaIcons.Warn)
            StatusLevel.LOADING -> Triple("확인 중", PillTone.NEUTRAL, HaidaIcons.Clock)
            StatusLevel.UNKNOWN -> Triple("확인 불가", PillTone.UNKNOWN, HaidaIcons.Unknown)
            else -> Triple("정상", PillTone.NEUTRAL, HaidaIcons.Ok)
        }
        StatusPill(text, tone, icon = icon)
        Text(report.summary, style = HaidaType.sentence, color = Haida.colors.t1, modifier = Modifier.weight(1f))
    }
}

/** 왼쪽: 차량 그림과 가장 급한 문제 말풍선(.car-stage · .callout) */
@Composable
private fun CarStage(problem: StatusItem?, modifier: Modifier) {
    val c = Haida.colors
    val stage = if (c.isNight) {
        Brush.radialGradient(0f to Color(0xFF2C3340), 0.58f to Color(0xFF1C2128), 1f to c.l1)
    } else {
        Brush.verticalGradient(listOf(c.l1, c.l2))
    }
    HaidaCard(modifier = modifier, raised = true, background = stage) {
        Image(
            painter = painterResource(if (c.isNight) R.drawable.haida_car_night else R.drawable.haida_car_day),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 10.dp),
        )
        problem?.let { Callout(it, Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 28.dp)) }
    }
}

@Composable
private fun Callout(item: StatusItem, modifier: Modifier) {
    val c = Haida.colors
    val urgent = item.level == StatusLevel.URGENT
    val color = if (urgent) c.crit else c.warn
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .height(56.dp)
            .shadow(8.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(shape)
            .background(c.l1)
            .border(1.5.dp, if (urgent) c.critLine else c.warnLine, shape)
            .padding(start = 14.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(item.icon(), contentDescription = null, tint = color, modifier = Modifier.size(30.dp))
        Text("${item.title} · ${item.detail}", style = HaidaType.smallBold, color = color, maxLines = 1)
    }
}

/** 오른쪽: 항목 목록(.car-list). 맨 아래 접기 버튼으로 나머지를 펼친다. */
@Composable
private fun ItemList(items: SortedItems, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    HaidaCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 8.dp)) {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                items.shown.forEachIndexed { index, item ->
                    if (index > 0) Separator()
                    ItemRow(item)
                }
                if (expanded && items.folded.isNotEmpty()) {
                    Separator()
                    Column(modifier = Modifier.padding(vertical = 10.dp)) {
                        items.folded.forEach { FoldedRow(it) }
                    }
                }
            }
            if (items.folded.isNotEmpty()) {
                val label = when {
                    expanded -> "접기"
                    items.folded.all { it.level == StatusLevel.OK } -> "정상 항목 모두 보기"
                    else -> "나머지 ${items.folded.size}개 보기"
                }
                FoldButton(label, expanded, onToggle)
            }
        }
    }
}

/** 목록 한 줄(.irow): 아이콘 칸 · 이름과 설명 · 오른쪽 상태 */
@Composable
private fun ItemRow(item: StatusItem) {
    val c = Haida.colors
    val tone = when (item.level) {
        StatusLevel.URGENT -> TileTone.CRIT
        StatusLevel.CAUTION -> TileTone.WARN
        else -> TileTone.NEUTRAL
    }
    val isEnergy = item.id in ENERGY && item.gauge != null
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        IconTile(if (item.level == StatusLevel.UNKNOWN) HaidaIcons.Unknown else item.icon(), tone)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (isEnergy) "${item.title} ${item.detail}" else item.title,
                style = HaidaType.bodyBold,
                color = c.t1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = when {
                item.advice != null -> item.advice.substringAfter(". ", item.advice)
                isEnergy -> null
                item.level == StatusLevel.INFO -> null
                item.level == StatusLevel.LOADING -> "값을 기다리는 중이에요"
                else -> item.detail
            }
            sub?.let { Text(it, style = HaidaType.small, color = c.t3, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            if (isEnergy) {
                LevelBar(item.gauge ?: 0f, item.level.barColor(), Modifier.padding(top = 6.dp).width(240.dp))
            }
        }
        when (item.level) {
            StatusLevel.URGENT -> StatusPill("즉시 조치", PillTone.CRIT)
            StatusLevel.CAUTION -> StatusPill("주의", PillTone.WARN)
            StatusLevel.UNKNOWN -> StatusPill("확인 불가", PillTone.UNKNOWN)
            StatusLevel.LOADING -> StatusPill("확인 중", PillTone.NEUTRAL)
            StatusLevel.INFO -> Text(item.detail, style = HaidaType.bodyBold, color = c.t1)
            else -> StatusPill("정상", PillTone.OK)
        }
    }
}

/** 펼친 목록의 작은 줄(.fl-row) */
@Composable
private fun FoldedRow(item: StatusItem) {
    val c = Haida.colors
    val supported = item.level != StatusLevel.UNSUPPORTED
    Row(
        modifier = Modifier.fillMaxWidth().height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(item.icon(), contentDescription = null, tint = c.t2, modifier = Modifier.size(28.dp))
        Text(item.title, style = HaidaType.small, color = c.t2, modifier = Modifier.weight(1f))
        Text(
            if (supported) item.detail else "이 차에서 못 읽어요",
            style = HaidaType.smallBold,
            color = if (supported) c.t1 else c.t3,
        )
    }
}

@Composable
private fun FoldButton(label: String, expanded: Boolean, onClick: () -> Unit) {
    val c = Haida.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(if (expanded) HaidaIcons.ChevronUp else HaidaIcons.ChevronDown, contentDescription = null, tint = c.t2, modifier = Modifier.size(30.dp))
        Text(label, style = HaidaType.bodySemi, color = c.t2)
    }
}

private fun StatusItem.icon(): ImageVector = when (id) {
    StatusItemId.ENGINE_OIL -> HaidaIcons.Oil
    StatusItemId.TRANSMISSION_OIL -> HaidaIcons.Oil
    StatusItemId.BRAKE_FLUID -> HaidaIcons.Brake
    StatusItemId.WASHER_FLUID -> HaidaIcons.Washer
    StatusItemId.FUEL -> HaidaIcons.Fuel
    StatusItemId.EV_BATTERY -> HaidaIcons.Battery
    StatusItemId.ENERGY -> HaidaIcons.Battery
    StatusItemId.ODOMETER -> HaidaIcons.Gauge
}

@Composable
private fun StatusLevel.barColor(): Color = when (this) {
    StatusLevel.URGENT -> Haida.colors.crit
    StatusLevel.CAUTION -> Haida.colors.warnFill
    else -> Haida.colors.ac
}
