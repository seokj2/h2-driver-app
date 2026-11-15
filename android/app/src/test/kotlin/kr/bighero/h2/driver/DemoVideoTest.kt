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
        // 운전 중 장면: 영상에서는 플레오스 순정 내비 화면 위에 우리 팝업을 얹어 보여 준다(make_video.py)
    }
    private val drives = StringBuilder()
    private fun tab(name: String) { rule.onAllNodesWithText(name)[0].performClick(); settle() }
    private fun ui(f: DriverViewModel.() -> Unit) = rule.runOnUiThread { vm().f() }

    private fun click(t: String) { rule.onAllNodesWithText(t)[0].performClick(); settle(); rule.mainClock.advanceTimeBy(200); settle() }
    private var showedStop = false
    private var chargeShots = 0

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
                shot(4.0, "터미널 도착 · 내 컨테이너 위치와 경로")
                click("자세히"); shot(3.0, "컨테이너 정보")
                click("접기"); click("도착했어요")
                click("문제 있음"); shot(3.0, "문제 있음 · 바로 신고"); rule.onAllNodesWithText("닫기").let { it[it.fetchSemanticsNodes().size - 1].performClick() }; settle()
                shot(2.5, "도착 알림 · 배차 담당자에게 전달")
                click("상·하차 끝났어요"); shot(3.5, "상·하차 끝 · 사진과 전자 서명"); click("보내기")
            }
            if (chargeShots == 0 && seg != null && seg.kind == SegKind.WAIT && sim.bundle.chargers.any { it.id == seg.from }) {
                chargeShots = -1; shot(3.5, "충전소 도착 · 내 차례와 다음 일")
            }
            if (seg != null && seg.kind == SegKind.CHARGE) {
                val f = (sim.now - seg.start) / (seg.end - seg.start)
                if (chargeShots <= 0) { chargeShots = 1; shot(3.5, "충전 시작") }
                else if (chargeShots == 1 && f > 0.4) { chargeShots = 2; shot(3.0, "충전 중 · 남은 시간") }
            }
            if (chargeShots == 2 && seg?.kind != SegKind.CHARGE) { chargeShots = 3; shot(2.5, "충전 끝 · 다음 일로") }
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
        shot(4.0, "출근 전 · 수소 50%")
        ui { skipToNext() }
        shot(4.0, "추천 일정 1~3안")
        shot(3.5, "1안 · 수소가 부족하면 충전을 넣어 줘요")
        ui { select(1) }; shot(3.5, "2안 ${vm().ui.value.picks.getOrNull(1)?.tag ?: ""}")
        ui { select(2) }; shot(3.5, "3안 ${vm().ui.value.picks.getOrNull(2)?.tag ?: ""}")
        click("이 일정 말고"); shot(3.0, "거절 · 이유에 맞춰 다시 추천")
        click("몸이 안 좋아요"); shot(3.5, "몸이 안 좋으면 여유 있는 일정")
        ui { select(0) }; shot(1.5, "1안으로")
        var jobs = 0
        while (jobs < 3 && vm().ui.value.plans.isNotEmpty() && vm().sim.now < vm().sim.shiftEnd) {
            ui { accept(); if (ui.value.running) toggleRun() }
            shot(2.5, if (jobs == 0) "이 일정 받기" else "다음 일정 받기")
            if (jobs == 0) {
                ui { advance(40.0) }
                shot(3.0, "운전 중 · 속도·제한속도·안전 안내")
                tab("전체 일정"); shot(3.0, "전체 일정")
                tab("오늘")
            }
            drive(4.0, vm().sim.segs.last().end + 1)
            jobs++
            if (vm().ui.value.plans.isNotEmpty()) shot(4.0, "일이 끝나면 다시 추천")
        }
        if (vm().sim.idle && vm().sim.now < vm().sim.shiftEnd + 60) {
            ui { goHome(); if (ui.value.running) toggleRun() }
            drive(4.0, vm().sim.segs.last().end + 1)
        }
        tab("알림"); shot(3.5, "알림 · 접안 지연·배차 변경")
        tab("충전"); shot(3.5, "충전소 · 대기 대수와 예약")
        tab("오늘"); shot(4.0, "쉴 때만 · 오늘 건수와 퇴근")
        // 사고: 운전 중 차량 고장·사고 → 남은 일은 다른 트럭으로, 사고 처리 뒤 다시 추천
        ui { restart() }
        settle()
        ui { skipToNext() }
        settle()
        ui { accept(); if (ui.value.running) toggleRun() }
        settle()
        val dseg = vm().sim.segs.firstOrNull { it.kind.name.startsWith("DRIVE") && it.from != it.to && it.end - it.start > 20 }
        if (dseg != null) {
            ui { advance(dseg.start + (dseg.end - dseg.start) * 0.4 - sim.now) }
            settle()
            shot(2.5, "다른 날 · 운전 중 사고")
            click("문제 있음"); shot(2.5, "문제 있음 · 사유 선택")
            click("차량 고장·사고"); settle(); shot(3.5, "사고 신고 · 남은 일은 다른 트럭으로")
            ui { advance(sim.segs.last().end + 1 - sim.now) }
            settle()
            shot(4.0, "사고 처리 뒤 · 새 일정 추천")
        }
        File(dir, "list.txt").writeText(list.toString())
        File(dir, "drive.txt").writeText(drives.toString())
    }
}
