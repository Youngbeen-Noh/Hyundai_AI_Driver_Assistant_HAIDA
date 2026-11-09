# Pleos Connect Virtual Console: 에뮬레이터 8088 포트를 PC 로 포워딩하고 브라우저로 연다.
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"

& $adb wait-for-device
& $adb forward tcp:8088 tcp:8088
if ($LASTEXITCODE -ne 0) { Write-Error "adb forward 실패. 에뮬레이터 실행 여부와 8088 포트 사용 여부를 확인하세요."; exit 1 }
Start-Process "http://127.0.0.1:8088/"
