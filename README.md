# AA Wireless Switch

Personal Android app for investigating and later toggling Android Auto wireless mode.

Current state: read-only diagnostic APK. It helps find where the Android Auto
wireless checkbox stores its state by comparing settings snapshots before and
after a manual toggle.

## Current findings

The Android Auto Wireless checkbox changes the user-0 component state of:

```text
com.google.android.projection.gearhead/com.google.android.apps.auto.wireless.bluetooth.WifiBluetoothReceiver
```

Observed mapping:

```text
enabled  = Android Auto Wireless enabled
disabled = Android Auto Wireless disabled
```

Direct ADB shell control is blocked on the tested Samsung Android 16 device:

```text
java.lang.SecurityException: Shell cannot change component state
```

Because Shizuku normally executes with shell-level privileges, this also rules
out a Shizuku implementation based on `pm enable` / `pm disable-user` for this
component. Remaining implementation paths are root/system privileges or
Accessibility automation of the Android Auto settings UI.

## Safety

The diagnostic app does not request Android permissions and does not write
system settings. It only reads publicly accessible settings through
`ContentResolver`, package versions, and device metadata.

## How to use

1. Install the APK on the phone.
2. Open the app.
3. Set the Android Auto wireless checkbox to the first state manually.
4. Tap `1. Снять ДО`.
5. Manually switch `Беспроводная связь с Android Auto`.
6. Return to the app and tap `2. Снять ПОСЛЕ`.
7. Tap `3. Показать diff для отправки`.
8. Send back the report text.

## Build

Use this repository directory as the working directory for project files and
generated artifacts:

```bash
cd /home/user/Distr/Git/AA-Wireless-Switch
```

Requirements:

- JDK 17
- Android SDK with platform 35
- Gradle 8.10.2 or compatible

Build debug APK:

```bash
gradle assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Second-stage read-only diagnosis

If the in-app diff is empty, run the ADB read-only diff from this repository:

```bash
scripts/adb-readonly-diff.sh
```

On Windows, run the PowerShell version:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\adb-readonly-diff.ps1
```

The PowerShell script looks for `adb.exe` in PATH, `C:\platform-tools`, the
standard Android SDK location, and `platform-tools\adb.exe` next to the script
or repository root.

The script asks for two manual captures around the Android Auto Wireless
checkbox toggle and writes a report under `captures/`. That directory is ignored
by git.
