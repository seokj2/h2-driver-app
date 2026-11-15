package kr.bighero.h2.driver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.bighero.h2.core.AlertKind
import kr.bighero.h2.core.SegKind
import kr.bighero.h2.core.clock
import kr.bighero.h2.core.won
import kr.bighero.h2.driver.DriverViewModel
import kr.bighero.h2.driver.UiState

/** 전체 일정: 지난 구간·지금·확정된 앞 구간 */
@Composable
fun ScheduleScreen(vm: DriverViewModel, ui: UiState) {
    val sim = vm.sim
    @Suppress("UNUSED_VARIABLE") val tick = ui.tick
    val segs = sim.segs.toList()
    if (segs.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp)) { Text("아직 받은 일정이 없어요. '오늘'에서 추천 일정을 받아 보세요.", color = C.sub, style = MaterialTheme.typography.bodyLarge) }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(segs) { s ->
            val past = s.end <= ui.now; val cur = s.start <= ui.now && ui.now < s.end
            Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(if (cur) C.panel2 else C.panel).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("${clock(s.start)}\n${clock(s.end)}", color = if (past) C.sub else C.text, modifier = Modifier.width(80.dp), fontWeight = FontWeight.SemiBold)
                Box(Modifier.size(12.dp).clip(CircleShape).background(kindColor(s.kind).copy(alpha = if (past) 0.4f else 1f)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val t = if (s.kind.name.startsWith("DRIVE")) "${sim.world.name(s.from)} → ${sim.world.name(s.to)}" else "${sim.world.name(s.from)} · ${s.note.ifBlank { s.kind.label }}"
                    Text(t, color = if (past) C.sub else C.text, style = MaterialTheme.typography.titleMedium)
                    Text(s.kind.label + (s.job?.let { " · " + sim.label(it) } ?: ""), color = C.sub, style = MaterialTheme.typography.bodyMedium)
                }
                Text("%.0fkg".format(s.h2End), color = C.sub)
                if (cur) { Spacer(Modifier.width(10.dp)); Chip("지금", C.brand, filled = true) }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
fun AlertsScreen(vm: DriverViewModel, ui: UiState) {
    @Suppress("UNUSED_VARIABLE") val tick = ui.tick
    val list = vm.sim.alerts.reversed()
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp)) { Text("아직 알림이 없어요. 배 접안이 늦어지거나 일감이 다른 트럭에 배정되면 여기에 떠요.", color = C.sub, style = MaterialTheme.typography.bodyLarge) }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list) { a ->
            val col = when (a.kind) { AlertKind.BERTH_LATE, AlertKind.EXPORT_RISK, AlertKind.MISSED -> C.warn; AlertKind.TAKEN_BY_OTHER -> C.export; else -> C.brand }
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(col)); Spacer(Modifier.width(10.dp))
                    Text(clock(a.t), color = C.sub); Spacer(Modifier.width(10.dp))
                    Text(a.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (a.kind == AlertKind.SENT) Chip("내가 보냄", C.sub)
                }
                Text(a.body, color = C.sub, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** 수소: 잔량, 오늘 충전, 충전소 운영시간 */
@Composable
fun H2Screen(vm: DriverViewModel, ui: UiState) {
    val sim = vm.sim
    @Suppress("UNUSED_VARIABLE") val tick = ui.tick
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip("수소전기 트랙터 · 탱크 %.0fkg".format(vm.vehicle.tankKg()), C.brand, filled = true)
            Spacer(Modifier.width(8.dp))
            Text("전기 트럭은 같은 화면이 배터리(kWh)·급속충전기로 바뀌어요(계획)", color = C.sub, style = MaterialTheme.typography.labelMedium)
        }
        H2Gauge(vm.vehicle.h2Kg(), vm.vehicle.tankKg(), sim.world.kgkm, sim.world.reserve, Modifier.fillMaxWidth())
        Text("값 출처: ${vm.vehicle.source} · 잔량 15%%(%.1fkg) 아래로 내려가는 일정은 추천하지 않아요".format(sim.world.reserve), color = C.sub, style = MaterialTheme.typography.bodyMedium)
        val ch = sim.segs.filter { it.kind == SegKind.CHARGE }
        Panel(Modifier.fillMaxWidth()) {
            Text("오늘 충전", style = MaterialTheme.typography.titleMedium)
            if (ch.isEmpty()) Text("아직 없어요", color = C.sub)
            ch.forEach { c -> KeyVal("${clock(c.start)} ${sim.world.name(c.from)}", c.note) }
        }
        sim.bundle.chargers.forEach { c ->
            Panel(Modifier.fillMaxWidth()) {
                Text(c.name, style = MaterialTheme.typography.titleLarge)
                ChargerLive(vm, c, ui.now)
                KeyVal("운영", "${clock(c.open)}~${clock(c.close)}" + (c.brk?.let { " (쉬는 시간 ${clock(it[0])}~${clock(it[1])})" } ?: ""))
                KeyVal("충전 속도", "%.0fkg/시간 · 50kg 약 %.0f분".format(c.rate_kg_h, c.setup_min + 50 / c.rate_kg_h * 60))
                KeyVal("단가", "${won(c.price_krw_kg)}/kg")
                KeyVal("트랙터 충전기", "${c.cap}기")
            }
        }
        Text("운영 중·닫힘은 실제 운영시간으로 계산해요. 대기 대수와 예약은 시연용 예시이고, 한국석유관리원 실시간 API 키를 넣으면 실제 값으로 바뀌어요.", color = C.sub, style = MaterialTheme.typography.labelMedium)
    }
}
