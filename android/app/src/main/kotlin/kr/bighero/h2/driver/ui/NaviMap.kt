package kr.bighero.h2.driver.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kr.bighero.h2.core.DaySim
import kr.bighero.h2.core.SegKind
import kr.bighero.h2.core.clock
import kr.bighero.h2.driver.UiState
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory as PF
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** 키 없이 쓰는 오픈 지도 스타일(OpenFreeMap, © OpenStreetMap). 낮은 회색·흰색 위주(positron)로 경로·트럭이 눈에 띄게, 밤은 dark */
private const val STYLE_DAY = "https://tiles.openfreemap.org/styles/positron"
private const val STYLE_NIGHT = "https://tiles.openfreemap.org/styles/dark"

private val isRobolectric = Build.FINGERPRINT == "robolectric"

private fun ll(p: DoubleArray) = Point.fromLngLat(p[1], p[0])
private fun line(pts: List<DoubleArray>) = Feature.fromGeometry(LineString.fromLngLats(pts.map(::ll)))
private fun hex(c: Color) = String.format("#%06X", 0xFFFFFF and c.toArgb())

/**
 * 내비 형식 지도: 실제 도로 지도 위에 오늘 다닌 길(회색)·지금 가는 길(초록)·확정된 앞길(점선)·추천안 미리보기(주황 점선),
 * 거점, 트럭. 운전 중이면 트럭을 따라가며 진행 방향을 위로, 아니면 전체를 보여 준다.
 * 위쪽 안내 카드: 다음 갈림길 안내, 남은 거리, 도착 시각.
 * 화면 확인 테스트(Robolectric)에서는 지도 엔진을 못 띄우므로 예전 개략도로 그린다.
 */
@Composable
fun NaviMap(sim: DaySim, ui: UiState, modifier: Modifier) {
    if (isRobolectric) { MapPane(sim, ui, modifier); return }
    val ctx = LocalContext.current
    val roads = remember { Roads.parse(ctx.assets.open("routes.json").bufferedReader(Charsets.UTF_8).readText()) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var follow by remember { mutableStateOf(true) }
    val dark = C.dark
    val view = remember {
        MapLibre.getInstance(ctx.applicationContext)
        MapView(ctx).apply {
            onCreate(null)
            getMapAsync { m ->
                m.uiSettings.isCompassEnabled = false
                m.uiSettings.isRotateGesturesEnabled = true
                m.uiSettings.isTiltGesturesEnabled = false
                m.addOnCameraMoveStartedListener { reason -> if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) follow = false }
                map = m
            }
        }
    }
    val life = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(life) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> view.onStart(); Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause(); Lifecycle.Event.ON_STOP -> view.onStop()
                else -> {}
            }
        }
        life.addObserver(obs)
        if (life.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStart()
        if (life.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
        onDispose { life.removeObserver(obs); view.onPause(); view.onStop(); view.onDestroy() }
    }
    // 스타일(낮/밤) 올리고 우리 층 만들기
    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        style = null
        m.setStyle(Style.Builder().fromUri(if (dark) STYLE_NIGHT else STYLE_DAY)) { s -> addLayers(s); style = s }
    }

    // 지금 상태 계산
    val (a, b, f) = sim.position(ui.now)
    val cur = sim.currentSeg(ui.now)
    val driving = cur != null && cur.kind.name.startsWith("DRIVE") && a != b
    val road = if (driving) roads.get(a, b) else null
    val node = sim.world.node
    val truckPt: DoubleArray; val heading: Double
    if (road != null) { val (p, h) = road.at(f); truckPt = p; heading = h }
    else { val n = node[a]!!; truckPt = doubleArrayOf(n.lat, n.lon); heading = 0.0 }

    LaunchedEffect(style, ui.tick, ui.selected, ui.plans, follow) {
        val s = style ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val past = ArrayList<Feature>(); val now = ArrayList<Feature>(); val ahead = ArrayList<Feature>()
        for (seg in sim.segs) {
            if (seg.from == seg.to || !seg.kind.name.startsWith("DRIVE")) continue
            val r = roads.get(seg.from, seg.to) ?: continue
            when {
                seg.end <= ui.now -> past.add(line(r.pts))
                seg.start <= ui.now -> { val (p0, p1) = r.split((ui.now - seg.start) / (seg.end - seg.start)); past.add(line(p0)); now.add(line(p1)) }
                else -> ahead.add(line(r.pts))
            }
        }
        val plan = ArrayList<Feature>()
        if (!driving && sim.idle) ui.plans.getOrNull(ui.selected)?.let { p ->
            var at = a
            for (st in p.steps) { roads.get(at, st.endLoc)?.let { plan.add(line(it.pts)) }; at = st.endLoc }
        }
        (s.getSource("r-past") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(past))
        (s.getSource("r-now") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(now))
        (s.getSource("r-ahead") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(ahead))
        (s.getSource("r-plan") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(plan))
        val nodes = sim.bundle.nodes.map { n ->
            Feature.fromGeometry(Point.fromLngLat(n.lon, n.lat)).also {
                it.addStringProperty("name", n.name)
                it.addStringProperty("col", hex(when { n.id.startsWith("CHG") -> C.charge; n.id.startsWith("DEP") -> C.box; n.id.startsWith("PORT") -> C.export; else -> C.sub }))
            }
        }
        (s.getSource("nodes") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(nodes))
        (s.getSource("truck") as? GeoJsonSource)?.setGeoJson(Feature.fromGeometry(ll(truckPt)))
        if (follow && driving) {
            m.easeCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(LatLng(truckPt[0], truckPt[1])).zoom(14.0).tilt(50.0).bearing(heading)
                    .padding(0.0, 260.0, 0.0, 0.0).build()), 300)
        } else if (follow) {
            val bb = LatLngBounds.Builder()
            sim.bundle.nodes.forEach { bb.include(LatLng(it.lat, it.lon)) }
            m.easeCamera(CameraUpdateFactory.newCameraPosition(
                m.getCameraForLatLngBounds(bb.build(), intArrayOf(120, 200, 120, 80))?.let { cp -> CameraPosition.Builder(cp).tilt(0.0).bearing(0.0).build() }
                    ?: m.cameraPosition), 600)
        }
    }

    Box(modifier.clip(RoundedCornerShape(14.dp))) {
        AndroidView({ view }, Modifier.fillMaxSize())
        // 위쪽 안내 카드
        Column(Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(14.dp)).background(C.brandDark.copy(alpha = 0.92f)).padding(14.dp)) {
            if (road != null && cur != null) {
                val (st, m) = road.next(f)
                Text(if (m >= 1000) "%.1fkm".format(m / 1000) else "${(m / 10).toInt() * 10}m", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                Text(st?.text ?: "${sim.label(b)} 도착", color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("${sim.label(b)}까지 %.1fkm · 도착 ${clock(cur.end)}".format((1 - f) * road.length / 1000), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(if (cur != null) "${cur.kind.label} 중 · ${sim.label(a)}" else "${sim.label(a)}에 있어요", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(if (sim.idle) "추천 일정의 길을 주황 점선으로 보여 줘요" else "끝나는 시각 ${clock(cur?.end ?: ui.now)}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        // 따라가기 / 전체 보기
        Text(if (follow) "내 위치" else "⌖ 내 위치로", color = if (follow) C.sub else Color.White, style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(RoundedCornerShape(12.dp))
                .background(if (follow) C.panel else C.accent).border(1.dp, C.line, RoundedCornerShape(12.dp))
                .clickable { follow = !follow }.padding(horizontal = 16.dp, vertical = 12.dp))
    }
}

private fun addLayers(s: Style) {
    // 지명은 한글로(OSM name:ko, 없으면 원래 이름), 3D 건물은 끔(운전 중 화면을 단순하게)
    for (l in s.layers) {
        if (l is org.maplibre.android.style.layers.FillExtrusionLayer) l.setProperties(PF.visibility(Property.NONE))
        if (l is SymbolLayer && l.textField.value != null)
            l.setProperties(PF.textField(Expression.coalesce(Expression.get("name:ko"), Expression.get("name"))))
    }
    // 낮 지도 다듬기: 바다·강은 옅은 파랑, 고속도로 테두리는 조금 진하게(내비처럼 큰길이 보이게)
    if (!C.dark) {
        (s.getLayer("water") as? org.maplibre.android.style.layers.FillLayer)?.setProperties(PF.fillColor("#CFE3F1"))
        (s.getLayer("waterway") as? LineLayer)?.setProperties(PF.lineColor("#CFE3F1"))
        for (id in listOf("highway_motorway_casing", "highway_motorway_bridge_casing", "highway_major_casing"))
            (s.getLayer(id) as? LineLayer)?.setProperties(PF.lineColor("#BFC5CC"))
    }
    for (id in listOf("r-past", "r-now", "r-ahead", "r-plan", "nodes")) s.addSource(GeoJsonSource(id))
    s.addSource(GeoJsonSource("truck"))
    val round = arrayOf(PF.lineCap(Property.LINE_CAP_ROUND), PF.lineJoin(Property.LINE_JOIN_ROUND))
    s.addLayer(LineLayer("l-past", "r-past").withProperties(*round, PF.lineColor("#9AA0A6"), PF.lineWidth(5f), PF.lineOpacity(0.7f)))
    s.addLayer(LineLayer("l-ahead", "r-ahead").withProperties(*round, PF.lineColor("#5F6B7A"), PF.lineWidth(5f), PF.lineDasharray(arrayOf(1.5f, 1.5f))))
    s.addLayer(LineLayer("l-plan", "r-plan").withProperties(*round, PF.lineColor("#F5821F"), PF.lineWidth(6f), PF.lineDasharray(arrayOf(1.5f, 1.2f))))
    s.addLayer(LineLayer("l-now-case", "r-now").withProperties(*round, PF.lineColor("#FFFFFF"), PF.lineWidth(12f)))
    s.addLayer(LineLayer("l-now", "r-now").withProperties(*round, PF.lineColor("#16A37F"), PF.lineWidth(8f)))
    s.addLayer(CircleLayer("c-nodes", "nodes").withProperties(PF.circleColor(Expression.get("col")), PF.circleRadius(8f),
        PF.circleStrokeColor("#FFFFFF"), PF.circleStrokeWidth(2.5f)))
    s.addLayer(SymbolLayer("t-nodes", "nodes").withProperties(PF.textField(Expression.get("name")), PF.textSize(15f),
        PF.textColor("#111111"), PF.textHaloColor("#FFFFFF"), PF.textHaloWidth(2f), PF.textOffset(arrayOf(0f, 1.3f)),
        PF.textAnchor(Property.TEXT_ANCHOR_TOP), PF.textAllowOverlap(false), PF.textOptional(true)))
    s.addLayer(CircleLayer("c-truck-halo", "truck").withProperties(PF.circleColor("#16A37F"), PF.circleOpacity(0.25f), PF.circleRadius(22f)))
    s.addLayer(CircleLayer("c-truck", "truck").withProperties(PF.circleColor("#16A37F"), PF.circleRadius(10f),
        PF.circleStrokeColor("#FFFFFF"), PF.circleStrokeWidth(3.5f)))
}
