# setup-pleos.ps1
# Rebuilds the whole Pleos Connect dev environment inside ONE folder on a fresh PC
# (Android Studio + Android SDK + emulator + Pleos Connect system image + AVD + hypervisor driver).
#
# Run from an Administrator PowerShell:
#   powershell -ExecutionPolicy Bypass -File setup-pleos.ps1
#   powershell -ExecutionPolicy Bypass -File setup-pleos.ps1 -Root D:\PleosDev
#
# NOTE: running this script accepts the Android SDK licenses (Google) and installs the
# Pleos Connect SDK image, which is governed by the 42dot SDK License Agreement.
param([string]$Root = 'C:\PleosDev')

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$sw = [Diagnostics.Stopwatch]::StartNew()
function Step($m) { Write-Host ("[{0,4}s] {1}" -f [int]$sw.Elapsed.TotalSeconds, $m) -ForegroundColor Cyan }

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) { throw 'Run this script from an Administrator PowerShell (needed for the hypervisor driver).' }

$PleosSite = 'https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml'
$dl = "$Root\downloads"
foreach ($d in "$dl\pleos", "$dl\studio", "$dl\cmdline", "$dl\node", "$dl\jdk", "$Root\sdk\cmdline-tools", "$Root\home\.android", "$Root\home\.gradle", "$Root\avd", "$Root\projects") {
    New-Item -ItemType Directory -Force $d | Out-Null
}

# ---------- 1. Resolve download URLs ----------
Step 'Resolving download URLs'
$page = (curl.exe -sL 'https://developer.android.com/studio') -join "`n"
$studioUrl = ([regex]::Match($page, 'https://[^"''\s]+?/android/studio/ide-zips/[^"''\s]+?-windows\.zip')).Value
$cmdUrl = ([regex]::Match($page, 'https://dl\.google\.com/android/repository/commandlinetools-win-[0-9]+_latest\.zip')).Value
if (-not $studioUrl -or -not $cmdUrl) { throw 'Could not find Android Studio download links on developer.android.com/studio' }
$studioUrl = $studioUrl -replace '^https://[^/]+/', 'https://dl.google.com/'
[xml]$site = (curl.exe -sL $PleosSite) -join "`n"
$rp = $site.DocumentElement.remotePackage
$pleosUrl = $rp.archives.archive.complete.url
$pleosSha1 = $rp.archives.archive.complete.checksum
$pkgPath = $rp.path                                   # e.g. system-images;android-34;pleos_car_emulator;x86_64
$td = $rp.'type-details'
$nodeIdx = ((curl.exe -sL 'https://nodejs.org/dist/index.json') -join '') | ConvertFrom-Json
$nodeVer = ($nodeIdx | Where-Object { $_.lts } | Select-Object -First 1).version
if ($nodeVer -notmatch '^v\d+\.\d+\.\d+$') { throw 'Could not read the Node.js LTS version list' }
$nodeName = "node-$nodeVer-win-x64"
$nodeUrl = "https://nodejs.org/dist/$nodeVer/$nodeName.zip"
$jdkUrl = 'https://aka.ms/download-jdk/microsoft-jdk-21-windows-x64.zip'   # Gradle 8.x builds need JDK 21 (Studio bundles 25)
Write-Host "  Studio : $studioUrl"
Write-Host "  Pleos  : $pleosUrl"

# ---------- 2. Download everything in parallel ----------
Step 'Downloading (Pleos image ~4 GB, Android Studio ~1.5 GB, command line tools, Node.js, JDK 21)'
$jobs = @(
    @{ u = $pleosUrl;  o = "$dl\pleos\pleos.zip" },
    @{ u = $studioUrl; o = "$dl\studio\studio.zip" },
    @{ u = $cmdUrl;    o = "$dl\cmdline\cmdline.zip" },
    @{ u = $nodeUrl;   o = "$dl\node\node.zip" },
    @{ u = $jdkUrl;    o = "$dl\jdk\jdk21.zip" }
)
$procs = foreach ($j in $jobs) {
    Start-Process curl.exe -ArgumentList @('-L', '--fail', '--retry', '8', '--retry-delay', '2', '-C', '-', '-s', '-S', '-o', "`"$($j.o)`"", $j.u) -NoNewWindow -PassThru
}
$procs | Wait-Process
foreach ($p in $procs) { if ($p.ExitCode -ne 0) { throw "A download failed (curl exit $($p.ExitCode)). Re-run the script; downloads resume." } }
$got = (Get-FileHash "$dl\pleos\pleos.zip" -Algorithm SHA1).Hash.ToLower()
if ($got -ne $pleosSha1.ToLower()) { throw "Pleos image checksum mismatch ($got)" }
$nodeSums = (curl.exe -sL "https://nodejs.org/dist/$nodeVer/SHASUMS256.txt") -join "`n"
$nodeWant = [regex]::Match($nodeSums, '([0-9a-f]{64})\s+' + [regex]::Escape("$nodeName.zip")).Groups[1].Value
if (-not $nodeWant -or $nodeWant -ne (Get-FileHash "$dl\node\node.zip" -Algorithm SHA256).Hash.ToLower()) { throw 'Node.js checksum mismatch' }
$jdkSums = ((curl.exe -sL "$jdkUrl.sha256sum.txt") -join ' ').ToLower()
if (-not $jdkSums.Contains((Get-FileHash "$dl\jdk\jdk21.zip" -Algorithm SHA256).Hash.ToLower())) { throw 'JDK 21 checksum mismatch' }

# ---------- 2b. Node.js + JDK 21 ----------
Step 'Extracting Node.js and JDK 21'
tar -xf "$dl\node\node.zip" -C "$dl\node"
tar -xf "$dl\jdk\jdk21.zip" -C "$dl\jdk"
foreach ($t in "$Root\nodejs", "$Root\jdk-21") { if (Test-Path $t) { Remove-Item $t -Recurse -Force } }
Move-Item "$dl\node\$nodeName" "$Root\nodejs"
Move-Item (Get-ChildItem "$dl\jdk" -Directory | Select-Object -First 1).FullName "$Root\jdk-21"
[IO.File]::WriteAllText("$Root\home\.gradle\gradle.properties", "org.gradle.java.home=" + ("$Root\jdk-21" -replace '\\', '/') + "`r`n", [Text.Encoding]::ASCII)

# ---------- 3. Android Studio + command line tools ----------
Step 'Extracting Android Studio'
tar -xf "$dl\studio\studio.zip" -C $Root
if ($LASTEXITCODE -ne 0) { throw 'Android Studio extraction failed' }
if (Test-Path "$Root\sdk\cmdline-tools\latest") { Remove-Item "$Root\sdk\cmdline-tools\latest" -Recurse -Force }
tar -xf "$dl\cmdline\cmdline.zip" -C "$Root\sdk\cmdline-tools"
Rename-Item "$Root\sdk\cmdline-tools\cmdline-tools" 'latest'

$env:JAVA_HOME = "$Root\android-studio\jbr"
$env:ANDROID_HOME = "$Root\sdk"; $env:ANDROID_SDK_ROOT = "$Root\sdk"
$env:ANDROID_USER_HOME = "$Root\home\.android"; $env:ANDROID_AVD_HOME = "$Root\avd"
$bin = "$Root\sdk\cmdline-tools\latest\bin"

# ---------- 4. SDK packages ----------
Step 'Installing Android SDK packages (platform-tools, emulator, platforms, build-tools, AEHD)'
$yes = "$Root\home\yes.txt"
[IO.File]::WriteAllText($yes, ("y`r`n" * 80), [Text.Encoding]::ASCII)
cmd /c "`"$bin\sdkmanager.bat`" --licenses < `"$yes`" >nul 2>&1"
cmd /c "`"$bin\sdkmanager.bat`" platform-tools emulator platforms;android-34 platforms;android-35 build-tools;35.0.0 extras;google;Android_Emulator_Hypervisor_Driver < `"$yes`" >nul 2>&1"
if (-not (Test-Path "$Root\sdk\emulator\emulator.exe")) { throw 'SDK package installation failed' }
Remove-Item $yes -Force

# ---------- 5. Pleos Connect system image ----------
Step 'Extracting Pleos Connect system image (~19 GB on disk, takes a few minutes)'
$parts = $pkgPath -split ';'
$imgParent = "$Root\sdk\" + (($parts[0..($parts.Count - 2)]) -join '\')
$img = "$imgParent\$($parts[-1])"
if (Test-Path $img) { Remove-Item $img -Recurse -Force }
New-Item -ItemType Directory -Force $imgParent | Out-Null
tar -xf "$dl\pleos\pleos.zip" -C $imgParent
if ($LASTEXITCODE -ne 0) { throw 'Pleos image extraction failed' }
$top = Get-ChildItem $imgParent -Directory | Where-Object { $_.Name -ne $parts[-1] } | Select-Object -First 1
Rename-Item $top.FullName $parts[-1]
$esc = { param($s) [Security.SecurityElement]::Escape([string]$s) }
$pkgXml = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/01" xmlns:si="http://schemas.android.com/sdk/android/repo/sys-img2/01">' +
    '<localPackage path="' + (& $esc $pkgPath) + '" obsolete="false">' +
    '<type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="si:sysImgDetailsType">' +
    '<api-level>' + $td.'api-level' + '</api-level>' +
    '<tag><id>' + (& $esc $td.tag.id) + '</id><display>' + (& $esc $td.tag.display) + '</display></tag>' +
    '<vendor><id>' + (& $esc $td.vendor.id) + '</id><display>' + (& $esc $td.vendor.display) + '</display></vendor>' +
    '<abi>' + $td.abi + '</abi></type-details>' +
    '<revision><major>' + $rp.revision.major + '</major></revision>' +
    '<display-name>' + (& $esc $rp.'display-name') + '</display-name>' +
    '</localPackage></ns2:repository>'
[IO.File]::WriteAllText("$img\package.xml", $pkgXml, (New-Object Text.UTF8Encoding $false))
$repoCfg = "### User Sources for Android Repository`r`ncount=1`r`ndisp00=Pleos Connect System Image`r`nenabled00=true`r`nsrc00=" + ($PleosSite -replace ':', '\:') + "`r`n"
[IO.File]::WriteAllText("$Root\home\.android\repositories.cfg", $repoCfg, [Text.Encoding]::ASCII)

# ---------- 6. Virtualization (no reboot needed) ----------
Step 'Installing Android Emulator hypervisor driver (AEHD)'
cmd /c "`"$Root\sdk\extras\google\Android_Emulator_Hypervisor_Driver\silent_install.bat`"" | Out-Null
& "$Root\sdk\emulator\emulator.exe" -accel-check

# ---------- 7. AVD ----------
Step 'Creating the PleosConnect virtual device'
$devId = ([regex]::Match((Get-Content "$img\devices.xml" -Raw), '<d:id>([^<]+)</d:id>')).Groups[1].Value
cmd /c "echo no| `"$bin\avdmanager.bat`" create avd -n PleosConnect -k $pkgPath -d $devId --force >nul 2>&1"
$cfg = "$Root\avd\PleosConnect.avd\config.ini"
if (-not (Test-Path $cfg)) { throw 'AVD creation failed' }
$c = Get-Content $cfg
$set = [ordered]@{ 'hw.ramSize' = '4096'; 'hw.gpu.enabled' = 'yes'; 'hw.gpu.mode' = 'host'; 'fastboot.forceColdBoot' = 'yes'; 'fastboot.forceFastBoot' = 'no'; 'hw.keyboard' = 'yes'; 'disk.dataPartition.size' = '6G'; 'avd.name' = 'PleosConnect'; 'avd.id' = 'PleosConnect' }
foreach ($k in $set.Keys) {
    $pat = '^' + [regex]::Escape($k) + '='
    if ($c -match $pat) { $c = $c -replace ($pat + '.*'), "$k=$($set[$k])" } else { $c += "$k=$($set[$k])" }
}
[IO.File]::WriteAllLines($cfg, $c, (New-Object Text.UTF8Encoding $false))

# ---------- 8. Launchers (everything stays inside $Root) ----------
Step 'Writing launchers'
$envBat = @'
@echo off
set "PLEOS_ROOT=%~dp0"
if "%PLEOS_ROOT:~-1%"=="\" set "PLEOS_ROOT=%PLEOS_ROOT:~0,-1%"
set "ANDROID_HOME=%PLEOS_ROOT%\sdk"
set "ANDROID_SDK_ROOT=%PLEOS_ROOT%\sdk"
set "ANDROID_USER_HOME=%PLEOS_ROOT%\home\.android"
set "ANDROID_AVD_HOME=%PLEOS_ROOT%\avd"
set "GRADLE_USER_HOME=%PLEOS_ROOT%\home\.gradle"
set "JAVA_HOME=%PLEOS_ROOT%\jdk-21"
set "STUDIO_PROPERTIES=%PLEOS_ROOT%\studio.properties"
set "PATH=%PLEOS_ROOT%\nodejs;%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\emulator;%ANDROID_HOME%\cmdline-tools\latest\bin;%PATH%"
'@
$studioBat = @'
@echo off
call "%~dp0env.bat"
start "" "%PLEOS_ROOT%\android-studio\bin\studio64.exe"
'@
$emuBat = @'
@echo off
call "%~dp0env.bat"
start "" "%ANDROID_HOME%\emulator\emulator.exe" -avd PleosConnect -no-snapshot-load -gpu host
'@
$props = @'
# Keep Android Studio settings and caches inside the PleosDev folder.
idea.config.path=${idea.home.path}/../home/studio/config
idea.system.path=${idea.home.path}/../home/studio/system
idea.plugins.path=${idea.config.path}/plugins
idea.log.path=${idea.system.path}/log
'@
$ascii = [Text.Encoding]::ASCII
[IO.File]::WriteAllText("$Root\env.bat", ($envBat -replace "`r?`n", "`r`n") + "`r`n", $ascii)
[IO.File]::WriteAllText("$Root\start-studio.bat", ($studioBat -replace "`r?`n", "`r`n") + "`r`n", $ascii)
[IO.File]::WriteAllText("$Root\start-emulator.bat", ($emuBat -replace "`r?`n", "`r`n") + "`r`n", $ascii)
[IO.File]::WriteAllText("$Root\studio.properties", ($props -replace "`r?`n", "`r`n") + "`r`n", $ascii)

Remove-Item $dl -Recurse -Force
Step "Done. Run $Root\start-studio.bat (Android Studio) or $Root\start-emulator.bat (Pleos Connect emulator)."
