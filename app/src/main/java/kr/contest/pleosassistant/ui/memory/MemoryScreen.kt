package kr.contest.pleosassistant.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.contest.pleosassistant.memory.BelongingRow
import kr.contest.pleosassistant.memory.MemoryMessages
import kr.contest.pleosassistant.memory.MemoryReport
import kr.contest.pleosassistant.memory.PersonRow
import kr.contest.pleosassistant.memory.Question
import kr.contest.pleosassistant.ui.common.ButtonKind
import kr.contest.pleosassistant.ui.common.CardShape
import kr.contest.pleosassistant.ui.common.CheckMark
import kr.contest.pleosassistant.ui.common.Eyebrow
import kr.contest.pleosassistant.ui.common.Haida
import kr.contest.pleosassistant.ui.common.HaidaButton
import kr.contest.pleosassistant.ui.common.HaidaCard
import kr.contest.pleosassistant.ui.common.HaidaChip
import kr.contest.pleosassistant.ui.common.HaidaIcons
import kr.contest.pleosassistant.ui.common.HaidaTabs
import kr.contest.pleosassistant.ui.common.HaidaTextField
import kr.contest.pleosassistant.ui.common.HaidaType
import kr.contest.pleosassistant.ui.common.IconTile
import kr.contest.pleosassistant.ui.common.ScreenHeader
import kr.contest.pleosassistant.ui.common.Separator
import kr.contest.pleosassistant.ui.common.TileTone

// 팀 UI 프로토타입의 '동승자·소지품' 화면: 왼쪽 좌석 현황, 오른쪽 질문 카드 + 소지품 카드. '동행 관리' 탭에서 기억을 지운다.
// 주행 중(P 가 아님)에는 탭 · 입력 · 고르기를 숨기고 읽기와 예/아니오만 남긴다.

private val TRUNK_PLACES = setOf("트렁크", "프렁크")

@Composable
fun MemoryScreen(report: MemoryReport, driving: Boolean, actions: MemoryActions, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val managing = tab == 1 && !driving
    val arriving = report.arrivalItems.isNotEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader("동승자·소지품") {
            if (!driving) HaidaTabs(listOf("탑승 중", "동행 관리"), tab, onSelect = { tab = it })
        }
        Row(
            modifier = Modifier.weight(1f).padding(start = 40.dp, end = 40.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            val c = Haida.colors
            HaidaCard(
                modifier = Modifier.weight(600f).fillMaxHeight(),
                raised = true,
                background = Brush.verticalGradient(listOf(c.l1, c.l2)),
            ) {
                // 프로토타입 크기(600 × 556)로 그리고 카드가 크면 같은 비율로 키운다
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val fit = minOf(maxWidth / 600.dp, maxHeight / 556.dp).coerceIn(0.5f, 1.5f)
                    SeatMap(
                        seats = report.seats,
                        eyebrow = if (arriving) "도착 · 뒷좌석과 트렁크를 확인해요" else "좌석 현황",
                        trunkItems = report.belongings.filter { it.place in TRUNK_PLACES }.map { it.item },
                        highlightTrunk = report.arrivalItems.any { it.place in TRUNK_PLACES },
                        onSeatClick = actions::askAgain,
                        modifier = Modifier.align(Alignment.Center).graphicsLayer {
                            scaleX = fit
                            scaleY = fit
                        },
                    )
                }
            }
            Column(modifier = Modifier.weight(608f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    managing -> ManagePanel(report, actions)
                    arriving -> {
                        TopCard { ArrivalHeadline(report.arrivalItems.size) }
                        ArrivalChecklist(report.arrivalItems, actions, Modifier.weight(1f))
                    }
                    else -> {
                        TopCard {
                            when (val q = report.question) {
                                is Question.Confirm -> ConfirmQuestion(q, actions)
                                is Question.AskName -> if (driving) AskLater(q) else AskName(q, actions)
                                null -> Headline("지금", report.summary)
                            }
                        }
                        BelongingCard(report, driving, actions, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ---- 위 카드: 질문 · 한 문장 ----

@Composable
private fun TopCard(content: @Composable () -> Unit) {
    HaidaCard(modifier = Modifier.fillMaxWidth().heightIn(min = 232.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun Headline(eyebrow: String, text: String, note: String? = null) {
    val c = Haida.colors
    Eyebrow(eyebrow)
    Text(text, style = HaidaType.sentence, color = c.t1)
    note?.let { Text(it, style = HaidaType.small, color = c.t3) }
}

@Composable
private fun ArrivalHeadline(left: Int) {
    Headline("도착했어요", MemoryMessages.arrivalLeft(left), "트렁크와 뒷좌석을 확인해 주세요")
}

/** "오늘도 민수님이죠?" [맞아요] [아니에요] — 예/아니오라 주행 중에도 보인다 */
@Composable
private fun ConfirmQuestion(q: Question.Confirm, actions: MemoryActions) {
    Eyebrow(q.seat.label)
    Text(q.text, style = HaidaType.title, color = Haida.colors.t1)
    Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        HaidaButton("맞아요", onClick = { actions.confirmPassenger(q.seat, isRight = true) }, kind = ButtonKind.PRIMARY, modifier = Modifier.weight(1f))
        HaidaButton("아니에요", onClick = { actions.confirmPassenger(q.seat, isRight = false) }, modifier = Modifier.weight(1f))
    }
}

/** 주행 중에는 고르기 · 입력을 숨기고 정차 후로 미룬다 */
@Composable
private fun AskLater(q: Question.AskName) {
    Headline(q.seat.label, "누가 타셨어요?", "정차하면 눌러서 알려 주세요")
}

/** "조수석에 누가 타셨어요?" 전에 탔던 사람 · 손님(이번엔 기억 안 함) · 새 사람(이름 입력) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskName(q: Question.AskName, actions: MemoryActions) {
    val c = Haida.colors
    var typing by remember(q.seat) { mutableStateOf(false) }
    var name by remember(q.seat) { mutableStateOf("") }
    val save = { if (name.isNotBlank()) actions.namePassenger(q.seat, name) }

    Eyebrow(q.text)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        q.suggestions.forEach { suggestion ->
            HaidaChip(suggestion, onClick = { actions.namePassenger(q.seat, suggestion) })
        }
        HaidaChip("손님", onClick = { actions.skipPassenger(q.seat) })
        HaidaChip("새 사람", onClick = { typing = !typing }, selected = typing, icon = HaidaIcons.Plus)
    }
    if (typing) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HaidaTextField(name, { name = it }, "이름", Modifier.weight(1f), onDone = save)
            HaidaButton("기억하기", onClick = save, kind = ButtonKind.PRIMARY, enabled = name.isNotBlank())
        }
    }
    Text("눌러서 알려 주세요. 처음 한 번만 물어봐요.", style = HaidaType.small, color = c.t3)
}

// ---- 아래 카드: 소지품 ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BelongingCard(report: MemoryReport, driving: Boolean, actions: MemoryActions, modifier: Modifier) {
    val c = Haida.colors
    var text by rememberSaveable { mutableStateOf("") }
    val add = { if (text.isNotBlank() && actions.addBelonging(text)) text = "" }

    HaidaCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Eyebrow("기억 중인 소지품")
            if (report.belongings.isEmpty()) {
                Text("기억한 물건이 없어요.", style = HaidaType.body, color = c.t3, modifier = Modifier.weight(1f).padding(top = 8.dp))
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(report.belongings, key = { _, row -> row.id }) { index, row ->
                        if (index > 0) Separator()
                        BelongingLine(row, onCheck = { actions.collect(row.id) })
                    }
                }
            }
            if (driving) {
                Text("정차하면 물건을 더 기억할 수 있어요.", style = HaidaType.small, color = c.t3)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HaidaTextField(text, { text = it }, "예: ${MemoryMessages.EXAMPLES.first()}", Modifier.weight(1f), onDone = add)
                    HaidaButton("기억하기", onClick = add, kind = ButtonKind.PRIMARY, enabled = text.isNotBlank())
                }
                report.inputError?.let { Text(it, style = HaidaType.small, color = c.warn) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MemoryMessages.EXAMPLES.forEach { example ->
                        HaidaChip("“$example”", onClick = { text = example }, small = true, height = 64.dp)
                    }
                }
            }
            if (!report.parkAlertAvailable) {
                Text("이 차는 P단 신호를 읽을 수 없어 하차 알림을 못 해요.", style = HaidaType.small, color = c.t3)
            }
        }
    }
}

/** 소지품 한 줄(.chk): 줄 전체를 누르면 챙긴 것으로 지운다 */
@Composable
private fun BelongingLine(row: BelongingRow, onCheck: () -> Unit) {
    val c = Haida.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClickLabel = "챙겼어요", onClick = onCheck)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        IconTile(itemIcon(row.item), if (row.remindNow) TileTone.ACCENT else TileTone.NEUTRAL)
        Column(modifier = Modifier.weight(1f)) {
            Text(row.item, style = HaidaType.title.copy(fontSize = 30.sp, lineHeight = 37.sp), color = c.t1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.note, style = HaidaType.small, color = c.t3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CheckMark(checked = false)
    }
}

private fun itemIcon(item: String): ImageVector = when {
    "우산" in item -> HaidaIcons.Umbrella
    "책" in item -> HaidaIcons.Book
    else -> HaidaIcons.Bag
}

/** 도착 체크 목록: 하나씩 누르거나 [다 챙겼어요] · [나중에 알려줘] */
@Composable
private fun ArrivalChecklist(items: List<BelongingRow>, actions: MemoryActions, modifier: Modifier) {
    HaidaCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 24.dp)) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(items, key = { _, row -> row.id }) { index, row ->
                    if (index > 0) Separator()
                    BelongingLine(row, onCheck = { actions.collect(row.id) })
                }
            }
            Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HaidaButton("다 챙겼어요", onClick = { actions.closeArrival(collected = true) }, kind = ButtonKind.PRIMARY, modifier = Modifier.weight(1f))
                HaidaButton("나중에 알려줘", onClick = { actions.closeArrival(collected = false) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ---- 동행 관리 ----

@Composable
private fun ColumnScope.ManagePanel(report: MemoryReport, actions: MemoryActions) {
    val c = Haida.colors
    var confirming by remember { mutableStateOf(false) }
    val nothing = report.people.isEmpty() && report.belongings.isEmpty()

    if (confirming) {
        HaidaCard(modifier = Modifier.fillMaxSize(), inset = true) {
            Column(modifier = Modifier.padding(40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Eyebrow("동행 관리")
                Text(MemoryMessages.forgetQuestion(report.people.size, report.belongings.size), style = HaidaType.sentence, color = c.t1)
                Text("이름 · 탑승 기록 · 소지품을 모두 지워요. 되돌릴 수 없어요.", style = HaidaType.body, color = c.t2)
                Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    HaidaButton(
                        "지우기",
                        onClick = {
                            actions.forgetAll()
                            confirming = false
                        },
                        kind = ButtonKind.DANGER,
                        icon = HaidaIcons.Close,
                        modifier = Modifier.weight(1f),
                    )
                    HaidaButton("취소", onClick = { confirming = false }, kind = ButtonKind.PRIMARY, modifier = Modifier.weight(1f))
                }
            }
        }
        return
    }

    Row(modifier = Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow("동행 관리 · 정차 중에만 수정")
        Spacer(Modifier.weight(1f))
        if (!nothing) {
            HaidaChip("기록 지우기", onClick = { confirming = true }, small = true, danger = true, icon = HaidaIcons.Close, height = 64.dp)
        }
    }
    if (report.people.isEmpty()) {
        HaidaCard(modifier = Modifier.fillMaxWidth().weight(1f), inset = true) {
            Text(
                "아직 기억한 동행이 없어요.\n누가 탔는지 알려 주면 여기에 모여요.",
                style = HaidaType.body,
                color = c.t2,
                modifier = Modifier.padding(32.dp),
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(report.people, key = { it.name }) { PersonCard(it) }
        }
    }
    StorageCard(report.belongings.size)
}

/** 동행 한 명(.pcard): 머리글자 동그라미 · 이름 · 자주 탄 때 */
@Composable
private fun PersonCard(person: PersonRow) {
    val c = Haida.colors
    val shape = CardShape
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .shadow(5.dp, shape, ambientColor = c.shadow.copy(alpha = 0.35f), spotColor = c.shadow.copy(alpha = 0.35f))
            .clip(shape)
            .background(c.l1)
            .border(1.5.dp, c.line, shape)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(c.acSoft), contentAlignment = Alignment.Center) {
            Text(person.name.take(1), style = HaidaType.bodyBold, color = c.ac)
        }
        Text(person.name, style = HaidaType.title.copy(fontSize = 30.sp, lineHeight = 37.sp), color = c.t1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(person.detail, style = HaidaType.small, color = c.t3, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** 저장 위치 안내: 기억은 이 차 안에만 둔다 */
@Composable
private fun StorageCard(itemCount: Int) {
    val c = Haida.colors
    HaidaCard(modifier = Modifier.fillMaxWidth(), inset = true) {
        Column(modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp)) {
            StorageRow("저장 위치", "이 차량 안 · 밖으로 보내지 않아요") {
                Row(
                    modifier = Modifier.height(44.dp).clip(RoundedCornerShape(50)).background(c.l3).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(HaidaIcons.Lock, contentDescription = null, tint = c.t2, modifier = Modifier.size(24.dp))
                    Text("로컬", style = HaidaType.smallBold, color = c.t2)
                }
            }
            Separator()
            StorageRow("소지품", "${itemCount}개 기억 중") {}
        }
    }
}

@Composable
private fun StorageRow(title: String, sub: String, trailing: @Composable () -> Unit) {
    val c = Haida.colors
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = HaidaType.bodyBold, color = c.t1)
            Text(sub, style = HaidaType.small, color = c.t3)
        }
        trailing()
    }
}
