package kr.contest.pleosassistant.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.contest.pleosassistant.music.Mood
import kr.contest.pleosassistant.music.MusicApp
import kr.contest.pleosassistant.music.PlayerLink
import kr.contest.pleosassistant.music.Track
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
import kr.contest.pleosassistant.ui.common.ScreenHeader
import kr.contest.pleosassistant.ui.common.Separator

// 팀 UI 프로토타입의 '음악' 화면. 본문 1474 × 1110 기준 왼쪽 카드 810(지금 곡), 오른쪽 640(다음 곡).
// - 정차 중: 위에 '분위기 바꾸기', 오른쪽에 다음 4곡 목록과 재생할 음악 앱
// - 주행 중: 버튼은 일시정지 · 다음 · 저장 3개만, 오른쪽은 다음 곡 한 곡만 크게(기능계획서 9장: 버튼 3개 이하)

/** 분위기별 앨범 그림 색(프로토타입 MOODS[].c) */
private fun Mood.colors(): List<Color> = when (this) {
    Mood.FRESH -> listOf(Color(0xFF7CC8FF), Color(0xFFB9F1D6))
    Mood.DRIVE -> listOf(Color(0xFFFF4F86), Color(0xFFFFB347))
    Mood.CRUISE -> listOf(Color(0xFF22A99B), Color(0xFFEAD9A4))
    Mood.RAIN -> listOf(Color(0xFF6C8FB8), Color(0xFF1B2A45))
    Mood.SUNSET -> listOf(Color(0xFFFF8A4C), Color(0xFF7B4FB5))
    Mood.NIGHT -> listOf(Color(0xFF1B2858), Color(0xFF6A57C8))
}

private fun time(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

@Composable
fun MusicScreen(ui: MusicUi, driving: Boolean, actions: MusicActions, modifier: Modifier = Modifier) {
    val s = ui.state
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader("음악", sub = { HeaderSub(if (s.manualMood != null) "직접 고른 분위기" else "내 분위기 추천") })
        Column(
            modifier = Modifier.weight(1f).padding(start = 40.dp, end = 40.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (!driving) MoodBar(s.manualMood, actions)
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                NowPlaying(ui, actions, Modifier.weight(810f).fillMaxHeight())
                if (driving) {
                    NextBig(s.upcoming.firstOrNull(), s.mood, Modifier.weight(640f).fillMaxHeight())
                } else {
                    UpNext(s.upcoming, s.mood, ui.app, actions, Modifier.weight(640f).fillMaxHeight())
                }
            }
        }
    }
}

/** 분위기 바꾸기(정차 중에만): 자동 + 6종 */
@Composable
private fun MoodBar(manual: Mood?, actions: MusicActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow("분위기", Modifier.padding(end = 4.dp))
        HaidaChip("자동", onClick = { actions.chooseMood(null) }, selected = manual == null, small = true, height = 64.dp)
        Mood.entries.forEach { m ->
            HaidaChip(m.label, onClick = { actions.chooseMood(m) }, selected = manual == m, small = true, height = 64.dp)
        }
    }
}

@Composable
private fun Art(mood: Mood, size: Dp, radius: Dp = 16.dp, shift: Int = 0) {
    val c = mood.colors()
    val colors = if (shift % 2 == 1) c.reversed() else c
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius)).background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(HaidaIcons.Music, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(size * 0.36f))
    }
}

/** 왼쪽: 분위기 · 이유, 앨범 그림, 곡 · 가수, 재생 앱, 진행, 버튼 3개 */
@Composable
private fun NowPlaying(ui: MusicUi, actions: MusicActions, modifier: Modifier) {
    val c = Haida.colors
    val s = ui.state
    val track = s.current
    HaidaCard(modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HaidaChip(s.mood.label, onClick = {}, selected = true, small = true, height = 60.dp)
                Text(s.decision.reason, style = HaidaType.small, color = c.t2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                // 앨범 그림은 고정 크기. 높이를 채우게 하면 글자 칸이 밀려 곡 이름이 안 보인다(에뮬레이터 확인)
                Art(s.mood, 300.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val waiting = ui.analyzing?.let { (done, total) -> if (total > 0) "곡 분위기 분석 중 $done/$total" else "곡 분위기 분석 중" }
                    Text(track?.title ?: waiting ?: "고를 곡이 없어요", style = HaidaType.hero, color = c.t1, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track?.artist.orEmpty(), style = HaidaType.body, color = c.t2, maxLines = 1)
                    // 소리를 듣고 잰 값(앱에 든 곡만)
                    track?.features?.let { Text("곡 분석 · ${it.summary()}", style = HaidaType.small, color = c.t3, maxLines = 2) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(HaidaIcons.Music, contentDescription = null, tint = c.t3, modifier = Modifier.size(24.dp))
                        Text(linkText(ui.app, ui.link), style = HaidaType.small, color = c.t3, maxLines = 2)
                    }
                }
            }
            if (track != null && ui.link is PlayerLink.Local) {
                Column {
                    LevelBar(ui.elapsedSec.toFloat() / track.durationSec, c.ac, Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(time(ui.elapsedSec), style = HaidaType.small, color = c.t3)
                        Spacer(Modifier.weight(1f))
                        Text(time(track.durationSec), style = HaidaType.small, color = c.t3)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                HaidaButton(
                    if (s.paused) "재생" else "일시정지",
                    onClick = actions::togglePause,
                    kind = ButtonKind.PRIMARY,
                    icon = if (s.paused) HaidaIcons.Play else HaidaIcons.Pause,
                    modifier = Modifier.weight(1f),
                )
                HaidaButton("다음", onClick = actions::skip, icon = HaidaIcons.Next, modifier = Modifier.weight(1f))
                HaidaButton(
                    if (s.currentSaved) "저장됨" else "저장",
                    onClick = actions::toggleSave,
                    icon = HaidaIcons.Save,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun linkText(app: MusicApp, link: PlayerLink): String = when (link) {
    PlayerLink.Local -> "앱에 든 데모 곡 · 직접 만든 곡이라 무료"
    PlayerLink.Connecting -> "${app.label}에 연결하는 중"
    is PlayerLink.Connected -> link.notFound?.let { "${app.label}에서 '$it' 곡을 찾지 못했어요 · 다음 곡을 눌러 주세요" }
        ?: ("${app.label}에서 재생" + (link.nowPlaying?.let { " · 지금 $it" } ?: ""))
    PlayerLink.NotInstalled -> "${app.label} 앱이 없어요 · 목록만 보여 드려요"
    PlayerLink.Refused -> "${app.label}이 연결을 받지 않아요 · 음악 앱에서 직접 골라 주세요"
}

/** 오른쪽(정차): 다음 4곡 + 재생할 음악 앱 */
@Composable
private fun UpNext(next: List<Track>, mood: Mood, app: MusicApp, actions: MusicActions, modifier: Modifier) {
    val c = Haida.colors
    HaidaCard(modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(32.dp)) {
            Eyebrow("다음 곡")
            next.forEachIndexed { i, t ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Art(mood, 68.dp, 12.dp, shift = i + 1)
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = HaidaType.bodySemi, color = c.t1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, style = HaidaType.small, color = c.t3, maxLines = 1)
                    }
                    // 음악 앱 곡은 길이를 모른다(0)
                    if (t.durationSec > 0) Text(time(t.durationSec), style = HaidaType.small, color = c.t3)
                }
            }
            Spacer(Modifier.weight(1f))
            Separator()
            Eyebrow("재생할 음악 앱", Modifier.padding(top = 20.dp, bottom = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MusicApp.entries.forEach { a ->
                    HaidaChip(a.label, onClick = { actions.chooseApp(a) }, selected = a == app, small = true, height = 60.dp)
                }
            }
        }
    }
}

/** 오른쪽(주행): 다음 곡 한 곡만 크게 */
@Composable
private fun NextBig(next: Track?, mood: Mood, modifier: Modifier) {
    val c = Haida.colors
    HaidaCard(modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Eyebrow("다음 곡")
            if (next != null) {
                Art(mood, 240.dp, shift = 1)
                Text(next.title, style = HaidaType.title, color = c.t1, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(next.artist, style = HaidaType.body, color = c.t2)
            }
            Spacer(Modifier.weight(1f))
            Text("곡 목록은 정차하면 볼 수 있어요", style = HaidaType.small, color = c.t3)
        }
    }
}
