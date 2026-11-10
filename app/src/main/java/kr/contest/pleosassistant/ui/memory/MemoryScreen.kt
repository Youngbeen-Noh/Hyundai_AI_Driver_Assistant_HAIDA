package kr.contest.pleosassistant.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirlineSeatReclineNormal
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.contest.pleosassistant.memory.BelongingRow
import kr.contest.pleosassistant.memory.MemoryMessages
import kr.contest.pleosassistant.memory.MemoryReport
import kr.contest.pleosassistant.memory.Question
import kr.contest.pleosassistant.memory.Seat
import kr.contest.pleosassistant.memory.SeatCard
import kr.contest.pleosassistant.memory.SeatTone
import kr.contest.pleosassistant.ui.common.BigButton
import kr.contest.pleosassistant.ui.common.CardShape
import kr.contest.pleosassistant.ui.common.IconTile
import kr.contest.pleosassistant.ui.common.Palette
import kr.contest.pleosassistant.ui.common.ScreenHeader
import kr.contest.pleosassistant.ui.common.SummaryBanner

// 임시 디자인. 팀 UI 템플릿을 받으면 이 파일만 바꾼다(MemoryReport · MemoryActions 는 그대로).

@Composable
fun MemoryScreen(report: MemoryReport, actions: MemoryActions, modifier: Modifier = Modifier) {
    var askForget by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.background)
            .padding(horizontal = 48.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        ScreenHeader(Icons.Filled.People, "동승자 · 소지품 기억", "차 안에만 저장하고 밖으로 보내지 않아요") {
            BigButton("다 잊어줘", onClick = { askForget = true }, color = Palette.urgent, filled = false, icon = Icons.Filled.DeleteSweep)
        }
        SummaryBanner(Icons.Filled.RecordVoiceOver, Palette.accent, report.summary)
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            PassengerPanel(report, actions, Modifier.weight(1f).fillMaxHeight())
            BelongingPanel(report, actions, Modifier.weight(1f).fillMaxHeight())
        }
    }

    if (askForget) {
        ConfirmDialog(
            title = "다 잊을까요?",
            text = "동승자 이름 · 탑승 기록 · 소지품을 모두 지워요. 되돌릴 수 없어요.",
            confirm = "다 잊어줘",
            confirmColor = Palette.urgent,
            onConfirm = {
                actions.forgetAll()
                askForget = false
            },
            onDismiss = { askForget = false },
        )
    }
}

/** 하차(P단) 알림. 어느 탭에 있든 앱 위에 뜬다. */
@Composable
fun ArrivalDialog(message: String, actions: MemoryActions) {
    ConfirmDialog(
        title = "도착했어요",
        text = message,
        confirm = "다 챙겼어요",
        dismiss = "나중에",
        icon = Icons.Filled.Luggage,
        onConfirm = { actions.closeArrival(collected = true) },
        onDismiss = { actions.closeArrival(collected = false) },
    )
}

// ---- 동승자 ----

@Composable
private fun PassengerPanel(report: MemoryReport, actions: MemoryActions, modifier: Modifier) {
    Panel(modifier, scrollable = true) {
        PanelTitle(Icons.Filled.AirlineSeatReclineNormal, "동승자", "좌석 센서로 누가 탔는지 알아요")
        SeatMap(report.seats, onAskAgain = actions::askAgain)
        report.question?.let { QuestionCard(it, actions) }
    }
}

/** 위에서 본 좌석 배치. 한국 차라 운전석이 왼쪽 앞 */
@Composable
private fun SeatMap(seats: List<SeatCard>, onAskAgain: (Seat) -> Unit) {
    val bySeat = seats.associateBy { it.seat }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SeatTile("운전석", "나", Palette.ok, Icons.Filled.Person, Modifier.weight(1f))
            bySeat[Seat.PASSENGER]?.let { PassengerSeatTile(it, onAskAgain, Modifier.weight(1f)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            bySeat[Seat.REAR_LEFT]?.let { PassengerSeatTile(it, onAskAgain, Modifier.weight(1f)) }
            bySeat[Seat.REAR_RIGHT]?.let { PassengerSeatTile(it, onAskAgain, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun PassengerSeatTile(card: SeatCard, onAskAgain: (Seat) -> Unit, modifier: Modifier) {
    val (color, icon) = when (card.tone) {
        SeatTone.KNOWN -> Palette.accent to Icons.Filled.Person
        SeatTone.ASKING -> Palette.caution to Icons.Filled.Person
        SeatTone.UNNAMED -> Palette.accent to Icons.Filled.Person
        SeatTone.EMPTY -> Palette.neutral to Icons.Filled.PersonOutline
        SeatTone.OFF -> Palette.neutral to Icons.Filled.PersonOutline
    }
    val hint = if (card.tone == SeatTone.UNNAMED) "눌러서 이름 알려 주기" else null
    SeatTile(
        label = card.seat.label,
        status = card.status,
        color = color,
        icon = icon,
        modifier = if (card.tone == SeatTone.UNNAMED) modifier.clickable { onAskAgain(card.seat) } else modifier,
        hint = hint,
        dim = card.tone == SeatTone.OFF,
    )
}

@Composable
private fun SeatTile(
    label: String,
    status: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier,
    hint: String? = null,
    dim: Boolean = false,
) {
    val shape = RoundedCornerShape(24.dp)
    val active = color != Palette.neutral
    Row(
        modifier = modifier
            .height(132.dp)
            .clip(shape)
            .background(if (active) color.copy(alpha = 0.08f) else Palette.background)
            .border(if (active) 2.dp else 1.dp, if (active) color.copy(alpha = 0.6f) else Palette.cardBorder, shape)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        IconTile(icon, color, tileSize = 68, iconSize = 40)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = Palette.textMuted, fontSize = 20.sp)
            Text(
                status,
                color = if (dim) Palette.textMuted else Palette.text,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            hint?.let { Text(it, color = color, fontSize = 18.sp) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionCard(question: Question, actions: MemoryActions) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.caution.copy(alpha = 0.08f))
            .border(2.dp, Palette.caution.copy(alpha = 0.6f), shape)
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(question.seat.label, color = Palette.caution, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(question.text, color = Palette.text, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 42.sp)
        when (question) {
            is Question.Confirm -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton("맞아요", onClick = { actions.confirmPassenger(question.seat, isRight = true) })
                BigButton("아니에요", onClick = { actions.confirmPassenger(question.seat, isRight = false) }, filled = false)
            }

            is Question.AskName -> {
                if (question.suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        question.suggestions.forEach { name ->
                            BigButton("${name}님", onClick = { actions.namePassenger(question.seat, name) }, filled = false)
                        }
                    }
                }
                var name by remember(question) { mutableStateOf("") }
                val save = { if (name.isNotBlank()) actions.namePassenger(question.seat, name) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InputField(name, { name = it }, "이름", Modifier.weight(1f), onDone = save)
                    BigButton("기억하기", onClick = save, enabled = name.isNotBlank())
                }
                TextButton(onClick = { actions.skipPassenger(question.seat) }) {
                    Text("건너뛰기", color = Palette.textMuted, fontSize = 22.sp)
                }
            }
        }
    }
}

// ---- 소지품 ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BelongingPanel(report: MemoryReport, actions: MemoryActions, modifier: Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val add = { if (text.isNotBlank() && actions.addBelonging(text)) text = "" }

    Panel(modifier) {
        PanelTitle(Icons.Filled.Luggage, "소지품", "말하듯 적으면 물건 · 위치 · 챙길 날을 기억해요")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InputField(text, { text = it }, "예: ${MemoryMessages.EXAMPLES.first()}", Modifier.weight(1f), onDone = add)
            BigButton("기억하기", onClick = add, enabled = text.isNotBlank())
        }
        report.inputError?.let { Text(it, color = Palette.caution, fontSize = 22.sp) }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("예시", color = Palette.textMuted, fontSize = 20.sp, modifier = Modifier.padding(vertical = 10.dp))
            MemoryMessages.EXAMPLES.forEach { example ->
                Text(
                    "“$example”",
                    color = Palette.accent,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Palette.accent.copy(alpha = 0.1f))
                        .clickable { text = example }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }

        if (report.belongings.isEmpty()) {
            Text("기억한 물건이 없어요.", color = Palette.textMuted, fontSize = 24.sp, modifier = Modifier.padding(top = 8.dp))
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(report.belongings, key = { it.id }) { row -> BelongingItem(row, onCollect = { actions.collect(row.id) }) }
            }
        }
        if (!report.parkAlertAvailable) {
            Text("이 차량은 P단 신호를 읽을 수 없어 하차 알림을 못 해요.", color = Palette.textMuted, fontSize = 20.sp)
        }
    }
}

@Composable
private fun BelongingItem(row: BelongingRow, onCollect: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val color = if (row.remindNow) Palette.accent else Palette.neutral
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.background)
            .border(1.dp, Palette.cardBorder, shape)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        IconTile(placeIcon(row.place), color, tileSize = 64, iconSize = 36)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.item, color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val where = row.place ?: "위치 모름"
            val due = row.dueLabel?.let { " · $it 챙기기" }.orEmpty()
            Text(where + due, color = Palette.textMuted, fontSize = 20.sp)
        }
        BigButton("챙겼어요", onClick = onCollect, filled = false)
    }
}

private fun placeIcon(place: String?): ImageVector = when (place) {
    "트렁크", "프렁크" -> Icons.Filled.Luggage
    "뒷자리", "조수석", "운전석" -> Icons.Filled.AirlineSeatReclineNormal
    "글로브박스", "콘솔" -> Icons.Filled.Inventory2
    "컵홀더" -> Icons.Filled.LocalCafe
    else -> Icons.Filled.Backpack
}

// ---- 공통 조각 ----

@Composable
private fun Panel(modifier: Modifier, scrollable: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val framed = modifier
        .clip(CardShape)
        .background(Palette.card)
        .border(1.dp, Palette.cardBorder, CardShape)
    Column(
        modifier = (if (scrollable) framed.verticalScroll(rememberScrollState()) else framed).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        content = content,
    )
}

@Composable
private fun PanelTitle(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconTile(icon, Palette.accent, tileSize = 56, iconSize = 32)
        Column {
            Text(title, color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Palette.textMuted, fontSize = 20.sp)
        }
    }
}

@Composable
private fun InputField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier, onDone: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text(placeholder, fontSize = 24.sp, color = Palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        textStyle = TextStyle(fontSize = 26.sp, color = Palette.text),
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.accent,
            unfocusedBorderColor = Palette.cardBorder,
            cursorColor = Palette.accent,
            focusedTextColor = Palette.text,
            unfocusedTextColor = Palette.text,
        ),
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String = "취소",
    confirmColor: Color = Palette.accent,
    icon: ImageVector? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.card,
        icon = icon?.let { { IconTile(it, Palette.accent, tileSize = 72, iconSize = 42) } },
        title = { Text(title, color = Palette.text, fontSize = 34.sp, fontWeight = FontWeight.Bold) },
        text = { Text(text, color = Palette.text, fontSize = 28.sp, lineHeight = 38.sp) },
        confirmButton = { BigButton(confirm, onClick = onConfirm, color = confirmColor) },
        dismissButton = { BigButton(dismiss, onClick = onDismiss, color = Palette.textMuted, filled = false) },
    )
}
