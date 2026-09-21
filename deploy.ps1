# 装到手表并拉日志。OPPO Watch 3 是磁吸充电、没有 USB 数据口，
# 只能走无线 adb：手表「设置 > 关于 > 连点版本号」开开发者选项，
# 打开「无线调试」，记下手表 Wi-Fi 的 IP。
#   .\deploy.ps1 192.168.1.23            装 watch 模块 debug 包
#   .\deploy.ps1 192.168.1.23 probe      装探针
#   .\deploy.ps1 192.168.1.23 watch release
param(
    [Parameter(Mandatory = $true)][string]$WatchIp,
    [string]$Module = 'watch',
    [ValidateSet('debug', 'release')][string]$Variant = 'debug',
    [int]$Port = 5555
)
$ErrorActionPreference = 'Stop'
$adb  = 'D:\Android\sdk\platform-tools\adb.exe'
$aapt = 'D:\Android\sdk\build-tools\34.0.0\aapt2.exe'
$apk  = "$PSScriptRoot\$Module\build\outputs\apk\$Variant\$Module-$Variant.apk"
if (-not (Test-Path $apk)) { throw "APK 不存在: $apk`n先跑 .\gradlew.bat :${Module}:assemble$($Variant.Substring(0,1).ToUpper() + $Variant.Substring(1))" }

# 包名从 APK 里读，不要按模块名去猜 —— applicationId 和模块名并不一致
$badging = & $aapt dump badging $apk
$pkg = ([regex]::Match(($badging -join "`n"), "package: name='([^']+)'")).Groups[1].Value
if (-not $pkg) { throw "无法从 APK 解析包名" }
Write-Host "package = $pkg" -ForegroundColor Cyan

$target = "${WatchIp}:${Port}"
& $adb connect $target | Out-Host
& $adb -s $target install -r $apk
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $adb -s $target shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host "已启动 $pkg，Ctrl+C 停止日志" -ForegroundColor Green

# 把本 App 所有 tag 都放进来。之前只过滤 probe:V，
# 结果 WeatherRepo 的同步失败原因全被吞掉了。
& $adb -s $target logcat -c
& $adb -s $target logcat WeatherRepo:V SnapshotStore:V CityStore:V probe:V AndroidRuntime:E '*:S'
