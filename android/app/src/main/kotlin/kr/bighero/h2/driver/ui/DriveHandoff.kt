package kr.bighero.h2.driver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.bighero.h2.core.AlertKind
import kr.bighero.h2.core.DaySim
import kr.bighero.h2.core.Seg
import kr.bighero.h2.core.clock
import kr.bighero.h2.driver.DriverViewModel
import kr.bighero.h2.driver.UiState

/** 지금 실제로 차가 움직이는 운전 구간(제자리 대기는 빼고) */
fun drivingSeg(sim: DaySim, now: Double): Seg? =
    sim.currentSeg(now)?.takeIf { it.kind.name.startsWith("DRIVE") && it.start <= now && it.from != it.to }

/**
 * 운전 중 화면: 길안내는 순정 내비가 맡고(앱이 운전 시작 때 넘김), 우리 앱은 그 위의 작은 팝업만.
 * 차량에서는 순정 내비가 앞에 오고 팝업은 헤즈업 알림(Popups.kt)으로 뜬다.
 * 이 화면은 그 모습을 앱 안에서 그대로 보여 준다(시연 영상·순정 내비가 없는 기기).
 * 도착하면 앱이 다시 앞으로 와서 터미널 컨테이너 위치·충전 화면을 띄운다.
 */
@Composable
fun DrivingScreen(vm: DriverViewModel, ui: UiState, d: Seg) {
    val sim = vm.sim
    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(18.dp))) {
        NaviMap(sim, ui, Modifier.fillMaxSize())
        Text("${vm.navi.label} 길안내 중", color = Color.White, style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC111111)).padding(horizontal = 12.dp, vertical = 6.dp))
        DrivePopup(vm, ui, d, Modifier.align(Alignment.BottomStart).padding(16.dp).widthIn(max = 520.dp).fillMaxWidth())
    }
}

/** 순정 내비 위에 뜨는 우리 앱 팝업: 어디로·몇 시 도착·도착하면 할 일, 그리고 꼭 알아야 할 알림만 */
@Composable
private fun DrivePopup(vm: DriverViewModel, ui: UiState, d: Seg, modifier: Modifier) {
    val sim = vm.sim
    val f = ((ui.now - d.start) / (d.end - d.start)).coerceIn(0.0, 1.0)
    val km = vm.roads?.get(d.from, d.to)?.let { (1 - f) * it.length / 1000 }
    val after = sim.upcoming().firstOrNull { it.start >= d.end - 0.01 }
    Column(modifier.shadow(10.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(C.panel).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(C.brand), contentAlignment = Alignment.Center) {
                Text("H2", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text("H2 회랑 기사", color = C.sub, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        ui.banner?.let { a ->
            val col = when (a.kind) { AlertKind.BERTH_LATE, AlertKind.EXPORT_RISK, AlertKind.MISSED -> C.warn; else -> C.brand }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(col.copy(alpha = 0.16f)).clickable { vm.dismissBanner() }.padding(12.dp)) {
                Text(a.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(a.body, color = C.sub, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(sim.world.name(d.to), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text((if (km != null) "%.1fkm · ".format(km) else "") + "${clock(d.end)} 도착", style = MaterialTheme.typography.titleLarge)
        if (after != null) Text("도착하면 · " + (if (after.kind.name.startsWith("DRIVE")) "${sim.world.name(after.to)}(으)로 출발" else after.note.ifBlank { after.kind.label }),
            color = C.sub, style = MaterialTheme.typography.titleMedium)
        val rest = drivenSinceBreak(sim, ui.now)
        if (rest >= 105) { Spacer(Modifier.height(8.dp)); RestLine(sim, ui.now) }
    }
}
