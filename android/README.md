# H2 회랑 기사 앱 (안드로이드 · 플레오스 Connect용)

수소 트랙터 운전자가 차 안에서 보는 **실시간 배차 일정 추천** 앱입니다.
대회 때 만든 기사 앱 화면(`docs/archive/2026-hackathon/final/H2_driver_app.html`)을 실제 안드로이드 앱으로 옮겼고,
추천은 앱 안에서 직접 계산합니다(시뮬레이터 `h2-dispatch/src/scenarios.py`의 1단계 Planner1을 Kotlin으로 옮김).

## 무엇을 하나

- **오늘**: 수소 잔량, 지금 하는 일(진행 막대·남은 시간·다음 할 일), 쉬고 있으면 추천 일정 1~3안
  - 일감 1~3개 + 충전 0~1회 조합을 만들고 수소 15%·반입 마감·근무시간(30분 여유)·충전소/디포 운영시간으로 거름
  - 점수 = 용차 단가 − 수소 − 통행료 − 대기·노는 시간(시간당 4만원) − 수입 늦음 − 수출 놓칠 위험(100만원 × 확률) − 초과근무
  - 수출: 접안 지연 시나리오 100개(XGBoost 분위수 + 구간 보정)로 "배 놓칠 확률"을 보여 줌
  - 수입: 반출 가능 시각 예측 범위와 맞춰 출발할 시각
  - "왜 이 일정?"에 돈 계산을 그대로 보여 줌
  - 첫 일만 확정하고, 끝나면 그때 상황으로 다시 추천(기업용 설정 `CORP_ROLL`과 같음)
- **전체 일정**: 지난·지금·확정된 구간
- **알림**: 접안 지연, 접안 확인, 추천에 있던 일감이 다른 트럭에 배정됨
- **수소**: 잔량, 오늘 충전, 충전소 운영시간·단가

지금은 **2026-07-16 하루 시연**입니다. 위쪽 ▶ / ×60 / ⏭ 버튼으로 하루를 빨리 돌립니다(실제 차량에서는 없앨 부분).
트럭은 "수소 5호"(대한유화 울산공장, 공동 배차 kpicjoint, 5대 중 1대)이고, 다른 4대가 오더를 잡아 가는 시각은 기업용 시뮬레이션(P2) 기록을 따릅니다.

## 구조

```
android/
  core/   순수 Kotlin(안드로이드 의존 없음): 자료 묶음 읽기, World(주행·운영시간·빈 컨테이너), Planner(추천 엔진), DaySim(시연 진행기)
  app/    Jetpack Compose 화면. 넓은 화면(차량 가로, 840dp 이상)은 2단 + 왼쪽 탭, 좁은 화면(휴대폰)은 1단 + 아래 탭
```

- 엔진은 **공지 값 + 지연 시나리오 + 이미 일어난 접안**만 봅니다. 정답(실제 마감·반출 가능)은 묶음의 `truth`에 따로 있고 `DaySim`만 읽습니다(저장소 CLAUDE.md 규칙과 같음).
- 차량 값(수소 잔량, 주행 중 여부)은 `app/.../vehicle/VehicleData.kt` 인터페이스로 분리했습니다. 지금은 시연 값, 플레오스 Vehicle SDK를 받으면 `PleosVehicleData`로 바꿔 끼웁니다.

## 플레오스 Connect

- 플레오스 Connect는 Android Automotive(AAOS) 기반이고 최소 API 28, 에뮬레이터 이미지는 API 34입니다(document.pleos.ai, Connect SDK).
- 이 앱: `minSdk 28`, `android.hardware.type.automotive`를 필수 아님으로 선언해서 휴대폰과 차량 에뮬레이터 모두 설치됩니다.
  주행 중에도 열리게 `distractionOptimized`를 켰습니다(앱 마켓 심사의 운전자 주의 분산 항목은 따로 확인 필요).
- Vehicle SDK 저장소·의존성은 `settings.gradle.kts`, `VehicleData.kt` 주석에 적어 두었습니다. 권한(`pleos.car.permission.*`)은 앱 마켓과 미리 협의된 것만 쓸 수 있습니다.

## 빌드·실행

```bash
cd android
./gradlew :core:test                 # 엔진이 파이썬 Planner1과 같은 일정안을 내는지(정답 5개 상황) + 하루 자동 진행
./gradlew :app:testDebugUnitTest     # 에뮬레이터 없이 화면을 그려 app/build/screens/*.png 로 저장(차량 가로·휴대폰 세로)
./gradlew :app:assembleDebug         # APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n kr.bighero.h2.driver/.MainActivity
```

안드로이드 스튜디오에서는 `android/` 폴더를 열면 됩니다(JDK 17 이상).

## 자료 묶음 다시 만들기

하루 자료는 별도 연구 저장소의 배차 시뮬레이터(`h2-dispatch/src/app_bundle.py`)에서 만들어 넣었습니다(이 저장소에는 결과 파일만 포함). 도로 경로는 `python scripts/app_routes.py`로 다시 받을 수 있습니다.

- `app/src/main/assets/day_<날>.json`: 노드·시간대별 주행시간·충전소·배(공지 + 지연 시나리오 100개)·오더·우리 트럭 출근 상태·다른 트럭이 잡은 시각·정답(truth)
- `core/src/test/resources/golden_<날>.json`: 같은 입력으로 파이썬 Planner1이 낸 상위 일정안(엔진 테스트 정답)

## 다음 할 일

- 실시간 연결: 오더·배차 서버(지금은 묶음 파일), 선석 현황(Port-i 등), 충전소 실시간 대기(한국석유관리원 API, `h2-dispatch/data/h2wait/`)
- 여러 트럭 함께 배차(2단계 CP-SAT 조정)는 서버 쪽 일. 앱은 서버가 준 후보를 보여 주고 수락만 보내는 구조로 바꿀 수 있음
- 플레오스 Vehicle SDK로 수소 잔량·위치 읽기, NaviHelper로 길안내 넘기기, 음성 안내(TTS)
