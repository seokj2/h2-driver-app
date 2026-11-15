package kr.bighero.h2.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** 하루를 처음부터 끝까지 "1안 수락"으로 돌려 본다: 멈춤·수소 바닥·근무시간 크게 넘김이 없는지 */
class DaySimTest {
    private val bundle = DayBundle.parse(File("../app/src/main/assets/day_2026-07-16.json").readText())

    @Test
    fun wholeDayAutoAccept() {
        val sim = DaySim(bundle)
        sim.advanceTo(sim.shiftStart)
        var t = sim.shiftStart
        var guard = 0
        while (t < sim.shiftEnd + 180 && guard++ < 500) {
            if (sim.idle) {
                val plans = sim.recommend()
                if (plans.isNotEmpty()) sim.accept(plans.first()) else if (sim.loc != bundle.params.home) sim.goHome()
            }
            t += 5.0
            sim.advanceTo(t)
        }
        println("일 ${sim.ledger.jobsDone}건, 아낀 용차 ${won(sim.ledger.fareSaved)}, 수소 ${won(sim.ledger.h2Won)}, 놓침 ${sim.ledger.missN}, 끝 ${clock(sim.segs.last().end)}")
        sim.segs.forEach { println("${clock(it.start)}-${clock(it.end)} ${it.kind.label} ${it.from}→${it.to} ${"%.1f".format(it.h2End)}kg ${it.note}") }
        sim.alerts.forEach { println("[${clock(it.t)}] ${it.title}: ${it.body}") }
        assertTrue(sim.ledger.jobsDone >= 2)
        assertTrue(sim.segs.all { it.h2End >= 0 })
        assertTrue(sim.segs.last().end < sim.shiftEnd + 120)
    }
}
