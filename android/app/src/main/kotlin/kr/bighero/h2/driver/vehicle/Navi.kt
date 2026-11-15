package kr.bighero.h2.driver.vehicle

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** 목적지 하나(이름·좌표) */
data class NaviDest(val name: String, val lat: Double, val lon: Double)

/**
 * 순정 내비로 길안내 넘기기.
 *
 * 지금 구현([IntentNavi]): 표준 내비 인텐트 `android.intent.action.NAVIGATE` + `geo:위도,경도`.
 *  플레오스 Connect 에뮬레이터의 순정 내비(ai.umos.maps.android.navigation.app)가 이 인텐트를 받는 것을 확인함(11/14).
 *  휴대폰처럼 받는 앱이 없으면 구글 지도 길안내(google.navigation:), 그다음 일반 geo: 보기로 넘어간다.
 *
 * 플레오스 정식 연동([PleosNavi], 아래 주석): NaviHelper SDK(ai.pleos.playground:NaviHelper:3.0.5)
 *  - 권한 pleos.car.permission.NAVI_ROUTE_SEARCH(requestRoute·경유지), NAVI_ROUTE(get*Info: 도착 예정·남은 거리)
 *  - NaviHelper(context).initialize(onConnected, onFailed) → requestRoute(RouteInfo(latitude, longitude, poiName …), RouteOption.RECOMMENDED)
 *  - addWaypoint()로 충전소 경유, NaviHelperEventListener.onDestinationArrived()로 도착 알림을 받아 일정 다음 단계로 넘길 수 있음
 *  - 권한은 플레오스 앱 마켓과 사전 협의·승인이 필요하고, SDK 저장소(nexus-playground.pleos.ai)는 개발자 등록 후 받는다
 */
interface NaviLauncher {
    /** 길안내를 시작했으면 true */
    fun start(dest: NaviDest): Boolean
    val label: String
}

class IntentNavi(private val ctx: Context) : NaviLauncher {
    override val label = "순정 내비"

    override fun start(dest: NaviDest): Boolean {
        val ll = "%.6f,%.6f".format(dest.lat, dest.lon)
        val tries = listOf(
            Intent("android.intent.action.NAVIGATE", Uri.parse("geo:$ll")),                       // 플레오스 순정 내비·AAOS 표준
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$ll")),                       // 휴대폰 구글 지도
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:$ll?q=$ll(${Uri.encode(dest.name)})")),       // 지도 앱 아무거나
        )
        for (i in tries) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }
}

/*
 * 플레오스 NaviHelper 연동 예시(SDK를 받은 뒤 주석을 풀고 맞춘다. 문서 v3.0.5 기준):
 *
 * class PleosNavi(ctx: Context) : NaviLauncher {
 *     override val label = "플레오스 내비"
 *     private val navi = NaviHelper(ctx.applicationContext).also { it.initialize(onConnected = {}, onFailed = {}) }
 *     override fun start(dest: NaviDest): Boolean {
 *         navi.requestRoute(RouteInfo(latitude = dest.lat, longitude = dest.lon, poiName = dest.name), RouteOption.RECOMMENDED)
 *         return true
 *     }
 * }
 */
