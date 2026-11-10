package kr.contest.pleosassistant.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 임시 디자인의 공통 부분. 팀 UI 템플릿을 받으면 화면 파일들과 함께 이 파일을 바꾼다.
// 크기는 Pleos 에뮬레이터 앱 영역(약 1680 × 1210dp, 160dpi)에서 정차 중 한눈에 읽히도록 잡았다.

internal object Palette {
    val background = Color(0xFF0E1116)
    val card = Color(0xFF181C23)
    val cardBorder = Color(0xFF262B34)
    val text = Color(0xFFF2F4F7)
    val textMuted = Color(0xFF9AA3AF)
    val accent = Color(0xFF6EA8FE)
    val ok = Color(0xFF3DD68C)
    val caution = Color(0xFFFFB020)
    val urgent = Color(0xFFFF5A52)
    val neutral = Color(0xFF7D8590)
}

internal val CardShape = RoundedCornerShape(28.dp)

/** 화면 좌측 상단 제목. trailing 은 오른쪽 끝(버튼 등) */
@Composable
internal fun ScreenHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        IconTile(icon, Palette.accent, tileSize = 68, iconSize = 40)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Palette.text, fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 52.sp)
            Text(subtitle, color = Palette.textMuted, fontSize = 22.sp)
        }
        trailing()
    }
}

/** 요약 한 문장 띠 */
@Composable
internal fun SummaryBanner(icon: ImageVector, color: Color, text: String, contentDescription: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(color.copy(alpha = 0.12f))
            .border(1.5.dp, color.copy(alpha = 0.4f), CardShape)
            .padding(horizontal = 36.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Box(
            modifier = Modifier.size(68.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = contentDescription, tint = color, modifier = Modifier.size(40.dp))
        }
        Text(text, color = Palette.text, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 44.sp)
    }
}

@Composable
internal fun IconTile(icon: ImageVector, color: Color, tileSize: Int, iconSize: Int) {
    Box(
        modifier = Modifier
            .size(tileSize.dp)
            .clip(RoundedCornerShape((tileSize / 4).dp))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize.dp))
    }
}

/** 차 화면에서 누르기 쉬운 큰 버튼(높이 64dp 이상) */
@Composable
internal fun BigButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Palette.accent,
    filled: Boolean = true,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val content: @Composable () -> Unit = {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(10.dp))
        }
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
    val shape = RoundedCornerShape(18.dp)
    val padding = PaddingValues(horizontal = 30.dp, vertical = 16.dp)
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 64.dp),
            shape = shape,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Palette.background),
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 64.dp),
            shape = shape,
            contentPadding = padding,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, color.copy(alpha = 0.7f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        ) { content() }
    }
}
