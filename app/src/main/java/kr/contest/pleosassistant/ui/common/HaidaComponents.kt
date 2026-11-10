package kr.contest.pleosassistant.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 프로토타입 공통 컴포넌트(.card · .btn · .chip · .st · .tabs · .hdr · .bar)를 옮겼다.
// 차 안 터치 기준: 버튼 80dp, 칩 · 탭 76dp 높이.

val CardShape = RoundedCornerShape(28.dp)
private val ButtonShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(50)

/** 흰 카드(.card). raised 는 그림자가 더 진하다(.card.raised), inset 은 카드 안의 회색 면(.card.inset) */
@Composable
fun HaidaCard(
    modifier: Modifier = Modifier,
    raised: Boolean = false,
    inset: Boolean = false,
    background: Brush? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Haida.colors
    val shadowColor = c.shadow.copy(alpha = if (c.isNight) 0.6f else 0.35f)
    val shadowed = if (inset) modifier else modifier.shadow(if (raised) 10.dp else 5.dp, CardShape, ambientColor = shadowColor, spotColor = shadowColor)
    Box(
        modifier = shadowed
            .clip(CardShape)
            .background(background ?: SolidColor(if (inset) c.l2 else c.l1))
            .border(1.dp, c.cardLine, CardShape),
        content = content,
    )
}

/** 화면 제목 줄(.hdr): 높이 76, 제목 34 · 보조 글 24 · 오른쪽 끝 버튼 */
@Composable
fun ScreenHeader(title: String, sub: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val c = Haida.colors
    Row(
        modifier = Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(title, style = HaidaType.h1, color = c.t1)
        sub?.let { Text(it, style = HaidaType.small, color = c.t3, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** 카드 안 작은 머리글(.eyebrow) */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Haida.colors.t3) {
    Text(text, style = HaidaType.eyebrow, color = color, modifier = modifier)
}

enum class ButtonKind { PRIMARY, DEFAULT, GHOST, DANGER }

/** 큰 버튼(.btn): 높이 80, 글자 28 */
@Composable
fun HaidaButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.DEFAULT,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = Haida.colors
    val (bg, fg) = when (kind) {
        ButtonKind.PRIMARY -> c.pri to c.onPri
        ButtonKind.DEFAULT -> c.l2 to c.t1
        ButtonKind.GHOST -> Color.Transparent to c.t1
        ButtonKind.DANGER -> c.l2 to c.crit
    }
    val bordered = kind != ButtonKind.PRIMARY
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = 176.dp)
            .height(80.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(ButtonShape)
            .background(bg)
            .then(if (bordered) Modifier.border(1.5.dp, c.line2, ButtonShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(32.dp)) }
        Text(label, style = HaidaType.bodyBold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 알약 모양 선택 칩(.chip). selected 면 강조색으로 채운다. */
@Composable
fun HaidaChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    small: Boolean = false,
    danger: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = 76.dp,
) {
    val c = Haida.colors
    val fg = when {
        selected -> c.onPri
        danger -> c.crit
        else -> c.t1
    }
    Row(
        modifier = modifier
            .height(height)
            .clip(PillShape)
            .background(if (selected) c.pri else c.l2)
            .then(if (selected) Modifier else Modifier.border(1.5.dp, c.line2, PillShape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 24.dp else 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(if (small) 28.dp else 30.dp)) }
        Text(label, style = if (small) HaidaType.eyebrow else HaidaType.bodySemi, color = fg, maxLines = 1)
    }
}

enum class PillTone { OK, NEUTRAL, WARN, CRIT, UNKNOWN }

/** 상태 표시 알약(.st): 정상 · 주의 · 즉시 조치 · 확인 불가 */
@Composable
fun StatusPill(text: String, tone: PillTone, modifier: Modifier = Modifier, icon: ImageVector? = tone.defaultIcon()) {
    val c = Haida.colors
    val (fg, bg, line) = when (tone) {
        PillTone.OK -> Triple(c.t2, Color.Transparent, Color.Transparent)
        PillTone.NEUTRAL -> Triple(c.t2, c.l2, c.line2)
        PillTone.WARN -> Triple(c.warn, c.warnSoft, c.warnLine)
        PillTone.CRIT -> Triple(c.crit, c.critSoft, c.critLine)
        PillTone.UNKNOWN -> Triple(c.unk, c.unkSoft, c.line2)
    }
    val borderModifier = when (tone) {
        PillTone.OK -> Modifier
        PillTone.UNKNOWN -> Modifier.dashedBorder(1.5.dp, line, 24.dp)
        else -> Modifier.border(1.5.dp, line, PillShape)
    }
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(PillShape)
            .background(bg)
            .then(borderModifier)
            .padding(start = if (tone == PillTone.OK) 2.dp else 18.dp, end = if (tone == PillTone.OK) 4.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(28.dp)) }
        Text(text, style = HaidaType.smallBold, color = fg, maxLines = 1)
    }
}

private fun PillTone.defaultIcon(): ImageVector? = when (this) {
    PillTone.OK -> HaidaIcons.Ok
    PillTone.NEUTRAL -> null
    PillTone.WARN -> HaidaIcons.Warn
    PillTone.CRIT -> HaidaIcons.Crit
    PillTone.UNKNOWN -> HaidaIcons.Unknown
}

enum class TileTone { NEUTRAL, ACCENT, WARN, CRIT }

/** 목록 앞 아이콘 칸(.ii): 56 × 56, 모서리 16 */
@Composable
fun IconTile(icon: ImageVector, tone: TileTone = TileTone.NEUTRAL, size: Dp = 56.dp, iconSize: Dp = 34.dp) {
    val c = Haida.colors
    val (bg, fg) = when (tone) {
        TileTone.NEUTRAL -> c.l2 to c.t2
        TileTone.ACCENT -> c.acSoft to c.ac
        TileTone.WARN -> c.warnSoft to c.warn
        TileTone.CRIT -> c.critSoft to c.crit
    }
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(size * 2 / 7)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(iconSize))
    }
}

/** 둥근 탭(.tabs). 고른 탭은 흰 바탕 */
@Composable
fun HaidaTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Haida.colors
    Row(
        modifier = modifier.clip(PillShape).background(c.l3).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val on = index == selected
            Box(
                modifier = Modifier
                    .height(66.dp)
                    .then(if (on) Modifier.shadow(3.dp, PillShape, ambientColor = c.shadow, spotColor = c.shadow) else Modifier)
                    .clip(PillShape)
                    .background(if (on) c.l1 else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = HaidaType.smallBold, color = if (on) c.t1 else c.t2)
            }
        }
    }
}

/** 잔량 막대(.bar): 높이 12 */
@Composable
fun LevelBar(value: Float, color: Color, modifier: Modifier = Modifier) {
    val c = Haida.colors
    Box(modifier = modifier.height(12.dp).clip(PillShape).background(c.l3)) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0.02f, 1f)).height(12.dp).clip(PillShape).background(color))
    }
}

/** 구분선(.sep) */
@Composable
fun Separator(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Haida.colors.line))
}

/** 점선 테두리(.st.unk · .seatlab.est). Compose border 에는 점선이 없어 직접 그린다. */
fun Modifier.dashedBorder(width: Dp, color: Color, radius: Dp, dash: Dp = 6.dp, gap: Dp = 5.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()))),
    )
}

/**
 * 비서 패널(.va-banner): 화면 아래에 떠서 비서가 할 말과 버튼을 보여 준다. 화면 전체를 막는 대화상자 대신 쓴다.
 * 예: 하차 알림 "트렁크에 둔 우산 챙기세요." [다 챙겼어요] [나중에]
 */
@Composable
fun AssistantPanel(
    tag: String,
    text: String,
    modifier: Modifier = Modifier,
    tagIcon: ImageVector? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Haida.colors
    val shadowColor = c.shadow.copy(alpha = if (c.isNight) 0.8f else 0.5f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .shadow(18.dp, CardShape, ambientColor = shadowColor, spotColor = shadowColor)
            .clip(CardShape)
            .background(c.l1)
            .border(1.dp, c.line2, CardShape)
            .padding(start = 28.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            modifier = Modifier.height(52.dp).clip(PillShape).background(c.acSoft).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tagIcon?.let { Icon(it, contentDescription = null, tint = c.ac, modifier = Modifier.size(28.dp)) }
            Text(tag, style = HaidaType.bodyBold.copy(fontSize = 26.sp), color = c.ac, maxLines = 1)
        }
        Text(
            text,
            style = HaidaType.title.copy(fontSize = 34.sp, lineHeight = 42.sp),
            color = c.t1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), content = actions)
    }
}

/** 한 줄 입력칸. 버튼과 같은 높이(80) · 모서리(20) */
@Composable
fun HaidaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    val c = Haida.colors
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(80.dp).onFocusChanged { focused = it.isFocused },
        singleLine = true,
        textStyle = HaidaType.body.copy(color = c.t1),
        cursorBrush = SolidColor(c.ac),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(ButtonShape)
                    .background(c.l2)
                    .border(if (focused) 2.dp else 1.5.dp, if (focused) c.ac else c.line2, ButtonShape)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = HaidaType.body, color = c.t3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                inner()
            }
        },
    )
}

/** 체크 칸(.chk .box): 48 × 48. 누르는 영역은 줄 전체로 잡는다. */
@Composable
fun CheckMark(checked: Boolean, modifier: Modifier = Modifier) {
    val c = Haida.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(shape)
            .background(if (checked) c.ac else c.l1)
            .border(2.5.dp, if (checked) c.ac else c.line2, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(HaidaIcons.Check, contentDescription = null, tint = c.onAc, modifier = Modifier.size(32.dp))
    }
}
