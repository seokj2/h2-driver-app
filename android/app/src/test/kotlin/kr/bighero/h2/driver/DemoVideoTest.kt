package kr.bighero.h2.driver

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import kr.bighero.h2.core.AlertKind
import kr.bighero.h2.core.clock
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 시연 영상용 장면 그리기: 하루를 처음부터 끝까지 진행하며 화면을 차례로 build/video/에 PNG로 남기고,
 * 장면마다 보여 줄 시간·자막을 build/video/list.txt에 적는다. 영상은 scripts/make_video.py가 이어 붙인다.
 * 평소 테스트에서는 건너뛰고, DEMO_VIDEO=1 일 때만 돈다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DemoVideoTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun vm() = rule.activity.let { a -> androidx.lifecycle.ViewModelProvider(a)[DriverViewModel::class.java] }
    private val dir = File("build/video").also { it.deleteRecursively(); it.mkdirs() }
    private val list = StringBuilder()
    private var n = 0

    private fun settle() { rule.waitForIdle(); rule.waitUntil(20_000) { !vm().ui.value.computing && !vm().ui.value.loading }; rule.waitForIdle() }
    private fun shot(sec: Double, caption: String) {
        settle()
        val f = "%04d.png".format(++n)
        rule.onRoot().captureRoboImage(File(dir, f).path)
        list.append("$f|$sec|$caption\n")
    }
    private fun tab(name: String) { rule.onAllNodesWithText(name)[0].performClick(); settle() }
    private fun ui(f: DriverViewModel.() -> Unit) = rule.runOnUiThread { vm().f() }

    /** 일이 끝날 때까지 몇 분씩 앞당기며 찍기 */
    private fun drive(stepMin: Double, until: Double) {
        var seen = vm().sim.alerts.size
        while (vm().sim.now < until) {
            ui { advance(stepMin) }
            settle()
            val sim = vm().sim
            if (sim.alerts.size > seen) {
                val a = sim.alerts.last(); seen = sim.alerts.size
                if (a.kind != AlertKind.INFO) { shot(2.6, "알림 · ${a.title}"); ui { dismissBanner() } }
            }
            val seg = sim.currentSeg()
            val cap = if (seg == null) "다음 일 기다리는 중" else "${clock(sim.now)} · ${seg.kind.label} · ${sim.label(seg.to)}"
            shot(0.16, cap)
            if (sim.idle) break
        }
    }

    @Test @Config(qualifiers = "w1280dp-h720dp-land-hdpi")
    fun video() {
        Assume.assumeTrue(System.getenv("DEMO_VIDEO") == "1")
        rule.waitUntil(20_000) { !vm().ui.value.loading }
        ui { restart() }
        settle()
        shot(4.0, "출근 10분 전 · 오늘 첫 일정을 미리 보여 줘요")
        ui { skipToNext() }
        shot(4.0, "출근 · 앱이 직접 계산한 추천 일정 1~3안")
        ui { select(1) }; shot(1.5, "2안 비교")
        ui { select(2) }; shot(1.5, "3안 비교")
        ui { select(0) }; shot(1.5, "1안: 수입 1건 + 수출 1건")
        rule.onAllNodesWithText("왜 이 일정?")[0].performScrollTo()
        shot(4.5, "왜 이 일정? · 용차 대신 직접 할 때 아끼는 돈을 그대로 계산")
        var jobs = 0
        while (jobs < 3 && vm().ui.value.plans.isNotEmpty() && vm().sim.now < vm().sim.shiftEnd) {
            ui { accept(); if (ui.value.running) toggleRun() }
            shot(2.5, if (jobs == 0) "이 일정 받기 · 첫 일만 확정하고 출발" else "다음 일정 받기")
            if (jobs == 0) {
                ui { advance(40.0) }
                shot(3.0, "운전 중 · 순정 내비로 길안내 버튼, 다음 할 일")
                tab("전체 일정"); shot(3.0, "전체 일정 · 지난·지금·앞으로 할 구간")
                tab("오늘")
            }
            drive(4.0, vm().sim.segs.last().end + 1)
            jobs++
            if (vm().ui.value.plans.isNotEmpty()) shot(4.0, "일이 끝나면 그때 상황으로 다시 추천")
        }
        if (vm().sim.idle && vm().sim.now < vm().sim.shiftEnd + 60) {
            ui { goHome(); if (ui.value.running) toggleRun() }
            drive(4.0, vm().sim.segs.last().end + 1)
        }
        tab("알림"); shot(3.5, "알림 · 접안 지연·접안 확인·다른 트럭이 가져간 일감")
        tab("수소"); shot(3.5, "수소 · 잔량, 오늘 충전, 충전소 운영시간·단가")
        tab("오늘"); shot(4.0, "하루 끝 · 오늘 한 일과 아낀 용차비")
        File(dir, "list.txt").writeText(list.toString())
    }
}
