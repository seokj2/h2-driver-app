package kr.bighero.h2.driver.ui

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 갈림길 안내 하나: 출발에서 at_m 미터 지점 */
@Serializable data class RouteStep(val at_m: Double, val text: String, val lat: Double, val lon: Double)
@Serializable data class RouteJson(val km: Double, val min: Double, val line: String, val steps: List<RouteStep>)
@Serializable data class RoutesFile(val source: String, val routes: Map<String, RouteJson>)

/** 실제 도로 경로 한 개(OSRM, assets/routes.json). 지도 그림·안내 문구용, 배차 계산에는 안 씀 */
class Road(val pts: List<DoubleArray>, val steps: List<RouteStep>) {
    /** 출발에서 각 점까지 누적 거리(m) */
    val cum: DoubleArray = DoubleArray(pts.size).also { c -> for (i in 1 until pts.size) c[i] = c[i - 1] + dist(pts[i - 1], pts[i]) }
    val length get() = cum.last()

    /** 진행 비율 f(0~1)에 있는 지점 [lat, lon]과 진행 방향(도, 북=0) */
    fun at(f: Double): Pair<DoubleArray, Double> {
        val d = (f.coerceIn(0.0, 1.0)) * length
        var i = cum.indexOfFirst { it >= d }.coerceAtLeast(1)
        if (i >= pts.size) i = pts.size - 1
        val a = pts[i - 1]; val b = pts[i]
        val seg = cum[i] - cum[i - 1]
        val t = if (seg > 0) (d - cum[i - 1]) / seg else 0.0
        val p = doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
        // 방향은 150m 앞 지점을 보고 정함(굽은 길에서 덜 흔들리게)
        val ahead = cum.indexOfFirst { it >= d + 150 }.let { if (it < 0) pts.size - 1 else it }
        val q = pts[ahead]
        return p to bearing(p, if (q === p || (q[0] == p[0] && q[1] == p[1])) b else q)
    }

    /** f까지 지난 부분과 남은 부분 */
    fun split(f: Double): Pair<List<DoubleArray>, List<DoubleArray>> {
        val d = f.coerceIn(0.0, 1.0) * length
        val (p, _) = at(f)
        val k = cum.indexOfFirst { it > d }.let { if (it < 0) pts.size else it }
        return (pts.subList(0, k) + listOf(p)) to (listOf(p) + pts.subList(k, pts.size))
    }

    /** 다음 안내(지금 지점 앞 첫 안내)와 남은 거리(m) */
    fun next(f: Double): Pair<RouteStep?, Double> {
        val d = f.coerceIn(0.0, 1.0) * length
        val s = steps.firstOrNull { it.at_m > d + 5 }
        return s to ((s?.at_m ?: length) - d)
    }

    companion object {
        fun dist(a: DoubleArray, b: DoubleArray): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(b[0] - a[0]); val dLon = Math.toRadians(b[1] - a[1])
            val h = sin(dLat / 2).let { it * it } + cos(Math.toRadians(a[0])) * cos(Math.toRadians(b[0])) * sin(dLon / 2).let { it * it }
            return 2 * r * atan2(sqrt(h), sqrt(1 - h))
        }
        fun bearing(a: DoubleArray, b: DoubleArray): Double {
            val y = sin(Math.toRadians(b[1] - a[1])) * cos(Math.toRadians(b[0]))
            val x = cos(Math.toRadians(a[0])) * sin(Math.toRadians(b[0])) - sin(Math.toRadians(a[0])) * cos(Math.toRadians(b[0])) * cos(Math.toRadians(b[1] - a[1]))
            return (Math.toDegrees(atan2(y, x)) + 360) % 360
        }
        /** Google 인코딩 폴리라인(정밀도 1e-6) 풀기 */
        fun decode(s: String, prec: Double = 1e6): List<DoubleArray> {
            val out = ArrayList<DoubleArray>(); var i = 0; var lat = 0L; var lon = 0L
            while (i < s.length) {
                for (k in 0..1) {
                    var shift = 0; var res = 0L
                    while (true) { val c = s[i++].code - 63; res = res or ((c and 0x1f).toLong() shl shift); shift += 5; if (c < 0x20) break }
                    val v = if (res and 1L != 0L) (res shr 1).inv() else res shr 1
                    if (k == 0) lat += v else lon += v
                }
                out.add(doubleArrayOf(lat / prec, lon / prec))
            }
            return out
        }
    }
}

class Roads(file: RoutesFile) {
    val source = file.source
    private val raw = file.routes
    private val cache = HashMap<String, Road>()
    fun get(from: String, to: String): Road? = cache.getOrPut("$from>$to") {
        val r = raw["$from>$to"] ?: return null
        Road(Road.decode(r.line), r.steps)
    }
    companion object {
        fun parse(text: String) = Roads(Json { ignoreUnknownKeys = true }.decodeFromString(RoutesFile.serializer(), text))
    }
}
