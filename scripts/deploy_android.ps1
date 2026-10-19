<#
.SYNOPSIS
    Automated BlueStacks Deployment & Launch Script for BidDrive Android App
.DESCRIPTION
    Seamlessly connects to BlueStacks via ADB, reinstalls the app (-r -d) without requiring
    manual uninstalls/downloads, auto-grants GPS permissions, and launches the app directly.
#>

param(
    [switch]$Build,
    [string]$AdbPath = ""
)

$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "[*] BidDrive Android Automated BlueStacks Deployer" -ForegroundColor Yellow
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Resolve ADB executable
if ([string]::IsNullOrWhiteSpace($AdbPath)) {
    $possibleAdbPaths = @(
        "adb.exe",
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "C:\Users\Naveen kasi\AppData\Local\Android\Sdk\platform-tools\adb.exe",
        "C:\Program Files\Android\platform-tools\adb.exe"
    )

    foreach ($p in $possibleAdbPaths) {
        $found = Get-Command $p -ErrorAction SilentlyContinue
        if ($found) {
            $AdbPath = $found.Source
            break
        }
        if (Test-Path $p) {
            $AdbPath = $p
            break
        }
    }
}

if ([string]::IsNullOrWhiteSpace($AdbPath) -or (-not (Test-Path $AdbPath))) {
    Write-Host "[!] adb.exe not found. Please ensure Android SDK platform-tools are installed." -ForegroundColor Red
    exit 1
}

Write-Host "[+] Using ADB: $AdbPath" -ForegroundColor DarkGray

# 2. Find or Connect to BlueStacks
Write-Host "[*] Detecting BlueStacks / Android Devices..." -ForegroundColor Cyan

$devicesOutput = & $AdbPath devices
$connected = $devicesOutput | Where-Object { $_ -match "\b(device)\b" -and $_ -notmatch "List of" }

if (-not $connected) {
    Write-Host "[!] No device immediately connected. Scanning BlueStacks ADB ports..." -ForegroundColor Yellow
    $candidatePorts = @(5555, 5554, 5556, 5565, 5575)
    $connectedTarget = $null

    foreach ($port in $candidatePorts) {
        Write-Host "    - Trying 127.0.0.1:$port..." -ForegroundColor DarkGray
        $connectRes = & $AdbPath connect 127.0.0.1:$port
        if ($connectRes -match "connected to") {
            $connectedTarget = "127.0.0.1:$port"
            Write-Host "    [+] Successfully connected to BlueStacks on $connectedTarget" -ForegroundColor Green
            break
        }
    }

    if (-not $connectedTarget) {
        Write-Host ""
        Write-Host "[!] BlueStacks could not be reached automatically on localhost ports." -ForegroundColor Yellow
        Write-Host "[*] Quick Setup Checklist:" -ForegroundColor Cyan
        Write-Host "    1. Open BlueStacks." -ForegroundColor White
        Write-Host "    2. Click Settings (gear icon) -> Advanced." -ForegroundColor White
        Write-Host "    3. Turn ON 'Android Debug Bridge (ADB)'." -ForegroundColor White
        Write-Host "    4. Re-run this script to deploy with 1-click!" -ForegroundColor Yellow
        Write-Host ""
    }
} else {
    Write-Host "[+] Active Device Detected: $connected" -ForegroundColor Green
}

# 3. Target APK
$apkPath = Join-Path $PSScriptRoot "..\biddrive-android\app\build\outputs\apk\debug\app-debug.apk"

$needsBuild = $false
if ($Build.IsPresent) {
    $needsBuild = $true
} elseif (-not (Test-Path $apkPath)) {
    $needsBuild = $true
}

if ($needsBuild) {
    Write-Host ""
    Write-Host "[*] Compiling debug APK via Gradle..." -ForegroundColor Cyan
    $projectDir = Join-Path $PSScriptRoot "..\biddrive-android"
    Push-Location $projectDir
    try {
        .\gradlew assembleDebug --quiet
        Write-Host "[+] APK compilation successful!" -ForegroundColor Green
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $apkPath)) {
    Write-Host "[!] APK not found at: $apkPath" -ForegroundColor Red
    exit 1
}

$apkItem = Get-Item $apkPath
$sizeMb = [math]::Round($apkItem.Length / 1048576, 2)
Write-Host "[+] Target APK: $($apkItem.FullName) ($sizeMb MB)" -ForegroundColor Cyan

# 4. Seamless Reinstall without uninstalling
$devicesOutput = & $AdbPath devices
$activeDevice = $devicesOutput | Where-Object { $_ -match "\b(device)\b" -and $_ -notmatch "List of" }

if ($activeDevice) {
    Write-Host ""
    Write-Host "[*] Installing APK directly to BlueStacks (reinstall mode: -r -d)..." -ForegroundColor Cyan
    $installResult = & $AdbPath install -r -d $apkPath
    Write-Host $installResult -ForegroundColor Green

    # 5. Grant Location & Notification permissions automatically
    Write-Host "[*] Auto-granting Location and Notification Permissions..." -ForegroundColor Cyan
    & $AdbPath shell pm grant com.biddrive.app android.permission.ACCESS_FINE_LOCATION 2>$null
    & $AdbPath shell pm grant com.biddrive.app android.permission.ACCESS_COARSE_LOCATION 2>$null
    & $AdbPath shell pm grant com.biddrive.app android.permission.POST_NOTIFICATIONS 2>$null

    # 6. Launch Application
    Write-Host "[*] Launching BidDrive App..." -ForegroundColor Cyan
    & $AdbPath shell am start -n com.biddrive.app/.MainActivity
    Write-Host "[+] BidDrive is now running on your screen!" -ForegroundColor Green
    Write-Host ""
} else {
    Write-Host ""
    Write-Host "[+] APK is ready at: $apkPath" -ForegroundColor Green
    Write-Host "[*] You can drag and drop this APK directly into BlueStacks to install!" -ForegroundColor Yellow
    Write-Host ""
}

# 7. Print Host IP for BlueStacks Backend Connectivity
$localIPs = Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -match "Wi-Fi|Ethernet" -and $_.IPAddress -notmatch "^169\." } | Select-Object -ExpandProperty IPAddress
Write-Host "========================================================" -ForegroundColor Magenta
Write-Host "[*] HOST PC NETWORK IP (For BlueStacks API Connection):" -ForegroundColor Magenta
foreach ($ip in $localIPs) {
    Write-Host "    -> http://${ip}:8080/ (Enter in Android App -> Account -> Server URL)" -ForegroundColor White
}
Write-Host "========================================================" -ForegroundColor Magenta
Write-Host ""
