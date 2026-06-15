# AA Wireless Switch

Personal Android app for investigating and later toggling Android Auto wireless mode.

Current state: experimental AccessibilityService APK plus read-only diagnostics.
The accessibility path opens Android Auto settings and tries to click the
Wireless Android Auto switch in the same UI a user would operate manually.

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
3. Tap `Включить службу Accessibility`.
4. Enable `AA Wireless Switch automation` in Android Accessibility settings.
5. Return to the app.
6. Tap `Включить Android Auto Wireless` or `Выключить Android Auto Wireless`.
7. The app opens Android Auto settings; the accessibility service tries to find
   and click the Wireless Android Auto switch.

This method depends on Android Auto UI text and layout. It may need adjustment
for different languages or Android Auto versions.

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
