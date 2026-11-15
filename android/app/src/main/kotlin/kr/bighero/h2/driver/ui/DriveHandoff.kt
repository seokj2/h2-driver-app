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
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp).widthIn(max = 520.dp).fillMaxWidth()) {
            H2Gauge(vm.vehicle.h2Kg(), vm.vehicle.tankKg(), sim.world.kgkm, sim.world.reserve,
                Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)))
            Spacer(Modifier.height(10.dp))
            DrivePopup(vm, ui, d, Modifier.fillMaxWidth())
        }
        val L = vm.roads?.get(d.from, d.to)?.length ?: return@Box
        val f = ((ui.now - d.start) / (d.end - d.start)).coerceIn(0.0, 1.0)
        val nav = navInfo(L, f, d.end - d.start, ui.now)
        // 오른쪽 위: 앞쪽 안전 안내(과속 단속·방지턱)
        nav.ahead?.let { (kind, m) -> SafetyCard(kind, m, nav.limit, Modifier.align(Alignment.TopEnd).padding(16.dp)) }
        // 오른쪽 아래: 제한 속도 표지 + 지금 속도
        SpeedCluster(nav.speed, nav.limit, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 84.dp))
    }
}

/** 내비 정보: 속도는 그 구간 평균 속도(구간 길이 ÷ 걸리는 시간)에 살짝 흔들림, 제한 속도는 도로 종류(평균 속도로 가늠),
 *  과속 단속·방지턱 위치는 경로를 따라 정해진 간격(실제 차량에서는 순정 내비 안전 정보로 바꿈) */
data class NavInfo(val speed: Int, val limit: Int, val ahead: Pair<String, Double>?)

fun navInfo(lenM: Double, f: Double, minutes: Double, now: Double): NavInfo {
    val pos = f * lenM; val left = lenM - pos
    val avg = lenM / 1000 / (minutes / 60).coerceAtLeast(0.01)
    val town = pos < 800 || left < 1500                         // 공장·항만 안팎 도로
    val limit = when { town -> 30; avg >= 55 -> 80; else -> 60 }  // 화물차 고속도로 80
    val wob = 0.94 + 0.05 * kotlin.math.sin(now * 3.1) + 0.02 * kotlin.math.sin(now * 11.0)
    val speed = (if (town) minOf(avg, 30.0) * 0.9 else minOf(avg * wob, limit - 2.0)).toInt().coerceAtLeast(0)
    val events = ArrayList<Pair<Double, String>>()
    var x = 2100.0
    while (x < lenM - 2000) { events.add(x to "camera"); x += 4300.0 }
    for (b in listOf(300.0, 600.0)) if (b < lenM) events.add(b to "bump")
    for (b in listOf(1100.0, 600.0, 250.0)) if (lenM - b > 0) events.add(lenM - b to "bump")
    val next = events.filter { it.first > pos && it.first - pos <= 1000 }.minByOrNull { it.first }
    return NavInfo(speed, limit, next?.let { it.second to (it.first - pos) })
}

@Composable
private fun SpeedCluster(speed: Int, limit: Int, modifier: Modifier) {
    val over = speed > limit
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.shadow(8.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
            .background(if (over) C.warn else C.panel).padding(horizontal = 18.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$speed", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = if (over) Color.White else C.text)
            Text("km/h", style = MaterialTheme.typography.labelLarge, color = if (over) Color.White else C.sub)
        }
        Spacer(Modifier.width(10.dp))
        LimitSign(limit, 76.dp)
    }
}

@Composable
private fun LimitSign(limit: Int, s: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(s).shadow(8.dp, androidx.compose.foundation.shape.CircleShape).clip(androidx.compose.foundation.shape.CircleShape)
        .background(Color(0xFFE53935)).padding(s * 0.11f).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White),
        contentAlignment = Alignment.Center) {
        Text("$limit", fontSize = (s.value * 0.38f).sp, fontWeight = FontWeight.Black, color = Color(0xFF111111))
    }
}

@Composable
private fun SafetyCard(kind: String, m: Double, limit: Int, modifier: Modifier) {
    Row(modifier.shadow(10.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(C.panel).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (kind == "camera") {
            LimitSign(limit, 56.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("📷 과속 단속", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${(m / 10).toInt() * 10}m 앞", style = MaterialTheme.typography.headlineSmall, color = C.warn, fontWeight = FontWeight.Bold)
            }
        } else {
            BumpSign(Modifier.size(60.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("과속방지턱", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${(m / 10).toInt() * 10}m 앞 · 천천히", style = MaterialTheme.typography.headlineSmall, color = C.warn, fontWeight = FontWeight.Bold)
            }
        }
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


/** 과속방지턱 주의 표지: 빨간 테두리 흰 삼각형 안에 검은 둔덕 */
@Composable
private fun BumpSign(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height
        fun tri(inset: Float) = androidx.compose.ui.graphics.Path().apply {
            moveTo(w / 2, inset * 1.6f); lineTo(w - inset, h - inset * 0.8f); lineTo(inset, h - inset * 0.8f); close()
        }
        drawPath(tri(0f), Color(0xFFE53935))
        drawPath(tri(w * 0.13f), Color.White)
        // 길(밑줄)과 둔덕
        val base = h * 0.74f
        drawRect(Color(0xFF111111), androidx.compose.ui.geometry.Offset(w * 0.27f, base), androidx.compose.ui.geometry.Size(w * 0.46f, h * 0.045f))
        drawPath(androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.36f, base); cubicTo(w * 0.42f, h * 0.50f, w * 0.58f, h * 0.50f, w * 0.64f, base); close()
        }, Color(0xFF111111))
    }
}
