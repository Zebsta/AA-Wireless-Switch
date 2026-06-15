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
$AndroidAutoPackage = "com.google.android.projection.gearhead"
$WirelessReceiver = "com.google.android.apps.auto.wireless.bluetooth.WifiBluetoothReceiver"

New-Item -ItemType Directory -Force -Path $BeforeDir, $AfterDir | Out-Null

function Find-Adb {
    $ScriptDir = Split-Path -Parent $MyInvocation.ScriptName
    $Candidates = @(
        (Join-Path $ScriptDir "adb.exe"),
        (Join-Path $ScriptDir "platform-tools\adb.exe"),
        (Join-Path $RootDir "platform-tools\adb.exe"),
        "C:\platform-tools\adb.exe",
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:ANDROID_HOME\platform-tools\adb.exe",
        "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe"
    )

    foreach ($Candidate in $Candidates) {
        if (-not [string]::IsNullOrWhiteSpace($Candidate) -and (Test-Path $Candidate)) {
            return $Candidate
        }
    }

    $Command = Get-Command adb -ErrorAction SilentlyContinue
    if ($Command) {
        return $Command.Source
    }

    throw "adb.exe not found. Put platform-tools next to this script, install Android Platform Tools, or add adb.exe to PATH."
}

$Adb = Find-Adb

function Run-AdbToFile {
    param(
        [string]$Path,
        [string[]]$Arguments
    )

    $header = "`$ `"$Adb`" $($Arguments -join ' ')"
    $body = & $Adb @Arguments 2>&1 | Out-String
    Set-Content -Path $Path -Value "$header`r`n$body" -Encoding UTF8
}

function Capture {
    param(
        [string]$Phase,
        [string]$Dir
    )

    Run-AdbToFile (Join-Path $Dir "device.txt") @("shell", "getprop", "ro.product.manufacturer")

    (& $Adb shell getprop 2>&1 | Out-String) | Set-Content -Path (Join-Path $Dir "getprop.txt") -Encoding UTF8

    (& $Adb shell settings list global 2>&1 | Out-String) | Set-Content -Path (Join-Path $Dir "settings-global.txt") -Encoding UTF8
    (& $Adb shell settings list secure 2>&1 | Out-String) | Set-Content -Path (Join-Path $Dir "settings-secure.txt") -Encoding UTF8
    (& $Adb shell settings list system 2>&1 | Out-String) | Set-Content -Path (Join-Path $Dir "settings-system.txt") -Encoding UTF8

    (& $Adb shell device_config list 2>&1 | Out-String) | Set-Content -Path (Join-Path $Dir "device-config-list.txt") -Encoding UTF8
    (& $Adb shell dumpsys device_config 2>&1 | Out-String) | Set-Content -Path (Join-Path $Dir "dumpsys-device-config.txt") -Encoding UTF8

    (& $Adb shell dumpsys package com.google.android.projection.gearhead 2>&1 | Out-String) | Set-Content -Path (Join-Path $Dir "package-android-auto.txt") -Encoding UTF8
    (& $Adb shell dumpsys package com.google.android.gms 2>&1 | Out-String) | Set-Content -Path (Join-Path $Dir "package-gms.txt") -Encoding UTF8

    (& $Adb shell cmd appops get com.google.android.projection.gearhead 2>&1 | Out-String) | Set-Content -Path (Join-Path $Dir "appops-android-auto.txt") -Encoding UTF8
    (& $Adb shell cmd appops get com.google.android.gms 2>&1 | Out-String) | Set-Content -Path (Join-Path $Dir "appops-gms.txt") -Encoding UTF8

    (& $Adb shell dumpsys activity services com.google.android.projection.gearhead 2>&1 | Out-String) | Set-Content -Path (Join-Path $Dir "services-android-auto.txt") -Encoding UTF8
    (& $Adb shell dumpsys activity services com.google.android.gms 2>&1 | Out-String) | Set-Content -Path (Join-Path $Dir "services-gms.txt") -Encoding UTF8
}

function Get-AndroidAutoWirelessReceiverState {
    param([string]$PackageDumpPath)

    $Lines = Get-Content $PackageDumpPath -ErrorAction SilentlyContinue
    $InUser = $false
    $Section = ""
    foreach ($Line in $Lines) {
        if ($Line -match "User 0:") {
            $InUser = $true
            $Section = ""
            continue
        }
        if ($InUser -and $Line -match "User \d+:") {
            break
        }
        if (-not $InUser) {
            continue
        }
        $Trimmed = $Line.Trim()
        if ($Trimmed -eq "disabledComponents:") {
            $Section = "disabled"
            continue
        }
        if ($Trimmed -eq "enabledComponents:") {
            $Section = "enabled"
            continue
        }
        if ($Trimmed.Contains($WirelessReceiver)) {
            if ([string]::IsNullOrWhiteSpace($Section)) {
                return "listed"
            }
            return $Section
        }
    }
    return "not_listed"
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

Write-Host "Waiting for an Android device..."
Write-Host "Using adb: $Adb"
& $Adb wait-for-device

$Header = @()
$Header += "AA Wireless Switch read-only ADB diff"
$Header += "time=$((Get-Date).ToString('o'))"
$Header += "output=$OutDir"
$Header += ""
$Header += "[adb]"
$Header += (& $Adb version 2>&1 | Out-String).TrimEnd()
$Header += ""
$Header += "[device]"
$Header += (& $Adb shell getprop ro.product.manufacturer 2>&1 | Out-String).TrimEnd()
$Header += (& $Adb shell getprop ro.product.model 2>&1 | Out-String).TrimEnd()
$Header += (& $Adb shell getprop ro.build.version.release 2>&1 | Out-String).TrimEnd()
$Header += (& $Adb shell getprop ro.build.version.sdk 2>&1 | Out-String).TrimEnd()
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
$DiffText += "[android_auto_wireless_receiver]"
$DiffText += "component=$AndroidAutoPackage/$WirelessReceiver"
$DiffText += "before=$(Get-AndroidAutoWirelessReceiverState (Join-Path $BeforeDir "package-android-auto.txt"))"
$DiffText += "after=$(Get-AndroidAutoWirelessReceiverState (Join-Path $AfterDir "package-android-auto.txt"))"
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
