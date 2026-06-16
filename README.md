# AA Wireless Switch

Personal Android app for toggling Android Auto wireless mode from a main switch,
a home-screen widget, and a Quick Settings tile.

The app is not intended for Play Store publication. It is built for personal use
on phones where Android Auto exposes `Wireless Android Auto` /
`Беспроводная связь с Android Auto` in Android Auto developer settings.

## How It Works

Android does not allow a normal app, nor `adb shell`, to directly change the
Android Auto component that backs the wireless setting. The working method is an
AccessibilityService that performs the same UI steps as a user:

1. Open Android Auto settings.
2. Open the three-dot overflow menu.
3. Open `Developer settings` / `Для разработчиков`.
4. Toggle `Wireless Android Auto` / `Беспроводная связь с Android Auto`.
5. Return to the home screen.

The user must explicitly enable the Accessibility service once.

## Phone Setup

Enable Android Auto developer mode:

1. Open Android Auto settings on the phone.
2. Scroll to the bottom.
3. Tap `Version` several times until developer mode is enabled.
4. Open the three-dot menu.
5. Confirm that `Developer settings` / `Для разработчиков` is visible.
6. Open it and confirm that `Wireless Android Auto` /
   `Беспроводная связь с Android Auto` is present.

Enable the app Accessibility service:

1. Install and open `AA Wireless Switch`.
2. Tap the main switch.
3. Android should open Accessibility settings.
4. Enable `AA Wireless Switch automation`.
5. Return to the app and use the switch again.

On Samsung devices the usual path is:

```text
Settings > Accessibility > Installed apps > AA Wireless Switch automation
```

The home-screen widget is added from the launcher widget picker. The Quick
Settings tile is added from the notification shade tile editor.

## Build

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
app/build/outputs/apk/debug/AA Wireless Switch-debug.apk
```

## Repository Contents

- `app/src/main/java/dev/local/aawirelessswitch/` - app source code.
- `app/src/main/res/` - Android resources, launcher icon, widget layout, tile
  icon assets, strings, and AccessibilityService config.
- `reference/aa-wireless-logo-original.png` - original generated logo reference.
  This file is not packaged into the APK.
- `AI_CONTEXT.md` - implementation context for future maintainers or AI coding
  agents.

## Important Notes

- The implementation depends on Android Auto UI text and layout. It may need
  adjustment for other languages, OEM skins, or Android Auto versions.
- The package/application id is `dev.local.aawirelessswitch`.
- If an older development APK using another package id was installed, uninstall
  it before installing this final package.
