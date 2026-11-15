package kr.bighero.h2.driver.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kr.bighero.h2.core.Seg
import kr.bighero.h2.driver.DriverViewModel

// 장치장 그림 색(낮 화면 기준, 실제 터미널 느낌)
private val SEA = Color(0xFF7FB3D9); private val SEA2 = Color(0xFF6BA3CC)
private val HULL = Color(0xFF2E3A4F); private val APRON = Color(0xFFD9DDE2)
private val ROAD = Color(0xFF8E959E); private val YARD = Color(0xFFE9ECEF)
private val CRANE = Color(0xFFF2B705); private val CRANE_D = Color(0xFFB98B00)
private val BOXES = listOf(Color(0xFF3B6EA5), Color(0xFFB94A48), Color(0xFF4F8A5B), Color(0xFF8A6D3B), Color(0xFF6C7A89), Color(0xFF7B4F9D), Color(0xFF2F8F9D))

/**
 * 터미널에 있는 동안 지도 대신: 위에서 본 장치장(안벽·배·안벽 크레인, 블록마다 쌓인 컨테이너와 야드 크레인, 도로, 게이트)과
 * 게이트에서 내 컨테이너 칸까지 가는 길, 오른쪽에 그 열을 옆에서 본 단면(몇 단째인지).
 * 위치는 시연용 값(실서비스는 터미널 반출입 예약·TOS가 주는 블록·베이·열·단).
 */
@Composable
fun YardPane(vm: DriverViewModel, s: Seg, modifier: Modifier = Modifier) {
    val sim = vm.sim
    val job = s.job ?: return
    val loc = yardLoc(job)
    val box = boxInfo(job, s.start)
    val export = sim.world.orders[job]?.type == "export"
    val dist = 250 + loc.col * 110 + (3 - "ABCD".indexOf(loc.line)) * 90
    val tm = rememberTextMeasurer()
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(18.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(if (export) "내려놓을 곳" else "실을 컨테이너 위치", C.accent, filled = true)
            Spacer(Modifier.width(8.dp))
            Text(sim.world.name(s.from), color = C.sub, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(loc.text, style = MaterialTheme.typography.headlineMedium)
        Text("게이트 ${box.gate}번 → ${loc.block} 블록 옆 → 베이 ${"%02d".format(loc.bay)} 앞 정차 · 약 ${dist}m",
            color = C.sub, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        val accent = C.accent; val brand = C.brand; val sub = C.sub; val text = C.text
        // 그림을 누르면 화면 가득 크게(다시 누르면 닫힘)
        var zoom by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        Canvas(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(YARD).clickable { zoom = true }) {
            drawYard(tm, loc, box.gate, accent, brand, sub, text, zoomed = false)
        }
        if (zoom) androidx.compose.ui.window.Dialog(onDismissRequest = { zoom = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f).clip(RoundedCornerShape(18.dp)).background(C.panel).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(loc.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("닫기", style = MaterialTheme.typography.titleLarge, color = C.sub, modifier = Modifier.clickable { zoom = false }.padding(8.dp))
                }
                Spacer(Modifier.height(8.dp))
                Canvas(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(YARD).clickable { zoom = false }) {
                    drawYard(tm, loc, box.gate, accent, brand, sub, text, zoomed = true)
                }
            }
        }
    }
}

private var RIGHT = Float.MAX_VALUE   // 글자가 넘지 말아야 할 오른쪽 끝(넘으면 글자를 줄임)

private fun DrawScope.label(tm: TextMeasurer, s: String, at: Offset, color: Color, size: TextUnit = 11.sp, bold: Boolean = false, center: Boolean = false) {
    var st = TextStyle(color = color, fontSize = size * K, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    var r = tm.measure(s, st)
    val room = if (center) 2 * minOf(RIGHT - at.x, Float.MAX_VALUE / 4) else RIGHT - at.x
    if (r.size.width > room && room > 0) { st = st.copy(fontSize = st.fontSize * (room / r.size.width) * 0.97f); r = tm.measure(s, st) }
    drawText(r, topLeft = if (center) Offset(at.x - r.size.width / 2f, at.y - r.size.height / 2f) else at)
}

/** 옆에서 본 수소 트랙터 + 섀시(트레일러): 캡·창·문, 캡 뒤 수소 탱크, 바퀴 5축 */
private fun DrawScope.sideTruck(tm: TextMeasurer, at: Offset, w: Float, h: Float, cab: Color) {
    val tire = Color(0xFF26292D); val hub = Color(0xFFB8BEC6); val steel = Color(0xFF7D858F)
    val wheelR = h * 0.17f; val groundY = at.y + h - wheelR * 0.1f
    val cabW = w * 0.24f; val cabH = h * 0.78f
    // 섀시
    drawRect(steel, Offset(at.x + cabW * 0.6f, groundY - wheelR * 1.9f), Size(w - cabW * 0.6f, wheelR * 0.55f))
    // 트레일러 데크와 트위스트락
    drawRect(Color(0xFF5A6169), Offset(at.x + cabW + w * 0.10f, groundY - wheelR * 2.35f), Size(w - cabW - w * 0.10f, wheelR * 0.5f))
    for (i in 0..3) drawRect(Color(0xFFF2B705), Offset(at.x + cabW + w * 0.12f + i * (w - cabW - w * 0.16f) / 3f, groundY - wheelR * 2.6f), Size(6f, 5f))
    // 수소 탱크(캡 뒤)
    for (i in 0..2) drawRoundRect(Color(0xFFDDE3E9), Offset(at.x + cabW + 4f + i * w * 0.028f, groundY - wheelR * 2.0f - h * 0.55f), Size(w * 0.024f, h * 0.55f), CornerRadius(8f))
    drawRect(Color(0xFF3B82C4), Offset(at.x + cabW + 4f, groundY - wheelR * 2.0f - h * 0.35f), Size(w * 0.08f, 4f))
    // 캡
    val cy = groundY - wheelR * 1.4f - cabH
    val cabPath = Path().apply {
        moveTo(at.x, cy + cabH); lineTo(at.x, cy + cabH * 0.25f); quadraticTo(at.x, cy, at.x + cabW * 0.25f, cy)
        lineTo(at.x + cabW, cy); lineTo(at.x + cabW, cy + cabH); close()
    }
    drawPath(cabPath, cab)
    drawRoundRect(Color(0xFFBFE3F5), Offset(at.x + cabW * 0.08f, cy + cabH * 0.10f), Size(cabW * 0.42f, cabH * 0.30f), CornerRadius(6f))   // 앞유리
    drawRoundRect(Color(0xFFBFE3F5), Offset(at.x + cabW * 0.56f, cy + cabH * 0.10f), Size(cabW * 0.34f, cabH * 0.30f), CornerRadius(4f))   // 옆창
    drawLine(Color(0x55000000), Offset(at.x + cabW * 0.52f, cy + cabH * 0.08f), Offset(at.x + cabW * 0.52f, cy + cabH * 0.95f), strokeWidth = 2f) // 문
    drawRect(Color(0xFF26292D), Offset(at.x - 2f, cy + cabH * 0.80f), Size(cabW * 0.18f, cabH * 0.16f))  // 범퍼
    drawCircle(Color(0xFFFFE08A), cabH * 0.05f, Offset(at.x + cabW * 0.07f, cy + cabH * 0.72f))           // 전조등
    val r = tm.measure("H2", TextStyle(color = Color.White, fontSize = 9.sp * K, fontWeight = FontWeight.Bold))
    drawText(r, topLeft = Offset(at.x + cabW * 0.62f, cy + cabH * 0.55f))
    // 바퀴: 캡 2축, 트레일러 3축
    for (fx in listOf(0.10f, 0.30f, 0.66f, 0.78f, 0.90f)) {
        val c = Offset(at.x + w * fx, groundY - wheelR)
        drawCircle(tire, wheelR, c); drawCircle(hub, wheelR * 0.45f, c); drawCircle(tire, wheelR * 0.15f, c)
    }
}

/** 위에서 본 수소 트랙터 + 빈 섀시. front = 캡 앞머리 위치(차로 한가운데), len·wid = 전체 길이·폭 */
private fun DrawScope.topTruck(front: Offset, len: Float, wid: Float, cab: Color, facingRight: Boolean) {
    val s = if (facingRight) -1f else 1f            // 앞머리에서 뒤쪽으로 가는 방향
    fun x(d: Float) = front.x + s * d                // 앞머리에서 d만큼 뒤
    fun rect(d0: Float, d1: Float, wFrac: Float, c: Color, r: Float = 3f) {
        val a = minOf(x(d0), x(d1)); val b = maxOf(x(d0), x(d1)); val ww = wid * wFrac
        drawRoundRect(c, Offset(a, front.y - ww / 2), Size(b - a, ww), CornerRadius(r))
    }
    val tire = Color(0xFF26292D)
    // 그림자
    drawRoundRect(Color(0x33000000), Offset(minOf(x(0f), x(len)) + 3f, front.y - wid / 2 + 4f), Size(len, wid), CornerRadius(5f))
    // 바퀴(옆으로 살짝 튀어나옴): 캡 앞축·뒤축, 섀시 3축
    for (d in listOf(0.08f, 0.28f, 0.74f, 0.82f, 0.90f)) {
        val cx = x(len * d); val tw = len * 0.055f
        drawRoundRect(tire, Offset(cx - tw / 2, front.y - wid * 0.58f), Size(tw, wid * 1.16f), CornerRadius(2f))
    }
    // 섀시 프레임(빈 차대) + 트위스트락
    rect(len * 0.30f, len, 0.86f, Color(0xFF5A6169))
    rect(len * 0.30f, len, 0.30f, Color(0xFF7D858F))
    for (d in listOf(0.36f, 0.97f)) for (sy in listOf(-1f, 1f))
        drawRect(Color(0xFFF2B705), Offset(x(len * d) - 3f, front.y + sy * wid * 0.36f - 3f), Size(6f, 6f))
    // 캡 뒤 수소 탱크 3개
    for (i in 0..2) rect(len * (0.20f + i * 0.035f), len * (0.23f + i * 0.035f), 0.92f, Color(0xFFDDE3E9), 4f)
    // 캡: 지붕, 앞유리, 사이드미러
    rect(0f, len * 0.20f, 1f, cab, 6f)
    rect(len * 0.02f, len * 0.07f, 0.84f, Color(0xFFBFE3F5), 2f)
    rect(len * 0.09f, len * 0.18f, 0.56f, Color.White.copy(alpha = 0.25f), 3f)   // 지붕 에어디플렉터
    for (sy in listOf(-1f, 1f)) drawRoundRect(Color(0xFF26292D), Offset(minOf(x(len * 0.05f), x(len * 0.08f)), front.y + sy * wid * 0.62f - 2f), Size(len * 0.03f, 4f), CornerRadius(2f))
}

private var K = 1f   // 그림 크기에 맞춘 글자 배율

/** 높이 600짜리 가상 화면에 그리고 통째로 늘이거나 줄임: 작은 칸·확대 화면 모두 그림과 글자 비율이 같게 */
private const val VH = 600f

private fun DrawScope.drawYard(tm: TextMeasurer, loc: YardLoc, gateNo: Int, accent: Color, brand: Color, sub: Color, text: Color, zoomed: Boolean) {
    val s = size.height / VH
    scale(s, pivot = Offset.Zero) { drawYardV(tm, loc, gateNo, accent, brand, sub, text, size.width / s, VH, zoomed) }
}

private fun DrawScope.drawYardV(tm: TextMeasurer, loc: YardLoc, gateNo: Int, accent: Color, brand: Color, sub: Color, text: Color, w: Float, h: Float, zoomed: Boolean) {
    RIGHT = Float.MAX_VALUE
    K = 18f / 11.sp.toPx()   // 11sp 글자 = 가상 화면 18(높이의 3%)
    val planW = w * (if (w / h < 1.35f) 0.66f else 0.73f)                     // 왼쪽: 위에서 본 배치도, 오른쪽: 단면
    // ---- 바다·배·안벽 ----
    val seaH = h * 0.10f
    drawRect(SEA, Offset.Zero, Size(planW, seaH))
    for (i in 0 until 14) drawLine(SEA2, Offset(i * planW / 14f + 10f, seaH * 0.25f + (i % 3) * 6f), Offset(i * planW / 14f + 34f, seaH * 0.25f + (i % 3) * 6f), strokeWidth = 2f)
    val shipL = planW * 0.10f; val shipR = planW * 0.86f; val shipT = seaH * 0.18f; val shipB = seaH * 0.98f
    val hull = Path().apply { moveTo(shipL + 30f, shipT); lineTo(shipR, shipT); lineTo(shipR + 26f, (shipT + shipB) / 2); lineTo(shipR, shipB); lineTo(shipL + 30f, shipB); lineTo(shipL, (shipT + shipB) / 2); close() }
    drawPath(hull, HULL)
    var bx = shipL + 40f; var k = 0
    while (bx < shipR - 50f) {
        for (r in 0 until 3) drawRect(BOXES[(k + r) % BOXES.size], Offset(bx, shipT + 5f + r * (shipB - shipT - 10f) / 3f), Size(16f, (shipB - shipT - 10f) / 3f - 2f))
        bx += 18f; k++
    }
    drawRect(Color.White, Offset(shipR - 46f, shipT + 4f), Size(26f, shipB - shipT - 8f))   // 선교
    val apronT = seaH; val apronB = h * 0.17f
    drawRect(APRON, Offset(0f, apronT), Size(planW, apronB - apronT))
    // 안벽 크레인 4대(다리는 안벽, 붐은 배 위로)
    for (i in 0 until 4) {
        val x = planW * (0.22f + i * 0.17f)
        drawLine(CRANE_D, Offset(x, shipT - 4f), Offset(x, apronB - 4f), strokeWidth = 7f)
        drawRect(CRANE, Offset(x - 22f, apronT + 6f), Size(44f, apronB - apronT - 14f), style = Stroke(5f))
    }
    // ---- 도로 ----
    val yardT = apronB + h * 0.035f; val yardB = h * 0.82f
    val gateY = h * 0.93f
    drawRect(ROAD, Offset(0f, apronB), Size(planW, yardT - apronB))                 // 안벽 뒤 도로
    drawRect(ROAD, Offset(0f, yardB), Size(planW, h * 0.04f))                         // 게이트 앞 도로
    val cols = 6; val lines = 4
    val laneW = planW * 0.035f
    val bw = (planW - laneW * (cols + 1)) / cols
    val bh = (yardB - yardT - laneW * (lines - 1)) / lines
    for (c in 0..cols) drawRect(ROAD, Offset(c * (bw + laneW), yardT), Size(laneW, yardB - yardT))
    for (r in 1 until lines) drawRect(ROAD, Offset(0f, yardT + r * bh + (r - 1) * laneW), Size(planW, laneW))
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
    drawLine(Color.White, Offset(0f, yardB + h * 0.02f), Offset(planW, yardB + h * 0.02f), strokeWidth = 2f, pathEffect = dash)
    // ---- 블록: 베이(가로) × 열(세로) 칸마다 컨테이너, 야드 크레인 ----
    val bays = 8; val rows = 3
    val ti = "ABCD".indexOf(loc.line); val tc = loc.col - 1
    val tBay = ((loc.bay / 2) - 1).mod(bays); val tRow = (loc.row - 1).mod(rows)
    var target = Offset.Zero; var tBlock = Offset.Zero; var tBlockSize = Size.Zero
    for (c in 0 until cols) for (r in 0 until lines) {
        val o = Offset(laneW + c * (bw + laneW), yardT + r * (bh + laneW))
        val hit = c == tc && r == ti
        drawRect(Color(0xFFF7F8F9), o, Size(bw, bh))
        val cw = (bw - 8f) / bays; val chh = (bh - 22f) / rows
        for (b in 0 until bays) for (q in 0 until rows) {
            val seed = (c * 131 + r * 71 + b * 17 + q * 5) % 23
            if (seed == 0 && !(hit && b == tBay && q == tRow)) continue        // 빈 칸 몇 개
            val col = BOXES[seed % BOXES.size].copy(alpha = if (hit) 1f else 0.3f)
            drawRoundRect(col, Offset(o.x + 4f + b * cw + 1.5f, o.y + 18f + q * chh + 1.5f), Size(cw - 3f, chh - 3f), CornerRadius(2f))
        }
        // 야드 크레인(레일 두 줄 + 가로 다리)
        drawLine(Color(0xFF9AA1A9), Offset(o.x + 2f, o.y + 16f), Offset(o.x + bw - 2f, o.y + 16f), strokeWidth = 2f)
        drawLine(Color(0xFF9AA1A9), Offset(o.x + 2f, o.y + bh - 2f), Offset(o.x + bw - 2f, o.y + bh - 2f), strokeWidth = 2f)
        val gx = o.x + 4f + ((c * 5 + r * 3) % bays + 0.5f) * cw
        drawRect(if (hit) CRANE else CRANE.copy(alpha = 0.35f), Offset(gx - 3f, o.y + 12f), Size(6f, bh - 10f))
        run {   // 블록 이름: 흰 바탕 위에(컨테이너 무늬와 안 겹치게)
            val nm = "${c + 1}${"ABCD"[r]}"
            val m = tm.measure(nm, TextStyle(fontSize = 10.sp * K, fontWeight = FontWeight.Bold))
            drawRoundRect(Color.White, Offset(o.x + 2f, o.y + 2f), Size(m.size.width + 8f, m.size.height.toFloat()), CornerRadius(4f))
            label(tm, nm, Offset(o.x + 6f, o.y + 2f), if (hit) accent else text, 10.sp, bold = true)
        }
        if (hit) {
            drawRect(accent, o, Size(bw, bh), style = Stroke(5f))
            target = Offset(o.x + 4f + (tBay + 0.5f) * cw, o.y + 18f + (tRow + 0.5f) * chh)
            tBlock = o; tBlockSize = Size(bw, bh)
            drawRect(accent, Offset(target.x - cw / 2 - 1f, target.y - chh / 2 - 1f), Size(cw + 2f, chh + 2f))
            drawRect(Color.White, Offset(target.x - cw / 2 - 1f, target.y - chh / 2 - 1f), Size(cw + 2f, chh + 2f), style = Stroke(3f))
        }
    }
    // ---- 게이트(아래): 6개 차로, 내 게이트 강조 ----
    drawRect(Color(0xFFCFD4DA), Offset(0f, yardB + h * 0.04f), Size(planW, h - yardB - h * 0.04f))
    val gw = bw * 0.5f
    fun gateX(g: Int) = laneW + (g - 1) * (bw + laneW) + bw / 2 - gw / 2   // 게이트 n = 블록 n열 바로 아래
    for (g in 1..6) {
        val x = gateX(g)
        val mine = g == gateNo
        drawRoundRect(if (mine) brand else Color(0xFF7D858F), Offset(x, gateY - h * 0.03f), Size(gw, h * 0.06f), CornerRadius(6f))
        label(tm, "$g", Offset(x + gw / 2, gateY), Color.White, 12.sp, bold = true, center = true)
    }
    label(tm, "게이트", Offset(6f, yardB + h * 0.042f), sub, 9.sp)
    // ---- 가는 길: 내 게이트 → 게이트 앞 도로 → 블록 왼쪽 도로 → 블록 옆 트럭 줄(베이 앞) ----
    val myGateX = gateX(gateNo) + gw / 2
    val laneX = tBlock.x - laneW / 2
    val roadY = yardB + h * 0.02f
    val lastLine = ti == lines - 1
    val stopY = if (lastLine) roadY else tBlock.y + tBlockSize.height + laneW / 2   // 블록 바로 아래 차로 한가운데
    val route = Path().apply {
        moveTo(myGateX, gateY - h * 0.03f); lineTo(myGateX, roadY)
        if (!lastLine) { lineTo(laneX, roadY); lineTo(laneX, stopY) }
        lineTo(target.x, stopY)
    }
    drawPath(route, accent, style = Stroke(laneW * 0.32f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f))))
    // 도착 지점: 베이 앞 정차 표시 + 칸까지 점선
    drawLine(accent, Offset(target.x, stopY), Offset(target.x, target.y), strokeWidth = 3f, pathEffect = dash)
    val fromX = if (lastLine) myGateX else laneX
    topTruck(Offset(target.x + (bw - 8f) / bays * 1.4f, stopY), (bw - 8f) / bays * 3f, laneW * 0.5f, brand, facingRight = target.x >= fromX)
    // 내 칸 표시 핀
    if (zoomed) { drawCircle(accent.copy(alpha = 0.25f), 16f, target); drawCircle(Color.White, 10f, target); drawCircle(accent, 6.5f, target) }
    else { drawCircle(Color.White, 6f, target); drawCircle(accent, 4f, target) }   // 작은 화면: 블록 이름 안 가리게 작게
    run {   // 핀이 블록 이름을 가리지 않게 이름을 한 번 더 위에
        val nm = "${loc.col}${loc.line}"
        val m = tm.measure(nm, TextStyle(fontSize = 10.sp * K, fontWeight = FontWeight.Bold))
        drawRoundRect(Color.White, Offset(tBlock.x + 2f, tBlock.y + 2f), Size(m.size.width + 8f, m.size.height.toFloat()), CornerRadius(4f))
        label(tm, nm, Offset(tBlock.x + 6f, tBlock.y + 2f), accent, 10.sp, bold = true)
    }
    // ---- 오른쪽: 그 줄을 옆에서 본 모습(단 = 아래부터 몇 번째, 열 = 옆으로 몇 번째) ----
    val sx = planW + w * 0.025f; val sw = w - sx - w * 0.015f
    RIGHT = w - 4f
    label(tm, "${loc.block} · 베이 %02d · 열 %02d".format(loc.bay, loc.row), Offset(sx, 8f), text, 13.sp, bold = true)
    val tiers = 5; val stackCols = 5
    val tl = 30f * K                                   // 왼쪽 "N단" 글자 자리
    val fx = sx + tl; val fw = sw - tl                 // 크레인 문형 안쪽
    val gT = h * 0.13f; val gB = h * 0.72f
    val legW = 10f; val beamH = 16f * K
    val cellW = (fw - 2 * legW - 8f) / stackCols; val cellH = (gB - gT - beamH - 34f) / tiers
    val myCol = (loc.row - 1).mod(stackCols)
    if (zoomed) {   // 확대 화면에만 야드 크레인(문형)과 집게
        drawRect(CRANE, Offset(fx, gT), Size(legW, gB - gT)); drawRect(CRANE, Offset(fx + fw - legW, gT), Size(legW, gB - gT))
        drawRect(CRANE, Offset(fx, gT), Size(fw, beamH))
        label(tm, "야드 크레인", Offset(fx + fw / 2, gT + beamH / 2), Color(0xFF3A3000), 9.sp, bold = true, center = true)
    }
    val colX = { c: Int -> fx + legW + 4f + c * cellW }
    val myTop = gB - loc.tier.coerceAtMost(tiers) * cellH
    // 컨테이너 쌓임: 내 열만 진하게
    for (cI in 0 until stackCols) {
        val height = if (cI == myCol) loc.tier.coerceAtMost(tiers) else 1 + (cI * 3 + loc.bay) % 5
        for (t in 0 until height) {
            val mine = cI == myCol && t == loc.tier - 1
            val o = Offset(colX(cI) + 2f, gB - (t + 1) * cellH + 2f)
            drawRoundRect(if (mine) accent else BOXES[(cI * 2 + t) % BOXES.size].copy(alpha = if (cI == myCol) 0.85f else 0.35f), o, Size(cellW - 4f, cellH - 4f), CornerRadius(3f))
            if (mine) {
                drawRoundRect(Color.White, o, Size(cellW - 4f, cellH - 4f), CornerRadius(3f), style = Stroke(3f))
            }
        }
    }
    if (zoomed) {
        val hx = colX(myCol) + cellW / 2
        drawLine(Color(0xFF555B63), Offset(hx, gT + beamH), Offset(hx, myTop - 6f), strokeWidth = 3f)
        drawRect(Color(0xFF555B63), Offset(colX(myCol) + 4f, myTop - 6f), Size(cellW - 8f, 6f))
    }
    // 왼쪽 단 번호, 아래 열 번호
    for (t in 1..tiers) label(tm, "${t}단", Offset(sx + tl / 2, gB - (t - 0.5f) * cellH), if (t == loc.tier) accent else sub, 10.sp, bold = t == loc.tier, center = true)
    drawLine(sub, Offset(fx - 4f, gB), Offset(fx + fw + 4f, gB), strokeWidth = 3f)
    for (cI in 0 until stackCols) label(tm, "${cI + 1}", Offset(colX(cI) + cellW / 2, gB + 12f * K), if (cI == myCol) accent else sub, 9.sp, bold = cI == myCol, center = true)
    label(tm, "열", Offset(sx + tl / 2, gB + 12f * K), sub, 9.sp, center = true)
    // 내 칸을 가리키는 표시: 단 번호에서 내 칸까지 점선
    drawLine(accent, Offset(fx + legW, gB - (loc.tier - 0.5f) * cellH), Offset(colX(myCol) + 2f, gB - (loc.tier - 0.5f) * cellH), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
    // 트럭이 서는 곳(크레인 다리 옆 차로)
    // 범례(왼쪽 배치도용)
    val ly = h * 0.85f
    val lh = 18f * K
    drawRect(accent, Offset(sx, ly + 3f), Size(18f, 12f)); label(tm, "내 컨테이너", Offset(sx + 26f, ly - 4f), text, 11.sp)
    drawLine(accent, Offset(sx, ly + lh + 9f), Offset(sx + 18f, ly + lh + 9f), strokeWidth = 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
    label(tm, "경로", Offset(sx + 26f, ly + lh - 4f), text, 11.sp)
}
