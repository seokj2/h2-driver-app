package kr.bighero.h2.core

import kotlin.math.floor
import kotlin.math.max

const val DAY = 1440.0

/** 주행 결과: 도착 시각(분), 거리(km), 통행료(원) */
data class Drive(val arr: Double, val km: Double, val toll: Double)

/**
 * 터미널 선석 현황(공개 정보): 지금까지 접안했으면 실제 지연(시간)을 준다. 아직이면 null.
 * 차량에서는 터미널/Port-i 연동으로, 시연에서는 [DaySim]이 정답 표로 채운다.
 */
fun interface BerthBoard {
    fun berthedDelayH(rowId: Int, now: Double): Double?
}

/**
 * h2-dispatch/src/world.py의 World 중 배차 쪽이 보는 부분(주행시간 표 평균, 운영시간, 빈 컨테이너 흐름, 지연 정보).
 * 실제 접안·마감 같은 정답은 여기 없다.
 */
class World(val b: DayBundle, val board: BerthBoard) {
    val p = b.params
    val tank = p.tank_kg
    val kgkm = p.kg_per_km
    val reserve = p.reserve_kg
    val wonKm = kgkm * p.h2_price_score
    val node = b.nodes.associateBy { it.id }
    val side = b.nodes.associate { it.id to it.side }
    private val tt = b.travel.associateBy { it.a to it.b }
    val chargers = b.chargers.associateBy { it.id }   // 순서 = 묶음 순서(태화강역, 내트럭)
    val chargerIds = b.chargers.map { it.id }
    val vessels = b.vessels.associateBy { it.row_id }
    private val scen = b.vessels.associate { it.row_id to it.scen.sorted().toDoubleArray() }
    val orders = b.orders.associateBy { it.id }
    private val latest = HashMap<String, Double>()

    fun name(id: String) = node[id]?.name ?: id

    private fun hourOf(t: Double) = Math.floorMod(floor(t / 60.0).toInt(), 24)

    fun drive(a: String, bb: String, t: Double): Drive {
        if (a == bb) return Drive(t, 0.0, 0.0)
        val e = tt[a to bb] ?: error("주행시간 없음 $a→$bb")
        return Drive(t + e.min[hourOf(t)], e.km, if (side[a] != side[bb]) p.toll_oneway else 0.0)
    }

    fun tt(a: String, bb: String, t: Double) = if (a == bb) 0.0 else tt[a to bb]!!.min[hourOf(t)]
    fun km(a: String, bb: String) = if (a == bb) 0.0 else tt[a to bb]!!.km

    // ---------- 지연 정보(world.delay_info, mode 'model') ----------
    fun etb(rowId: Int) = vessels[rowId]!!.etb

    fun delayInfo(rowId: Int, now: Double): DoubleArray {
        if (rowId < 0) return doubleArrayOf(0.0)
        board.berthedDelayH(rowId, now)?.let { return doubleArrayOf(it) }
        val e = (now - etb(rowId)) / 60.0
        val sc = scen[rowId]!!
        if (sc[0] > e) return sc
        val y = sc.filter { it > e }
        if (y.size >= 10) return y.toDoubleArray()
        val base = sc[sc.size - 10]
        return DoubleArray(10) { e + 0.1 + (sc[sc.size - 10 + it] - base) }   // 시나리오가 거의 다 지나갔으면 위쪽 꼬리 모양만 빌린다
    }

    // ---------- 시간창 ----------
    fun latestPickup(o: Order): Double = latest.getOrPut(o.id) {
        when {
            o.isExport -> o.latest_rule!! - tt[o.origin to o.dest]!!.min[6] - p.shipper_handling_min
            o.isImport -> o.free_until!! - p.terminal_min
            else -> o.due!! - tt[o.origin to o.dest]!!.min[6] - p.depot_min
        }
    }

    fun depotWindow(t: Double): Pair<Double, Double> {
        val d0 = floor(t / DAY) * DAY
        return (d0 + p.depot_open) to (d0 + p.depot_close)
    }

    data class ChWin(val open: Double, val close: Double, val brk: Pair<Double, Double>?)

    fun chargerWindow(c: String, t: Double): ChWin {
        val ch = chargers[c]!!
        val d0 = floor(t / DAY) * DAY
        return ChWin(d0 + ch.open, d0 + ch.close, ch.brk?.let { (d0 + it[0]) to (d0 + it[1]) })
    }

    // ---------- 빈 컨테이너 흐름(world.box_stop, home_trip) ----------
    data class BoxStop(val at: String, val done: Double, val km: Double, val toll: Double, val waitMin: Double)

    fun boxStop(a: String, dest: String, t: Double): BoxStop {
        var best: BoxStop? = null
        var bestArr = Double.POSITIVE_INFINITY
        for (s in p.depots + p.terminal) {
            val d = drive(a, s, t)
            val w: Double
            val done: Double
            if (s.startsWith("DEP")) {
                val (op, cl) = depotWindow(d.arr)
                if (d.arr > cl) continue
                w = max(0.0, op - d.arr); done = d.arr + w + p.depot_min
            } else {
                w = 0.0; done = d.arr + p.terminal_min
            }
            val arr = drive(s, dest, done).arr
            if (best == null || arr < bestArr) { best = BoxStop(s, done, d.km, d.toll, w); bestArr = arr }
        }
        return best!!
    }

    fun homeTrip(a: String, home: String, t: Double, box: Boolean): Pair<Double, Double> {
        if (a == home) return t to 0.0
        if (!box && side[a] != side[home]) {
            val s = boxStop(a, home, t)
            val d2 = drive(s.at, home, s.done)
            return d2.arr to (s.km + d2.km)
        }
        val d = drive(a, home, t)
        return d.arr to d.km
    }
}

/** numpy.quantile 기본(linear)과 같은 분위수. a는 정렬 안 돼 있어도 됨 */
fun quantile(a: DoubleArray, q: Double): Double {
    val s = a.sortedArray()
    if (s.size == 1) return s[0]
    val pos = q * (s.size - 1)
    val lo = floor(pos).toInt()
    val hi = minOf(lo + 1, s.size - 1)
    return s[lo] + (s[hi] - s[lo]) * (pos - lo)
}
