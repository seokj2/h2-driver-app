package kr.bighero.h2.driver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.bighero.h2.core.Order
import kr.bighero.h2.core.SegKind
import kr.bighero.h2.core.clock
import kr.bighero.h2.driver.DriverViewModel
import kr.bighero.h2.driver.UiState

@Composable
fun Panel(modifier: Modifier = Modifier, color: Color = C.panel, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(color).border(1.dp, C.line, RoundedCornerShape(18.dp)).padding(18.dp), content = content)
}

@Composable
fun Chip(text: String, color: Color, filled: Boolean = false) {
    Text(text, color = if (filled) Color.White else color, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (filled) color else color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp))
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = C.brandDark, enabled: Boolean = true) {
    Box(modifier.height(64.dp).clip(RoundedCornerShape(14.dp)).background(if (enabled) color else C.line)
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = if (enabled) Color.White else C.sub, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun SmallButton(text: String, onClick: () -> Unit, selected: Boolean = false) {
    Text(text, color = if (selected) Color.White else C.sub, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) C.brandDark else C.panel2)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp))
}

fun kindColor(k: SegKind): Color = when (k) {
    SegKind.DRIVE_LOADED -> C.brand
    SegKind.DRIVE_EMPTY -> C.empty
    SegKind.DRIVE_BOX -> C.box
    SegKind.CHARGE -> C.charge
    SegKind.WAIT -> C.warn
    SegKind.TERMINAL -> Color(0xFF6FA8DC)
    SegKind.DEPOT -> C.box
    SegKind.LOAD -> Color(0xFFE0C35A)
}

fun orderColor(o: Order?) = when (o?.type) { "export" -> C.export; "import" -> C.import; null -> C.charge; else -> C.empty }

/** 맨 위 줄: 앱 이름·트럭·지금 시각·시연 시계 조작 */
@Composable
fun TopBar(vm: DriverViewModel, ui: UiState, wide: Boolean) {
    val sim = vm.sim
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(C.brand), contentAlignment = Alignment.Center) {
            Text("H2", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        if (!wide) Spacer(Modifier.weight(1f)) else Column(Modifier.weight(1f)) {
            Text("H2 회랑 · ${sim.driver.name}", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${sim.bundle.meta.day} · 근무 ${clock(sim.shiftStart)}~${clock(sim.shiftEnd)}", color = C.sub, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            FreshnessDot(vm, ui.now)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                MicButton(vm, ui.listening)
                SmallButton(if (vm.voiceOn) "🔊" else "🔇", { vm.toggleVoice() })
            }
        }
        Text(clock(ui.now), style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 12.dp))
        // 시연 시계(실제 차량에서는 없앰): ▶/일시정지, 배속, 다음 일까지
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, C.line, RoundedCornerShape(12.dp)).padding(6.dp)) {
            SmallButton(if (ui.running) "❚❚" else "▶", { vm.toggleRun() }, selected = ui.running)
            if (wide) vm.speeds.forEach { s -> SmallButton("×$s", { vm.setSpeed(s) }, selected = ui.speed == s) }
            else SmallButton("×${ui.speed}", { vm.setSpeed(vm.speeds[(vm.speeds.indexOf(ui.speed) + 1) % vm.speeds.size]) })
            SmallButton("⏭", { vm.skipToNext() })
            if (wide) SmallButton("처음", { vm.restart() })
        }
    }
}

/** 수소 게이지 */
@Composable
fun H2Gauge(kg: Double, tank: Double, kgPerKm: Double, reserve: Double, modifier: Modifier = Modifier) {
    val f = (kg / tank).coerceIn(0.0, 1.0)
    val col = when { kg < reserve * 1.3 -> C.bad; f < 0.35 -> C.warn; else -> C.brand }
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${(f * 100).toInt()}", style = MaterialTheme.typography.displaySmall, color = col)
            Text("%", style = MaterialTheme.typography.titleMedium, color = C.sub, modifier = Modifier.padding(start = 2.dp, end = 16.dp))
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(C.line)) {
                    Box(Modifier.fillMaxWidth(f.toFloat()).fillMaxHeight().clip(CircleShape).background(col))
                }
                Spacer(Modifier.height(6.dp))
                Text("%.1fkg / %.0fkg · 약 %.0fkm 더 갈 수 있음".format(kg, tank, ((kg - reserve).coerceAtLeast(0.0)) / kgPerKm),
                    color = C.sub, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun KeyVal(k: String, v: String, color: Color = C.text, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(k, color = C.sub, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(v, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}
