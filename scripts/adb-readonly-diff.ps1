param(
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
if ([string]::IsNullOrWhiteSpace($OutDir)) {
    $Stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutDir = Join-Path $RootDir "captures\adb-$Stamp"
}

$BeforeDir = Join-Path $OutDir "before"
$AfterDir = Join-Path $OutDir "after"
$Report = Join-Path $OutDir "report.txt"

New-Item -ItemType Directory -Force -Path $BeforeDir, $AfterDir | Out-Null

function Assert-Adb {
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $adb) {
        throw "adb not found in PATH. Install Android Platform Tools or run from a terminal where adb.exe is available."
    }
}

function Run-AdbToFile {
    param(
        [string]$Path,
        [string[]]$Arguments
    )

    $header = "`$ adb $($Arguments -join ' ')"
    $body = & adb @Arguments 2>&1 | Out-String
    Set-Content -Path $Path -Value "$header`r`n$body" -Encoding UTF8
}

function Capture {
    param(
        [string]$Phase,
        [string]$Dir
    )

    Run-AdbToFile (Join-Path $Dir "device.txt") @("shell", "getprop", "ro.product.manufacturer")

    & adb shell getprop > (Join-Path $Dir "getprop.txt") 2>&1

    & adb shell settings list global > (Join-Path $Dir "settings-global.txt") 2>&1
    & adb shell settings list secure > (Join-Path $Dir "settings-secure.txt") 2>&1
    & adb shell settings list system > (Join-Path $Dir "settings-system.txt") 2>&1

    & adb shell device_config list > (Join-Path $Dir "device-config-list.txt") 2>&1
    & adb shell dumpsys device_config > (Join-Path $Dir "dumpsys-device-config.txt") 2>&1

    & adb shell dumpsys package com.google.android.projection.gearhead > (Join-Path $Dir "package-android-auto.txt") 2>&1
    & adb shell dumpsys package com.google.android.gms > (Join-Path $Dir "package-gms.txt") 2>&1

    & adb shell cmd appops get com.google.android.projection.gearhead > (Join-Path $Dir "appops-android-auto.txt") 2>&1
    & adb shell cmd appops get com.google.android.gms > (Join-Path $Dir "appops-gms.txt") 2>&1

    & adb shell dumpsys activity services com.google.android.projection.gearhead > (Join-Path $Dir "services-android-auto.txt") 2>&1
    & adb shell dumpsys activity services com.google.android.gms > (Join-Path $Dir "services-gms.txt") 2>&1
}

function Read-SortedCombined {
    param([string]$Dir)

    Get-Content `
        (Join-Path $Dir "settings-global.txt"), `
        (Join-Path $Dir "settings-secure.txt"), `
        (Join-Path $Dir "settings-system.txt"), `
        (Join-Path $Dir "device-config-list.txt") `
        -ErrorAction SilentlyContinue | Sort-Object
}

Assert-Adb

Write-Host "Waiting for an Android device..."
& adb wait-for-device

$Header = @()
$Header += "AA Wireless Switch read-only ADB diff"
$Header += "time=$((Get-Date).ToString('o'))"
$Header += "output=$OutDir"
$Header += ""
$Header += "[adb]"
$Header += (& adb version 2>&1 | Out-String).TrimEnd()
$Header += ""
$Header += "[device]"
$Header += (& adb shell getprop ro.product.manufacturer 2>&1 | Out-String).TrimEnd()
$Header += (& adb shell getprop ro.product.model 2>&1 | Out-String).TrimEnd()
$Header += (& adb shell getprop ro.build.version.release 2>&1 | Out-String).TrimEnd()
$Header += (& adb shell getprop ro.build.version.sdk 2>&1 | Out-String).TrimEnd()
$Header += ""
Set-Content -Path $Report -Value ($Header -join "`r`n") -Encoding UTF8

Write-Host ""
Write-Host "1. Set Android Auto Wireless to the first state manually."
Read-Host "Press Enter to capture BEFORE"
Capture "before" $BeforeDir

Write-Host ""
Write-Host "2. Toggle Android Auto Wireless manually."
Read-Host "Press Enter to capture AFTER"
Capture "after" $AfterDir

$DiffText = @()
$DiffText += ""
$DiffText += "[settings/device_config diff]"
$DiffText += (Compare-Object (Read-SortedCombined $BeforeDir) (Read-SortedCombined $AfterDir) | Out-String).TrimEnd()
$DiffText += ""
$DiffText += "[android auto related changes]"
$Related = Compare-Object `
    (Get-ChildItem -Path $BeforeDir -File | ForEach-Object { Get-Content $_.FullName -ErrorAction SilentlyContinue }) `
    (Get-ChildItem -Path $AfterDir -File | ForEach-Object { Get-Content $_.FullName -ErrorAction SilentlyContinue }) |
    Out-String
$DiffText += (($Related -split "`r?`n") | Select-String -Pattern "android_auto|android auto|gearhead|projection|wireless|wifi|car|auto" | ForEach-Object { $_.Line }) -join "`r`n"

Add-Content -Path $Report -Value ($DiffText -join "`r`n") -Encoding UTF8

Write-Host ""
Write-Host "Report written to:"
Write-Host $Report
Write-Host ""
Write-Host "Send this file back for analysis."
