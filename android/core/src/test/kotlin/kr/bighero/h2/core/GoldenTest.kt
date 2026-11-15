package kr.bighero.h2.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Kotlin 엔진이 Python Planner1(h2-dispatch/src/scenarios.py)과 같은 일정안을 내는지 본다.
 * 정답은 h2-dispatch/src/app_bundle.py가 같은 하루치 묶음으로 만든 golden_<날>.json.
 */
class GoldenTest {
    private val day = "2026-07-16"
    private val bundle = DayBundle.parse(File("../app/src/main/assets/day_$day.json").readText())
    private val golden = Json.parseToJsonElement(javaClass.getResource("/golden_$day.json")!!.readText()).jsonObject

    @Test
    fun plansMatchPython() {
        val board = TruthBoard(bundle)
        val w = World(bundle, board)
        val cases = golden["cases"]!!.jsonArray
        assertTrue(cases.size >= 4)
        for (c in cases) {
            c as JsonObject
            val name = c["name"]!!.jsonPrimitive.content
            val s = c["state"]!!.jsonObject
            val st = TruckState(s["loc"]!!.jsonPrimitive.content, s["t"]!!.jsonPrimitive.double, s["h2"]!!.jsonPrimitive.double,
                s["shift_end"]!!.jsonPrimitive.double, bundle.params.home, s["box"]!!.jsonPrimitive.boolean)
            val pool = c["pool"]!!.jsonArray.map { it.jsonPrimitive.content }
            val got = Planner(w).candidates(st, pool)
            val want = c["plans"]!!.jsonArray.map { it.jsonObject }
            assertEquals(want.size, got.size, "$name: 안 개수")
            for ((i, pw) in want.withIndex()) {
                val g = got[i]
                val seq = pw["seq"]!!.jsonArray.map { it.jsonPrimitive.content }
                val v = pw["v"]!!.jsonPrimitive.double
                assertTrue(abs(g.v - v) < 1.0, "$name #$i 점수 ${g.v} vs $v (${g.seq} vs $seq)")
                // 점수가 같은 안끼리는 순서가 바뀌어도 된다(부동소수 끝자리). 다르면 순서도 같아야 함
                val tiedSeqs = want.filter { abs(it["v"]!!.jsonPrimitive.double - v) < 1.0 }.map { o -> o["seq"]!!.jsonArray.map { it.jsonPrimitive.content } }
                assertTrue(g.seq in tiedSeqs, "$name #$i 일정 ${g.seq} vs $seq")
                assertTrue(abs(g.planEnd - pw["plan_end"]!!.jsonPrimitive.double) < 0.01, "$name #$i 끝 시각")
            }
        }
    }

    @Test
    fun quantileLikeNumpy() {
        val a = doubleArrayOf(3.0, 1.0, 2.0, 4.0)
        assertEquals(1.0, quantile(a, 0.0))
        assertEquals(2.5, quantile(a, 0.5), 1e-12)
        assertEquals(3.25, quantile(a, 0.75), 1e-12)
    }
}
