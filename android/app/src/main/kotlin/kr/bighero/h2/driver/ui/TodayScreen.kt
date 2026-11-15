package kr.bighero.h2.driver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.bighero.h2.core.AlertKind
import kr.bighero.h2.core.DaySim
import kr.bighero.h2.core.Plan
import kr.bighero.h2.core.Step
import kr.bighero.h2.core.clock
import kr.bighero.h2.core.quantile
import kr.bighero.h2.core.won
import kr.bighero.h2.driver.DriverViewModel
import kr.bighero.h2.driver.UiState

@Composable
fun TodayScreen(vm: DriverViewModel, ui: UiState, wide: Boolean) {
    val sim = vm.sim
    if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LeftColumn(vm, ui)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DaySummary(sim, ui.now)
            NaviMap(sim, ui, Modifier.weight(1f).fillMaxWidth())
        }
    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LeftColumn(vm, ui)
        NaviMap(sim, ui, Modifier.fillMaxWidth().height(360.dp))
        DaySummary(sim, ui.now)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LeftColumn(vm: DriverViewModel, ui: UiState) {
    val sim = vm.sim
    ui.banner?.let { a ->
        val col = when (a.kind) { AlertKind.BERTH_LATE, AlertKind.EXPORT_RISK, AlertKind.MISSED -> C.warn; AlertKind.TAKEN_BY_OTHER -> C.export; else -> C.brand }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(col.copy(alpha = 0.18f)).clickable { vm.dismissBanner() }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(col)); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${clock(a.t)} · ${a.title}", fontWeight = FontWeight.SemiBold)
                Text(a.body, color = C.sub, style = MaterialTheme.typography.bodyMedium)
            }
            Text("닫기", color = C.sub)
        }
    }
    H2Gauge(vm.vehicle.h2Kg(), vm.vehicle.tankKg(), sim.world.kgkm, sim.world.reserve, Modifier.fillMaxWidth())
    if (!sim.idle) CurrentCard(vm, ui)
    else Recommendation(vm, ui)
}

/** 지금 하는 일 */
@Composable
private fun CurrentCard(vm: DriverViewModel, ui: UiState) {
    val sim = vm.sim
    val s = sim.currentSeg() ?: sim.upcoming().firstOrNull()
    val endAll = sim.segs.lastOrNull()?.end ?: ui.now
    Panel(Modifier.fillMaxWidth(), color = C.panel2) {
        if (s == null) { Text("다음 일을 준비 중", style = MaterialTheme.typography.headlineSmall); return@Panel }
        Chip(if (s.start > ui.now) "곧 시작" else s.kind.label, kindColor(s.kind), filled = true)
        Spacer(Modifier.height(10.dp))
        val title = when {
            s.kind.name.startsWith("DRIVE") -> "${sim.world.name(s.to)}(으)로 이동"
            else -> "${sim.world.name(s.from)}에서 ${s.note.ifBlank { s.kind.label }}"
        }
        Text(title, style = MaterialTheme.typography.headlineMedium)
        s.job?.let { Text(sim.label(it), color = C.sub, style = MaterialTheme.typography.bodyLarge) }
        Spacer(Modifier.height(12.dp))
        val f = ((ui.now - s.start) / (s.end - s.start)).coerceIn(0.0, 1.0)
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(C.line)) {
            Box(Modifier.fillMaxWidth(f.toFloat()).fillMaxHeight().clip(CircleShape).background(kindColor(s.kind)))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("${clock(s.start)} 시작", color = C.sub, modifier = Modifier.weight(1f))
            Text("${clock(s.end)} 끝 (${DaySim.fmtH(((s.end - ui.now).coerceAtLeast(0.0)) / 60)} 남음)", color = C.sub)
        }
        Spacer(Modifier.height(10.dp))
        Text("이번 일 모두 마치는 시각 ${clock(endAll)}", style = MaterialTheme.typography.titleMedium)
        vm.nextDriveTarget()?.let { to ->
            Spacer(Modifier.height(12.dp))
            BigButton("${vm.navi.label}로 길안내 · ${sim.world.name(to)}", { vm.navigate(to) }, Modifier.fillMaxWidth())
        }
        val next = sim.upcoming().take(3)
        if (next.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("다음", color = C.sub, style = MaterialTheme.typography.labelMedium)
            next.forEach { n ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(clock(n.start), fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(kindColor(n.kind))); Spacer(Modifier.width(8.dp))
                    Text(if (n.kind.name.startsWith("DRIVE")) "${sim.world.name(n.to)}(으)로" else n.note.ifBlank { n.kind.label })
                }
            }
        }
    }
}

/** 쉬고 있을 때: 추천 일정 1~3안과 이유 */
@Composable
private fun Recommendation(vm: DriverViewModel, ui: UiState) {
    val sim = vm.sim
    Panel(Modifier.fillMaxWidth(), color = C.panel2) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(if (sim.onShift) "추천 일정" else "출근 전 미리 보기", C.brand, filled = true)
            Spacer(Modifier.weight(1f))
            if (ui.computing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else SmallButton("다시 계산", { vm.replan() })
        }
        Spacer(Modifier.height(10.dp))
        val plans = ui.plans
        if (plans.isEmpty()) {
            Text(if (ui.computing) "계산 중이에요" else "지금 근무시간 안에 맡을 수 있는 일이 없어요", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text("수소 15% 남기기·반입 마감·근무 끝(30분 여유)·충전소 운영시간을 모두 지키는 조합이 없어요.", color = C.sub)
            if (sim.loc != sim.bundle.params.home) {
                Spacer(Modifier.height(12.dp))
                BigButton("공장으로 돌아가기", { vm.goHome() }, Modifier.fillMaxWidth())
            }
            return@Panel
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            plans.take(3).forEachIndexed { i, p -> SmallButton("${i + 1}안  ${won(p.v)}", { vm.select(i) }, selected = ui.selected == i) }
        }
        val p = plans[ui.selected.coerceIn(0, plans.size - 1)]
        Spacer(Modifier.height(12.dp))
        p.steps.forEachIndexed { i, st -> StepRow(sim, st, i == 0, ui.now) }
        Spacer(Modifier.height(10.dp))
        BigButton(if (sim.onShift) "이 일정 받기" else "출근하면 받을 수 있어요", { vm.accept() }, Modifier.fillMaxWidth(), enabled = sim.onShift)
        Text("첫 일만 확정하고, 끝나면 그때 상황으로 다시 추천해요.", color = C.sub, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
        WhyBox(sim, p)
    }
}

@Composable
private fun StepRow(sim: DaySim, st: Step, first: Boolean, now: Double) {
    val o = sim.world.orders[st.id]
    val col = orderColor(o)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(Modifier.padding(top = 6.dp).size(12.dp).clip(CircleShape).background(col))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (st.isCharge) {
                val ch = sim.world.chargers[st.id]!!
                Text("${ch.name} 충전", style = MaterialTheme.typography.titleLarge)
                Text("${clock(st.chargeStart!!)} 시작 · ${clock(st.end)} 끝 · %.0fkg · 약 ${won(st.chargeWon!!)}".format(st.chargeKg), color = C.sub)
                if (st.waitH > 0.01) Text("앞 차 충전 기다림 ${DaySim.fmtH(st.waitH)}", color = C.warn)
            } else if (o != null) {
                Text(sim.label(o.id), style = MaterialTheme.typography.titleLarge)
                Text("${sim.world.name(o.origin)} → ${sim.world.name(o.dest)}", color = C.sub)
                st.boxStop?.let { b ->
                    Text("가는 길에 ${sim.world.name(b.at)}에서 빈 컨테이너 ${if (o.isExport) "받기" else "내려놓기"}", color = C.box)
                }
                if (o.isExport) {
                    Text("공장 도착 ${clock(st.pickupArr!!)} · 터미널 도착 ${clock(st.portArr!!)} (공지 마감 ${clock(o.cutoff!!)})", color = C.sub)
                    val pm = st.pMiss ?: 0.0
                    val warn = pm >= sim.bundle.params.export_risk_warn
                    Text("배 놓칠 확률 ${"%.0f".format(pm * 100)}%" + if (st.risk > 0) " · 위험 비용 ${won(st.risk)}" else "",
                        color = if (warn) C.bad else if (pm > 0) C.warn else C.brand)
                } else if (o.isImport) {
                    val y = sim.world.delayInfo(o.vessel_row, now)
                    val av = o.avail!!; val lo = av + 60 * quantile(y, 0.1); val hi = av + 60 * quantile(y, 0.9)
                    Text("터미널 도착 ${clock(st.portArr!!)} · " + (if (y.size == 1) "반출 가능 ${clock(lo)} (접안 확인됨)" else "반출 예측 ${clock(lo)}~${clock(hi)} (공지 ${clock(av)})"), color = C.sub)
                    if (st.idleH > 0.05) Text("${DaySim.fmtH(st.idleH)} 뒤에 출발하면 맞아요", color = C.warn)
                    if (st.waitH > 0.05) Text("터미널 대기 예상 ${DaySim.fmtH(st.waitH)}", color = C.warn)
                }
                Text("끝 ${clock(st.end)} · 수소 %.0fkg 남음".format(st.h2End), color = C.sub, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (first) Chip("지금", C.brand)
    }
}

/** 왜 이 일정? 점수 = 용차 단가 − 수소 − 통행료 − 대기·노는 시간 − 늦음 − 놓침 위험 − 초과근무 */
@Composable
private fun WhyBox(sim: DaySim, p: Plan) {
    val t = p.totals
    val wage = sim.bundle.params.wait_won_h
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.panel).padding(14.dp)) {
        Text("왜 이 일정?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        KeyVal("용차에 안 맡겨서 아끼는 돈", "+${won(t.fare)}", C.brand)
        KeyVal("수소 (%.0fkm)".format(t.kmEmpty + t.kmLoaded), "−${won(p.h2Cost)}")
        KeyVal("통행료", "−${won(t.toll)}")
        if (t.waitH + t.idleH > 0.01) KeyVal("기다리는 시간 ${DaySim.fmtH(t.waitH + t.idleH)}", "−${won((t.waitH + t.idleH) * wage)}")
        if (t.lateCost > 1) KeyVal("수입 늦음", "−${won(t.lateCost)}")
        if (t.risk > 1) KeyVal("배 놓칠 위험", "−${won(t.risk)}", C.warn)
        if (p.overtimeCost > 1) KeyVal("초과근무(공장 복귀 기준)", "−${won(p.overtimeCost)}", C.warn)
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(C.line))
        KeyVal("회사가 아끼는 돈(점수)", won(p.v), C.text, bold = true)
        Text("빈 차 %.0fkm · 짐 실은 %.0fkm".format(t.kmEmpty, t.kmLoaded), color = C.sub, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DaySummary(sim: DaySim, now: Double) {
    val l = sim.ledgerAt(now)
    Panel(Modifier.fillMaxWidth()) {
        Text("오늘 누적", color = C.sub, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("맡은 일", "${l.jobsDone}건")
            Stat("아낀 용차비", won(l.fareSaved))
            Stat("수소·통행료", won(l.h2Won + l.tollWon))
            Stat("놓침", "${l.missN}건", if (l.missN > 0) C.bad else C.text)
        }
    }
}

@Composable
private fun Stat(k: String, v: String, color: Color = C.text) {
    Column { Text(v, style = MaterialTheme.typography.titleLarge, color = color); Text(k, color = C.sub, style = MaterialTheme.typography.labelMedium) }
}
