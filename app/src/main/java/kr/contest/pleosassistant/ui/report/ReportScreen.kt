package kr.contest.pleosassistant.ui.report

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.contest.pleosassistant.drive.DriveReport
import kr.contest.pleosassistant.drive.TripSummary
import kr.contest.pleosassistant.drive.WorstScene
import kr.contest.pleosassistant.ui.common.Eyebrow
import kr.contest.pleosassistant.ui.common.Haida
import kr.contest.pleosassistant.ui.common.HaidaCard
import kr.contest.pleosassistant.ui.common.HaidaIcons
import kr.contest.pleosassistant.ui.common.HaidaType
import kr.contest.pleosassistant.ui.common.HeaderSub
import kr.contest.pleosassistant.ui.common.PillTone
import kr.contest.pleosassistant.ui.common.ScreenHeader
import kr.contest.pleosassistant.ui.common.Separator
import kr.contest.pleosassistant.ui.common.StatusPill
import java.time.format.DateTimeFormatter

// 팀 UI 프로토타입의 리포트 화면(.rp-ring · .rp-scene · .rp-trend · .rp-locked)을 따른다.
// 주행 중에는 점수를 숨기고 잠금 카드만 보인다(기능계획서 7장 '상세 화면과 추이 그래프는 정차 중에만').

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun ReportScreen(report: DriveReport, actions: DriveActions, modifier: Modifier = Modifier) {
    // 하차 직후 점수 패널을 보고 이 화면으로 왔으면 패널은 닫는다
    if (report.finished != null) LaunchedEffect(report.finished) { actions.dismissFinished() }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            "주행 리포트",
            sub = {
                report.last?.takeIf { !report.driving }?.let {
                    HeaderSub("${it.start.format(TIME)}~${it.end.format(TIME)} · ${"%.1f".format(it.distanceKm)}km · ${duration(it.movingSec)}")
                }
            },
        )
        Box(modifier = Modifier.weight(1f).padding(start = 40.dp, end = 40.dp, bottom = 24.dp)) {
            when {
                report.speedProblem != null && report.last == null -> Notice(HaidaIcons.Warn, report.speedProblem, "권한을 허용하면 다음 주행부터 기록해요")
                report.driving -> Notice(HaidaIcons.Lock, "점수는 정차한 뒤에 알려 드릴게요", report.drivingText.orEmpty())
                report.last == null -> Notice(HaidaIcons.Gauge, "아직 주행 기록이 없어요", "주행(D)한 뒤 P 에 놓으면 점수를 알려 드려요")
                else -> Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxSize()) {
                    RingCard(report.last, Modifier.weight(1f).fillMaxHeight())
                    Column(modifier = Modifier.weight(1.1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        SceneCard(report.last, report.worst, report.lastMessage.orEmpty(), Modifier.fillMaxWidth().weight(1f))
                        TrendCard(report.recentScores, Modifier.fillMaxWidth().height(400.dp))
                    }
                }
            }
        }
    }
}

/** 잠금 · 빈 상태 카드(.rp-locked): 회색 면 가운데 아이콘 + 큰 한 문장 */
@Composable
private fun Notice(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String) {
    val c = Haida.colors
    HaidaCard(Modifier.fillMaxSize(), inset = true) {
        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = c.t3, modifier = Modifier.size(80.dp))
            Text(title, style = HaidaType.hero, color = c.t1, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 28.dp))
            Text(sub, style = HaidaType.body, color = c.t3, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** 점수 고리(.rp-ring): 270° 고리 · 가운데 점수 · 한 줄 평 · 안전/연비 · 아래 통계 3칸 */
@Composable
private fun RingCard(trip: TripSummary, modifier: Modifier) {
    val c = Haida.colors
    HaidaCard(modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.padding(top = 24.dp).size(400.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 28.dp.toPx()
                    val inset = stroke / 2 + 14.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    drawArc(c.l3, 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(c.ac, 135f, 270f * trip.score / 100f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${trip.score}", style = HaidaType.count.copy(fontSize = 120.sp, lineHeight = 120.sp), color = c.t1)
                    Text("/ 100점", style = HaidaType.body, color = c.t3)
                }
            }
            Text(levelText(trip.score), style = HaidaType.title, color = c.t1, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 16.dp)) {
                StatusPill("안전 ${trip.safety}", PillTone.NEUTRAL, icon = null)
                StatusPill("연비 ${trip.eco}", PillTone.NEUTRAL, icon = null)
            }
            Spacer(Modifier.weight(1f))
            Separator()
            Row(modifier = Modifier.fillMaxWidth().padding(top = 28.dp)) {
                Stat("%.1fkm".format(trip.distanceKm), "주행 거리", Modifier.weight(1f))
                Stat(duration(trip.movingSec), "움직인 시간", Modifier.weight(1f))
                Stat("${(trip.steadyRatio * 100).toInt()}%", "정속 주행", Modifier.weight(1f))
            }
        }
    }
}

/** 1분이 안 되면 초로 */
private fun duration(sec: Int) = if (sec < 60) "${sec}초" else "${sec / 60}분"

private fun levelText(score: Int) = when {
    score >= 90 -> "아주 안정적이에요"
    score >= 80 -> "안정적이에요"
    score >= 70 -> "무난해요"
    else -> "조금 거칠었어요"
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val c = Haida.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = HaidaType.hero.copy(fontSize = 44.sp), color = c.t1)
        Text(label, style = HaidaType.small, color = c.t3)
    }
}

/** 가장 크게 깎인 장면(.rp-scene): 한 장면만 크게, 아래에 이벤트 기록 */
@Composable
private fun SceneCard(trip: TripSummary, worst: WorstScene?, message: String, modifier: Modifier) {
    val c = Haida.colors
    HaidaCard(modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (worst == null) {
                StatusPill("깎인 장면 없음", PillTone.OK)
                Text("급한 조작 없이 부드럽게 달렸어요", style = HaidaType.sentence, color = c.t1, modifier = Modifier.padding(top = 8.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusPill("가장 크게 깎인 장면", PillTone.WARN)
                    Text("−${worst.minus}점", style = HaidaType.title, color = c.t1)
                }
                Text("${worst.type.label} ${worst.count}회", style = HaidaType.sentence, color = c.t1, modifier = Modifier.padding(top = 8.dp))
                Text("처음 ${worst.firstAt.format(TIME)} · 10km 당 횟수로 비교해요", style = HaidaType.body, color = c.t2)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    trip.events.takeLast(3).forEach { e ->
                        Text("${e.at.format(TIME)} · ${e.type.label} · ${e.fromKmh} → ${e.toKmh}km/h", style = HaidaType.small, color = c.t3)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Separator()
            Text(message, style = HaidaType.body, color = c.t2)
        }
    }
}

/** 최근 점수 추이(.rp-trend): 꺾은선, 마지막 점만 크게 */
@Composable
private fun TrendCard(scores: List<Int>, modifier: Modifier) {
    val c = Haida.colors
    HaidaCard(modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("최근 ${scores.size}번 평균 ", style = HaidaType.body, color = c.t2)
                Text("${scores.average().toInt()}점", style = HaidaType.bodyBold, color = c.t1)
                Spacer(Modifier.weight(1f))
                if (scores.size >= 2) {
                    val diff = scores.last() - scores[scores.size - 2]
                    if (diff != 0) StatusPill(if (diff > 0) "▲ 지난번보다 ${diff}점" else "▼ 지난번보다 ${-diff}점", PillTone.NEUTRAL, icon = null)
                }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 20.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val lo = 50f
                    val padX = 30.dp.toPx()
                    val n = scores.size
                    fun x(i: Int) = if (n <= 1) size.width / 2 else padX + i * (size.width - padX * 2) / (n - 1)
                    fun y(v: Int) = size.height - 16.dp.toPx() - (v.coerceAtLeast(lo.toInt()) - lo) / (100f - lo) * (size.height - 40.dp.toPx())
                    listOf(60, 80, 100).forEach { g -> drawLine(c.line, Offset(0f, y(g)), Offset(size.width, y(g)), 1.dp.toPx()) }
                    if (n >= 2) {
                        val path = Path().apply {
                            moveTo(x(0), y(scores[0]))
                            for (i in 1 until n) lineTo(x(i), y(scores[i]))
                        }
                        drawPath(path, c.ac, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    scores.forEachIndexed { i, v ->
                        val last = i == n - 1
                        drawCircle(c.ac, radius = (if (last) 11 else 6).dp.toPx(), center = Offset(x(i), y(v)))
                        if (!last) drawCircle(c.l1, radius = 3.dp.toPx(), center = Offset(x(i), y(v)))
                    }
                }
            }
            Eyebrow("오래된 주행 → 이번 주행 · 정차 중에만 보여요", modifier = Modifier.padding(top = 8.dp))
        }
    }
}
