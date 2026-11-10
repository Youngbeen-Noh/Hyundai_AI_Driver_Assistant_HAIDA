package kr.contest.pleosassistant.ui.status

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.contest.pleosassistant.status.StatusItem
import kr.contest.pleosassistant.status.StatusItemId
import kr.contest.pleosassistant.status.StatusLevel
import kr.contest.pleosassistant.status.StatusReport
import kr.contest.pleosassistant.ui.common.Eyebrow
import kr.contest.pleosassistant.ui.common.Haida
import kr.contest.pleosassistant.ui.common.HaidaCard
import kr.contest.pleosassistant.ui.common.HaidaColors
import kr.contest.pleosassistant.ui.common.HaidaIcons
import kr.contest.pleosassistant.ui.common.HaidaType
import kr.contest.pleosassistant.ui.common.HeaderSub
import kr.contest.pleosassistant.ui.common.IconTile
import kr.contest.pleosassistant.ui.common.LevelBar
import kr.contest.pleosassistant.ui.common.PillTone
import kr.contest.pleosassistant.ui.common.ScreenHeader
import kr.contest.pleosassistant.ui.common.Separator
import kr.contest.pleosassistant.ui.common.StatusPill
import kr.contest.pleosassistant.ui.common.TileTone
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// 팀 UI 프로토타입(수정본)의 '차량 상태' 화면. 본문 1474 × 1110 기준 왼쪽 카드 810, 오른쪽 열 640.
// - 왼쪽: 상태 알약 + 요약 문장, 위에서 본 평면 도식(시스템 화면의 3D 차와 겹치지 않게), 문제는 보닛 위 말풍선
// - 오른쪽: 항목 목록(정상 · 지원 안 함은 접어 둠) + 상태별 개수 요약
// - 주행 중(P 가 아님): 목록 대신 지금 문제 하나만 크게

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun VehicleStatusScreen(report: StatusReport, driving: Boolean, modifier: Modifier = Modifier) {
    val sorted = report.items.sortedForList()
    var expanded by rememberSaveable { mutableStateOf(false) }
    // 값이 마지막으로 바뀐 시각 = 마지막 측정
    val measuredAt = remember(report) { LocalTime.now().format(CLOCK) }
    val problem = primaryProblem(report)

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader("차량 상태", sub = { HeaderSub(headerSub(report, measuredAt)) })
        Row(
            modifier = Modifier.weight(1f).padding(start = 40.dp, end = 40.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            CarStage(report, problem, if (driving) null else measuredAt, Modifier.weight(810f).fillMaxHeight())
            if (driving) {
                DriveCard(problem, Modifier.weight(640f).fillMaxHeight())
            } else {
                BoxWithConstraints(modifier = Modifier.weight(640f).fillMaxHeight()) {
                    // 목록은 내용 높이, 남는 높이는 개수 요약이 채운다(최소 240). 펼치면 요약을 숨긴다.
                    val listMax = if (expanded) maxHeight else maxHeight - 264.dp
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        ItemList(sorted, expanded, onToggle = { expanded = !expanded }, Modifier.heightIn(max = listMax))
                        if (!expanded) SummaryCard(report, measuredAt, Modifier.weight(1f).fillMaxWidth())
                    }
                }
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

private fun headerSub(report: StatusReport, measuredAt: String): String {
    val readable = report.items.filter { it.level != StatusLevel.UNSUPPORTED }
    if (readable.any { it.level == StatusLevel.LOADING }) return "확인하는 중"
    val unknown = readable.count { it.level == StatusLevel.UNKNOWN }
    return "$measuredAt 점검" + if (unknown > 0) " · ${unknown}개 응답 없음" else ""
}

/** 왼쪽 카드: 상태 알약 · 요약 문장 · 평면 도식 · 마지막 측정 */
@Composable
private fun CarStage(report: StatusReport, problem: StatusItem?, measuredAt: String?, modifier: Modifier) {
    val c = Haida.colors
    HaidaCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(start = 44.dp, end = 44.dp, top = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OverallPill(report.overall)
                Text(report.summary, style = HaidaType.carSentence, color = c.t1)
            }
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // 프로토타입: 도식 높이 740. 위에 말풍선(88) + 잇는 선(30) 자리와 위아래 여백 20 을 남긴다
                val height = minOf(740.dp, maxHeight - 158.dp).coerceAtLeast(240.dp)
                val unit = height / 640f
                val hoodTop = unit * 40f
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (problem != null) {
                        Callout(problem)
                        Box(Modifier.width(2.dp).height(30.dp + hoodTop).background(problem.lineColor()))
                    } else {
                        Spacer(Modifier.height(118.dp + hoodTop))
                    }
                    CarFlat(
                        hood = problem?.level,
                        // 잇는 선이 보닛 표시까지 닿도록 도식을 그만큼 위로 겹친다(자리도 그만큼만 차지)
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                val overlap = hoodTop.roundToPx()
                                layout(placeable.width, placeable.height - overlap) { placeable.place(0, -overlap) }
                            }
                            .size(unit * 300f, height),
                    )
                }
            }
            measuredAt?.let {
                Text(
                    "마지막 측정 $it",
                    style = HaidaType.small,
                    color = c.t3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
                )
            }
        }
    }
}

@Composable
private fun OverallPill(level: StatusLevel) {
    val (text, tone, icon) = when (level) {
        StatusLevel.URGENT -> Triple("즉시 조치", PillTone.CRIT, HaidaIcons.Crit)
        StatusLevel.CAUTION -> Triple("주의", PillTone.WARN, HaidaIcons.Warn)
        StatusLevel.LOADING -> Triple("확인 중", PillTone.NEUTRAL, HaidaIcons.Clock)
        StatusLevel.UNKNOWN -> Triple("응답 없음", PillTone.UNKNOWN, HaidaIcons.Unknown)
        else -> Triple("정상", PillTone.NEUTRAL, HaidaIcons.Ok)
    }
    StatusPill(text, tone, icon = icon)
}

@Composable
private fun StatusItem.lineColor(): Color = if (level == StatusLevel.URGENT) Haida.colors.critLine else Haida.colors.warnLine

/** 도식 위 말풍선(.callout): 아이콘 · 이름 · 값 */
@Composable
private fun Callout(item: StatusItem) {
    val c = Haida.colors
    val urgent = item.level == StatusLevel.URGENT
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .heightIn(min = 88.dp)
            .clip(shape)
            .background(c.l1)
            .border(1.5.dp, item.lineColor(), shape)
            .padding(start = 18.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(item.icon(), contentDescription = null, tint = if (urgent) c.crit else c.warn, modifier = Modifier.size(36.dp))
        Column {
            Text(item.title, style = HaidaType.bodyBold, color = c.t1, maxLines = 1)
            Text(item.detail, style = HaidaType.smallBold, color = if (urgent) c.crit else c.warn, maxLines = 1)
        }
    }
}

/** 오른쪽: 항목 목록(.car-list). 맨 아래 접기 버튼으로 나머지를 펼친다. */
@Composable
private fun ItemList(items: SortedItems, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    HaidaCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 16.dp, bottom = 20.dp)) {
            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                items.shown.forEachIndexed { index, item ->
                    if (index > 0) Separator()
                    ItemRow(item)
                }
                if (expanded && items.folded.isNotEmpty()) {
                    Separator()
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        IconTile(if (item.level == StatusLevel.UNKNOWN) HaidaIcons.Unknown else item.icon(), tone)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                LevelBar(item.gauge ?: 0f, item.level.barColor(), Modifier.padding(top = 8.dp).width(240.dp))
            }
        }
        when (item.level) {
            StatusLevel.URGENT -> StatusPill("즉시 조치", PillTone.CRIT)
            StatusLevel.CAUTION -> StatusPill("주의", PillTone.WARN)
            StatusLevel.UNKNOWN -> StatusPill("응답 없음", PillTone.UNKNOWN)
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
        modifier = Modifier.fillMaxWidth().height(60.dp),
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
            .padding(top = 8.dp)
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

/** 상태별 개수 요약(.car-sum): 즉시 조치 · 주의 · 정상 */
@Composable
private fun SummaryCard(report: StatusReport, measuredAt: String, modifier: Modifier) {
    val checked = report.items.filter { it.level != StatusLevel.UNSUPPORTED && it.level != StatusLevel.INFO }
    val unknown = checked.count { it.level == StatusLevel.UNKNOWN }
    val unsupported = report.items.count { it.level == StatusLevel.UNSUPPORTED }
    val title = buildString {
        append("점검 항목 ${checked.size}개 · $measuredAt 측정")
        if (unknown > 0) append(" · ${unknown}개 응답 없음")
        if (unsupported > 0) append(" · ${unsupported}개는 이 차에서 못 읽어요")
    }
    HaidaCard(modifier = modifier.heightIn(min = 240.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 32.dp)) {
            Eyebrow(title)
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CountTile(checked.count { it.level == StatusLevel.URGENT }, "즉시 조치", HaidaIcons.Crit, TileTone.CRIT, Modifier.weight(1f))
                CountTile(checked.count { it.level == StatusLevel.CAUTION }, "주의", HaidaIcons.Warn, TileTone.WARN, Modifier.weight(1f))
                CountTile(checked.count { it.level == StatusLevel.OK }, "정상", HaidaIcons.Ok, TileTone.NEUTRAL, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CountTile(count: Int, label: String, icon: ImageVector, tone: TileTone, modifier: Modifier) {
    val c = Haida.colors
    val (bg, line, fg) = when {
        count == 0 -> Triple(c.l2, c.line, c.t3)
        tone == TileTone.CRIT -> Triple(c.critSoft, c.critLine, c.crit)
        tone == TileTone.WARN -> Triple(c.warnSoft, c.warnLine, c.warn)
        else -> Triple(c.l2, c.line, c.t2)
    }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier.fillMaxHeight().clip(shape).background(bg).border(1.dp, line, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("$count", style = HaidaType.count, color = if (count == 0) c.t3 else c.t1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(28.dp))
            Text(label, style = HaidaType.smallBold, color = fg)
        }
    }
}

/** 주행 중 오른쪽 카드: 지금 문제 하나(아이콘 · 이름 · 할 일)만 크게 */
@Composable
private fun DriveCard(problem: StatusItem?, modifier: Modifier) {
    val c = Haida.colors
    HaidaCard(modifier = modifier) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp, vertical = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val tone = when (problem?.level) {
                StatusLevel.URGENT -> TileTone.CRIT
                StatusLevel.CAUTION -> TileTone.WARN
                else -> TileTone.NEUTRAL
            }
            IconTile(problem?.icon() ?: HaidaIcons.Ok, tone, size = 128.dp, iconSize = 64.dp)
            Text(
                problem?.title ?: "모두 정상이에요",
                style = HaidaType.sentence.copy(fontWeight = HaidaType.bodyBold.fontWeight),
                color = c.t1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 28.dp),
            )
            problem?.let {
                Text(
                    listOfNotNull(it.detail, it.advice?.substringAfter(". ", it.advice)).joinToString(" · "),
                    style = HaidaType.body,
                    color = c.t2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            val urgent = problem?.level == StatusLevel.URGENT
            Text(
                if (urgent) "안전한 곳에 세우세요" else "정차하면 모든 항목을 볼 수 있어요",
                style = if (urgent) HaidaType.title else HaidaType.small,
                color = if (urgent) c.t1 else c.t3,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 40.dp),
            )
        }
    }
}

@Composable
private fun StatusLevel.barColor(): Color = when (this) {
    StatusLevel.URGENT -> Haida.colors.crit
    StatusLevel.CAUTION -> Haida.colors.warnFill
    else -> Haida.colors.ac
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

// ---- 평면 탑뷰 도식 (프로토타입 carFlat, SVG 300 × 640, 앞이 위) ----

/** @param hood 문제가 있으면 보닛 자리에 표시(주의 = 노랑, 즉시 조치 = 빨강) */
@Composable
private fun CarFlat(hood: StatusLevel?, modifier: Modifier) {
    val c = Haida.colors
    val shapes = remember { FlatShapes() }
    Canvas(modifier) {
        val k = size.width / 300f
        scale(k, k, pivot = Offset.Zero) {
            drawPath(shapes.body, c.l2)
            drawPath(shapes.body, c.t3, style = Stroke(2f))
            drawPath(shapes.hoodLine, c.line2, style = Stroke(2f))
            drawPath(shapes.windshield, c.l3)
            drawPath(shapes.windshield, c.t3, style = Stroke(2f, join = StrokeJoin.Round))
            drawRoundRect(c.l1, Offset(82f, 230f), Size(136f, 196f), CornerRadius(30f))
            drawRoundRect(c.line2, Offset(82f, 230f), Size(136f, 196f), CornerRadius(30f), style = Stroke(2f))
            drawPath(shapes.rearGlass, c.l3)
            drawPath(shapes.rearGlass, c.t3, style = Stroke(2f, join = StrokeJoin.Round))
            mirror(c, Offset(35f, 204f), -20f)
            mirror(c, Offset(265f, 204f), 20f)
            drawPath(shapes.headlights, c.t3, style = Stroke(4f, cap = StrokeCap.Round))
            drawPath(shapes.taillights, c.t3, style = Stroke(4f, cap = StrokeCap.Round))
            if (hood != null && hood.severity > 0) {
                val urgent = hood == StatusLevel.URGENT
                drawCircle(if (urgent) c.critSoft else c.warnSoft, 26f, Offset(150f, 66f))
                drawCircle(if (urgent) c.critLine else c.warnLine, 26f, Offset(150f, 66f), style = Stroke(2f))
                drawCircle(if (urgent) c.crit else c.warnFill, 11f, Offset(150f, 66f))
            }
            listOf(Offset(14f, 112f), Offset(256f, 112f), Offset(14f, 446f), Offset(256f, 446f)).forEach {
                drawRoundRect(c.l3, it, Size(30f, 82f), CornerRadius(10f))
                drawRoundRect(c.t3, it, Size(30f, 82f), CornerRadius(10f), style = Stroke(2f))
            }
        }
    }
}

private fun DrawScope.mirror(c: HaidaColors, center: Offset, degrees: Float) {
    rotate(degrees, pivot = center) {
        drawOval(c.l3, Offset(center.x - 11f, center.y - 7f), Size(22f, 14f))
        drawOval(c.t3, Offset(center.x - 11f, center.y - 7f), Size(22f, 14f), style = Stroke(2f))
    }
}

private class FlatShapes {
    private fun path(d: String) = PathParser().parsePathString(d).toPath()
    val body = path("M150 14C214 14 250 44 255 118L261 520C262 588 226 626 150 626C74 626 38 588 39 520L45 118C50 44 86 14 150 14Z")
    val hoodLine = path("M72 100C110 84 190 84 228 100")
    val windshield = path("M92 168C112 154 188 154 208 168L222 226C190 214 110 214 78 226Z")
    val rearGlass = path("M84 430C112 442 188 442 216 430L206 480C185 490 115 490 94 480Z")
    val headlights = path("M66 52c10-9 24-13 38-14M234 52c-10-9-24-13-38-14")
    val taillights = path("M62 608c12 8 26 11 40 12M238 608c-12 8-26 11-40 12")
}
