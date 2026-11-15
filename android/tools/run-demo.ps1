# 데스크탑에서 한 번에: 플레오스 에뮬레이터 켜기 → 최신 APK 받아 설치 → 권한 주고 앱 실행 → (선택) 화면 녹화
#   powershell -ExecutionPolicy Bypass -File run-demo.ps1            # 켜고 설치하고 실행
#   powershell -ExecutionPolicy Bypass -File run-demo.ps1 -Record    # 실행 뒤 3분 녹화, 바탕화면에 h2_emulator.mp4
#   -Apk C:\경로\앱.apk 를 주면 내려받지 않고 그 파일을 설치
param([string]$Root = "C:\PleosDev", [string]$Apk = "", [switch]$Record, [int]$Seconds = 180)
$ErrorActionPreference = "Stop"
$adb = Join-Path $Root "sdk\platform-tools\adb.exe"
$emu = Join-Path $Root "sdk\emulator\emulator.exe"
$env:ANDROID_AVD_HOME = Join-Path $Root "avd"
if (!(Test-Path $adb)) { throw "adb가 없어요: $adb (설치 위치가 다르면 -Root 로 지정)" }

# 1) 에뮬레이터: 이미 켜져 있으면 그대로
& $adb start-server | Out-Null
$dev = (& $adb devices) -match "emulator-\d+\s+device"
if (!$dev) {
  Write-Host "에뮬레이터 켜는 중 (PleosConnect)..."
  Start-Process -FilePath $emu -ArgumentList "-avd","PleosConnect","-no-snapshot-load","-gpu","host"
  & $adb wait-for-device
}
Write-Host "부팅 기다리는 중..."
do { Start-Sleep 3; $b = (& $adb shell getprop sys.boot_completed 2>$null) } while ("$b".Trim() -ne "1")
Start-Sleep 5

# 2) APK: 제출 저장소의 최신 release APK
if ($Apk -eq "") {
  $Apk = Join-Path $env:TEMP "h2_driver_release.apk"
  $url = "https://github.com/seokj2/h2-driver-app/raw/main/apk/%EB%B9%85%ED%9E%88%EC%96%B4%EB%A1%9C_H2%ED%9A%8C%EB%9E%91%EA%B8%B0%EC%82%AC_release.apk"
  Write-Host "최신 APK 받는 중..."
  Invoke-WebRequest -Uri $url -OutFile $Apk -UseBasicParsing
}
Write-Host "설치 중: $Apk"
& $adb install -r -g $Apk
if ($LASTEXITCODE -ne 0) { & $adb uninstall kr.bighero.h2.driver | Out-Null; & $adb install -g $Apk }

# 3) 권한(마이크·알림) 주고 실행
foreach ($p in "android.permission.RECORD_AUDIO","android.permission.POST_NOTIFICATIONS") { & $adb shell pm grant kr.bighero.h2.driver $p 2>$null }
& $adb shell am force-stop kr.bighero.h2.driver
& $adb shell am start -n kr.bighero.h2.driver/.MainActivity
Write-Host "앱 실행됨. 추천 일정에서 [이 일정 받기]를 누르면 순정 내비로 넘어가요. 오른쪽 위 x60으로 시계를 돌리면 도착 때 앱이 다시 앞으로 와요."

# 4) 녹화(선택): 에뮬레이터 화면을 그대로(최대 180초)
if ($Record) {
  $out = Join-Path ([Environment]::GetFolderPath("Desktop")) "h2_emulator.mp4"
  Write-Host "$Seconds 초 녹화 시작. 지금 시연하세요..."
  & $adb shell screenrecord --time-limit $Seconds --bit-rate 8000000 /sdcard/h2.mp4
  & $adb pull /sdcard/h2.mp4 $out
  Write-Host "녹화 저장: $out"
}
