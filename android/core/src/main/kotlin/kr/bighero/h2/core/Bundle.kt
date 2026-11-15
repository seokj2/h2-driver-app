package kr.bighero.h2.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 하루치 자료 묶음(h2-dispatch/src/app_bundle.py가 만든 JSON).
 * 시각은 모두 그날 0시부터의 분(Double). truth는 시뮬레이션 피드([DaySim])만 읽고, 엔진([Planner])에는 넘기지 않는다.
 */
@Serializable
data class DayBundle(
    val meta: Meta,
    val params: Params,
    val nodes: List<Node>,
    val travel: List<Travel>,
    val chargers: List<Charger>,
    val vessels: List<Vessel>,
    val orders: List<Order>,
    val fleet: List<Truck>,
    val claims: List<Claim>,
    val truth: Truth,
    val driver: DriverStart,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): DayBundle = json.decodeFromString(serializer(), text)
    }
}

@Serializable data class Meta(val day: String, val made: String, val source: String, val note: String = "")

@Serializable
data class Params(
    val tank_kg: Double,
    val kg_per_km: Double,
    val reserve_kg: Double,
    val h2_price_score: Double,
    val toll_oneway: Double,
    val wait_won_h: Double,
    val shipper_handling_min: Double,
    val terminal_min: Double,
    val depot_min: Double,
    val depot_open: Double,
    val depot_close: Double,
    val shift_min: Double,
    val plan_margin_min: Double,
    val ot_won_h: Double,
    val lam_manwon_h: Double,
    val slot_min: Double,
    val max_jobs: Int,
    val top: Int,
    val K: Int,
    val group_cap: Int,
    val terminal: String,
    val home: String,
    val depots: List<String>,
    val export_risk_warn: Double,
)

@Serializable data class Node(val id: String, val name: String, val lat: Double, val lon: Double, val side: String)

/** a→b 평균 주행시간(시간대 0~23시별, 분)과 거리(km) */
@Serializable data class Travel(val a: String, val b: String, val km: Double, val min: List<Double>)

@Serializable
data class Charger(
    val id: String, val name: String, val rate_kg_h: Double, val price_krw_kg: Double, val setup_min: Double, val cap: Int,
    val open: Double, val close: Double, val brk: List<Double>? = null,
)

/** 배: 공지 접안(etb)과 지연 예측 시나리오 100개(시간, XGBoost 분위수 + 구간 보정) */
@Serializable data class Vessel(val row_id: Int, val name: String, val etb: Double, val scen: List<Double>)

@Serializable
data class Order(
    val id: String,
    val type: String,               // export | import | empty
    val vessel_row: Int,
    val vessel: String? = null,
    val origin: String,
    val dest: String,
    val service_day: Double,
    val post: Double,
    val ready: Double? = null,
    val gate_open: Double? = null,
    val cutoff: Double? = null,      // 공지 마감
    val latest_rule: Double? = null, // 현행 규칙(공지 마감 3시간 전)
    val avail: Double? = null,       // 공지 기준 반출 가능
    val free_until: Double? = null,
    val due: Double? = null,
    val c_miss_manwon: Double? = null,
    val c_late_manwon_h: Double? = null,
    val fare: Double,
    val late_ready: Boolean = false,
) {
    val isExport get() = type == "export"
    val isImport get() = type == "import"
}

@Serializable data class Truck(val id: String, val name: String)

/** 다른 트럭이 이 오더를 잡아 간 시각(기업용 시뮬레이션 기록) */
@Serializable data class Claim(val order: String, val t: Double, val truck: String)

@Serializable data class Truth(val vessels: List<TruthVessel>, val orders: List<TruthOrder>)
@Serializable data class TruthVessel(val row_id: Int, val berth: Double, val delay_h: Double)
@Serializable data class TruthOrder(val id: String, val cutoff: Double? = null, val avail: Double? = null)

@Serializable
data class DriverStart(val id: String, val name: String, val home: String, val start_loc: String, val shift_start: Double, val h2_kg: Double, val box: Boolean)
