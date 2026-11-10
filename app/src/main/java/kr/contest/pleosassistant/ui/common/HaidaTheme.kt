package kr.contest.pleosassistant.ui.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.io.File

// 팀 UI 프로토타입(haida.html 수정본, 2026-11-10)의 디자인 토큰을 옮겼다. 새 화면도 여기 값만 쓴다.
// 프로토타입은 Pleos 에뮬레이터 화면(2560 × 1440, 앱 창 1682 × 1210, 본문 1474 × 1110) 기준이고 px 를 dp · sp 로 1:1 옮겼다.
// Pleos 톤: 그림자 · 그라데이션 없이 평면, 작은 모서리(8 · 12 · 16), 가는 선(1).

/** 프로토타입 CSS 변수 이름을 그대로 따른다. L0 = 앱 바탕, L1 = 카드, L2 · L3 = 카드 안 면 */
@Immutable
data class HaidaColors(
    val l0: Color,
    val l1: Color,
    val l2: Color,
    val l3: Color,
    val line: Color,
    val line2: Color,
    val cardLine: Color,
    /** 본문 글자: t1 진하게 → t3 흐리게 */
    val t1: Color,
    val t2: Color,
    val t3: Color,
    val ac: Color,
    val onAc: Color,
    val acSoft: Color,
    val acLine: Color,
    /** 채운 강조 버튼. 야간에는 눈부시지 않게 ac 보다 어둡다. */
    val pri: Color,
    val onPri: Color,
    val warn: Color,
    val warnFill: Color,
    val warnSoft: Color,
    val warnLine: Color,
    val crit: Color,
    val critSoft: Color,
    val critLine: Color,
    val unk: Color,
    val unkSoft: Color,
    val shadow: Color,
    val isNight: Boolean,
)

val DayColors = HaidaColors(
    l0 = Color(0xFFE6EBF0),
    l1 = Color(0xFFFFFFFF),
    l2 = Color(0xFFF4F7FA),
    l3 = Color(0xFFE9EEF3),
    line = Color(0x1C121A24),
    line2 = Color(0x2E121A24),
    cardLine = Color(0x1C121A24),
    t1 = Color(0xFF10151B),
    t2 = Color(0xFF3F4752),
    t3 = Color(0xFF4C5560),
    ac = Color(0xFF00786E),
    onAc = Color(0xFFFFFFFF),
    acSoft = Color(0xFFDAF1EE),
    acLine = Color(0xFF7FC7C0),
    pri = Color(0xFF00786E),
    onPri = Color(0xFFFFFFFF),
    warn = Color(0xFF8A5200),
    warnFill = Color(0xFFF5A000),
    warnSoft = Color(0xFFFFF0D2),
    warnLine = Color(0xFFE0A23A),
    crit = Color(0xFFB3261E),
    critSoft = Color(0xFFFDE6E4),
    critLine = Color(0xFFE58E88),
    unk = Color(0xFF4C5560),
    unkSoft = Color(0xFFEEF1F4),
    shadow = Color(0xFF0A141E),
    isNight = false,
)

val NightColors = HaidaColors(
    l0 = Color(0xFF0A0C0F),
    l1 = Color(0xFF14171B),
    l2 = Color(0xFF1C2025),
    l3 = Color(0xFF252A30),
    line = Color(0x17FFFFFF),
    line2 = Color(0x29FFFFFF),
    cardLine = Color(0x29FFFFFF),
    t1 = Color(0xFFEDF0F3),
    t2 = Color(0xFFBCC3CC),
    t3 = Color(0xFF9AA2AC),
    ac = Color(0xFF4FD1C5),
    onAc = Color(0xFF00201D),
    acSoft = Color(0xFF10302C),
    acLine = Color(0xFF2C7F77),
    pri = Color(0xFF2A9D93),
    onPri = Color(0xFF04201D),
    warn = Color(0xFFF2B24A),
    warnFill = Color(0xFFF0A020),
    warnSoft = Color(0xFF2B2112),
    warnLine = Color(0xFF8A6420),
    crit = Color(0xFFFF7C70),
    critSoft = Color(0xFF331513),
    critLine = Color(0xFF95443C),
    unk = Color(0xFF9AA2AC),
    unkSoft = Color(0xFF1C2025),
    shadow = Color(0xFF000000),
    isNight = true,
)

val LocalHaidaColors = staticCompositionLocalOf { DayColors }

/**
 * Pleos 의 시스템 글꼴은 Asta Sans(42dot) 인데, 시스템 sans-serif 는 굵기를 한 단계씩 밀어 매핑한다(500 → Regular, 600 → Bold).
 * 프로토타입과 같은 굵기가 나오게 /system/fonts 의 파일을 직접 쓴다. 파일이 없는 기기에서는 기본 글꼴.
 */
val HaidaFont: FontFamily by lazy {
    val weights = listOf(
        "Light" to FontWeight.Light,
        "Regular" to FontWeight.Normal,
        "Medium" to FontWeight.Medium,
        "SemiBold" to FontWeight.SemiBold,
        "Bold" to FontWeight.Bold,
        "ExtraBold" to FontWeight.ExtraBold,
    )
    val files = weights.map { (name, weight) -> File("/system/fonts/AstaSansOTF-$name.otf") to weight }
    if (files.all { (file, _) -> file.canRead() }) FontFamily(files.map { (file, weight) -> Font(file, weight) }) else FontFamily.Default
}

/** 글자 크기 단계 (프로토타입 --f-hero · --f-t · --f-b · --f-s). Text(style = …) 는 바깥 글꼴을 물려받지 않아 글꼴을 함께 넣는다. */
object HaidaType {
    private fun style(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) = TextStyle(
        fontFamily = HaidaFont,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = line.sp,
        letterSpacing = spacing.em,
    )

    val hero = style(52, FontWeight.SemiBold, 62, -0.03)

    /** 카드 안 큰 한 문장(.t-hero 44) */
    val sentence = style(44, FontWeight.SemiBold, 53, -0.03)

    /** 차량 상태 요약 문장(40, 줄 간격 1.3) */
    val carSentence = style(40, FontWeight.SemiBold, 52, -0.03)
    val h1 = style(34, FontWeight.SemiBold, 42, -0.02)
    val title = style(32, FontWeight.SemiBold, 40, -0.02)
    val bodyBold = style(28, FontWeight.Bold, 35, -0.02)
    val bodySemi = style(28, FontWeight.SemiBold, 35, -0.02)
    val body = style(28, FontWeight.Normal, 40)
    val smallBold = style(24, FontWeight.Bold, 31, -0.01)
    val smallSemi = style(24, FontWeight.SemiBold, 30, -0.01)
    val small = style(24, FontWeight.Normal, 31)
    val eyebrow = style(24, FontWeight.Medium, 31, -0.01)

    /** 상태별 개수(.cs-n 72) */
    val count = style(72, FontWeight.Bold, 72, -0.02)
}

/** 앱 전체를 감싼다. 차량이 야간 모드면 야간 색을 쓴다. */
@Composable
fun HaidaTheme(night: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (night) NightColors else DayColors
    val scheme = if (night) {
        darkColorScheme(primary = c.ac, onPrimary = c.onAc, background = c.l0, surface = c.l1, onSurface = c.t1, error = c.crit)
    } else {
        lightColorScheme(primary = c.ac, onPrimary = c.onAc, background = c.l0, surface = c.l1, onSurface = c.t1, error = c.crit)
    }
    CompositionLocalProvider(LocalHaidaColors provides c) {
        MaterialTheme(colorScheme = scheme) {
            androidx.compose.material3.ProvideTextStyle(TextStyle(fontFamily = HaidaFont, color = c.t1), content)
        }
    }
}

/** 화면 코드에서 `Haida.colors.ac` 처럼 쓴다. */
object Haida {
    val colors: HaidaColors
        @Composable get() = LocalHaidaColors.current
}
