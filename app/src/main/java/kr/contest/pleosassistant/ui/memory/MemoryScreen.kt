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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.contest.pleosassistant.memory.BelongingRow
import kr.contest.pleosassistant.memory.MemoryMessages
import kr.contest.pleosassistant.memory.MemoryReport
import kr.contest.pleosassistant.memory.PersonRow
import kr.contest.pleosassistant.memory.Question
import kr.contest.pleosassistant.ui.common.ButtonKind
import kr.contest.pleosassistant.ui.common.ButtonShape
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
import kr.contest.pleosassistant.ui.common.HeaderSub
import kr.contest.pleosassistant.ui.common.IconTile
import kr.contest.pleosassistant.ui.common.PillShape
import kr.contest.pleosassistant.ui.common.PillTone
import kr.contest.pleosassistant.ui.common.ScreenHeader
import kr.contest.pleosassistant.ui.common.Separator
import kr.contest.pleosassistant.ui.common.StatusPill
import kr.contest.pleosassistant.ui.common.TileTone

// 팀 UI 프로토타입(수정본)의 '동승자·소지품' 화면. 본문 1474 × 1110 기준 왼쪽 좌석 현황 760, 오른쪽 열 690.
// - 오른쪽 위 카드(남는 높이): 지금 할 말 — 누가 탔는지 묻기 · 확인 · 요약 · 도착 진행
// - 오른쪽 아래 카드(내용 높이): 기억 중인 소지품 · 도착 체크 목록
// - '기억 관리' 탭: 동행 목록과 기록 지우기
// 주행 중(P 가 아님)에는 탭 · 입력 · 고르기 · 소지품 카드 · 좌석 이름표를 숨기고 한 문장과 예/아니오만 남긴다.

private val TRUNK_PLACES = setOf("트렁크", "프렁크")

@Composable
fun MemoryScreen(report: MemoryReport, driving: Boolean, actions: MemoryActions, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val arriving = report.arrivalItems.isNotEmpty()
    val managing = tab == 1 && !driving && !arriving

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            "동승자·소지품",
            sub = {
                if (arriving) {
                    StatusPill("도착", PillTone.OK, icon = HaidaIcons.Pin)
                    HeaderSub("챙길 것을 확인해요")
                }
            },
            trailing = {
                if (!driving) HaidaTabs(listOf("이번 탑승", "기억 관리"), if (arriving) 0 else tab, onSelect = { tab = it })
            },
        )
        Row(
            modifier = Modifier.weight(1f).padding(start = 40.dp, end = 40.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            HaidaCard(modifier = Modifier.weight(760f).fillMaxHeight()) {
                // 프로토타입 크기(760 × 1110)로 그리고 카드 크기에 맞춰 같은 비율로 줄이거나 키운다
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val fit = minOf(maxWidth / SEAT_MAP_WIDTH.dp, maxHeight / SEAT_MAP_HEIGHT.dp)
                    SeatMap(
                        seats = report.seats,
                        eyebrow = if (arriving) "도착 · 트렁크와 뒷좌석을 확인해요" else "좌석 현황",
                        trunkItems = report.belongings.filter { it.place in TRUNK_PLACES }.map { it.item },
                        highlightTrunk = report.arrivalItems.any { it.place in TRUNK_PLACES },
                        showLabels = !driving,
                        onSeatClick = actions::askAgain,
                        modifier = Modifier.align(Alignment.Center).graphicsLayer {
                            scaleX = fit
                            scaleY = fit
                        },
                    )
                }
            }
            Column(modifier = Modifier.weight(690f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                when {
                    managing -> ManagePanel(report, actions)
                    arriving -> {
                        NowCard { ArrivalProgress(report) }
                        ArrivalChecklist(report, actions)
                    }
                    else -> {
                        NowCard {
                            when (val q = report.question) {
                                is Question.Confirm -> ConfirmQuestion(q, actions)
                                is Question.AskName -> if (driving) AskLater(q) else AskName(q, report.people, actions)
                                null -> Headline("지금", report.summary)
                            }
                        }
                        if (!driving) BelongingCard(report, actions)
                    }
                }
            }
        }
    }
}

// ---- 위 카드: 지금 할 말 ----

/** 남는 높이를 갖고 내용은 세로 가운데(#pp-mem) */
@Composable
private fun ColumnScope.NowCard(content: @Composable ColumnScope.() -> Unit) {
    HaidaCard(modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 400.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

@Composable
private fun Headline(eyebrow: String, text: String, note: String? = null) {
    val c = Haida.colors
    Eyebrow(eyebrow)
    Text(text, style = HaidaType.hero, color = c.t1, modifier = Modifier.padding(top = 16.dp))
    note?.let { Text(it, style = HaidaType.small, color = c.t3, modifier = Modifier.padding(top = 16.dp)) }
}

/** "오늘도 민수님이죠?" [맞아요] [아니에요] — 예/아니오라 주행 중에도 보인다 */
@Composable
private fun ConfirmQuestion(q: Question.Confirm, actions: MemoryActions) {
    Headline(q.seat.label, q.text)
    Row(modifier = Modifier.padding(top = 48.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        HaidaButton("맞아요", onClick = { actions.confirmPassenger(q.seat, isRight = true) }, kind = ButtonKind.PRIMARY, height = 96.dp, modifier = Modifier.weight(1f))
        HaidaButton("아니에요", onClick = { actions.confirmPassenger(q.seat, isRight = false) }, height = 96.dp, modifier = Modifier.weight(1f))
    }
}

/** 주행 중에는 고르기 · 입력을 숨기고 정차 후로 미룬다 */
@Composable
private fun AskLater(q: Question.AskName) {
    Headline(q.seat.label, "누가 타셨어요?", "정차하면 눌러서 고를 수 있어요")
}

/** "누가 타셨어요?" 큰 선택 카드 2열(.pp-pick): 전에 탔던 사람 · 손님(기억 안 함) · 새 사람(이름 입력) */
@Composable
private fun AskName(q: Question.AskName, people: List<PersonRow>, actions: MemoryActions) {
    val c = Haida.colors
    var typing by remember(q.seat) { mutableStateOf(false) }
    var name by remember(q.seat) { mutableStateOf("") }
    val save = { if (name.isNotBlank()) actions.namePassenger(q.seat, name) }
    val details = people.associate { it.name to it.detail }

    Headline(q.seat.label, "누가 타셨어요?")
    val picks = q.suggestions.map { suggestion ->
        Pick(suggestion, details[suggestion].orEmpty(), avatar = suggestion.take(1)) { actions.namePassenger(q.seat, suggestion) }
    } + listOf(
        Pick("손님", "기억하지 않아요", icon = HaidaIcons.People) { actions.skipPassenger(q.seat) },
        Pick("새 사람", "직접 입력", icon = HaidaIcons.Plus, selected = typing) { typing = !typing },
    )
    Column(modifier = Modifier.padding(top = 32.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        picks.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { PickCard(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    if (typing) {
        Row(modifier = Modifier.padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HaidaTextField(name, { name = it }, "이름", Modifier.weight(1f), onDone = save)
            HaidaButton("기억하기", onClick = save, kind = ButtonKind.PRIMARY, enabled = name.isNotBlank())
        }
    }
    Text("눌러서 알려 주세요. 처음 한 번만 물어봐요.", style = HaidaType.small, color = c.t3)
}

private class Pick(
    val title: String,
    val sub: String,
    val avatar: String? = null,
    val icon: ImageVector? = null,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun PickCard(pick: Pick, modifier: Modifier) {
    val c = Haida.colors
    Row(
        modifier = modifier
            .height(148.dp)
            .clip(ButtonShape)
            .background(if (pick.selected) c.acSoft else c.l2)
            .border(if (pick.selected) 2.dp else 1.dp, if (pick.selected) c.ac else c.line2, ButtonShape)
            .clickable(role = Role.Button, onClick = pick.onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(if (pick.avatar != null) c.acSoft else c.l3),
            contentAlignment = Alignment.Center,
        ) {
            pick.avatar?.let { Text(it, style = HaidaType.title, color = c.ac) }
            pick.icon?.let { Icon(it, contentDescription = null, tint = c.t2, modifier = Modifier.size(36.dp)) }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(pick.title, style = HaidaType.title.copy(fontWeight = HaidaType.bodyBold.fontWeight), color = c.t1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (pick.sub.isNotEmpty()) {
                Text(pick.sub, style = HaidaType.small, color = c.t3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** 도착: 남은 개수 · 진행 막대(.pp-prog) · n / N 챙김 */
@Composable
private fun ArrivalProgress(report: MemoryReport) {
    val c = Haida.colors
    val total = report.arrivalItems.size
    val done = report.arrivalItems.count { it.id in report.arrivalDone }
    Headline("도착했어요", MemoryMessages.arrivalLeft(total - done), "트렁크와 뒷좌석을 확인해 주세요")
    Row(modifier = Modifier.padding(top = 48.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        report.arrivalItems.forEach { row ->
            Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(if (row.id in report.arrivalDone) c.ac else c.l3))
        }
    }
    Text(MemoryMessages.arrivalProgress(done, total), style = HaidaType.bodyBold, color = c.t2, modifier = Modifier.padding(top = 14.dp))
}

// ---- 아래 카드: 소지품 ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BelongingCard(report: MemoryReport, actions: MemoryActions) {
    val c = Haida.colors
    var text by rememberSaveable { mutableStateOf("") }
    val add = { if (text.isNotBlank() && actions.addBelonging(text)) text = "" }

    HaidaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 40.dp, vertical = 36.dp)) {
            Eyebrow("기억 중인 소지품")
            if (report.belongings.isEmpty()) {
                Text("기억한 물건이 없어요.", style = HaidaType.body, color = c.t3, modifier = Modifier.padding(vertical = 20.dp))
            } else {
                // 세 줄까지 보이고 나머지는 밀어서 본다
                LazyColumn(modifier = Modifier.padding(top = 8.dp).heightIn(max = 372.dp)) {
                    itemsIndexed(report.belongings, key = { _, row -> row.id }) { index, row ->
                        if (index > 0) Separator()
                        BelongingLine(row)
                    }
                }
            }
            // 음성 등록 전까지는 글로 적는다
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HaidaTextField(text, { text = it }, "예: ${MemoryMessages.EXAMPLES.first()}", Modifier.weight(1f), onDone = add)
                HaidaButton("기억하기", onClick = add, kind = ButtonKind.PRIMARY, enabled = text.isNotBlank())
            }
            report.inputError?.let { Text(it, style = HaidaType.small, color = c.warn, modifier = Modifier.padding(top = 12.dp)) }
            FlowRow(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MemoryMessages.EXAMPLES.forEach { example ->
                    HaidaChip("“$example”", onClick = { text = example }, small = true, height = 64.dp)
                }
            }
            Text(
                if (report.parkAlertAvailable) "도착하면 챙길 것을 알려 드려요" else "이 차는 P단 신호를 읽을 수 없어 하차 알림을 못 해요",
                style = HaidaType.small,
                color = c.t3,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

/** 소지품 한 줄(.chk): 아이콘 칸 72 · 이름 32 · 둘째 줄 */
@Composable
private fun BelongingLine(row: BelongingRow, check: (@Composable () -> Unit)? = null, done: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = Haida.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Checkbox, onClickLabel = "챙겼어요", onClick = onClick) else Modifier)
            .padding(vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        IconTile(itemIcon(row.item), TileTone.NEUTRAL, size = 72.dp, iconSize = 38.dp, radius = 12.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.item,
                style = HaidaType.title.copy(fontWeight = HaidaType.bodyBold.fontWeight, textDecoration = if (done) TextDecoration.LineThrough else null),
                color = if (done) c.t3 else c.t1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(row.note, style = HaidaType.small, color = c.t3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        check?.invoke()
    }
}

private fun itemIcon(item: String): ImageVector = when {
    "우산" in item -> HaidaIcons.Umbrella
    "책" in item -> HaidaIcons.Book
    else -> HaidaIcons.Bag
}

/** 도착 체크 목록: 하나씩 눌러 챙김 표시, [다 챙겼어요] · [나중에 알려줘] */
@Composable
private fun ArrivalChecklist(report: MemoryReport, actions: MemoryActions) {
    HaidaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 40.dp, vertical = 36.dp)) {
            Eyebrow("챙길 것")
            LazyColumn(modifier = Modifier.padding(top = 8.dp).heightIn(max = 372.dp)) {
                itemsIndexed(report.arrivalItems, key = { _, row -> row.id }) { index, row ->
                    if (index > 0) Separator()
                    val done = row.id in report.arrivalDone
                    BelongingLine(row, check = { CheckMark(done) }, done = done, onClick = { actions.collect(row.id) })
                }
            }
            Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HaidaButton("다 챙겼어요", onClick = { actions.closeArrival(collected = true) }, kind = ButtonKind.PRIMARY, height = 96.dp, modifier = Modifier.weight(1f))
                HaidaButton("나중에 알려줘", onClick = { actions.closeArrival(collected = false) }, height = 96.dp, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ---- 기억 관리 ----

@Composable
private fun ColumnScope.ManagePanel(report: MemoryReport, actions: MemoryActions) {
    val c = Haida.colors
    var confirming by remember { mutableStateOf(false) }
    val nothing = report.people.isEmpty() && report.belongings.isEmpty()

    Row(modifier = Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow("기억 관리 · 정차 중에만 수정")
        Spacer(Modifier.weight(1f))
        if (!nothing && !confirming) {
            HaidaChip("동행 기록 지우기", onClick = { confirming = true }, small = true, danger = true, icon = HaidaIcons.Close, height = 64.dp)
        }
    }
    if (confirming) {
        HaidaCard(modifier = Modifier.fillMaxWidth().height(600.dp), inset = true) {
            Column(modifier = Modifier.padding(48.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(MemoryMessages.forgetQuestion(report.people.size, report.belongings.size), style = HaidaType.sentence, color = c.t1)
                Text("이름 · 탑승 기록 · 소지품을 모두 지워요.\n되돌릴 수 없어요.", style = HaidaType.body, color = c.t2)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    HaidaButton(
                        "지우기",
                        onClick = {
                            actions.forgetAll()
                            confirming = false
                        },
                        kind = ButtonKind.DANGER,
                        icon = HaidaIcons.Close,
                        height = 96.dp,
                        modifier = Modifier.weight(1f),
                    )
                    HaidaButton("취소", onClick = { confirming = false }, kind = ButtonKind.PRIMARY, height = 96.dp, modifier = Modifier.weight(1f))
                }
            }
        }
        return
    }
    if (report.people.isEmpty()) {
        HaidaCard(modifier = Modifier.fillMaxWidth().height(280.dp)) {
            Text(
                "아직 기억한 동행이 없어요.\n누가 탔는지 알려 주면 여기에 모여요.",
                style = HaidaType.body,
                color = c.t2,
                modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 32.dp),
            )
        }
    } else {
        // 세 명씩 한 줄, 두 줄을 넘으면 밀어서 본다
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.heightIn(max = 580.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(report.people, key = { it.name }) { PersonCard(it) }
        }
    }
    HaidaCard(modifier = Modifier.fillMaxWidth().weight(1f), inset = true) {
        Column(modifier = Modifier.padding(horizontal = 36.dp, vertical = 16.dp)) {
            val items = report.belongings.map { it.item }
            ManageRow("소지품 기록", items.joinToString(" · ").ifEmpty { "없음" }, HaidaIcons.Bag, "${items.size}개")
            Separator()
            ManageRow("저장 위치", "이 차량 안 · 밖으로 보내지 않아요", HaidaIcons.Lock, "로컬")
        }
    }
}

/** 동행 한 명(.pcard): 머리글자 동그라미 · 이름 · 자주 탄 때 */
@Composable
private fun PersonCard(person: PersonRow) {
    val c = Haida.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(CardShape)
            .background(c.l1)
            .border(1.dp, c.line, CardShape)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(c.acSoft), contentAlignment = Alignment.Center) {
            Text(person.name.take(1), style = HaidaType.title, color = c.ac)
        }
        Text(person.name, style = HaidaType.title.copy(fontSize = 30.sp, lineHeight = 37.sp, fontWeight = HaidaType.bodyBold.fontWeight), color = c.t1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // "월·금 오전 · 3번 탔어요" → 자주 탄 때와 횟수를 두 줄로
        Text(person.detail.replace(" · ", "\n"), style = HaidaType.small, color = c.t3, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** 기억 관리 목록 한 줄(.set-row): 이름 · 설명 · 오른쪽 꼬리표 */
@Composable
private fun ManageRow(title: String, sub: String, tagIcon: ImageVector, tag: String) {
    val c = Haida.colors
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = HaidaType.bodyBold, color = c.t1)
            Text(sub, style = HaidaType.small, color = c.t3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(
            modifier = Modifier.height(44.dp).clip(PillShape).background(c.l3).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(tagIcon, contentDescription = null, tint = c.t2, modifier = Modifier.size(24.dp))
            Text(tag, style = HaidaType.smallBold, color = c.t2)
        }
    }
}
