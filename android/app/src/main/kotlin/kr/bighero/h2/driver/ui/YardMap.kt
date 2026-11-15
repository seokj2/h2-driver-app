package kr.bighero.h2.driver.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
        Canvas(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(YARD)) {
            drawYard(tm, loc, box.gate, accent, brand, sub, text)
        }
    }
}

private fun DrawScope.label(tm: TextMeasurer, s: String, at: Offset, color: Color, size: TextUnit = 11.sp, bold: Boolean = false, center: Boolean = false) {
    val st = TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    val r = tm.measure(s, st)
    drawText(r, topLeft = if (center) Offset(at.x - r.size.width / 2f, at.y - r.size.height / 2f) else at)
}

private fun DrawScope.truck(at: Offset, s: Float, color: Color, up: Boolean = true) {
    // 위에서 본 트랙터+트레일러(위쪽이 진행 방향)
    val w = s * 0.55f
    val dir = if (up) -1f else 1f
    drawRoundRect(Color(0xFF555B63), Offset(at.x - w / 2, at.y - s * 0.1f * dir.coerceAtLeast(0f) - (if (up) 0f else s)), Size(w, s), CornerRadius(3f))
    drawRoundRect(color, Offset(at.x - w / 2 - 1f, at.y + (if (up) -s * 0.55f - s * 0.12f else s * 0.12f)), Size(w + 2f, s * 0.55f), CornerRadius(5f))
}

private fun DrawScope.drawYard(tm: TextMeasurer, loc: YardLoc, gateNo: Int, accent: Color, brand: Color, sub: Color, text: Color) {
    val w = size.width; val h = size.height
    val planW = w * 0.73f                     // 왼쪽: 위에서 본 배치도, 오른쪽: 단면
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
    label(tm, "안벽", Offset(8f, apronT + 4f), sub, 10.sp)
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
        label(tm, "${c + 1}${"ABCD"[r]}", Offset(o.x + 4f, o.y + 1f), if (hit) accent else sub, 11.sp, bold = hit)
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
    val gw = planW * 0.06f; val g0 = planW * 0.08f
    for (g in 1..6) {
        val x = g0 + (g - 1) * gw * 1.25f
        val mine = g == gateNo
        drawRoundRect(if (mine) brand else Color(0xFF7D858F), Offset(x, gateY - h * 0.03f), Size(gw, h * 0.06f), CornerRadius(6f))
        label(tm, "$g", Offset(x + gw / 2, gateY), Color.White, 12.sp, bold = true, center = true)
    }
    label(tm, "게이트", Offset(g0 + 6 * gw * 1.25f + 6f, gateY - 8f), sub, 11.sp)
    // ---- 가는 길: 내 게이트 → 게이트 앞 도로 → 블록 왼쪽 도로 → 블록 옆 트럭 줄(베이 앞) ----
    val myGateX = g0 + (gateNo - 1) * gw * 1.25f + gw / 2
    val laneX = tBlock.x - laneW / 2
    val roadY = yardB + h * 0.02f
    val stopY = tBlock.y + tBlockSize.height + laneW / 2
    val route = Path().apply {
        moveTo(myGateX, gateY - h * 0.03f); lineTo(myGateX, roadY); lineTo(laneX, roadY)
        lineTo(laneX, stopY); lineTo(target.x, stopY)
    }
    drawPath(route, Color.White, style = Stroke(13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(route, accent, style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f))))
    // 도착 지점: 베이 앞 정차 표시 + 칸까지 점선
    drawLine(accent, Offset(target.x, stopY), Offset(target.x, target.y), strokeWidth = 3f, pathEffect = dash)
    truck(Offset(target.x, stopY + 14f), 30f, brand)
    // 내 칸 표시 핀
    drawCircle(accent.copy(alpha = 0.25f), 26f, target)
    drawCircle(Color.White, 15f, target); drawCircle(accent, 10f, target)
    // 말풍선
    val tag = "${loc.block} · 베이 %02d · 열 %02d".format(loc.bay, loc.row)
    val r = tm.measure(tag, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold))
    val bxl = (target.x - r.size.width / 2f - 10f).coerceIn(4f, planW - r.size.width - 24f); val byt = tBlock.y - r.size.height - 18f
    drawRoundRect(text, Offset(bxl, byt), Size(r.size.width + 20f, r.size.height + 10f), CornerRadius(8f))
    drawText(tm, tag, Offset(bxl + 10f, byt + 5f), style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold))

    // ---- 오른쪽: 단면(그 열을 옆에서 본 모습) ----
    val sx = planW + w * 0.03f; val sw = w - sx - w * 0.02f
    label(tm, "옆에서 본 모습", Offset(sx, 6f), text, 13.sp, bold = true)
    label(tm, "${loc.block} 베이 %02d · 열 %02d".format(loc.bay, loc.row), Offset(sx, 28f), sub, 11.sp)
    val tiers = 5; val stackCols = 5
    val gT = h * 0.18f; val gB = h * 0.70f
    val cellW = (sw - 24f) / stackCols; val cellH = (gB - gT - 30f) / (tiers + 0.6f)
    // 야드 크레인 문형
    drawRect(CRANE, Offset(sx + 2f, gT), Size(10f, gB - gT)); drawRect(CRANE, Offset(sx + sw - 12f, gT), Size(10f, gB - gT))
    drawRect(CRANE, Offset(sx + 2f, gT), Size(sw - 4f, 12f))
    drawRect(Color(0xFF555B63), Offset(sx + 12f + (tRow % stackCols) * cellW + cellW * 0.2f, gT + 12f), Size(cellW * 0.6f, 10f))
    drawLine(Color(0xFF555B63), Offset(sx + 12f + (tRow % stackCols + 0.5f) * cellW, gT + 22f), Offset(sx + 12f + (tRow % stackCols + 0.5f) * cellW, gT + 40f), strokeWidth = 2f)
    val myCol = tRow % stackCols
    for (cI in 0 until stackCols) {
        val height = if (cI == myCol) tiers.coerceAtLeast(loc.tier) else 2 + (cI * 3 + loc.bay) % 4
        for (t in 0 until height) {
            val mine = cI == myCol && t == loc.tier - 1
            val o = Offset(sx + 12f + cI * cellW + 2f, gB - (t + 1) * cellH)
            drawRect(if (mine) accent else BOXES[(cI * 2 + t) % BOXES.size].copy(alpha = if (cI == myCol) 0.9f else 0.5f), o, Size(cellW - 4f, cellH - 3f))
            if (mine) drawRect(Color.White, o, Size(cellW - 4f, cellH - 3f), style = Stroke(3f))
        }
    }
    drawLine(sub, Offset(sx, gB), Offset(sx + sw, gB), strokeWidth = 3f)
    for (t in 1..tiers) label(tm, "${t}단", Offset(sx + sw + 2f - 28f, gB - t * cellH + cellH / 2 - 8f), sub, 9.sp)
    // 트럭(옆모습) 대기 위치
    val tkY = gB + 8f
    drawRoundRect(brand, Offset(sx + 6f, tkY), Size(sw * 0.28f, 26f), CornerRadius(5f))
    drawRect(Color(0xFF9AA1A9), Offset(sx + 6f + sw * 0.28f, tkY + 10f), Size(sw * 0.6f, 10f))
    for (wx in listOf(0.12f, 0.5f, 0.75f)) drawCircle(Color(0xFF333333), 6f, Offset(sx + 6f + sw * wx, tkY + 28f))
    label(tm, "트럭 대는 줄", Offset(sx, tkY + 40f), sub, 10.sp)
    // 범례
    val ly = h * 0.86f
    drawRect(accent, Offset(sx, ly), Size(18f, 12f)); label(tm, "내 컨테이너", Offset(sx + 24f, ly - 3f), text, 11.sp)
    drawLine(accent, Offset(sx, ly + 26f), Offset(sx + 18f, ly + 26f), strokeWidth = 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
    label(tm, "가는 길", Offset(sx + 24f, ly + 19f), text, 11.sp)
    drawRect(CRANE, Offset(sx, ly + 44f), Size(18f, 10f)); label(tm, "크레인", Offset(sx + 24f, ly + 38f), text, 11.sp)
}
