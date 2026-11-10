package kr.contest.pleosassistant.ui.memory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.contest.pleosassistant.memory.Seat
import kr.contest.pleosassistant.memory.SeatCard
import kr.contest.pleosassistant.memory.SeatTone
import kr.contest.pleosassistant.ui.common.Eyebrow
import kr.contest.pleosassistant.ui.common.Haida
import kr.contest.pleosassistant.ui.common.HaidaColors
import kr.contest.pleosassistant.ui.common.HaidaFont
import kr.contest.pleosassistant.ui.common.HaidaIcons
import kr.contest.pleosassistant.ui.common.HaidaType
import kr.contest.pleosassistant.ui.common.dashedBorder

// 팀 UI 프로토타입(수정본)의 좌석 현황(.pp-map): 위에서 본 차(carTop SVG 300 × 640 을 옮김)와 좌석 이름표 · 범례.
// 프로토타입과 같은 760 × 1110 칸에 그리고, 카드 크기에 맞춰 같은 비율로 줄이거나 키운다.

private enum class SeatDraw { PERSON, ASK, EMPTY }

private enum class TrunkDraw { NONE, ITEM, HIGHLIGHT }

/**
 * @param trunkItems 트렁크 · 프렁크에 둔 물건 이름. 있으면 트렁크 이름표를 띄운다.
 * @param highlightTrunk 하차 알림에서 트렁크를 확인하라고 강조
 * @param onSeatClick 이름을 건너뛴 좌석(손님)을 누르면 다시 묻는다
 */
/** 프로토타입 좌석 현황 칸 크기 */
const val SEAT_MAP_WIDTH = 760
const val SEAT_MAP_HEIGHT = 1110

@Composable
fun SeatMap(
    seats: List<SeatCard>,
    eyebrow: String,
    trunkItems: List<String>,
    highlightTrunk: Boolean,
    showLabels: Boolean,
    onSeatClick: (Seat) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Haida.colors
    val bySeat = seats.associateBy { it.seat }
    fun draw(seat: Seat) = when (bySeat[seat]?.tone) {
        SeatTone.KNOWN, SeatTone.UNNAMED -> SeatDraw.PERSON
        SeatTone.ASKING -> SeatDraw.ASK
        else -> SeatDraw.EMPTY
    }
    val trunk = when {
        trunkItems.isEmpty() -> TrunkDraw.NONE
        highlightTrunk -> TrunkDraw.HIGHLIGHT
        else -> TrunkDraw.ITEM
    }

    Box(modifier = modifier.size(SEAT_MAP_WIDTH.dp, SEAT_MAP_HEIGHT.dp)) {
        Eyebrow(eyebrow, Modifier.offset(32.dp, 28.dp))
        CarTopView(
            fl = SeatDraw.PERSON,
            fr = draw(Seat.PASSENGER),
            rl = draw(Seat.REAR_LEFT),
            rr = draw(Seat.REAR_RIGHT),
            trunk = trunk,
            modifier = Modifier.offset(208.dp, 150.dp).size(345.dp, 736.dp),
        )
        // 주행 중에는 도식만 남긴다(이름표 · 범례 숨김)
        if (!showLabels) return@Box
        // 이름표와 좌석을 잇는 점선(프로토타입 좌표 그대로)
        Canvas(Modifier.size(SEAT_MAP_WIDTH.dp, SEAT_MAP_HEIGHT.dp)) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
            listOf(
                Offset(236f, 371f) to Offset(292f, 450f),
                Offset(524f, 371f) to Offset(468f, 450f),
                Offset(236f, 651f) to Offset(292f, 590f),
                Offset(524f, 651f) to Offset(468f, 590f),
            ).forEach { (a, b) ->
                drawLine(c.t3, Offset(a.x.dp.toPx(), a.y.dp.toPx()), Offset(b.x.dp.toPx(), b.y.dp.toPx()), 2.dp.toPx(), pathEffect = dash)
            }
        }
        SeatLabel("운전석", "나", LabelStyle.NORMAL, HaidaIcons.Check, Modifier.offset(16.dp, 330.dp))
        PassengerLabel(bySeat[Seat.PASSENGER], "조수석", onSeatClick, Modifier.offset(524.dp, 330.dp))
        PassengerLabel(bySeat[Seat.REAR_LEFT], "뒤 왼쪽", onSeatClick, Modifier.offset(16.dp, 610.dp))
        PassengerLabel(bySeat[Seat.REAR_RIGHT], "뒤 오른쪽", onSeatClick, Modifier.offset(524.dp, 610.dp))
        if (trunkItems.isNotEmpty()) {
            SeatLabel(
                title = "트렁크",
                sub = trunkItems.joinToString(" · "),
                style = if (highlightTrunk) LabelStyle.HIGHLIGHT else LabelStyle.EMPTY,
                icon = HaidaIcons.Bag,
                modifier = Modifier.offset(260.dp, 894.dp),
                width = 240,
                centered = true,
            )
        }
        Legend(Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp))
    }
}

/** 범례(.pp-legend): 탑승 · 확인 필요 · 소지품 */
@Composable
private fun Legend(modifier: Modifier) {
    val c = Haida.colors
    val shape = RoundedCornerShape(7.dp)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        listOf(
            "탑승" to Modifier.background(c.ac, shape).border(2.dp, c.ac, shape),
            "확인 필요" to Modifier.background(c.acSoft, shape).dashedBorder(2.dp, c.ac, 7.dp, 4.dp, 3.dp),
            "소지품" to Modifier.background(c.l3, shape).dashedBorder(2.dp, c.t3, 7.dp, 4.dp, 3.dp),
        ).forEach { (label, swatch) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(22.dp, 26.dp).then(swatch))
                Text(label, style = HaidaType.eyebrow, color = c.t3)
            }
        }
    }
}

private enum class LabelStyle { NORMAL, ASK, EMPTY, HIGHLIGHT }

@Composable
private fun PassengerLabel(card: SeatCard?, title: String, onSeatClick: (Seat) -> Unit, modifier: Modifier) {
    card ?: return
    val (style, icon) = when (card.tone) {
        SeatTone.KNOWN, SeatTone.UNNAMED -> LabelStyle.NORMAL to HaidaIcons.Check
        SeatTone.ASKING -> LabelStyle.ASK to null
        SeatTone.EMPTY, SeatTone.OFF -> LabelStyle.EMPTY to null
    }
    // 이름을 건너뛴 사람(동승자)은 눌러서 다시 이름을 알려 줄 수 있다
    val click = if (card.tone == SeatTone.UNNAMED) Modifier.clickable(role = Role.Button) { onSeatClick(card.seat) } else Modifier
    SeatLabel(title, card.status, style, icon, modifier.then(click))
}

/** 좌석 이름표(.seatlab): 너비 188, 이름 26 · 상태 24 */
@Composable
private fun SeatLabel(
    title: String,
    sub: String,
    style: LabelStyle,
    icon: ImageVector?,
    modifier: Modifier,
    width: Int = 220,
    centered: Boolean = false,
) {
    val c = Haida.colors
    val shape = RoundedCornerShape(12.dp)
    val frame = when (style) {
        LabelStyle.NORMAL -> Modifier.border(1.dp, c.line2, shape)
        LabelStyle.ASK -> Modifier.border(1.dp, c.ac, shape)
        LabelStyle.EMPTY -> Modifier.dashedBorder(1.dp, c.line2, 12.dp)
        LabelStyle.HIGHLIGHT -> Modifier.border(2.dp, c.ac, shape)
    }
    Column(
        modifier = modifier
            .size(width.dp, 80.dp)
            .clip(shape)
            .background(if (style == LabelStyle.ASK) c.acSoft else c.l1)
            .then(frame)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            icon?.let { Icon(it, contentDescription = null, tint = c.ac, modifier = Modifier.size(24.dp)) }
            Text(
                title,
                style = HaidaType.title.copy(fontSize = 26.sp, lineHeight = 31.sp),
                color = if (style == LabelStyle.ASK) c.ac else c.t1,
                maxLines = 1,
            )
        }
        Text(sub, style = HaidaType.small.copy(lineHeight = 29.sp), color = c.t3, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 위에서 본 차. 좌표는 프로토타입 carTop SVG(300 × 640) 단위 */
@Composable
private fun CarTopView(fl: SeatDraw, fr: SeatDraw, rl: SeatDraw, rr: SeatDraw, trunk: TrunkDraw, modifier: Modifier) {
    val c = Haida.colors
    val shapes = remember { CarShapes() }
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val k = size.width / 300f
        scale(k, k, pivot = Offset.Zero) {
            // 바퀴
            listOf(Offset(20f, 116f), Offset(252f, 116f), Offset(20f, 450f), Offset(252f, 450f)).forEach {
                drawRoundRect(Color(0xFF20262C), it, Size(28f, 74f), CornerRadius(9f))
            }
            // 차체
            drawPath(shapes.body, Brush.horizontalGradient(listOf(c.l3, c.l1, c.l3), startX = 39f, endX = 261f))
            drawPath(shapes.body, c.line2, style = Stroke(2.5f))
            drawPath(shapes.hood, c.line2, style = Stroke(2.5f))
            drawPath(shapes.windshield, Brush.verticalGradient(listOf(c.t3.copy(alpha = 0.55f), c.t3.copy(alpha = 0.25f)), startY = 154f, endY = 226f))
            drawRoundRect(c.l1, Offset(82f, 230f), Size(136f, 196f), CornerRadius(30f))
            drawRoundRect(c.line2, Offset(82f, 230f), Size(136f, 196f), CornerRadius(30f), style = Stroke(2f))
            drawPath(shapes.rearGlass, Brush.verticalGradient(listOf(c.t3.copy(alpha = 0.55f), c.t3.copy(alpha = 0.25f)), startY = 430f, endY = 490f))
            mirror(c, Offset(35f, 204f), -20f)
            mirror(c, Offset(265f, 204f), 20f)
            drawPath(shapes.headlights, c.t3.copy(alpha = 0.7f), style = Stroke(5f, cap = StrokeCap.Round))
            drawPath(shapes.taillights, c.crit.copy(alpha = 0.55f), style = Stroke(5f, cap = StrokeCap.Round))
            if (trunk != TrunkDraw.NONE) {
                val fill = if (trunk == TrunkDraw.HIGHLIGHT) c.ac.copy(alpha = 0.26f) else c.l3.copy(alpha = 0.7f)
                drawRoundRect(fill, Offset(96f, 498f), Size(108f, 84f), CornerRadius(16f))
                drawRoundRect(c.line2, Offset(96f, 498f), Size(108f, 84f), CornerRadius(16f), style = Stroke(2f))
                translate(131f, 520f) {
                    scale(1.4f, 1.4f, pivot = Offset.Zero) {
                        drawPath(shapes.bag, c.t2, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
            seat(c, shapes, 70f, 242f, fl)
            seat(c, shapes, 176f, 242f, fr)
            seat(c, shapes, 70f, 340f, rl)
            seat(c, shapes, 176f, 340f, rr)
            // 누가 탔는지 묻는 좌석의 "?"
            val question = TextStyle(fontFamily = HaidaFont, fontWeight = FontWeight.Bold, fontSize = (36f / (density * fontScale)).sp, color = c.ac)
            listOf(Offset(176f, 242f) to fr, Offset(70f, 340f) to rl, Offset(176f, 340f) to rr)
                .filter { it.second == SeatDraw.ASK }
                .forEach { (at, _) ->
                    val layout = measurer.measure("?", question)
                    drawText(layout, topLeft = Offset(at.x + 27f - layout.size.width / 2f, at.y + 31f - layout.size.height / 2f))
                }
        }
    }
}

private fun DrawScope.mirror(c: HaidaColors, center: Offset, degrees: Float) {
    rotate(degrees, pivot = center) {
        drawOval(c.l3, Offset(center.x - 11f, center.y - 7f), Size(22f, 14f))
        drawOval(c.line2, Offset(center.x - 11f, center.y - 7f), Size(22f, 14f), style = Stroke(2f))
    }
}

private fun DrawScope.seat(c: HaidaColors, shapes: CarShapes, x: Float, y: Float, state: SeatDraw) {
    val fill = when (state) {
        SeatDraw.PERSON -> c.ac
        SeatDraw.ASK -> c.acSoft
        SeatDraw.EMPTY -> c.l3
    }
    val line = if (state == SeatDraw.ASK) c.ac else c.line2
    val dash = if (state == SeatDraw.ASK) PathEffect.dashPathEffect(floatArrayOf(5f, 4f)) else null
    drawRoundRect(fill, Offset(x, y), Size(54f, 62f), CornerRadius(14f))
    drawRoundRect(line, Offset(x, y), Size(54f, 62f), CornerRadius(14f), style = Stroke(2.5f, pathEffect = dash))
    drawRoundRect(fill, Offset(x + 9f, y - 5f), Size(36f, 10f), CornerRadius(5f))
    drawRoundRect(line, Offset(x + 9f, y - 5f), Size(36f, 10f), CornerRadius(5f), style = Stroke(2f, pathEffect = dash))
    if (state == SeatDraw.PERSON) {
        val white = Color.White.copy(alpha = 0.95f)
        drawCircle(white, 9f, Offset(x + 27f, y + 27f))
        translate(x, y) { drawPath(shapes.shoulders, white) }
    }
}

/** carTop SVG 의 path 들. 한 번만 읽어 둔다. */
private class CarShapes {
    private fun path(d: String) = PathParser().parsePathString(d).toPath()
    val body = path("M150 14C214 14 250 44 255 118L261 520C262 588 226 626 150 626C74 626 38 588 39 520L45 118C50 44 86 14 150 14Z")
    val hood = path("M72 100C110 84 190 84 228 100")
    val windshield = path("M92 168C112 154 188 154 208 168L222 226C190 214 110 214 78 226Z")
    val rearGlass = path("M84 430C112 442 188 442 216 430L206 480C185 490 115 490 94 480Z")
    val headlights = path("M66 52c10-9 24-13 38-14M234 52c-10-9-24-13-38-14")
    val taillights = path("M62 608c12 8 26 11 40 12M238 608c-12 8-26 11-40 12")
    val shoulders = path("M12 54c2-12 8-16 15-16s13 4 15 16Z")
    val bag = path("M6.9 8h10.2a2.4 2.4 0 0 1 2.4 2.4v7.2a2.4 2.4 0 0 1-2.4 2.4H6.9a2.4 2.4 0 0 1-2.4-2.4v-7.2A2.4 2.4 0 0 1 6.9 8zM9 8V6.5a3 3 0 0 1 6 0V8")
}
