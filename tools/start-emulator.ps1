# Pleos Connect 에뮬레이터를 Cold boot 로 실행한다 (Quick boot 는 VHAL 미동작).
param(
    [string]$Avd = "",
    [ValidateSet("auto", "host", "swiftshader_indirect")]
    [string]$Gpu = "auto"
)

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$emulator = Join-Path $sdk "emulator\emulator.exe"

if (-not $Avd) {
    $list = & $emulator -list-avds
    if (-not $list) { Write-Error "AVD 가 없습니다. README.md의 개발 환경 준비대로 먼저 만드세요."; exit 1 }
    Write-Host "사용 가능한 AVD:"; $list | ForEach-Object { Write-Host "  $_" }
    $Avd = @($list)[0]
    Write-Host "→ '$Avd' 로 실행합니다. 다른 AVD 는 -Avd 로 지정하세요."
}

& $emulator -avd $Avd -no-snapshot-load -gpu $Gpu
