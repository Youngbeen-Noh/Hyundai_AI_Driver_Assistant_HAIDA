package kr.contest.pleosassistant.ui.briefing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.contest.pleosassistant.briefing.Briefing
import kr.contest.pleosassistant.briefing.Category
import kr.contest.pleosassistant.briefing.Playback
import kr.contest.pleosassistant.briefing.START_AFTER_MIN
import kr.contest.pleosassistant.ui.common.ButtonKind
import kr.contest.pleosassistant.ui.common.Eyebrow
import kr.contest.pleosassistant.ui.common.Haida
import kr.contest.pleosassistant.ui.common.HaidaButton
import kr.contest.pleosassistant.ui.common.HaidaCard
import kr.contest.pleosassistant.ui.common.HaidaChip
import kr.contest.pleosassistant.ui.common.HaidaIcons
import kr.contest.pleosassistant.ui.common.HaidaType
import kr.contest.pleosassistant.ui.common.HeaderSub
import kr.contest.pleosassistant.ui.common.LevelBar
import kr.contest.pleosassistant.ui.common.PillTone
import kr.contest.pleosassistant.ui.common.ScreenHeader
import kr.contest.pleosassistant.ui.common.Separator
import kr.contest.pleosassistant.ui.common.StatusPill

// 팀 UI 프로토타입의 '브리핑' 화면. 본문 1474 × 1110 기준 왼쪽 944(지금 소주제), 오른쪽 506(자막 · 다음 분야 · 용어).
// - 주행 중: 버튼은 더 자세히 · 용어 설명 · 다음 브리핑 3개, 오른쪽은 다음 분야만(자막은 정차하면)
// - 듣는 중이 아니면: 시작 조건 안내 · 지금 듣기, 관심 분야 고르기

@Composable
fun BriefingScreen(ui: BriefingUi, driving: Boolean, actions: BriefingActions, modifier: Modifier = Modifier) {
    val s = ui.state
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            "최근 이슈 브리핑",
            sub = { HeaderSub("선택 기능 · 관심 분야 ${s.settings.selected.size}개") },
            trailing = {
                if (s.playing && !driving) HaidaButton("멈추기", onClick = actions::stop, kind = ButtonKind.GHOST, height = 64.dp)
            },
        )
        Row(
            modifier = Modifier.weight(1f).padding(start = 40.dp, end = 40.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            val p = s.playback
            if (p != null) {
                NowCard(p, ui.progress, driving, actions, Modifier.weight(944f).fillMaxHeight())
                if (driving) {
                    QueueCard(s.queue, Modifier.weight(506f).fillMaxHeight())
                } else {
                    CaptionCard(p, s.queue, ui.shownTerm, Modifier.weight(506f).fillMaxHeight())
                }
            } else {
                IdleCard(ui, driving, actions, Modifier.weight(944f).fillMaxHeight())
                if (driving) {
                    QueueCard(s.queue, Modifier.weight(506f).fillMaxHeight())
                } else {
                    SettingsCard(ui, actions, Modifier.weight(506f).fillMaxHeight())
                }
            }
        }
    }
}

/** 왼쪽(듣는 중): 분야 · 진행 점 · 소주제 n/m, 제목, 언론사, 소주제 목록, 진행, 버튼 3개 */
@Composable
private fun NowCard(p: Playback, progress: Float, driving: Boolean, actions: BriefingActions, modifier: Modifier) {
    val c = Haida.colors
    val sub = p.subtopic
    HaidaCard(modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 48.dp, bottom = 48.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                HaidaChip(p.briefing.category.label, onClick = {}, selected = true, small = true, height = 60.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    p.briefing.subtopics.indices.forEach { i ->
                        Box(Modifier.size(18.dp).clip(CircleShape).background(if (i <= p.sub) c.ac else c.l3))
                    }
                }
                Text("소식 ${p.sub + 1}/${p.briefing.subtopics.size}", style = HaidaType.small, color = c.t3)
            }
            if (driving) Spacer(Modifier.weight(1f))
            Text(
                sub.title,
                style = HaidaType.hero.copy(fontSize = if (driving) 72.sp else 64.sp, lineHeight = if (driving) 86.sp else 77.sp),
                color = c.t1,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = if (driving) 0.dp else 56.dp, bottom = 20.dp),
            )
            Text("${sub.press} · ${sub.publishedAt}", style = HaidaType.body, color = c.t2)
            if (!driving) {
                Column(Modifier.padding(top = 40.dp)) {
                    Separator()
                    p.briefing.subtopics.forEachIndexed { i, s -> SubRow(i, s.title, on = i == p.sub, done = i < p.sub) }
                }
            }
            Spacer(Modifier.weight(1f))
            LevelBar(progress, c.ac, Modifier.fillMaxWidth().padding(top = 24.dp))
            Row(Modifier.padding(top = 32.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                HaidaButton("더 자세히", onClick = actions::more, modifier = Modifier.weight(1f), height = 96.dp)
                HaidaButton("용어 설명", onClick = actions::explainTerm, modifier = Modifier.weight(1f), height = 96.dp)
                HaidaButton("다음 브리핑", onClick = actions::nextBriefing, kind = ButtonKind.PRIMARY, modifier = Modifier.weight(1f), height = 96.dp)
            }
        }
    }
}

@Composable
private fun SubRow(index: Int, title: String, on: Boolean, done: Boolean) {
    val c = Haida.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 88.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (on) c.ac else c.l1)
                .border(1.5.dp, if (on) c.ac else c.line2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("${index + 1}", style = HaidaType.smallBold, color = if (on) c.onAc else if (done) c.t3 else c.t2)
        }
        Text(
            title,
            style = if (on) HaidaType.bodyBold else HaidaType.body,
            color = if (on) c.t1 else c.t3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
    Separator()
}

/** 오른쪽(정차 · 듣는 중): 자막 → 다음 분야 → 용어 */
@Composable
private fun CaptionCard(p: Playback, queue: List<Briefing>, shownTerm: String?, modifier: Modifier) {
    val c = Haida.colors
    HaidaCard(modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 44.dp)) {
            Eyebrow("자막")
            Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // 지금 문장과 앞뒤 한 문장
                val from = (p.line - 1).coerceAtLeast(0)
                p.script.subList(from, (p.line + 2).coerceAtMost(p.script.size)).forEachIndexed { i, line ->
                    val on = from + i == p.line
                    Row(Modifier.fillMaxWidth()) {
                        if (on) Box(Modifier.width(4.dp).heightIn(min = 40.dp).background(c.ac))
                        Text(
                            line,
                            style = if (on) HaidaType.bodyBold else HaidaType.body,
                            color = if (on) c.t1 else c.t3,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = if (on) 20.dp else 0.dp),
                        )
                    }
                }
            }
            Eyebrow("다음 분야", Modifier.padding(top = 40.dp, bottom = 12.dp))
            Separator()
            queue.forEach { b ->
                Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusPill(b.category.label, PillTone.NEUTRAL)
                    Text(b.subtopics.first().title, style = HaidaType.small, color = c.t2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Separator()
            }
            Spacer(Modifier.weight(1f))
            val term = shownTerm ?: p.subtopic.term?.let { "${it.word} · ${it.meaning}" }
            if (term != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.acSoft).padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(if (shownTerm != null) "용어 설명" else "용어", style = HaidaType.smallBold, color = c.ac)
                    Text(term, style = HaidaType.body, color = c.t1, maxLines = 5, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 오른쪽(주행): 다음 분야만 */
@Composable
private fun QueueCard(queue: List<Briefing>, modifier: Modifier) {
    val c = Haida.colors
    HaidaCard(modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 44.dp)) {
            Eyebrow("다음 분야")
            Column(Modifier.padding(top = 12.dp)) {
                queue.forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 96.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Icon(HaidaIcons.News, contentDescription = null, tint = c.t3, modifier = Modifier.size(36.dp))
                        Text(b.category.label, style = HaidaType.title, color = c.t1)
                    }
                    Separator()
                }
            }
            Spacer(Modifier.weight(1f))
            Text("자막은 정차하면 볼 수 있어요", style = HaidaType.small, color = c.t3)
        }
    }
}

/** 왼쪽(듣는 중이 아님): 시작 조건 · 지금 듣기 */
@Composable
private fun IdleCard(ui: BriefingUi, driving: Boolean, actions: BriefingActions, modifier: Modifier) {
    val c = Haida.colors
    val none = ui.state.settings.selected.isEmpty()
    HaidaCard(modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(56.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Icon(HaidaIcons.News, contentDescription = null, tint = c.ac, modifier = Modifier.size(56.dp))
            Text(
                when {
                    none -> "관심 분야를 고르면 브리핑을 들려 드려요"
                    ui.autoStart -> "목적지 없이 ${START_AFTER_MIN}분 달리면 브리핑을 시작해요"
                    else -> "자동 시작을 껐어요. 듣고 싶을 때 눌러 주세요"
                },
                style = HaidaType.hero,
                color = c.t1,
            )
            Text(
                "분야를 돌아가며 2~3분씩 들려 드려요. 언론사 이름과 함께 말하고, 기사 원문은 가져오지 않아요.",
                style = HaidaType.body,
                color = c.t2,
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                HaidaButton(
                    "지금 듣기",
                    onClick = actions::startNow,
                    kind = ButtonKind.PRIMARY,
                    enabled = !none,
                    modifier = Modifier.weight(1f),
                    height = 96.dp,
                )
                if (!driving) {
                    HaidaButton(
                        if (ui.autoStart) "자동 시작 끄기" else "자동 시작 켜기",
                        onClick = { actions.setAutoStart(!ui.autoStart) },
                        modifier = Modifier.weight(1f),
                        height = 96.dp,
                    )
                }
            }
        }
    }
}

/** 오른쪽(정차 · 듣는 중이 아님): 관심 분야 · 준비된 브리핑 처리 · 다음 분야 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsCard(ui: BriefingUi, actions: BriefingActions, modifier: Modifier) {
    val c = Haida.colors
    val s = ui.state
    HaidaCard(modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 44.dp)) {
            Eyebrow("관심 분야")
            FlowRow(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Category.entries.forEach { cat ->
                    HaidaChip(cat.label, onClick = { actions.toggleCategory(cat) }, selected = cat in s.settings.selected, small = true, height = 64.dp)
                }
            }
            Text(
                if (s.settings.keepPrepared) "분야를 빼도 준비된 브리핑은 들어요" else "분야를 빼면 준비된 브리핑도 지워요",
                style = HaidaType.small,
                color = c.t3,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
            )
            HaidaChip(
                "이미 준비된 브리핑은 듣기",
                onClick = { actions.setKeepPrepared(!s.settings.keepPrepared) },
                selected = s.settings.keepPrepared,
                small = true,
                icon = if (s.settings.keepPrepared) HaidaIcons.Check else null,
                height = 64.dp,
            )
            Eyebrow("준비된 브리핑", Modifier.padding(top = 40.dp, bottom = 12.dp))
            Separator()
            s.queue.forEach { b ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatusPill(b.category.label, PillTone.NEUTRAL)
                    Text(b.subtopics.first().title, style = HaidaType.small, color = c.t2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Separator()
            }
        }
    }
}
