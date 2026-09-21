# 构建 debug APK。不依赖任何全局环境变量。
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'D:\Android\jdk17'
Set-Location $PSScriptRoot

$module = if ($args.Count -gt 0) { $args[0] } else { 'probe' }
& '.\gradlew.bat' ":${module}:assembleDebug" @($args | Select-Object -Skip 1)
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$apk = "$PSScriptRoot\$module\build\outputs\apk\debug\$module-debug.apk"
Write-Host ""
Write-Host "APK: $apk" -ForegroundColor Green
Write-Host "     $([math]::Round((Get-Item $apk).Length/1MB,2)) MB"
