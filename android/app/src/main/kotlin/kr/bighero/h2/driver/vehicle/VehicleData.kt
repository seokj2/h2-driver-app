package kr.bighero.h2.driver.vehicle

import kr.bighero.h2.core.DaySim

/**
 * 차량에서 읽는 값. 지금은 시연 진행기([DaySim])가 채우고, 플레오스 Connect에 올릴 때 [PleosVehicleData]로 바꾼다.
 *
 * 플레오스 Vehicle SDK(문서 document.pleos.ai, Connect SDK):
 *  - settings.gradle.kts에 maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
 *  - app 의존성 implementation("ai.pleos.playground:Vehicle:<버전>")   // 문서 기준 2.3.x
 *  - Vehicle(context).initialize() 뒤 getXxx()로 도메인 객체(Fuel·Odometer·DrivingMode 등), 끝나면 release()
 *  - 권한(pleos.car.permission.*)은 플레오스 앱 마켓과 미리 협의·승인된 것만 선언할 수 있다
 *  - 수소 잔량 전용 API가 있는지는 문서에서 확인 못 함(Fuel 도메인 확인 필요). 위치는 FusedLocation SDK, 길안내는 NaviHelper SDK
 */
interface VehicleData {
    /** 수소 잔량(kg) */
    fun h2Kg(): Double
    /** 탱크 용량(kg) */
    fun tankKg(): Double
    /** 주행 중이면 true(화면에서 긴 목록·입력을 줄이는 데 씀) */
    fun isMoving(): Boolean
    val source: String
}

class SimVehicleData(private val sim: () -> DaySim) : VehicleData {
    override fun h2Kg() = sim().h2At()
    override fun tankKg() = sim().world.tank
    override fun isMoving() = sim().currentSeg()?.kind?.name?.startsWith("DRIVE") == true
    override val source = "시연 시뮬레이션"
}

/*
 * 플레오스 연결 예시(SDK를 받은 뒤 주석을 풀고 맞춘다. API 이름은 문서 버전에 따라 다를 수 있음):
 *
 * class PleosVehicleData(context: Context) : VehicleData {
 *     private val vehicle = Vehicle(context).also { it.initialize() }
 *     override fun h2Kg(): Double = TODO("vehicle.getFuel() 등에서 수소 잔량 읽기")
 *     override fun tankKg() = 68.0
 *     override fun isMoving(): Boolean = TODO("vehicle.getDrivingMode() 또는 차속")
 *     override val source = "플레오스 Vehicle SDK"
 * }
 */
