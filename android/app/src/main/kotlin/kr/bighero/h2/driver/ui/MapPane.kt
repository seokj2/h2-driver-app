package kr.bighero.h2.driver.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kr.bighero.h2.core.DaySim
import kr.bighero.h2.core.SegKind
import kr.bighero.h2.driver.UiState
import kotlin.math.cos
import kotlin.math.min

/** 이름표 위치(점에서 x, y 떨어진 곳, 음수 x면 왼쪽): 울산 쪽·신항 쪽에 점이 몰려 겹치지 않게 */
private val LABEL = mapOf(
    "ULS_KPIC" to (-34f to -34f), "ULS_PETRO" to (30f to 34f), "CHG_TAEHWA" to (24f to -20f),
    "PORT_PNC" to (52f to -2f), "DEP_NORTH" to (40f to -34f), "DEP_UNGDONG" to (-30f to -44f), "CHG_NETRUCK" to (6f to 44f),
    "DEP_YANGSAN" to (20f to 0f),
)

/** 노드 좌표를 직선으로 이은 개략도(실제 도로 아님). 지난 길은 흐리게, 확정된 앞길은 점선, 트럭은 지금 위치 */
@Composable
fun MapPane(sim: DaySim, ui: UiState, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    @Suppress("UNUSED_VARIABLE") val tick = ui.tick
    Canvas(modifier) {
        val nodes = sim.bundle.nodes
        val lat0 = nodes.minOf { it.lat }; val lat1 = nodes.maxOf { it.lat }
        val lon0 = nodes.minOf { it.lon }; val lon1 = nodes.maxOf { it.lon }
        val kx = cos(Math.toRadians((lat0 + lat1) / 2))
        val pad = 56f * density
        val sx = (size.width - 2 * pad) / ((lon1 - lon0) * kx).toFloat()
        val sy = (size.height - 2 * pad) / (lat1 - lat0).toFloat()
        val s = min(sx, sy)
        val ox = (size.width - ((lon1 - lon0) * kx).toFloat() * s) / 2
        val oy = (size.height - (lat1 - lat0).toFloat() * s) / 2
        fun pt(id: String): Offset {
            val n = sim.world.node[id]!!
            return Offset(ox + ((n.lon - lon0) * kx).toFloat() * s, oy + (lat1 - n.lat).toFloat() * s)
        }
        // 고속도로 축(공장 ↔ 신항) 바탕선
        drawLine(C.line, pt(sim.bundle.params.home), pt(sim.bundle.params.terminal), strokeWidth = 18f, cap = StrokeCap.Round)
        for (seg in sim.segs) {
            if (seg.from == seg.to) continue
            val past = seg.end <= ui.now
            val col = if (past) C.sub.copy(alpha = 0.35f) else when (seg.kind) {
                SegKind.DRIVE_LOADED -> C.brand; SegKind.DRIVE_BOX -> C.box; else -> C.empty
            }
            drawLine(col, pt(seg.from), pt(seg.to), strokeWidth = if (past) 5f else 8f, cap = StrokeCap.Round,
                pathEffect = if (past || seg.start <= ui.now) null else PathEffect.dashPathEffect(floatArrayOf(18f, 12f)))
        }
        val hl = setOf(sim.bundle.params.home, sim.bundle.params.terminal) + sim.segs.flatMap { listOf(it.from, it.to) } +
            (ui.plans.getOrNull(ui.selected)?.steps?.map { it.endLoc } ?: emptyList())
        data class Lbl(val p: Offset, val text: String, val on: Boolean, val id: String)
        val labels = ArrayList<Lbl>()
        for (n in nodes) {
            val p = pt(n.id)
            val on = n.id in hl
            val col = when { n.id.startsWith("CHG") -> C.charge; n.id.startsWith("DEP") -> C.box; n.id.startsWith("PORT") -> C.export; else -> C.sub }
            drawCircle(col.copy(alpha = if (on) 1f else 0.45f), radius = if (on) 11f else 7f, center = p)
            labels.add(Lbl(p, n.name, on, n.id))
        }
        // 트럭
        val (a, b, f) = sim.position(ui.now)
        val pa = pt(a); val pb = pt(b)
        val tp = Offset(pa.x + (pb.x - pa.x) * f.toFloat(), pa.y + (pb.y - pa.y) * f.toFloat())
        // 트럭: 초록 원 + 흰 테두리(낮·밤 모두 눈에 띄게, 크기는 dp 기준)
        drawCircle(C.brand.copy(alpha = 0.25f), 22f * density, tp)
        drawCircle(Color.White, 12f * density, tp)
        drawCircle(C.brand, 9f * density, tp)
        // 이름표는 맨 위에: 점에서 지시선을 빼서 어두운 바탕 위에 쓴다(경로선·마커에 가리지 않게). 간격은 dp 기준
        val k = density
        for (l in labels) {
            val lay = tm.measure(l.text, TextStyle(color = if (l.on) C.text else C.sub, fontSize = 15.sp))
            val (dx0, dy0) = LABEL[l.id] ?: (16f to -12f)
            val dx = dx0 * k; val dy = dy0 * k
            val anchor = Offset(l.p.x + dx, l.p.y + dy)
            val padX = 6f * k; val padY = 3f * k
            val left = (if (dx < 0) anchor.x - lay.size.width - padX else anchor.x + padX).coerceIn(0f, size.width - lay.size.width)
            val top = (anchor.y - lay.size.height / 2f).coerceIn(0f, size.height - lay.size.height)
            drawLine(C.sub.copy(alpha = 0.6f), l.p, anchor, strokeWidth = 1.5f * k)
            drawRoundRect(C.bg.copy(alpha = 0.85f), Offset(left - padX, top - padY),
                androidx.compose.ui.geometry.Size(lay.size.width + 2 * padX, lay.size.height + 2 * padY),
                androidx.compose.ui.geometry.CornerRadius(6f * k))
            drawText(lay, topLeft = Offset(left, top))
        }
    }
}
