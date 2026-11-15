package kr.bighero.h2.driver.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 색. 플레오스 Connect 기본 화면에서 잰 값에 맞춤(에뮬레이터, 라이트 모드): 바탕 #F7F7F7, 카드 #FFFFFF, 글자 #050505, 보조 글자 #6B6B6B.
 * 주 버튼은 플레오스 검색창과 같은 검정, 선택 표시는 플레오스 주황. 초록은 수소·짐 실은 주행에만 의미 색으로 씀.
 * 시스템이 야간 모드면 어두운 색으로 자동 전환(플레오스 낮/밤 동작과 같게).
 */
object C {
    var dark = false
    private fun p(light: Long, night: Long) = Color(if (dark) night else light)
    val bg get() = p(0xFFF7F7F7, 0xFF0E1726)
    val panel get() = p(0xFFFFFFFF, 0xFF16233A)
    val panel2 get() = p(0xFFFFFFFF, 0xFF1D2D49)
    val line get() = p(0xFFE3E3E3, 0xFF2A3B5C)
    val text get() = p(0xFF050505, 0xFFF1F5FB)
    val sub get() = p(0xFF6B6B6B, 0xFF9DB0CC)
    val brand get() = p(0xFF16A37F, 0xFF2BB58F)      // 수소·짐 실은 주행
    val brandDark get() = p(0xFF111111, 0xFF2BB58F)  // 주 버튼
    val accent get() = p(0xFFF5821F, 0xFFF5921F)     // 선택·강조(플레오스 주황)
    val export get() = p(0xFFE5623A, 0xFFF08A5D)
    val import get() = p(0xFF16A37F, 0xFF4FC3A1)
    val empty get() = p(0xFF8A929E, 0xFF8C9BB5)
    val warn get() = p(0xFFC98300, 0xFFF4B740)
    val bad get() = p(0xFFD64545, 0xFFEF5D5D)
    val charge get() = p(0xFF2F7FD6, 0xFF5DA9F0)
    val box get() = p(0xFF8A6FD8, 0xFFB4A0F0)
}

private val typo = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun H2Theme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    C.dark = dark
    MaterialTheme(
        colorScheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
            primary = C.accent, onPrimary = Color.White, background = C.bg, surface = C.panel, onSurface = C.text,
            onBackground = C.text, surfaceVariant = C.panel2, onSurfaceVariant = C.sub, outline = C.line,
            secondaryContainer = if (dark) Color(0xFF5A3A1C) else Color(0xFFFFE7D2), onSecondaryContainer = C.text,
        ),
        typography = typo,
        content = { key(dark) { content() } },   // 낮/밤이 바뀌면 화면 전체를 새 색으로 다시 그림
    )
}
