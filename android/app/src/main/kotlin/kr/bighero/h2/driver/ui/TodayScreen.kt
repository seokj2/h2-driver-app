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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kr.bighero.h2.core.SegKind
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            // 건수·퇴근 같은 개인 정보는 쉴 때(일정 고를 때)만. 일하는 중에는 업무 정보만
            if (sim.idle) DaySummary(sim, ui.now)
            // 터미널 안에서는 지도 대신 컨테이너 위치(장치장 배치도)
            val yard = sim.currentSeg()?.takeIf { it.kind == SegKind.TERMINAL && it.job != null }
            val chg = chargeSegNow(sim)
            if (yard != null) YardPane(vm, yard, Modifier.weight(1f).fillMaxWidth())
            else if (chg != null) ChargePane(vm, chg, ui.now, Modifier.weight(1f).fillMaxWidth())
            else NaviMap(sim, ui, Modifier.weight(1f).fillMaxWidth())
        }
    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LeftColumn(vm, ui)
        val yard = sim.currentSeg()?.takeIf { it.kind == SegKind.TERMINAL && it.job != null }
        val chg = chargeSegNow(sim)
        if (yard != null) YardPane(vm, yard, Modifier.fillMaxWidth().height(420.dp))
        else if (chg != null) ChargePane(vm, chg, ui.now, Modifier.fillMaxWidth().height(460.dp))
        else NaviMap(sim, ui, Modifier.fillMaxWidth().height(360.dp))
        if (sim.idle) DaySummary(sim, ui.now)
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
    // 충전 중에는 오른쪽 충전 화면이 잔량을 보여 주므로 게이지를 한 번만
    if (chargeSegNow(sim) == null) H2Gauge(vm.vehicle.h2Kg(), vm.vehicle.tankKg(), sim.world.kgkm, sim.world.reserve, Modifier.fillMaxWidth())
    if (!sim.idle) CurrentCard(vm, ui)
    else Recommendation(vm, ui)
}

/** 지금 하는 일 */
@Composable
private fun CurrentCard(vm: DriverViewModel, ui: UiState) {
    val sim = vm.sim
    val s = sim.currentSeg() ?: sim.upcoming().firstOrNull()
    Panel(Modifier.fillMaxWidth(), color = C.panel2) {
        if (s == null) { Text("다음 일을 준비 중", style = MaterialTheme.typography.headlineSmall); return@Panel }
        // 운전 중: 다음 할 일 + 남은 거리 + 도착 시각만 크게(주행 중 주의 분산 줄이기)
        if (s.kind.name.startsWith("DRIVE") && s.start <= ui.now && s.from != s.to) {
            val f = ((ui.now - s.start) / (s.end - s.start)).coerceIn(0.0, 1.0)
            val km = vm.roads?.get(s.from, s.to)?.let { (1 - f) * it.length / 1000 }
            val after = sim.upcoming().firstOrNull { it.start >= s.end - 0.01 }
            Chip("운전 중", kindColor(s.kind), filled = true)
            Spacer(Modifier.height(12.dp))
            Text(sim.world.name(s.to), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                if (km != null) {
                    Text("%.1f".format(km), style = MaterialTheme.typography.displaySmall.copy(fontSize = 56.sp))
                    Text("km", style = MaterialTheme.typography.titleLarge, color = C.sub, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, end = 28.dp))
                }
                Text(clock(s.end), style = MaterialTheme.typography.displaySmall.copy(fontSize = 56.sp))
                Text("도착", style = MaterialTheme.typography.titleLarge, color = C.sub, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
            }
            if (after != null) {
                Spacer(Modifier.height(8.dp))
                Text("도착하면 · " + (if (after.kind.name.startsWith("DRIVE")) "${sim.world.name(after.to)}(으)로 출발" else after.note.ifBlank { after.kind.label }),
                    style = MaterialTheme.typography.titleLarge, color = C.sub)
            }
            Spacer(Modifier.height(12.dp))
            RestLine(sim, ui.now)
            Spacer(Modifier.height(12.dp))
            BigButton("${vm.navi.label}로 길안내", { vm.navigate(s.to) }, Modifier.fillMaxWidth())
            return@Panel
        }
        Chip(if (s.start > ui.now) "곧 시작" else s.kind.label, kindColor(s.kind), filled = true)
        Spacer(Modifier.height(10.dp))
        val title = when {
            s.kind.name.startsWith("DRIVE") -> "${sim.world.name(s.to)}(으)로 이동"
            else -> "${sim.world.name(s.from)}에서 ${s.note.ifBlank { s.kind.label }}"
        }
        Text(title, style = MaterialTheme.typography.headlineMedium)
        s.job?.let { Text(sim.label(it), color = C.sub, style = MaterialTheme.typography.bodyLarge) }
        // 충전소에서는 남은 시간·다음 일을 오른쪽 충전 화면이 보여 주므로 여기선 버튼만
        if (chargeSegNow(sim) != null) {
            Spacer(Modifier.height(12.dp))
            if (s.start <= ui.now && s.kind != SegKind.WAIT) StopActions(vm, s, ui.tick)
            return@Panel
        }
        Spacer(Modifier.height(12.dp))
        val f = ((ui.now - s.start) / (s.end - s.start)).coerceIn(0.0, 1.0)
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(C.line)) {
            Box(Modifier.fillMaxWidth(f.toFloat()).fillMaxHeight().clip(CircleShape).background(kindColor(s.kind)))
        }
        Spacer(Modifier.height(6.dp))
        Text("${clock(s.end)} 끝 (${DaySim.fmtH(((s.end - ui.now).coerceAtLeast(0.0)) / 60)} 남음)", color = C.sub)
        if (s.job != null && s.kind != SegKind.WAIT) { Spacer(Modifier.height(10.dp)); ContainerLine(s.job!!, s) }
        RestLine(sim, ui.now)
        Spacer(Modifier.height(12.dp))
        val drive = vm.nextDriveTarget()
        if (s.start <= ui.now && s.kind != SegKind.WAIT) StopActions(vm, s, ui.tick)
        else if (drive != null) BigButton("${vm.navi.label}로 길안내 · ${sim.world.name(drive)}", { vm.navigate(drive) }, Modifier.fillMaxWidth())
        sim.upcoming().firstOrNull()?.let { n ->
            Spacer(Modifier.height(10.dp))
            Text("다음 · ${clock(n.start)} " + (if (n.kind.name.startsWith("DRIVE")) "${sim.world.name(n.to)}(으)로" else n.note.ifBlank { n.kind.label }),
                color = C.sub, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** 쉬고 있을 때: 추천 일정 1~3안과 이유 */
@Composable
private fun Recommendation(vm: DriverViewModel, ui: UiState) {
    val sim = vm.sim
    var reject by remember { mutableStateOf(false) }
    if (reject) ChoiceDialog("왜 이 일정이 어려운가요?", listOf("몸이 안 좋아요", "이 일감은 못 해요", "차량 점검이 필요해요"),
        onPick = { r -> reject = false; vm.reject(r) },
        extra = "배차 담당자에게 전화" to { reject = false; vm.report("배차 담당자 전화 연결", "시연에서는 전화 대신 기록만 남겨요") },
        onDismiss = { reject = false })
    Panel(Modifier.fillMaxWidth(), color = C.panel2) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(if (sim.onShift) "추천 일정" else "출근 전 미리 보기", C.brand, filled = true)
            Spacer(Modifier.weight(1f))
            if (ui.computing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else {
                if (sim.onShift && ui.plans.isNotEmpty()) { SmallButton("이 일정 말고", { reject = true }); Spacer(Modifier.width(6.dp)) }
                SmallButton("다시 계산", { vm.replan() })
            }
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
            plans.take(3).forEachIndexed { i, p -> SmallButton("${i + 1}안 ${ui.picks.getOrNull(i)?.tag ?: ""}", { vm.select(i) }, selected = ui.selected == i) }
        }
        val sel = ui.selected.coerceIn(0, plans.size - 1)
        val p = plans[sel]
        // 이 안이 기사에게 어떤 하루인지 한 줄로
        ui.picks.getOrNull(sel)?.let { pk ->
            Spacer(Modifier.height(12.dp))
            Text(pk.note, style = MaterialTheme.typography.titleMedium, color = C.text,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.accent.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 12.dp))
        }
        Spacer(Modifier.height(12.dp))
        p.steps.forEachIndexed { i, st -> StepRow(sim, st, i == 0, ui.now) }
        Spacer(Modifier.height(10.dp))
        BigButton(if (sim.onShift) "이 일정 받기" else "출근하면 받을 수 있어요", { vm.accept() }, Modifier.fillMaxWidth(), enabled = sim.onShift)
        Text("첫 일만 확정하고, 끝나면 그때 상황으로 다시 추천해요.", color = C.sub, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 8.dp))
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
                Text("${clock(st.chargeStart!!)} 시작 · ${clock(st.end)} 끝 · %.0fkg 충전".format(st.chargeKg), color = C.sub)
                if (st.waitH > 0.01) Text("앞 차 충전 기다림 ${DaySim.fmtH(st.waitH)}", color = C.warn)
            } else if (o != null) {
                Text(sim.label(o.id), style = MaterialTheme.typography.titleLarge)
                Text("${sim.world.name(o.origin)} → ${sim.world.name(o.dest)}", color = C.sub)
                st.boxStop?.let { b ->
                    Text("가는 길에 ${sim.world.name(b.at)}에서 빈 컨테이너 ${if (o.isExport) "받기" else "내려놓기"}", color = C.box)
                }
                if (o.isExport) {
                    Text("공장 도착 ${clock(st.pickupArr!!)} · 터미널 도착 ${clock(st.portArr!!)}", color = C.sub)
                    // 기사에게는 확률보다 "마감까지 여유"가 바로 와닿는다. 놓칠 위험이 경고 수준이면 색으로 알린다
                    val pm = st.pMiss ?: 0.0
                    val warn = pm >= sim.bundle.params.export_risk_warn
                    val slack = (o.cutoff!! - st.portArr!!) / 60.0
                    Text(if (slack >= 0) "반입 마감 ${clock(o.cutoff!!)}까지 여유 ${DaySim.fmtH(slack)}" else "반입 마감 ${clock(o.cutoff!!)}보다 ${DaySim.fmtH(-slack)} 늦음 · 배가 늦게 들어와야 실을 수 있어요",
                        color = if (warn || slack < 0) C.bad else if (slack < 2) C.warn else C.brand, fontWeight = FontWeight.SemiBold)
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
private fun DaySummary(sim: DaySim, now: Double) {
    // 기사가 궁금한 것: 오늘·이번 달 건수(운행 수당과 연결), 일한 시간, 퇴근·초과근무(회사용 숫자인 아낀 용차비·비용은 배차 담당자 화면으로)
    val l = sim.ledgerAt(now)
    val worked = (now - sim.shiftStart).coerceAtLeast(0.0) / 60
    val left = (sim.shiftEnd - now).coerceAtLeast(0.0) / 60
    Panel(Modifier.fillMaxWidth()) {
        Text("오늘", color = C.sub, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("오늘", "${l.jobsDone}건")
            Stat("이번 달", "${monthBefore(sim) + l.jobsDone}건")
            Stat("일한 시간", DaySim.fmtH(worked))
            // 퇴근: 받은 일정이 근무 끝을 넘기면 초과근무 예상(수당이 붙는 시간)
            val over = ((sim.segs.lastOrNull()?.end ?: now) - sim.shiftEnd)
            if (now < sim.shiftStart) Stat("퇴근", clock(sim.shiftEnd))
            else if (over > 1) Stat("퇴근 · 초과 예상", "${over.toInt()}분", C.warn)
            else Stat("퇴근 ${clock(sim.shiftEnd)}까지", DaySim.fmtH(left), if (left < 1) C.warn else C.text)
        }
    }
}

/** 이번 달 오늘 전까지 한 건수(시연용 예시: 평일 하루 3건으로 가정). 실제로는 배차 서버 기록 */
private fun monthBefore(sim: DaySim): Int {
    val d = sim.bundle.meta.day.takeLast(2).toIntOrNull() ?: 1
    return (d - 1) * 5 / 7 * 3
}

@Composable
private fun Stat(k: String, v: String, color: Color = C.text) {
    Column { Text(v, style = MaterialTheme.typography.titleLarge, color = color); Text(k, color = C.sub, style = MaterialTheme.typography.labelMedium) }
}
