package kr.bighero.h2.driver

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kr.bighero.h2.core.SegKind
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
        @OptIn(ExperimentalRoborazziApi::class) captureScreenRoboImage(File(dir, f).path)
        list.append("$f|$sec|$caption\n")
    }
    private fun tab(name: String) { rule.onAllNodesWithText(name)[0].performClick(); settle() }
    private fun ui(f: DriverViewModel.() -> Unit) = rule.runOnUiThread { vm().f() }

    private fun click(t: String) { rule.onAllNodesWithText(t)[0].performClick(); settle(); rule.mainClock.advanceTimeBy(200); settle() }
    private var showedStop = false

    /** 일이 끝날 때까지 몇 분씩 앞당기며 찍기 */
    private fun drive(stepMin: Double, until: Double) {
        var seen = vm().sim.alerts.size
        while (vm().sim.now < until) {
            ui { advance(stepMin) }
            settle()
            val sim = vm().sim
            if (sim.alerts.size > seen) {
                val a = sim.alerts.last(); seen = sim.alerts.size
                if (a.kind != AlertKind.INFO && a.kind != AlertKind.SENT) { shot(2.6, "알림 · ${a.title}"); ui { dismissBanner() } }
            }
            val seg = sim.currentSeg()
            if (!showedStop && seg != null && seg.kind == SegKind.TERMINAL && seg.job != null) {
                showedStop = true
                shot(4.0, "터미널 도착 · 지도 대신 컨테이너가 있는 블록·베이·열·단과 가는 길 (시연용 예시)")
                click("자세히"); shot(3.0, "컨테이너 정보 · 번호·크기·무게·게이트·예약 시간 (시연용 예시)")
                click("접기"); click("도착했어요")
                click("문제 있음"); shot(3.0, "문제 있음 · 사유를 누르거나 배차 담당자에게 전화"); rule.onAllNodesWithText("닫기").let { it[it.fetchSemanticsNodes().size - 1].performClick() }; settle()
                shot(2.5, "도착을 알리면 배차 담당자에게 바로 전해지고 다음 버튼으로 바뀌어요")
                click("상·하차 끝났어요"); shot(3.5, "상·하차 끝 · 컨테이너 사진과 전자 인수증 서명"); click("보내기")
            }
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
        shot(3.5, "1안 알찬 일정 · 기사 입장의 한 줄 설명과 반입 마감까지 남은 여유")
        ui { select(1) }; shot(3.5, "2안 ${vm().ui.value.picks.getOrNull(1)?.tag ?: ""} · 기사 입장의 한 줄 설명")
        ui { select(2) }; shot(3.5, "3안 ${vm().ui.value.picks.getOrNull(2)?.tag ?: ""} · 컨디션·마감 걱정에 맞춰 골라요")
        click("이 일정 말고"); shot(3.0, "이 일정 말고 · 이유를 고르면 거기에 맞춰 다시 골라 줘요")
        click("몸이 안 좋아요"); shot(3.5, "몸이 안 좋다고 하면 일찍 끝나는 여유 있는 일정으로")
        ui { select(0) }; shot(1.5, "1안으로")
        var jobs = 0
        while (jobs < 3 && vm().ui.value.plans.isNotEmpty() && vm().sim.now < vm().sim.shiftEnd) {
            ui { accept(); if (ui.value.running) toggleRun() }
            shot(2.5, if (jobs == 0) "이 일정 받기 · 첫 일만 확정하고 출발" else "다음 일정 받기")
            if (jobs == 0) {
                ui { advance(40.0) }
                shot(3.0, "운전 중 · 남은 거리와 도착 시각만 크게, 음성으로도 안내")
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
        tab("알림"); shot(3.5, "알림 · 접안 지연·다른 트럭이 가져간 일감·내가 보낸 알림")
        tab("충전"); shot(3.5, "충전 · 남은 연료, 충전소 운영 여부, 대기 대수와 예약 요청(예시)")
        tab("오늘"); shot(4.0, "하루 끝 · 오늘 한 일과 아낀 용차비")
        File(dir, "list.txt").writeText(list.toString())
    }
}
