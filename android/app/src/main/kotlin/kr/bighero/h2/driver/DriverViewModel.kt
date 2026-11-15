package kr.bighero.h2.driver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.bighero.h2.core.Alert
import kr.bighero.h2.core.AlertKind
import kr.bighero.h2.core.DayBundle
import kr.bighero.h2.core.DaySim
import kr.bighero.h2.core.Plan
import kr.bighero.h2.driver.vehicle.IntentNavi
import kr.bighero.h2.driver.vehicle.NaviDest
import kr.bighero.h2.driver.vehicle.NaviLauncher
import kr.bighero.h2.driver.vehicle.SimVehicleData
import kr.bighero.h2.driver.vehicle.VehicleData

/** 화면이 그리는 한 장면. DaySim은 바뀌는 객체라 tick으로 다시 그리게 한다 */
data class UiState(
    val loading: Boolean = true,
    val tick: Long = 0,
    val now: Double = 0.0,
    val running: Boolean = false,
    val speed: Int = 60,
    val plans: List<Plan> = emptyList(),
    val selected: Int = 0,
    val computing: Boolean = false,
    val newAlerts: Int = 0,
    val banner: Alert? = null,
    val error: String? = null,
)

class DriverViewModel(app: Application) : AndroidViewModel(app) {
    lateinit var sim: DaySim; private set
    lateinit var vehicle: VehicleData; private set
    val navi: NaviLauncher = IntentNavi(app)

    /** 다음에 차로 갈 곳(지금 주행 중이면 그 목적지, 아니면 확정된 다음 주행의 목적지) */
    fun nextDriveTarget(): String? {
        val cur = sim.currentSeg()
        if (cur != null && cur.kind.name.startsWith("DRIVE")) return cur.to
        return sim.upcoming().firstOrNull { it.kind.name.startsWith("DRIVE") }?.to
    }

    /** 순정 내비로 길안내 넘기기. 좌표는 노드 표(일부 근사값) */
    fun navigate(nodeId: String) {
        val n = sim.world.node[nodeId] ?: return
        val ok = navi.start(NaviDest(n.name, n.lat, n.lon))
        if (!ok) bump { it.copy(banner = kr.bighero.h2.core.Alert(sim.now, AlertKind.INFO, "길안내를 못 열었어요", "이 기기에 길안내를 받을 내비 앱이 없어요")) }
    }
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui
    private var ticker: Job? = null
    private var seenAlerts = 0
    private var lastPlanAt = -1e9
    private var lastPoolSize = -1

    /** 시연 시계 배속(시뮬레이션 1분 = 실제 몇 초가 아니라, 실제 1초에 흐르는 시뮬레이션 초) */
    val speeds = listOf(1, 60, 300, 900)

    init {
        viewModelScope.launch {
            try {
                val b = withContext(Dispatchers.IO) {
                    val name = app.assets.list("")!!.first { it.startsWith("day_") && it.endsWith(".json") }
                    DayBundle.parse(app.assets.open(name).bufferedReader(Charsets.UTF_8).readText())
                }
                sim = DaySim(b)
                withContext(Dispatchers.Default) { restore() }
                vehicle = SimVehicleData { sim }
                seenAlerts = sim.alerts.size
                _ui.value = UiState(loading = false, now = sim.now)
                replan()
            } catch (e: Exception) {
                _ui.value = UiState(loading = false, error = "자료를 못 읽었어요: ${e.message}")
            }
        }
    }

    // ---- 상태 저장: 앱이 다시 만들어져도(내비 다녀오기 등) 하던 데서 이어지게. 받은 일정·퇴근·시각만 적고 다시 돌려 복원 ----
    private val prefs = app.getSharedPreferences("h2_driver", android.content.Context.MODE_PRIVATE)
    private var lastSaved = -1e9
    private fun log(entry: String) {
        val l = prefs.getString("log", "")!!
        prefs.edit().putString("log", if (l.isEmpty()) entry else "$l|$entry").putFloat("now", sim.now.toFloat()).apply()
    }
    private fun saveNow() { if (kotlin.math.abs(sim.now - lastSaved) >= 1) { prefs.edit().putFloat("now", sim.now.toFloat()).apply(); lastSaved = sim.now } }
    private fun restore() {
        val day = prefs.getString("day", null)
        if (day != sim.bundle.meta.day) { prefs.edit().clear().putString("day", sim.bundle.meta.day).apply(); return }
        try {
            for (e in prefs.getString("log", "")!!.split("|").filter { it.isNotEmpty() }) {
                val f = e.split(":", limit = 3)
                sim.advanceTo(f[1].toDouble())
                if (f[0] == "a") sim.accept(sim.recommend().firstOrNull { it.seq.joinToString(",") == f[2] } ?: break) else sim.goHome()
            }
            sim.advanceTo(prefs.getFloat("now", sim.now.toFloat()).toDouble())
        } catch (_: Exception) { sim = DaySim(sim.bundle); prefs.edit().clear().putString("day", sim.bundle.meta.day).apply() }
    }

    private fun bump(f: (UiState) -> UiState = { it }) {
        val s = f(_ui.value)
        val nAlerts = sim.alerts.size
        val banner = if (nAlerts > seenAlerts && sim.alerts.last().kind != AlertKind.INFO) sim.alerts.last() else s.banner
        _ui.value = s.copy(tick = s.tick + 1, now = sim.now, newAlerts = nAlerts - seenAlerts, banner = banner)
    }

    fun markAlertsRead() { seenAlerts = sim.alerts.size; bump { it.copy(banner = null) } }
    fun dismissBanner() = bump { it.copy(banner = null) }

    fun replan() {
        if (!::sim.isInitialized) return
        _ui.value = _ui.value.copy(computing = true)
        viewModelScope.launch {
            val plans = withContext(Dispatchers.Default) { sim.recommend() }
            lastPlanAt = sim.now; lastPoolSize = sim.pool().size
            bump { it.copy(plans = plans, selected = 0, computing = false) }
        }
    }

    fun select(i: Int) = bump { it.copy(selected = i) }

    fun accept() {
        val p = _ui.value.plans.getOrNull(_ui.value.selected) ?: return
        log("a:${sim.now}:${p.seq.joinToString(",")}")
        sim.accept(p)
        bump { it.copy(plans = emptyList(), selected = 0, running = true) }
        startTicker()
    }

    fun goHome() {
        log("h:${sim.now}:")
        sim.goHome()
        bump { it.copy(plans = emptyList(), running = true) }
        startTicker()
    }

    fun setSpeed(x: Int) = bump { it.copy(speed = x) }

    fun toggleRun() {
        val r = !_ui.value.running
        bump { it.copy(running = r) }
        if (r) startTicker()
    }

    /** 지금 하는 일이 끝날 때까지(또는 출근 시각까지) 시계를 건너뛴다 */
    fun skipToNext() {
        val target = when {
            !sim.onShift -> sim.shiftStart
            !sim.idle -> sim.segs.last().end
            else -> sim.now + 15
        }
        stepTo(target)
    }

    fun restart() {
        ticker?.cancel()
        sim = DaySim(sim.bundle)
        prefs.edit().clear().putString("day", sim.bundle.meta.day).apply()
        seenAlerts = 0
        _ui.value = UiState(loading = false, now = sim.now)
        replan()
    }

    /** 시계를 분 단위로 앞당김(시연·테스트용) */
    fun advance(min: Double) = stepTo(sim.now + min)

    private fun stepTo(t: Double) {
        val becameIdle = sim.advanceTo(t)
        saveNow()
        if (sim.alerts.any { it.kind == AlertKind.TAKEN_BY_OTHER && it.t > lastPlanAt }) lastPoolSize = -1
        val needPlan = sim.idle && sim.onShift && (becameIdle || sim.pool().size != lastPoolSize || sim.now - lastPlanAt >= 15)
        if (becameIdle) bump { it.copy(running = false) }   // 일이 끝나면 멈추고 새 추천을 보여 준다
        else bump()
        if (needPlan) replan()
        if (sim.now >= sim.shiftEnd + 180) bump { it.copy(running = false) }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive && _ui.value.running) {
                delay(250)
                val dMin = _ui.value.speed * 0.25 / 60.0
                stepTo(sim.now + dMin)
            }
        }
    }
}
