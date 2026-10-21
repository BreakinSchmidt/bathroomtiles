param(
    [string]$Device = "192.168.11.124:35917",
    [string]$PairEndpoint = "",
    [string]$PairCode = ""
)

$adb = "C:\Users\mschm\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$apk = "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"

if ($PairEndpoint -ne "" -and $PairCode -ne "") {
    Write-Host "Pairing with watch at $PairEndpoint..." -ForegroundColor Cyan
    & $adb pair $PairEndpoint $PairCode
}

Write-Host "Connecting to watch at $Device..." -ForegroundColor Cyan
& $adb connect $Device

Write-Host "Installing RadialTiles APK..." -ForegroundColor Cyan
& $adb -s $Device install -r $apk

Write-Host "Launching app on watch..." -ForegroundColor Cyan
& $adb -s $Device shell am start -n com.radialtiles/.MainActivity

Write-Host "Done!" -ForegroundColor Green
