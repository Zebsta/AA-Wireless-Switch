# AA Wireless Switch

Personal Android app for investigating and later toggling Android Auto wireless mode.

Current state: read-only diagnostic APK. It helps find where the Android Auto
wireless checkbox stores its state by comparing settings snapshots before and
after a manual toggle.

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

The script asks for two manual captures around the Android Auto Wireless
checkbox toggle and writes a report under `captures/`. That directory is ignored
by git.
