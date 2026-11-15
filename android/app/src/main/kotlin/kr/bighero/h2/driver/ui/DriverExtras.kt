package kr.bighero.h2.driver.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kr.bighero.h2.core.Charger
import kr.bighero.h2.core.DaySim
import kr.bighero.h2.core.Seg
import kr.bighero.h2.core.SegKind
import kr.bighero.h2.driver.DriverViewModel
import kotlin.math.abs

// 기사 입장에서 더한 것들. 평소에는 한 줄·버튼 두 개만 보이고, 나머지는 눌러야 열린다(화면을 복잡하게 하지 않기).

// ---------- 휴게시간 ----------

/** 지금까지 쉬지 않고(15분 이상 멈춤 없이) 운전한 시간(분) */
fun drivenSinceBreak(sim: DaySim, now: Double): Double {
    var drove = 0.0
    for (s in sim.segs.asReversed()) {
        if (s.start >= now) continue
        val end = minOf(s.end, now)
        if (s.kind.name.startsWith("DRIVE")) drove += end - s.start
        else if (end - s.start >= 15) break
    }
    return drove
}

/** 화물차 운전자 휴게: 2시간 운전 → 15분 이상 쉬기. 1시간 45분부터 미리 알려 줌. 필요 없으면 아무것도 안 보임 */
@Composable
fun RestLine(sim: DaySim, now: Double) {
    val d = drivenSinceBreak(sim, now)
    if (d < 105) return
    val over = d >= 120
    val col = if (over) C.bad else C.warn
    Text(if (over) "${DaySim.fmtH(d / 60)} 쉬지 않고 운전했어요 · 가까운 휴게소에서 15분 쉬어 가세요"
         else "곧 2시간 운전 · ${(120 - d).toInt()}분 안에 15분 쉬어 가세요",
        color = col, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(col.copy(alpha = 0.14f)).padding(horizontal = 14.dp, vertical = 10.dp))
}

// ---------- 컨테이너 정보(시연용 예시) ----------

private val carriers = listOf("HDMU", "MSKU", "HMMU", "SKLU", "PCIU", "KMTU")

/** 오더 번호에서 늘 같은 값이 나오게 만든 가짜 컨테이너 정보. 실제로는 배차 서버(화주·운송사 오더)에서 받음 */
data class BoxInfo(val no: String, val size: String, val tons: Int, val gate: Int, val slot: String)

fun boxInfo(job: String, start: Double): BoxInfo {
    val h = abs(job.hashCode())
    val num = (h % 1_000_000).toString().padStart(6, '0')
    val slotH = ((start / 60).toInt()) % 24
    return BoxInfo("${carriers[h % carriers.size]} $num-${h % 10}", if (h % 3 == 0) "20ft" else "40ft",
        18 + h % 9, 1 + h % 6, "%02d:00~%02d:00".format(slotH, (slotH + 1) % 24))
}

/** 한 줄로 접혀 있다가 누르면 펼쳐짐 */
@Composable
fun ContainerLine(job: String, seg: Seg) {
    var open by remember { mutableStateOf(false) }
    val b = boxInfo(job, seg.start)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.panel).clickable { open = !open }.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📦 ${b.no} · ${b.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(if (open) "접기" else "자세히", color = C.sub, style = MaterialTheme.typography.labelLarge)
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            KeyVal("무게", "${b.tons}톤")
            KeyVal("터미널 게이트", "${b.gate}번")
            KeyVal("반출입 예약", b.slot)
            Text("시연용 예시 값이에요. 실제로는 배차 서버에서 받아요.", color = C.sub, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ---------- 진행 알리기·문제 신고·증빙 ----------

/** 멈춰서 하는 일(상·하차, 터미널, 충전 등)에서: [도착/완료 알리기] [문제 있음] 두 버튼만 */
@Composable
fun StopActions(vm: DriverViewModel, s: Seg, @Suppress("UNUSED_PARAMETER") tick: Long) {   // tick: 알린 뒤 버튼이 바로 바뀌게
    val key = "${s.start.toInt()}"
    val arrived = "$key:a" in vm.done
    val finished = "$key:d" in vm.done
    val place = vm.sim.world.name(s.from)
    var problem by remember { mutableStateOf(false) }
    var proof by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            !arrived -> BigButton("도착했어요", { vm.confirm("$key:a", "도착", place) }, Modifier.weight(1f))
            !finished -> BigButton(if (s.kind == SegKind.TERMINAL || s.kind == SegKind.LOAD) "상·하차 끝났어요" else "끝났어요",
                { if (s.job != null) proof = true else vm.confirm("$key:d", "완료", place) }, Modifier.weight(1f))
            else -> BigButton("알렸어요 ✓", {}, Modifier.weight(1f), enabled = false)
        }
        BigButton("문제 있음", { problem = true }, Modifier.weight(0.6f), color = C.warn)
    }
    if (problem) ChoiceDialog("어떤 문제가 있나요?", listOf("터미널 대기가 길어요", "컨테이너에 문제가 있어요", "차량 고장·사고", "길이 막혀 늦어요"),
        onPick = { r -> problem = false; vm.report("문제 신고 · $r", "$place · 배차 담당자에게 보냈어요") },
        extra = "배차 담당자에게 전화" to { problem = false; vm.report("배차 담당자 전화 연결", "시연에서는 전화 대신 기록만 남겨요") },
        onDismiss = { problem = false })
    if (proof) ProofDialog(s.job ?: "", onDone = { photo, sign ->
        proof = false
        vm.confirm("$key:d", "완료" + (if (photo) " · 사진" else "") + (if (sign) " · 인수증 서명" else ""), place)
    }, onDismiss = { proof = false })
}

/** 큰 버튼 몇 개로 고르는 창 */
@Composable
fun ChoiceDialog(title: String, options: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit, extra: Pair<String, () -> Unit>? = null) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(20.dp)).background(C.bg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            options.forEach { o -> BigButton(o, { onPick(o) }, Modifier.fillMaxWidth(), color = C.brandDark) }
            extra?.let { (t, f) -> BigButton("📞 $t", f, Modifier.fillMaxWidth(), color = C.export) }
            Text("닫기", color = C.sub, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.End).clickable(onClick = onDismiss).padding(8.dp))
        }
    }
}

/** 상·하차 끝: 컨테이너 사진 + 전자 인수증 서명. 카메라가 없으면 사진은 건너뜀 */
@Composable
fun ProofDialog(job: String, onDone: (photo: Boolean, sign: Boolean) -> Unit, onDismiss: () -> Unit) {
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var noCam by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf<List<Offset>>() }
    var cur by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp = it }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.widthIn(max = 620.dp).clip(RoundedCornerShape(20.dp)).background(C.bg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("상·하차 확인", style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(C.panel2), contentAlignment = Alignment.Center) {
                    bmp?.let { Image(it.asImageBitmap(), null, Modifier.size(96.dp)) } ?: Text("📷", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.width(12.dp))
                BigButton(if (bmp == null) "컨테이너 사진 찍기" else "다시 찍기",
                    { try { cam.launch(null) } catch (_: Exception) { noCam = true } }, Modifier.weight(1f))
            }
            if (noCam) Text("이 기기에는 카메라가 없어 사진은 건너뛰어요.", color = C.sub, style = MaterialTheme.typography.labelMedium)
            Text("인수증 서명 (손가락으로)", color = C.sub)
            Canvas(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, C.line, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures(onDragStart = { cur = listOf(it) }, onDragEnd = { lines.add(cur); cur = emptyList() }) { ch, _ -> cur = cur + ch.position }
                }) {
                (lines + listOf(cur)).forEach { l -> for (i in 1 until l.size) drawLine(Color(0xFF1B2430), l[i - 1], l[i], strokeWidth = 5f) }
                if (lines.isEmpty() && cur.isEmpty()) drawRect(C.line, style = Stroke(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("지우기", { lines.clear() }, Modifier.weight(0.5f), color = C.sub)
                BigButton("보내기", { onDone(bmp != null, lines.isNotEmpty()) }, Modifier.weight(1f))
            }
        }
    }
}

// ---------- 충전소 실시간(대기·예약은 시연용 예시) ----------

fun isOpen(c: Charger, now: Double): Boolean {
    val t = now % 1440
    if (t < c.open || t >= c.close) return false
    c.brk?.let { if (t >= it[0] && t < it[1]) return false }
    return true
}

/** 대기 대수 예시: 시각·충전소마다 늘 같은 값. 실제로는 한국석유관리원 실시간 API */
fun demoQueue(c: Charger, now: Double) = abs((c.id.hashCode() + (now / 30).toInt() * 7) % 4)

@Composable
fun ChargerLive(vm: DriverViewModel, c: Charger, now: Double) {
    val open = isOpen(c, now)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        Chip(if (open) "지금 운영 중" else "닫힘", if (open) C.brand else C.bad, filled = true)
        Spacer(Modifier.width(8.dp))
        if (open) Chip("대기 ${demoQueue(c, now)}대 (예시)", C.sub)
        Spacer(Modifier.weight(1f))
        if (open) SmallButton("충전 예약 요청", { vm.report("충전 예약 요청 · ${c.name}", "예약은 시연용 예시예요. 실제로는 충전소 예약 시스템으로 보내요") })
    }
}

/** 맨 위 작은 점: 배차 서버와 마지막으로 맞춘 시각 */
@Composable
fun FreshnessDot(vm: DriverViewModel, now: Double) {
    val last = maxOf(vm.lastPlanAt, vm.lastSyncAt)
    val m = (now - last).toInt()
    val (t, col) = when {
        last < 0 -> "연결 중" to C.sub
        m <= 1 -> "방금 업데이트" to C.brand
        m < 30 -> "${m}분 전 업데이트" to C.brand
        else -> "${m}분 전 업데이트" to C.warn
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(col)); Spacer(Modifier.width(6.dp))
        Text(t, color = C.sub, style = MaterialTheme.typography.labelMedium)
    }
}

/** 음성 명령 버튼: 기기에 음성 인식 서비스가 있을 때만 보임 */
@Composable
fun MicButton(vm: DriverViewModel, listening: Boolean) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val avail = remember { try { android.speech.SpeechRecognizer.isRecognitionAvailable(ctx) } catch (_: Throwable) { false } }
    if (!avail) return
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) vm.listen() }
    SmallButton(if (listening) "듣는 중…" else "🎤 말하기", {
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) vm.listen()
        else perm.launch(android.Manifest.permission.RECORD_AUDIO)
    }, selected = listening)
}
