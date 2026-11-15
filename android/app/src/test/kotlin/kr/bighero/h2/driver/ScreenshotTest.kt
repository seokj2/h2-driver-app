package kr.bighero.h2.driver

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 에뮬레이터 없이 화면을 그려 build/screens/에 PNG로 남긴다(차량 가로 화면, 휴대폰 세로 화면) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun vm() = rule.activity.let { a -> androidx.lifecycle.ViewModelProvider(a)[DriverViewModel::class.java] }

    private fun waitReady() {
        rule.waitUntil(20_000) { val u = vm().ui.value; !u.loading && !u.computing && u.plans.isNotEmpty() }
    }

    private fun shoot(name: String) { rule.waitForIdle(); rule.onRoot().captureRoboImage("build/screens/$name.png") }

    private fun flow(prefix: String) {
        rule.waitUntil(20_000) { !vm().ui.value.loading }
        rule.runOnUiThread { vm().restart() }   // 앞 테스트가 저장한 상태 지우고 처음부터
        waitReady(); shoot("${prefix}_1_before_shift")
        rule.runOnUiThread { vm().skipToNext() }
        waitReady(); shoot("${prefix}_2_recommend")
        rule.runOnUiThread { vm().accept(); vm().toggleRun(); vm().advance(110.0) }
        rule.waitForIdle(); shoot("${prefix}_3_working")
        rule.runOnUiThread { vm().skipToNext() }
        waitReady(); shoot("${prefix}_4_next")
    }

    @Test @Config(qualifiers = "w1408dp-h792dp-land-mdpi") fun car() = flow("car")
    @Test @Config(qualifiers = "w2560dp-h1440dp-land-mdpi") fun pleos() = flow("pleos")
    @Test @Config(qualifiers = "w411dp-h891dp-port-xxhdpi") fun phone() = flow("phone")
}
