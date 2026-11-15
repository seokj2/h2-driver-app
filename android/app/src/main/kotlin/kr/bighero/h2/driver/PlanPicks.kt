package kr.bighero.h2.driver

import kr.bighero.h2.core.DaySim
import kr.bighero.h2.core.Plan
import kr.bighero.h2.core.clock
import kr.bighero.h2.core.won

/** 기사에게 보여 줄 한 안: 성격(이름)과 기사 입장의 한 줄 설명 */
data class Pick(val plan: Plan, val tag: String, val note: String)

/**
 * 엔진이 낸 후보(점수순, 최대 20개)에서 성격이 다른 세 안을 고른다. 엔진 계산은 그대로 두고 보여 주는 순서만 정한다.
 *  1안 알찬 일정: 회사에 가장 이득(점수 1등)
 *  2안 여유 있는 일정: 가장 일찍 끝나 쉬는 시간이 많은 안(이득이 나는 것 중)
 *  3안 마음 편한 일정: 배 놓칠 걱정이 가장 적고 수소가 넉넉한 안
 * 같은 일감 묶음은 두 번 고르지 않는다. 고를 게 없으면 다음 점수 순으로 채운다.
 */
fun pickPlans(sim: DaySim, all: List<Plan>, light: List<Plan> = emptyList()): List<Pick> {
    if (all.isEmpty()) return emptyList()
    val best = all.first()
    val used = HashSet<List<String>>()
    fun key(p: Plan) = p.jobs.map { sim.label(it) }.sorted()   // 같은 배·같은 종류 일감만 다른 안은 같은 안으로 봄
    fun ok(p: Plan) = key(p) !in used
        fun maxMiss(p: Plan) = p.steps.maxOfOrNull { it.pMiss ?: 0.0 } ?: 0.0
    val out = ArrayList<Pick>()

    used += key(best)
    val busy = best.totals.idleH + best.totals.waitH < 0.5
    out += Pick(best, "알찬 일정",
        (if (busy) "쉬는 틈이 거의 없어 바쁘지만, " else "") +
            "오늘 가장 알찬 일정이에요 · 일 ${best.jobs.size}건, ${clock(best.planEnd)}에 끝나요")

    val floor = best.v * 0.3
    (all + light).filter { ok(it) && it.v > 0 && it.planEnd < best.planEnd - 20 }.minByOrNull { it.planEnd }?.let { p ->
        used += key(p)
        out += Pick(p, "여유 있는 일정",
            "쉬는 시간이 넉넉해 컨디션이 좋지 않은 날 알맞은 일정이에요 · ${clock(p.planEnd)}에 끝나 1안보다 ${DaySim.fmtH((best.planEnd - p.planEnd) / 60.0)} 일찍 쉬어요")
    }

    all.filter { ok(it) && it.v >= floor }
        .minWithOrNull(compareBy<Plan>({ maxMiss(it) }, { -it.endH2 }, { -it.v }))
        ?.takeIf { maxMiss(it) < maxMiss(best) || it.endH2 > best.endH2 + 5 || it.charge != null }
        ?.let { p ->
            used += key(p)
            val why = buildList {
                if (maxMiss(p) < 0.01) add("배 놓칠 걱정 없이") else if (maxMiss(p) < maxMiss(best)) add("마감 여유가 더 있고")
                if (p.charge != null) add("가는 길에 충전까지 해서")
                add("끝날 때 수소 %.0f%% 남아요".format(p.endH2 / sim.world.tank * 100))
            }
            out += Pick(p, "마음 편한 일정", why.joinToString(" ") + " · 1안보다 ${won(best.v - p.v)} 적음")
        }

    for (p in all) {
        if (out.size >= 3) break
        if (!ok(p)) continue
        used += key(p)
        out += Pick(p, "다른 조합", "일 ${p.jobs.size}건, ${clock(p.planEnd)}에 끝나요 · 1안보다 ${won(best.v - p.v)} 적음")
    }
    return out
}
