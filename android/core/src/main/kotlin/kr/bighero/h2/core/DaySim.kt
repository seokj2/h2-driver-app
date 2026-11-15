package kr.bighero.h2.core

import kotlin.math.max
import kotlin.math.min

/** 시연용 선석 현황: 정답 표의 실제 접안 시각이 지나면 실제 지연을 알려 준다(공개 정보 흉내) */
class TruthBoard(b: DayBundle) : BerthBoard {
    private val v = b.truth.vessels.associateBy { it.row_id }
    override fun berthedDelayH(rowId: Int, now: Double): Double? = v[rowId]?.takeIf { now >= it.berth }?.delay_h
    fun berth(rowId: Int) = v[rowId]?.berth
}

enum class SegKind(val label: String) {
    DRIVE_EMPTY("빈 차 주행"), DRIVE_BOX("빈 컨테이너 싣고 주행"), DRIVE_LOADED("짐 싣고 주행"),
    WAIT("대기"), LOAD("상·하차"), TERMINAL("터미널 처리"), DEPOT("빈 컨테이너 받기·내려놓기"), CHARGE("충전"),
}

/** 실제로 일어난(또는 진행 중인) 구간. 화면의 타임라인·지도·수소 그래프가 이걸 읽는다 */
data class Seg(
    val kind: SegKind, val start: Double, val end: Double, val from: String, val to: String,
    val h2Start: Double, val h2End: Double, val job: String?, val note: String = "",
)

enum class AlertKind { BERTH_LATE, BERTH_CONFIRMED, TAKEN_BY_OTHER, EXPORT_RISK, PLAN_CHANGED, MISSED, INFO, SENT }   // SENT: 기사가 배차 담당자에게 보낸 것

data class Alert(val t: Double, val kind: AlertKind, val title: String, val body: String)

/** 하루 장부(총물류비 관점). 용차를 안 쓴 만큼이 이득 */
data class Ledger(
    var jobsDone: Int = 0, var fareSaved: Double = 0.0, var h2Won: Double = 0.0, var tollWon: Double = 0.0,
    var waitMin: Double = 0.0, var missN: Int = 0, var lateH: Double = 0.0, var kmEmpty: Double = 0.0, var kmLoaded: Double = 0.0,
)

/**
 * 하루 시연 진행기. 시계를 움직이고, 운전자가 받은 일정을 "실제로" 실행한다(정답 표 사용: 실제 반출 가능·실제 마감).
 * 엔진([Planner])에는 공지 값 + 지연 시나리오 + 이미 일어난 접안만 들어간다(CLAUDE.md 규칙과 같음).
 * 다른 트럭이 오더를 잡는 시각은 기업용 시뮬레이션(P2, 5대) 기록을 따른다.
 */
class DaySim(val bundle: DayBundle) {
    val board = TruthBoard(bundle)
    val world = World(bundle, board)
    val planner = Planner(world)
    private val lightPlanner = Planner(world).also { it.maxJobs = 1 }   // 따로 둬서 추천 계산과 섞이지 않게
    private val p = bundle.params
    private val truthOrder = bundle.truth.orders.associateBy { it.id }
    private val claims = bundle.claims.associateBy { it.order }
    val driver = bundle.driver

    var now: Double = driver.shift_start - 10.0; private set
    val shiftStart = driver.shift_start
    val shiftEnd = driver.shift_start + p.shift_min

    // 트럭 상태(지금 구간이 끝난 뒤 기준)
    var loc: String = driver.start_loc; private set
    var h2: Double = driver.h2_kg; private set
    var box: Boolean = driver.box; private set
    private var freeAt: Double = now                        // 맡은 일을 다 마치는 시각
    val segs = ArrayList<Seg>()                             // 실행 기록 + 확정된 앞으로의 구간
    val taken = LinkedHashSet<String>()                     // 우리가 맡은 오더
    val alerts = ArrayList<Alert>()
    private val book = ArrayList<Pair<Double, (Ledger) -> Unit>>()   // (일어난 시각, 장부 반영)
    private fun book(t: Double, f: (Ledger) -> Unit) { book.add(t to f) }
    /** t 시각까지 실제로 끝난 것만 합친 장부 */
    fun ledgerAt(t: Double = now): Ledger = Ledger().also { l -> book.filter { it.first <= t }.forEach { it.second(l) } }
    /** 수락해서 확정된 것까지 합친 장부 */
    val ledger get() = ledgerAt(Double.MAX_VALUE)
    var committed: Plan? = null; private set                // 지금 하고 있는 일정(첫 단계만 확정)
    var lastPlans: List<Plan> = emptyList(); private set
    private val seen = HashSet<String>()

    val idle get() = now >= freeAt
    val onShift get() = now >= shiftStart

    fun pool(t: Double = now): List<String> = bundle.orders.filter { o ->
        o.post <= t && o.id !in taken &&
            (claims[o.id]?.let { it.t > t } ?: true) &&
            t < o.service_day + DAY + 12 * 60 &&
            !(o.isExport && t >= (truthOrder[o.id]?.cutoff ?: Double.MAX_VALUE))
    }.map { it.id }.sorted()

    fun state() = TruckState(loc, max(now, freeAt), h2, shiftEnd - p.plan_margin_min, p.home, box)

    /** 지금 상태에서 추천 일정(점수순) */
    fun recommend(): List<Plan> {
        lastPlans = planner.candidates(state(), pool(max(now, freeAt)))
        return lastPlans
    }

    /** 일감 1개짜리 가벼운 후보(기사가 쉬고 싶은 날 보여 줄 '여유 있는 일정'용). 추천 계산과 같은 입력만 씀 */
    fun lighter(): List<Plan> {
        return lightPlanner.candidates(state(), pool(max(now, freeAt)))
    }

    /** 현재 위치·수소(진행 중 구간이면 보간) */
    fun position(t: Double = now): Triple<String, String, Double> {
        val s = segs.lastOrNull { it.start <= t && t < it.end } ?: return Triple(locAt(t), locAt(t), 0.0)
        val f = if (s.end > s.start) (t - s.start) / (s.end - s.start) else 1.0
        return Triple(s.from, s.to, f)
    }

    private fun locAt(t: Double) = segs.lastOrNull { it.end <= t }?.to ?: driver.start_loc

    fun h2At(t: Double = now): Double {
        val s = segs.lastOrNull { it.start <= t } ?: return driver.h2_kg
        if (t >= s.end) return s.h2End
        val f = (t - s.start) / max(1e-9, s.end - s.start)
        return s.h2Start + (s.h2End - s.h2Start) * f
    }

    fun currentSeg(t: Double = now) = segs.lastOrNull { it.start <= t && t < it.end }
    fun upcoming(t: Double = now) = segs.filter { it.start >= t }

    /** 일정 수락: 첫 일감(과 그 앞 충전)만 확정하고 실행 구간을 만든다(굴러가는 계획, corp.py ROLL과 같음) */
    fun accept(plan: Plan) {
        val first = plan.steps.indexOfFirst { !it.isCharge }
        val commit = if (first < 0) plan.steps else plan.steps.subList(0, first + 1)
        var t = max(now, freeAt)
        for (st in commit) {
            t = if (st.isCharge) execCharge(st.id, t) else execJob(world.orders[st.id]!!, t, st)
        }
        freeAt = t
        committed = plan
        alerts.add(Alert(now, AlertKind.INFO, "일정 확정", commit.joinToString(" → ") { label(it.id) }))
    }

    /** 사고·고장: 지금 구간까지만 하고 앞으로 확정된 일은 취소(다른 트럭으로 넘김), 그 자리에서 holdMin 동안 사고 처리. 그 뒤 다시 추천 */
    fun accident(holdMin: Double = 60.0): List<String> {
        val cur = currentSeg()
        val keepEnd = cur?.end ?: now
        val dropped = segs.filter { it.start >= keepEnd - 1e-6 }
        segs.removeAll(dropped.toSet())
        val kept = segs.mapNotNull { it.job }.toSet()
        val gaveUp = dropped.mapNotNull { it.job }.distinct().filter { it !in kept }
        taken.removeAll(gaveUp.toSet())
        loc = segs.lastOrNull()?.to ?: driver.start_loc
        h2 = segs.lastOrNull()?.h2End ?: driver.h2_kg
        segs.add(Seg(SegKind.WAIT, keepEnd, keepEnd + holdMin, loc, loc, h2, h2, null, "사고 처리"))
        freeAt = keepEnd + holdMin
        committed = null
        alerts.add(Alert(now, AlertKind.PLAN_CHANGED, "사고 신고 · 일정 변경",
            if (gaveUp.isEmpty()) "사고 처리 뒤 다시 추천해요" else gaveUp.joinToString(", ") { label(it) } + " → 다른 트럭으로 넘겼어요. 사고 처리 뒤 다시 추천해요"))
        return gaveUp
    }

    /** 일이 없을 때 공장으로 돌아가기(신항 쪽이면 가는 길에 빈 컨테이너 받기) */
    fun goHome() {
        var t = max(now, freeAt)
        if (loc == p.home) return
        if (!box && world.side[loc] != world.side[p.home]) {
            val s = world.boxStop(loc, p.home, t)
            t = drive(s.at, t, null, loaded = false)
            t = depotOrTerminal(s.at, t, null, pick = true)
        }
        t = drive(p.home, t, null, loaded = false)
        freeAt = t
        committed = null
    }

    fun label(id: String): String {
        val o = world.orders[id] ?: return world.name(id)
        val ty = when (o.type) { "export" -> "수출"; "import" -> "수입"; else -> "빈 컨테이너" }
        return "$ty ${o.vessel ?: (world.name(o.origin) + "→" + world.name(o.dest))}"
    }

    // ---------- 실행(정답 표 사용) ----------
    private fun add(kind: SegKind, s: Double, e: Double, from: String, to: String, h2e: Double, job: String?, note: String = "") {
        if (e - s > 0.01) segs.add(Seg(kind, s, e, from, to, h2, h2e, job, note))
        h2 = h2e
    }

    private fun drive(to: String, t: Double, job: String?, loaded: Boolean): Double {
        if (loc == to) return t
        val d = world.drive(loc, to, t)
        val kind = if (loaded) SegKind.DRIVE_LOADED else if (box) SegKind.DRIVE_BOX else SegKind.DRIVE_EMPTY
        add(kind, t, d.arr, loc, to, h2 - d.km * world.kgkm, job)
        val won = d.km * world.wonKm; val toll = d.toll; val km = d.km
        book(d.arr) { if (loaded) it.kmLoaded += km else it.kmEmpty += km; it.h2Won += won; it.tollWon += toll }
        loc = to
        return d.arr
    }

    private fun wait(until: Double, t: Double, job: String?, note: String): Double {
        if (until > t) add(SegKind.WAIT, t, until, loc, loc, h2, job, note)
        return max(t, until)
    }

    private fun depotOrTerminal(at: String, t0: Double, job: String?, pick: Boolean): Double {
        var t = t0
        if (at.startsWith("DEP")) {
            val (op, _) = world.depotWindow(t)
            t = wait(op, t, job, "디포 문 열기 기다림")
            add(SegKind.DEPOT, t, t + p.depot_min, at, at, h2, job, if (pick) "빈 컨테이너 받기" else "빈 컨테이너 내려놓기")
            t += p.depot_min
        } else {
            add(SegKind.TERMINAL, t, t + p.terminal_min, at, at, h2, job, if (pick) "빈 컨테이너 받기" else "빈 컨테이너 내려놓기")
            t += p.terminal_min
        }
        box = pick
        return t
    }

    private fun execCharge(c: String, t0: Double): Double {
        var t = drive(c, t0, null, loaded = false)
        val win = world.chargerWindow(c, t)
        t = wait(win.open, t, null, "충전소 문 열기 기다림")
        win.brk?.let { if (it.first <= t && t < it.second) t = wait(it.second, t, null, "충전소 쉬는 시간") }
        val ch = world.chargers[c]!!
        val kg = world.tank - h2
        val dur = ch.setup_min + kg / ch.rate_kg_h * 60.0
        add(SegKind.CHARGE, t, t + dur, c, c, world.tank, null, "%.1fkg 충전".format(kg))
        book(t + dur) { it.h2Won += kg * ch.price_krw_kg - kg * world.p.h2_price_score }   // 주행 때 점수 단가로 넣은 것과 실제 충전 단가 차이만 더함
        return t + dur
    }

    private fun execJob(o: Order, t0: Double, plan: Step): Double {
        var t = t0
        taken.add(o.id)
        if ((o.isExport && !box) || (o.isImport && box)) {
            val s = world.boxStop(loc, o.origin, t)
            t = drive(s.at, t, o.id, loaded = false)
            t = depotOrTerminal(s.at, t, o.id, pick = o.isExport)
        }
        if (o.isImport) {
            // 엔진이 정한 터미널 도착 목표에 맞춰 출발(먼저 가서 노는 대신 지금 자리에서 기다림)
            val tt = world.tt(loc, o.origin, t)
            val target = plan.target ?: t
            t = wait(target - tt, t, o.id, "터미널 도착 시각에 맞춰 출발 대기")
            t = drive(o.origin, t, o.id, loaded = false)
            val avail = truthOrder[o.id]?.avail ?: o.avail!!
            if (avail > t) {
                val wm = avail - t; book(avail) { it.waitMin += wm }
                t = wait(avail, t, o.id, "반출 가능 기다림(배 작업 중)")
            }
            add(SegKind.TERMINAL, t, t + p.terminal_min, loc, loc, h2, o.id, "수입 컨테이너 싣기")
            t += p.terminal_min
            box = true
            t = drive(o.dest, t, o.id, loaded = true)
            add(SegKind.LOAD, t, t + p.shipper_handling_min, loc, loc, h2, o.id, "수입 컨테이너 내려 주기")
            t += p.shipper_handling_min
        } else if (o.isExport) {
            t = drive(o.origin, t, o.id, loaded = false)
            t = wait(o.ready!!, t, o.id, "컨테이너 준비 기다림")
            add(SegKind.LOAD, t, t + p.shipper_handling_min, loc, loc, h2, o.id, "수출 컨테이너 싣기")
            t += p.shipper_handling_min
            t = drive(o.dest, t, o.id, loaded = true)
            o.gate_open?.let { t = wait(it, t, o.id, "반입 개시 기다림") }
            val cut = truthOrder[o.id]?.cutoff ?: o.cutoff!!
            val missed = t > cut
            add(SegKind.TERMINAL, t, t + p.terminal_min, loc, loc, h2, o.id, if (missed) "반입 마감 지남(선적 못 함)" else "수출 컨테이너 반입")
            t += p.terminal_min
            box = false
            if (missed) book(t) { it.missN++ }
        } else {
            t = drive(o.origin, t, o.id, loaded = false)
            t = wait(o.ready ?: t, t, o.id, "준비 기다림")
            t = depotOrTerminal(o.origin, t, o.id, pick = true)
            t = drive(o.dest, t, o.id, loaded = true)
            t = depotOrTerminal(o.dest, t, o.id, pick = false)
        }
        val fare = o.fare
        book(t) { it.jobsDone++; it.fareSaved += fare }
        return t
    }

    // ---------- 시계 ----------
    /** 시계를 t까지 움직이며 알림을 만든다. 일이 끝나 쉬게 되면 true */
    fun advanceTo(t: Double): Boolean {
        val wasBusy = !idle
        val from = now
        now = max(now, t)
        makeAlerts(from, now)
        return wasBusy && idle
    }

    private fun makeAlerts(t0: Double, t1: Double) {
        val rows = (lastPlans.flatMap { it.jobs } + taken).mapNotNull { world.orders[it]?.vessel_row }.filter { it >= 0 }.toSet()
        for (r in rows) {
            val v = world.vessels[r] ?: continue
            val berth = board.berth(r) ?: continue
            if (v.etb in t0..t1 && berth > v.etb + 30 && seen.add("late$r")) {
                val y = world.delayInfo(r, v.etb + 1)
                val q50 = quantile(y, 0.5); val q90 = quantile(y, 0.9)
                alerts.add(Alert(v.etb, AlertKind.BERTH_LATE, "${v.name} 접안 늦어짐",
                    "공지 접안 시각이 지났는데 아직 안 들어왔어요. 예측: 보통 ${fmtH(q50)} 늦게, 늦으면 ${fmtH(q90)}"))
            }
            if (berth in t0..t1 && seen.add("berth$r")) {
                val dh = (berth - v.etb) / 60.0
                alerts.add(Alert(berth, AlertKind.BERTH_CONFIRMED, "${v.name} 접안 확인",
                    if (kotlin.math.abs(dh) < 0.25) "공지대로 들어왔어요" else "공지보다 ${fmtH(kotlin.math.abs(dh))} ${if (dh > 0) "늦게" else "일찍"} 들어왔어요"))
            }
        }
        val best = lastPlans.firstOrNull()
        if (best != null && idle) for (j in best.jobs) {
            val c = claims[j] ?: continue
            if (c.t in t0..t1 && seen.add("claim$j")) {
                alerts.add(Alert(c.t, AlertKind.TAKEN_BY_OTHER, "${label(j)} 다른 트럭 배정", "${c.truck}가 맡았어요. 추천을 다시 계산했어요"))
            }
        }
    }

    companion object {
        fun fmtH(h: Double): String {
            val m = Math.round(h * 60).toInt()
            return if (m < 60) "${m}분" else if (m % 60 == 0) "${m / 60}시간" else "${m / 60}시간 ${m % 60}분"
        }
    }
}

/** 그날 0시부터 분 → "07:30" (24시 넘으면 다음 날 표시) */
fun clock(t: Double): String {
    val m = Math.floorMod(Math.round(t).toInt(), 1440)
    val d = Math.floorDiv(Math.round(t).toInt(), 1440)
    return "%02d:%02d".format(m / 60, m % 60) + if (d > 0) " (+${d}일)" else if (d < 0) " (${d}일)" else ""
}

fun won(x: Double): String = if (kotlin.math.abs(x) >= 10000) "%.1f만원".format(x / 10000) else "%,d원".format(Math.round(x))

@Suppress("unused") private fun clamp(x: Double, a: Double, b: Double) = min(b, max(a, x))
