package kr.bighero.h2.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** 트럭 상태: 위치, 시각, 수소(kg), 계획이 끝나야 하는 시각(근무 끝 − 30분), 빈 컨테이너를 싣고 있나 */
data class TruckState(val loc: String, val t: Double, val h2: Double, val shiftEnd: Double, val home: String, val box: Boolean)

/** 일정의 한 단계(일감 하나 또는 충전 한 번) 계산 결과. 화면의 "왜 이 추천?"에 그대로 쓴다 */
data class Step(
    val id: String,                 // 오더 id 또는 충전소 id
    val isCharge: Boolean,
    val begin: Double,              // 이 단계를 시작하는 시각(앞 단계 끝)
    val end: Double,                // 이 단계를 마친 시각
    val endLoc: String,
    val h2End: Double,
    val fare: Double,
    val kmEmpty: Double,
    val kmLoaded: Double,
    val toll: Double,
    val waitH: Double,
    val lateCost: Double,
    val risk: Double,
    val idleH: Double,
    val boxAfter: Boolean?,
    val boxStop: World.BoxStop? = null,   // 가는 길에 빈 컨테이너를 받거나 내려놓을 곳
    val target: Double? = null,           // 수입: 터미널에 맞춰 갈 시각
    val portArr: Double? = null,          // 터미널 도착(예정)
    val pMiss: Double? = null,            // 수출: 이 도착이면 배를 놓칠 확률
    val pickupArr: Double? = null,        // 싣는 곳 도착
    val chargeStart: Double? = null,
    val chargeKg: Double? = null,
    val chargeWon: Double? = null,
)

data class Totals(
    val fare: Double = 0.0, val kmEmpty: Double = 0.0, val kmLoaded: Double = 0.0, val toll: Double = 0.0,
    val waitH: Double = 0.0, val lateCost: Double = 0.0, val risk: Double = 0.0, val idleH: Double = 0.0,
) {
    operator fun plus(s: Step) = Totals(fare + s.fare, kmEmpty + s.kmEmpty, kmLoaded + s.kmLoaded, toll + s.toll,
        waitH + s.waitH, lateCost + s.lateCost, risk + s.risk, idleH + s.idleH)
}

data class Plan(
    val steps: List<Step>,
    val totals: Totals,
    val v: Double,                 // 점수 = 용차 단가 − 수소 − 통행료 − (대기+놀기)×4만 − 늦음 − 놓침 위험 − 초과근무
    val overtimeCost: Double,
    val h2Cost: Double,
    val planEnd: Double,
    val endLoc: String,
    val endH2: Double,
) {
    val seq get() = steps.map { it.id }
    val jobs get() = steps.filter { !it.isCharge }.map { it.id }
    val charge get() = steps.firstOrNull { it.isCharge }
}

/**
 * 1단계: 트럭 한 대의 다음 일정 후보(h2-dispatch/src/scenarios.py Planner1을 그대로 옮김, 기업용 P2 설정).
 * 일감 1~3개 + 충전 0~1회를 깊이 제한 탐색으로 이어 붙이고 수소 15%·시간창·근무시간·운영시간으로 거른다.
 * 기업용 설정: idle_cost = 켬, λ = 0, 초과근무 시간당 4만원, 같은 묶음 K개, 급한 일감 추가 끔.
 */
class Planner(private val w: World) {
    private val p = w.p
    var maxJobs = p.max_jobs
    var top = p.top
    var K = p.K
    var groupCap = p.group_cap
    var otWonH = p.ot_won_h
    var lam = p.lam_manwon_h
    var exportBufferH = 0.0
    private var busy: Map<String, List<Pair<Double, Double>>> = emptyMap()

    private fun fail(@Suppress("UNUSED_PARAMETER") why: String): Step? = null

    // ---------- 일감 하나(do_job) ----------
    fun doJob(o: Order, loc0: String, t0: Double, h20: Double, info: (Int) -> DoubleArray, shiftEnd: Double, box: Boolean?): Step? {
        var loc = loc0; var t = t0; var h2 = h20
        var kmE = 0.0; var kmL = 0.0; var toll = 0.0; var waitH: Double; var late = 0.0; var risk = 0.0; var idleH = 0.0
        var target: Double? = null; var portArr: Double? = null; var pMiss: Double? = null; var pick: Double? = null
        var bw = 0.0; var stop: World.BoxStop? = null
        if (box != null && (o.isExport || o.isImport) && box == o.isImport) {
            val s = w.boxStop(loc, o.origin, t)
            kmE += s.km; toll += s.toll; h2 -= s.km * w.kgkm; bw = s.waitMin / 60.0; stop = s
            if (h2 < w.reserve) return fail("수소 부족")
            loc = s.at; t = s.done
        }
        val boxAfter: Boolean? = if (box == null) null else when { o.isExport -> false; o.isImport -> true; else -> box }
        if (o.isImport) {
            val y = info(o.vessel_row); val cLate = o.c_late_manwon_h ?: 1.0; val wm = p.wait_won_h / 1e4
            val q = lam / (wm + lam + cLate)
            target = o.avail!! + 60.0 * quantile(y, q)
            val d1 = w.drive(loc, o.origin, t)
            idleH = max(0.0, target - d1.arr) / 60.0
            kmE += d1.km; toll += d1.toll; h2 -= d1.km * w.kgkm
            if (h2 < w.reserve) return fail("수소 부족")
            pick = d1.arr
            val arr = max(d1.arr, target)
            if (arr > o.free_until!!) return fail("시간창 위반")
            if (arr > shiftEnd) return fail("근무시간 초과")
            val bh = (arr - o.avail) / 60.0
            waitH = y.sumOf { max(0.0, it - bh) } / y.size
            late = y.sumOf { max(0.0, bh - it) } / y.size * cLate * 1e4
            portArr = arr
            t = arr + 60.0 * waitH + p.terminal_min
            val d2 = w.drive(o.origin, o.dest, t)
            kmL += d2.km; toll += d2.toll; h2 -= d2.km * w.kgkm
            if (h2 < w.reserve) return fail("수소 부족")
            t = d2.arr + p.shipper_handling_min
        } else if (o.isExport) {
            val d1 = w.drive(loc, o.origin, t)
            kmE += d1.km; toll += d1.toll; h2 -= d1.km * w.kgkm
            if (h2 < w.reserve) return fail("수소 부족")
            pick = d1.arr
            t = max(d1.arr, o.ready!!)
            idleH = (t - d1.arr) / 60.0
            if (t > shiftEnd) return fail("근무시간 초과")
            t += p.shipper_handling_min
            val d2 = w.drive(o.origin, o.dest, t)
            kmL += d2.km; toll += d2.toll; h2 -= d2.km * w.kgkm
            if (h2 < w.reserve) return fail("수소 부족")
            val go = o.gate_open
            val early = if (go == null || go.isNaN()) 0.0 else max(0.0, go - d2.arr)
            val arr = d2.arr + early
            val y0 = info(o.vessel_row)
            val bh = (arr - o.cutoff!!) / 60.0
            val pm = y0.count { it - exportBufferH < bh }.toDouble() / y0.size
            if (arr > o.latest_rule!!) {
                // 마감 3시간 전보다 늦게 닿는 수출도 보내고, 놓칠 확률 × C를 위험 비용으로 뺀다
                if (pm >= 0.99) return fail("시간창 위반")
                risk = (o.c_miss_manwon ?: 100.0) * 1e4 * pm
            }
            pMiss = pm
            waitH = early / 60.0; portArr = arr
            t = arr + p.terminal_min
        } else {
            val d1 = w.drive(loc, o.origin, t)
            kmE += d1.km; toll += d1.toll; h2 -= d1.km * w.kgkm
            if (h2 < w.reserve) return fail("수소 부족")
            pick = d1.arr
            t = max(d1.arr, o.ready!!)
            if (o.origin.startsWith("DEP")) {
                val (op, cl) = w.depotWindow(t)
                if (t < op) t = op
                if (t > cl) return fail("디포 운영시간 밖")
                t += p.depot_min
            } else t += p.shipper_handling_min
            val d2 = w.drive(o.origin, o.dest, t)
            kmL += d2.km; toll += d2.toll; h2 -= d2.km * w.kgkm
            if (h2 < w.reserve) return fail("수소 부족")
            t = d2.arr
            if (o.dest.startsWith("DEP")) {
                val (op, cl) = w.depotWindow(t)
                if (t < op) t = op
                if (t > cl) return fail("디포 운영시간 밖")
                t += p.depot_min
            } else t += p.shipper_handling_min
            waitH = 0.0
        }
        waitH += bw
        return Step(o.id, false, t0, t, o.dest, h2, o.fare, kmE, kmL, toll, waitH, late, risk, idleH, boxAfter,
            boxStop = stop, target = target, portArr = portArr, pMiss = pMiss, pickupArr = pick)
    }

    // ---------- 충전(do_charge) ----------
    fun doCharge(c: String, loc: String, t: Double, h20: Double): Step? {
        val d = w.drive(loc, c, t)
        val h2 = h20 - d.km * w.kgkm
        if (h2 < 0) return fail("충전소까지 못 감")
        val win = w.chargerWindow(c, d.arr)
        var s = max(d.arr, win.open)
        val brk = win.brk
        if (brk != null && brk.first <= s && s < brk.second) s = brk.second
        val ch = w.chargers[c]!!
        val dur = ch.setup_min + (w.tank - h2) / ch.rate_kg_h * 60.0
        val ready = s
        val sl = p.slot_min
        val bl = (busy[c] ?: emptyList()).map { (bs, be) -> floor(bs / sl) * sl to ceil(be / sl) * sl }
            .sortedWith(compareBy({ it.first }, { it.second }))
        if (ch.cap == 1) {
            for ((bs, be) in bl) {
                if (s < be && s + dur > bs) {
                    s = be
                    if (brk != null && brk.first <= s && s < brk.second) s = brk.second
                }
            }
        } else {
            var found = false
            for (cand0 in listOf(s) + bl.filter { it.second > s }.map { it.second }.sorted()) {
                var cand = cand0
                if (brk != null && brk.first <= cand && cand < brk.second) cand = brk.second
                if (chargerFree(bl, cand, cand + dur, ch.cap)) { s = cand; found = true; break }
            }
            if (!found) return fail("충전소 운영시간 밖")
        }
        if (s + dur > win.close) return fail("충전소 운영시간 밖")
        val kg = w.tank - h2
        return Step(c, true, t, s + dur, c, w.tank, 0.0, d.km, 0.0, d.toll, (s - ready) / 60.0, 0.0, 0.0, 0.0, null,
            chargeStart = s, chargeKg = kg, chargeWon = kg * ch.price_krw_kg, pickupArr = d.arr)
    }

    fun score(r: Totals) = r.fare - (r.kmEmpty + r.kmLoaded) * w.wonKm - r.toll - (r.waitH + r.idleH) * p.wait_won_h - r.lateCost - r.risk
    private fun score(s: Step) = score(Totals() + s)

    // ---------- 후보 만들기(candidates) ----------
    fun candidates(st: TruckState, pool: List<String>, busy: Map<String, List<Pair<Double, Double>>> = emptyMap()): List<Plan> {
        val now = st.t
        this.busy = busy
        val cache = HashMap<Int, DoubleArray>()
        val info = { rid: Int -> cache.getOrPut(rid) { w.delayInfo(rid, now) } }
        val se = st.shiftEnd
        val seTrue = se + p.plan_margin_min
        val box0 = st.box
        val after = w.chargerIds.mapNotNull { doCharge(it, st.loc, st.t, st.h2) }
        data class Pre(val loc: String, val t: Double, val h2: Double, val kmE: Double, val toll: Double)
        val starts = listOf(Pre(st.loc, st.t, st.h2, 0.0, 0.0)) + after.map { Pre(it.endLoc, it.end, it.h2End, it.kmEmpty, it.toll) }
        val single = ArrayList<Pair<Double, String>>()
        for (j in pool) {
            val o = w.orders[j] ?: continue
            var best: Double? = null
            for (s in starts) {
                val r = doJob(o, s.loc, s.t, s.h2, info, se, box0) ?: continue
                if (r.end > se) continue
                val sc = score(r) - s.kmE * w.wonKm - s.toll
                best = if (best == null) sc else max(best, sc)
            }
            if (best != null) single.add(best to j)
        }
        // 같은 종류·출발·도착 묶음마다 점수 높고 급한 순으로 group_cap개(빈 컨테이너 1개)
        val grp = LinkedHashMap<Triple<String, String, String>, MutableList<Pair<Double, String>>>()
        for (x in single.sortedWith(compareBy({ -it.first }, { w.latestPickup(w.orders[it.second]!!) }))) {
            val o = w.orders[x.second]!!
            val g = grp.getOrPut(Triple(o.type, o.origin, o.dest)) { ArrayList() }
            if (g.size < (if (o.type == "empty") 1 else groupCap)) g.add(x)
        }
        // Python: sorted([(s, j)...], reverse=True) → 점수 내림차순, 같으면 id 내림차순
        val keep = grp.values.flatten().sortedWith(compareByDescending<Pair<Double, String>> { it.first }.thenByDescending { it.second })
            .take(K).map { it.second }
        val res = ArrayList<Plan>()
        fun dfs(loc: String, t: Double, h2: Double, tot: Totals, steps: List<Step>, used: Set<String>, charged: Boolean, nj: Int, box: Boolean) {
            for (s in keep + (if (charged) emptyList() else w.chargerIds)) {
                if (s in used) continue
                val isCh = s.startsWith("CHG")
                val r = (if (isCh) doCharge(s, loc, t, h2) else doJob(w.orders[s]!!, loc, t, h2, info, se, box)) ?: continue
                if (r.end > se) continue
                val nt = tot + r
                val st2 = steps + r
                val n2 = nj + (if (isCh) 0 else 1)
                val b2 = r.boxAfter ?: box
                if (n2 >= 1) res.add(pack(st2, nt, r, b2, st.home, seTrue))
                if (n2 < maxJobs) dfs(r.endLoc, r.end, r.h2End, nt, st2, used + s, charged || isCh, n2, b2)
            }
        }
        dfs(st.loc, st.t, st.h2, Totals(), emptyList(), emptySet(), false, 0, box0)
        val sorted = res.sortedByDescending { it.v }   // 안정 정렬(파이썬 list.sort와 같음)
        val out = ArrayList<Plan>(); val cnt = HashMap<String, Int>()
        for (pl in sorted) {
            if (pl.jobs.any { (cnt[it] ?: 0) >= 5 }) continue
            out.add(pl)
            for (j in pl.jobs) cnt[j] = (cnt[j] ?: 0) + 1
            if (out.size == top) break
        }
        return out
    }

    private fun pack(steps: List<Step>, tot: Totals, last: Step, box: Boolean, home: String, seTrue: Double): Plan {
        var v = score(tot)
        var ot = 0.0
        if (otWonH > 0) {
            val back = w.homeTrip(last.endLoc, home, last.end, box).first
            ot = max(0.0, back - seTrue) / 60.0 * otWonH
            v -= ot
        }
        return Plan(steps, tot, v, ot, (tot.kmEmpty + tot.kmLoaded) * w.wonKm, last.end, last.endLoc, last.h2End)
    }
}

fun chargerFree(busy: List<Pair<Double, Double>>, s: Double, e: Double, cap: Int): Boolean {
    val iv = busy.filter { it.first < e && it.second > s }.map { max(it.first, s) to minOf(it.second, e) }
    if (iv.size < cap) return true
    var n = 0
    val ev = (iv.map { it.first to 1 } + iv.map { it.second to -1 }).sortedWith(compareBy({ it.first }, { it.second }))
    for ((_, k) in ev) { n += k; if (n >= cap) return false }
    return true
}
