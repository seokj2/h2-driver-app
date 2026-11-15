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
    val picks: List<Pick> = emptyList(),   // plans와 같은 순서: 각 안의 성격·기사용 설명
    val selected: Int = 0,
    val computing: Boolean = false,
    val newAlerts: Int = 0,
    val banner: Alert? = null,
    val error: String? = null,
    val listening: Boolean = false,
)

class DriverViewModel(private val app: Application) : AndroidViewModel(app) {
    lateinit var sim: DaySim; private set
    lateinit var vehicle: VehicleData; private set
    val navi: NaviLauncher = IntentNavi(app)
    /** 실제 도로 경로(남은 거리 표시용) */
    val roads: kr.bighero.h2.driver.ui.Roads? by lazy {
        try { kr.bighero.h2.driver.ui.Roads.parse(app.assets.open("routes.json").bufferedReader(Charsets.UTF_8).readText()) } catch (_: Exception) { null }
    }

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
    var lastPlanAt = -1e9; private set
    /** 배차 서버와 마지막으로 맞춘 시각(시연: 시계가 움직일 때마다 받아 옴) */
    var lastSyncAt = -1e9; private set
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
                when (f[0]) {
                    "a" -> sim.accept((sim.recommend() + sim.lighter()).firstOrNull { it.seq.joinToString(",") == f[2] } ?: break)
                    "x" -> excluded += f[2]
                    else -> sim.goHome()
                }
            }
            sim.advanceTo(prefs.getFloat("now", sim.now.toFloat()).toDouble())
        } catch (_: Exception) { sim = DaySim(sim.bundle); prefs.edit().clear().putString("day", sim.bundle.meta.day).apply() }
    }

    // ---- 순정 내비로 넘기기: 운전이 시작되면 순정 내비, 그동안은 팝업, 도착하면 우리 앱 ----
    val popups = kr.bighero.h2.driver.vehicle.Popups(app, MainActivity::class.java)
    /** 지금 길안내를 맡긴 운전 구간(시작 시각). 바뀔 때만 넘기고 부른다 */
    private var handedOff: Double? = null
    private fun fast() = _ui.value.running && _ui.value.speed > 60
    private fun handoff() {
        val d = kr.bighero.h2.driver.ui.drivingSeg(sim, sim.now)
        val key = d?.start
        if (key == handedOff) return
        val was = handedOff
        handedOff = key
        if (fast()) return   // 빨리 돌리는 시연 중에는 앱을 오가지 않음
        if (d != null) {
            navigate(d.to)
            val after = sim.upcoming().firstOrNull { it.start >= d.end - 0.01 }
            popups.show("${sim.world.name(d.to)} · ${kr.bighero.h2.core.clock(d.end)} 도착",
                "도착하면 · " + (if (after == null) "오늘 일 끝" else if (after.kind.name.startsWith("DRIVE")) "${sim.world.name(after.to)}(으)로 출발" else after.note.ifBlank { after.kind.label }) + "\n도착하면 앱이 다시 열려요")
        } else if (was != null) {
            val s = sim.currentSeg()
            popups.bringToFront("도착 · ${s?.let { sim.world.name(it.to) } ?: ""}", s?.note?.ifBlank { s.kind.label } ?: "다음 할 일을 확인하세요")
        }
    }

    private fun bump(f: (UiState) -> UiState = { it }) {
        val s = f(_ui.value)
        val nAlerts = sim.alerts.size
        // 순정 내비 자동 넘기기는 끔(에뮬레이터 순정 내비가 외부 목적지를 받지 않음). handoff()는 플레오스 NaviHelper 연동 때 다시 켬
        // 운전 중(순정 내비 화면)에는 새 알림을 팝업으로
        if (handedOff != null && nAlerts > spokenAlerts)
            sim.alerts.drop(spokenAlerts).lastOrNull { it.kind != AlertKind.INFO && it.kind != AlertKind.SENT }?.let { popups.show(it.title, it.body) }
        val banner = if (nAlerts > seenAlerts && sim.alerts.last().kind != AlertKind.INFO && sim.alerts.last().kind != AlertKind.SENT) sim.alerts.last() else s.banner
        if (nAlerts > spokenAlerts) {
            sim.alerts.drop(spokenAlerts).lastOrNull { it.kind != AlertKind.INFO && it.kind != AlertKind.SENT }?.let { speak(it.title) }
            spokenAlerts = nAlerts
        }
        _ui.value = s.copy(tick = s.tick + 1, now = sim.now, newAlerts = nAlerts - seenAlerts, banner = banner)
    }

    // ---- 기사 입장 기능: 음성 안내, 진행 알리기·문제 신고, 추천 거절, 증빙 ----
    private var spokenAlerts = 0
    private var lastSpokenPlan: List<String>? = null
    var voiceOn = true; private set
    private var ttsReady = false
    private val tts: android.speech.tts.TextToSpeech? = try {
        android.speech.tts.TextToSpeech(app) { st ->
            ttsReady = st == android.speech.tts.TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = java.util.Locale.KOREAN
        }
    } catch (_: Throwable) { null }
    /** 음성 안내(플레오스 TTS SDK로 바꿀 자리). 빨리 돌리는 시연(×300 이상) 중에는 말하지 않음 */
    fun speak(text: String) {
        if (!voiceOn || !ttsReady || _ui.value.running && _ui.value.speed > 60) return
        try { tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "h2") } catch (_: Throwable) {}
    }
    fun toggleVoice() { voiceOn = !voiceOn; if (!voiceOn) try { tts?.stop() } catch (_: Throwable) {}; bump() }
    override fun onCleared() { try { tts?.shutdown(); rec?.destroy() } catch (_: Throwable) {}; super.onCleared() }

    /** 기사가 못 한다고 한 일감(다시 추천하지 않음) */
    val excluded = HashSet<String>()
    /** 기사가 알린 진행(구간 시작 시각 + 무엇) */
    val done = HashSet<String>()

    /** 배차 담당자에게 보내기. 시연에서는 알림 목록에 "보냄"으로 남기고, 실제로는 배차 서버로 보낸다 */
    fun report(title: String, body: String) {
        sim.alerts.add(Alert(sim.now, AlertKind.SENT, title, body))
        bump()
    }
    fun confirm(key: String, what: String, place: String) {
        done += key
        report("$what 알림 보냄", "$place · ${kr.bighero.h2.core.clock(sim.now)}")
    }
    /** 추천 거절: 사유에 따라 다른 안을 보여 주거나 그 일감을 빼고 다시 계산 */
    fun reject(reason: String) {
        val p = _ui.value.plans.getOrNull(_ui.value.selected)
        when (reason) {
            "몸이 안 좋아요" -> {
                val i = _ui.value.picks.indexOfFirst { it.tag == "여유 있는 일정" }
                report("추천 거절 · $reason", if (i >= 0) "여유 있는 일정을 골라 보여 드려요" else "더 가벼운 일정이 없어요. 배차 담당자가 연락드릴 거예요")
                if (i >= 0) select(i)
            }
            "차량 점검이 필요해요" -> { report("추천 거절 · $reason", "공장으로 돌아가요. 배차 담당자에게 알렸어요"); goHome() }
            else -> {
                val j = p?.jobs?.firstOrNull() ?: return
                excluded += j; log("x:${sim.now}:$j")
                report("추천 거절 · $reason", "${sim.label(j)}을(를) 빼고 다시 계산해요")
                replan()
            }
        }
    }

    // ---- 음성 명령(짧은 말만): 받기 / 다시 / 도착 / 끝났어요 / 문제 ----
    private var rec: android.speech.SpeechRecognizer? = null
    fun listen() {
        val r = try { rec ?: android.speech.SpeechRecognizer.createSpeechRecognizer(app).also { rec = it } } catch (_: Throwable) { return }
        r.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(p: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: android.os.Bundle?) {}
            override fun onEvent(t: Int, b: android.os.Bundle?) {}
            override fun onError(e: Int) { bump { it.copy(listening = false) }; speak("잘 못 들었어요") }
            override fun onResults(b: android.os.Bundle?) {
                bump { it.copy(listening = false) }
                b?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(::voiceCommand)
            }
        })
        r.startListening(android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ko-KR"))
        bump { it.copy(listening = true) }
    }
    fun voiceCommand(t: String) {
        val s = sim.currentSeg()
        val stop = s != null && !s.kind.name.startsWith("DRIVE")
        val key = s?.start?.toInt()?.toString()
        when {
            "받" in t && sim.idle && _ui.value.plans.isNotEmpty() -> accept()
            "다시" in t && sim.idle -> replan()
            "도착" in t && stop -> confirm("$key:a", "도착", sim.world.name(s!!.from))
            ("끝" in t || "완료" in t) && stop -> confirm("$key:d", "완료", sim.world.name(s!!.from))
            "문제" in t -> report("문제 신고 · 음성", "\"$t\"")
            else -> speak("받기, 도착, 끝났어요, 문제 있어 중에 하나로 말해 주세요")
        }
    }

    fun markAlertsRead() { seenAlerts = sim.alerts.size; bump { it.copy(banner = null) } }
    fun dismissBanner() = bump { it.copy(banner = null) }

    fun replan() {
        if (!::sim.isInitialized) return
        _ui.value = _ui.value.copy(computing = true)
        viewModelScope.launch {
            val picks = withContext(Dispatchers.Default) { pickPlans(sim, sim.recommend().filter { p -> p.jobs.none { it in excluded } }, sim.lighter().filter { p -> p.jobs.none { it in excluded } }) }
            val plans = picks.map { it.plan }
            lastPlanAt = sim.now; lastPoolSize = sim.pool().size
            bump { it.copy(plans = plans, picks = picks, selected = 0, computing = false) }
            if (sim.onShift && picks.isNotEmpty() && picks.first().plan.seq != lastSpokenPlan) {
                lastSpokenPlan = picks.first().plan.seq
                speak("새 추천 일정이에요. ${picks.first().note.substringBefore(" · ")}")
            }
        }
    }

    fun select(i: Int) = bump { it.copy(selected = i) }

    fun accept() {
        val p = _ui.value.plans.getOrNull(_ui.value.selected) ?: return
        log("a:${sim.now}:${p.seq.joinToString(",")}")
        sim.accept(p)
        sim.upcoming().firstOrNull { it.kind.name.startsWith("DRIVE") }?.let { speak("일정을 받았어요. ${sim.world.name(it.to)}(으)로 출발하세요. ${kr.bighero.h2.core.clock(it.end)} 도착 예정이에요.") }
        bump { it.copy(plans = emptyList(), picks = emptyList(), selected = 0, running = true) }
        startTicker()
    }

    fun goHome() {
        log("h:${sim.now}:")
        sim.goHome()
        bump { it.copy(plans = emptyList(), picks = emptyList(), running = true) }
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

    /** 처음부터. h2Kg를 주면 출근 때 수소 잔량을 바꾼 시연(예: 충전이 필요한 날) */
    fun restart(h2Kg: Double? = null) {
        ticker?.cancel()
        sim = DaySim(if (h2Kg == null) sim.bundle else sim.bundle.copy(driver = sim.bundle.driver.copy(h2_kg = h2Kg)))
        prefs.edit().clear().putString("day", sim.bundle.meta.day).apply()
        excluded.clear(); done.clear(); lastSyncAt = sim.now; spokenAlerts = 0; handedOff = null
        seenAlerts = 0
        _ui.value = UiState(loading = false, now = sim.now)
        replan()
    }

    /** 시계를 분 단위로 앞당김(시연·테스트용) */
    fun advance(min: Double) = stepTo(sim.now + min)

    private fun stepTo(t: Double) {
        val becameIdle = sim.advanceTo(t)
        lastSyncAt = sim.now
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
