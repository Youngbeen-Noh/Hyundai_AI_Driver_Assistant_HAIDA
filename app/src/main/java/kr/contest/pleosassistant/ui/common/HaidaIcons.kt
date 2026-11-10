package kr.contest.pleosassistant.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 팀 UI 프로토타입의 선 아이콘(24 격자, 선 굵기 2, 둥근 끝)을 옮겼다. Icon(tint = …) 로 색을 입힌다.
 * 프로토타입에 없는 것(배터리 · 브레이크액 · 사람)은 같은 규칙으로 그렸다.
 */
object HaidaIcons {
    val Home by lazy { icon("home", "M3.5 11 12 3.5 20.5 11v8.5a1 1 0 0 1-1 1H15v-6H9v6H4.5a1 1 0 0 1-1-1z") }
    val Car by lazy {
        icon(
            "car",
            "M4.5 15.5 6 10.6A2 2 0 0 1 7.9 9.2h8.2a2 2 0 0 1 1.9 1.4l1.5 4.9",
            "M3.5 15.5h17v3.2h-17z",
            circle(7.5f, 18.8f, 1.4f),
            circle(16.5f, 18.8f, 1.4f),
        )
    }
    val People by lazy {
        icon(
            "people",
            circle(9f, 8f, 3.2f),
            "M3 19.5c.4-3.3 2.8-5.2 6-5.2s5.6 1.9 6 5.2",
            circle(17.2f, 9f, 2.4f),
            "M17.5 14.2c2.2.2 3.6 1.7 3.9 4.4",
        )
    }
    val Person by lazy { icon("person", circle(12f, 8f, 3.6f), "M5 20c.5-3.8 3.3-6 7-6s6.5 2.2 7 6") }
    val Tire by lazy { icon("tire", circle(12f, 12f, 8.5f), circle(12f, 12f, 3.4f), "M12 3.5v5M12 15.5v5M3.5 12h5M15.5 12h5") }
    val Oil by lazy { icon("oil", "M12 3.5c3 3.8 5.5 6.6 5.5 10a5.5 5.5 0 0 1-11 0c0-3.4 2.5-6.2 5.5-10z") }
    val Washer by lazy { icon("washer", "M3.5 17.5C6 9 18 9 20.5 17.5", "M9 16l.8-3M12 16.5V12M15 16l-.8-3", "M5 20.5h14") }
    val Fuel by lazy {
        icon("fuel", rect(4.5f, 4f, 9f, 16f, 1.6f), "M13.5 9H16a2 2 0 0 1 2 2v5.5a1.5 1.5 0 0 0 3 0V9l-3-3", "M7.5 8h3")
    }
    val Battery by lazy { icon("battery", rect(2.5f, 7f, 16.5f, 10f, 2.4f), "M21.5 10.5v3", "M6.5 10.5v3M10 10.5v3") }

    /** 계기판의 브레이크 경고등 모양 ( ! ) */
    val Brake by lazy {
        icon(
            "brake",
            circle(12f, 12f, 6.5f),
            "M4.2 6.5A9.6 9.6 0 0 0 4.2 17.5",
            "M19.8 6.5A9.6 9.6 0 0 1 19.8 17.5",
            "M12 8.6v3.6",
            circle(12f, 15.2f, 0.5f),
        )
    }
    val Gauge by lazy { icon("gauge", "M4.5 17a8.5 8.5 0 1 1 15 0", "M12 13.5 16 8.5", circle(12f, 14f, 1.4f)) }
    val Umbrella by lazy { icon("umbrella", "M3.5 12.5a8.5 8.5 0 0 1 17 0z", "M12 12.5V19a2 2 0 0 1-4 0", "M12 4V3") }
    val Book by lazy { icon("book", "M5 4.5h10.5A2.5 2.5 0 0 1 18 7v13H7.5A2.5 2.5 0 0 1 5 17.5z", "M5 17.5A2.5 2.5 0 0 1 7.5 15H18") }
    val Bag by lazy { icon("bag", rect(4.5f, 8f, 15f, 12f, 2.4f), "M9 8V6.5a3 3 0 0 1 6 0V8") }
    val Check by lazy { icon("check", "m5 12.5 4.5 4.5L19 7.5") }
    val Ok by lazy { icon("ok", circle(12f, 12f, 9f), "m7.8 12.4 3 3 5.4-6") }
    val Warn by lazy { icon("warn", "M12 3.8 21 19.5H3z", "M12 10v4.4", circle(12f, 17f, 0.5f)) }
    val Crit by lazy { icon("crit", "M8.2 3.5h7.6l4.7 4.7v7.6l-4.7 4.7H8.2l-4.7-4.7V8.2z", "M12 7.8v5", circle(12f, 16.2f, 0.5f)) }
    val Unknown by lazy {
        icon("unknown", dashedCircle(12f, 12f, 9f), "M9.6 9.6a2.5 2.5 0 1 1 3.4 2.3c-.7.4-1 .8-1 1.6", circle(12f, 16.6f, 0.5f))
    }
    val Clock by lazy { icon("clock", circle(12f, 12f, 8.5f), "M12 7.5V12l3 2") }
    val Chevron by lazy { icon("chevron", "m9 5 7 7-7 7") }
    val ChevronDown by lazy { icon("chevron-down", "m5 9 7 7 7-7") }
    val ChevronUp by lazy { icon("chevron-up", "m5 15 7-7 7 7") }
    val Close by lazy { icon("close", "M6 6l12 12M18 6 6 18") }
    val Plus by lazy { icon("plus", "M12 5v14M5 12h14") }
    val Lock by lazy { icon("lock", rect(5f, 10.5f, 14f, 10f, 2.4f), "M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5") }
    val Mic by lazy { icon("mic", rect(9f, 3.5f, 6f, 11f, 3f), "M5.5 11.5a6.5 6.5 0 0 0 13 0M12 18v3") }
    val Settings by lazy { icon("settings", "M4 7h9M17 7h3M4 17h3M11 17h9", circle(15f, 7f, 2f), circle(9f, 17f, 2f)) }

    /** 앱 로고(H). 색은 테마에 따라 바뀌어 매번 만든다. */
    fun logo(background: Color, foreground: Color): ImageVector =
        ImageVector.Builder("logo", 64.dp, 64.dp, 64f, 64f).apply {
            addPath(addPathNodes(rect(0f, 0f, 64f, 64f, 18f)), fill = SolidColor(background))
            val bar = SolidColor(foreground)
            addPath(addPathNodes("M20.5 17v30"), stroke = bar, strokeLineWidth = 6.4f, strokeLineCap = StrokeCap.Round)
            addPath(addPathNodes("M43.5 17v30"), stroke = bar, strokeLineWidth = 6.4f, strokeLineCap = StrokeCap.Round)
            addPath(addPathNodes("M20.5 36c5-8.6 18-8.6 23 0"), stroke = bar, strokeLineWidth = 5f, strokeLineCap = StrokeCap.Round)
            addPath(addPathNodes(circle(48.5f, 14.5f, 3.4f)), fill = bar, fillAlpha = 0.95f)
        }.build()

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}" +
            "h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

    /** 점선 원(프로토타입 stroke-dasharray 3 3). 벡터에는 점선이 없어 짧은 호 10개로 그린다. */
    private fun dashedCircle(cx: Float, cy: Float, r: Float): String = (0 until 10).joinToString("") { i ->
        val a0 = Math.toRadians(i * 36.0)
        val a1 = Math.toRadians(i * 36.0 + 18.0)
        val x0 = cx + r * cos(a0).toFloat()
        val y0 = cy + r * sin(a0).toFloat()
        val x1 = cx + r * cos(a1).toFloat()
        val y1 = cy + r * sin(a1).toFloat()
        "M$x0 ${y0}A$r $r 0 0 1 $x1 $y1"
    }
}
