package kr.bighero.h2.driver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.bighero.h2.driver.ui.AlertsScreen
import kr.bighero.h2.driver.ui.C
import kr.bighero.h2.driver.ui.H2Screen
import kr.bighero.h2.driver.ui.H2Theme
import kr.bighero.h2.driver.ui.ScheduleScreen
import kr.bighero.h2.driver.ui.TodayScreen
import kr.bighero.h2.driver.ui.TopBar

class MainActivity : ComponentActivity() {
    private val vm: DriverViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { H2Theme { App(vm) } }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("오늘", Icons.Filled.Home), Tab("전체 일정", Icons.Filled.DateRange),
    Tab("알림", Icons.Filled.Notifications), Tab("충전", Icons.Filled.Star),
)

@Composable
fun App(vm: DriverViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    CompositionLocalProvider(LocalContentColor provides C.text) { Box(Modifier.fillMaxSize().background(C.bg).safeDrawingPadding()) {
        when {
            ui.loading -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(); Spacer(Modifier.width(12.dp)); Text("엑시언트 기사용 배차·충전 도우미 · 오늘 배차 자료를 읽는 중", color = C.sub)
            }
            ui.error != null -> Text(ui.error!!, color = C.bad, modifier = Modifier.padding(24.dp))
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
            // 차량 화면(플레오스 Connect 2560×1440, 160dpi)은 dp가 너무 넓어 글자가 작게 보인다 → 가로 1280dp 기준으로 전체를 키움
            val d = LocalDensity.current
            val scale = if (maxWidth > 1600.dp) (maxWidth / 1280.dp).coerceAtMost(2.2f) else 1f
            CompositionLocalProvider(LocalDensity provides Density(d.density * scale, d.fontScale)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 840.dp
                val onTab = { i: Int -> tab = i; if (i == 2) vm.markAlertsRead() }
                val body: @Composable (Modifier) -> Unit = { m ->
                    Column(m) {
                        TopBar(vm, ui, wide)
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (tab) {
                                0 -> TodayScreen(vm, ui, wide)
                                1 -> ScheduleScreen(vm, ui)
                                2 -> AlertsScreen(vm, ui)
                                else -> H2Screen(vm, ui)
                            }
                        }
                    }
                }
                if (wide) Row(Modifier.fillMaxSize()) {
                    NavigationRail(containerColor = C.panel, modifier = Modifier.padding(end = 0.dp)) {
                        Spacer(Modifier.padding(top = 8.dp))
                        tabs.forEachIndexed { i, t ->
                            NavigationRailItem(selected = tab == i, onClick = { onTab(i) },
                                icon = { TabIcon(t, i == 2 && ui.newAlerts > 0, ui.newAlerts) },
                                label = { Text(t.label, style = MaterialTheme.typography.labelMedium) })
                        }
                    }
                    body(Modifier.weight(1f).fillMaxSize())
                } else Column(Modifier.fillMaxSize()) {
                    body(Modifier.weight(1f).fillMaxWidth())
                    NavigationBar(containerColor = C.panel) {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(selected = tab == i, onClick = { onTab(i) },
                                icon = { TabIcon(t, i == 2 && ui.newAlerts > 0, ui.newAlerts) },
                                label = { Text(t.label, style = MaterialTheme.typography.labelMedium) })
                        }
                    }
                }
            }
            } }
        }
    } }
}

@Composable
private fun TabIcon(t: Tab, badge: Boolean, n: Int) {
    if (badge) BadgedBox(badge = { Badge { Text("$n") } }) { Icon(t.icon, t.label) } else Icon(t.icon, t.label)
}
