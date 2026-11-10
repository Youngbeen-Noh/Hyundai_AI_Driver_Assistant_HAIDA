package kr.contest.pleosassistant.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.contest.pleosassistant.drive.DriveReport
import kr.contest.pleosassistant.drive.TripSummary
import kr.contest.pleosassistant.drive.WorstScene
import kr.contest.pleosassistant.ui.common.BigButton
import kr.contest.pleosassistant.ui.common.CardShape
import kr.contest.pleosassistant.ui.common.IconTile
import kr.contest.pleosassistant.ui.common.Palette
import kr.contest.pleosassistant.ui.common.ScreenHeader
import kr.contest.pleosassistant.ui.common.SummaryBanner
import java.time.format.DateTimeFormatter

// 임시 디자인. 팀 UI 템플릿을 받으면 이 파일만 바꾼다(DriveReport · DriveActions 는 그대로).

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun ReportScreen(report: DriveReport, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.background)
            .padding(horizontal = 48.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        ScreenHeader(Icons.Filled.Insights, "주행 리포트", "하차하면 점수와 가장 아쉬운 장면을 알려 드려요")
        report.speedProblem?.let { SummaryBanner(Icons.Filled.WarningAmber, Palette.caution, it) }
        when {
            // 주행 중에는 점수를 숨긴다(기획서 7장 · 9장)
            report.driving -> DrivingLocked(report.drivingText.orEmpty(), Modifier.weight(1f))
            report.last == null -> Empty(Modifier.weight(1f))
            else -> Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                ScorePanel(report.last, report.lastMessage.orEmpty(), Modifier.weight(1f).fillMaxHeight())
                Column(modifier = Modifier.weight(1.1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    ScenePanel(report.last, report.worst, Modifier.fillMaxWidth().weight(1f))
                    TrendPanel(report.recentScores, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** 하차(P단) 직후 한 번. 어느 탭에 있든 앱 위에 뜬다 */
@Composable
fun TripFinishedDialog(summary: TripSummary, message: String, actions: DriveActions) {
    AlertDialog(
        onDismissRequest = actions::dismissFinished,
        containerColor = Palette.card,
        icon = { IconTile(Icons.Filled.EmojiEvents, Palette.accent, tileSize = 80, iconSize = 48) },
        title = { Text("${summary.score}점", color = Palette.text, fontSize = 56.sp, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = Palette.text, fontSize = 30.sp, lineHeight = 42.sp) },
        confirmButton = { BigButton("확인", onClick = actions::dismissFinished) },
    )
}

@Composable
private fun DrivingLocked(text: String, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().clip(CardShape).background(Palette.card).border(1.dp, Palette.cardBorder, CardShape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = Palette.textMuted, modifier = Modifier.size(72.dp))
        Text("점수는 정차한 뒤에 알려 드릴게요", color = Palette.text, fontSize = 44.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
        Text(text, color = Palette.textMuted, fontSize = 28.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Empty(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().clip(CardShape).background(Palette.card).border(1.dp, Palette.cardBorder, CardShape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Speed, contentDescription = null, tint = Palette.textMuted, modifier = Modifier.size(72.dp))
        Text("아직 주행 기록이 없어요", color = Palette.text, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
        Text("주행(D)한 뒤 P 에 놓으면 점수를 알려 드려요", color = Palette.textMuted, fontSize = 28.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ScorePanel(trip: TripSummary, message: String, modifier: Modifier) {
    Panel(modifier) {
        Text("${trip.start.format(TIME)} ~ ${trip.end.format(TIME)} 주행", color = Palette.textMuted, fontSize = 22.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${trip.score}", color = Palette.text, fontSize = 120.sp, fontWeight = FontWeight.Bold, lineHeight = 120.sp)
            Text(" / 100점", color = Palette.textMuted, fontSize = 32.sp, modifier = Modifier.padding(bottom = 18.dp))
        }
        Text(message, color = Palette.text, fontSize = 28.sp, lineHeight = 40.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Pill("안전 ${trip.safety}", Palette.accent)
            Pill("연비 ${trip.eco}", Palette.ok)
        }
        Box(Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth()) {
            Stat("%.1fkm".format(trip.distanceKm), "주행 거리", Modifier.weight(1f))
            Stat("${trip.movingSec / 60}분", "움직인 시간", Modifier.weight(1f))
            Stat("${(trip.steadyRatio * 100).toInt()}%", "정속 주행", Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScenePanel(trip: TripSummary, worst: WorstScene?, modifier: Modifier) {
    Panel(modifier) {
        PanelTitle(Icons.Filled.WarningAmber, "가장 크게 깎인 장면", "점수는 10km 당 횟수로 비교해요")
        if (worst == null) {
            Text("깎인 장면이 없어요. 부드럽게 달렸어요", color = Palette.ok, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${worst.type.label} ${worst.count}회", color = Palette.text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Pill("−${worst.minus}점", Palette.caution)
            }
            Text("처음 ${worst.firstAt.format(TIME)}", color = Palette.textMuted, fontSize = 24.sp)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                trip.events.takeLast(4).forEach { e ->
                    Text("${e.at.format(TIME)} · ${e.type.label} · ${e.fromKmh}→${e.toKmh}km/h", color = Palette.textMuted, fontSize = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun TrendPanel(scores: List<Int>, modifier: Modifier) {
    Panel(modifier) {
        Text("최근 주행 ${scores.size}번", color = Palette.textMuted, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
            scores.forEachIndexed { i, s ->
                val last = i == scores.lastIndex
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("$s", color = if (last) Palette.text else Palette.textMuted, fontSize = 20.sp, fontWeight = if (last) FontWeight.Bold else FontWeight.Normal)
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .width(28.dp)
                            .height((s.coerceIn(0, 100) * 0.9).dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (last) Palette.accent else Palette.accent.copy(alpha = 0.35f)),
                    )
                }
            }
        }
    }
}

// ---- 공통 조각 ----

@Composable
private fun Panel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.clip(CardShape).background(Palette.card).border(1.dp, Palette.cardBorder, CardShape).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        content = content,
    )
}

@Composable
private fun PanelTitle(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconTile(icon, Palette.caution, tileSize = 56, iconSize = 32)
        Column {
            Text(title, color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Palette.textMuted, fontSize = 20.sp)
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Palette.text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Palette.textMuted, fontSize = 20.sp, textAlign = TextAlign.Center)
    }
}
