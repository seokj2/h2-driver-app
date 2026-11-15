# H2 기사 앱: 빌드 -> 플레오스 Connect 에뮬레이터에 설치·실행 -> 화면 캡처
# 쓰는 법(저장소를 git으로 받은 경우, 저장소 맨 위에서):
#   powershell -ExecutionPolicy Bypass -File android\tools\h2-build.ps1
# 브랜치 zip을 받은 경우:
#   powershell -ExecutionPolicy Bypass -File h2-build.ps1 -Zip <브랜치.zip>
param(
  [string]$Zip = '',
  [string]$PleosRoot = 'C:\PleosDev',
  [string]$Out = (Join-Path $env:USERPROFILE 'Downloads'),
  [int]$Shot = 1,
  [string]$Dev = 'emulator-5554'
)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $PleosRoot 'sdk\platform-tools\adb.exe'
$env:JAVA_HOME        = Join-Path $PleosRoot 'jdk-21'
$env:ANDROID_HOME     = Join-Path $PleosRoot 'sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $PleosRoot 'home\.gradle'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 1. 소스 위치: zip이면 새로 풀고, 아니면 이 스크립트가 있는 저장소의 android 폴더
if ($Zip) {
  $dest = Join-Path $Out 'h2-corridor-app'
  if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
  Expand-Archive -Path $Zip -DestinationPath $dest
  $android = Join-Path (Get-ChildItem $dest -Directory | Select-Object -First 1).FullName 'android'
} else {
  $android = Split-Path $PSScriptRoot -Parent
}
$sdkEsc = ($env:ANDROID_HOME -replace '\\', '\\\\') -replace ':', '\:'
Set-Content -Path (Join-Path $android 'local.properties') -Value "sdk.dir=$sdkEsc" -Encoding ascii

# 2. 빌드
Push-Location $android
& .\gradlew.bat :app:assembleDebug
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) { throw "Gradle build failed (exit $code)" }
$apk = Join-Path $android 'app\build\outputs\apk\debug\app-debug.apk'

# 3. 에뮬레이터가 꺼져 있으면 켜기
if (-not ((& $adb devices) -match "$Dev\s+device")) {
  Start-Process cmd.exe -ArgumentList '/c', (Join-Path $PleosRoot 'start-emulator.bat') -WindowStyle Hidden
  & $adb -s $Dev wait-for-device
  do { Start-Sleep 3 } until ((& $adb -s $Dev shell getprop sys.boot_completed).Trim() -eq '1')
}

# 4. 설치·실행(다른 PC에서 만든 APK면 서명이 달라 지우고 다시 설치: 앱 안 시연 상태는 처음으로 돌아감)
& $adb -s $Dev logcat -c
$o = (& $adb -s $Dev install -r $apk) -join "`n"
Write-Host $o
if ($o -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
  & $adb -s $Dev uninstall kr.bighero.h2.driver
  & $adb -s $Dev install $apk
}
& $adb -s $Dev shell am force-stop kr.bighero.h2.driver
& $adb -s $Dev shell am start -n kr.bighero.h2.driver/.MainActivity
Start-Sleep 8
& $adb -s $Dev shell "screencap -p /sdcard/h2s.png"
& $adb -s $Dev pull /sdcard/h2s.png (Join-Path $Out "h2_shot$Shot.png") | Out-Null
Write-Host "saved $(Join-Path $Out "h2_shot$Shot.png")"
Write-Host '--- AndroidRuntime:E ---'
& $adb -s $Dev logcat -d -s AndroidRuntime:E
