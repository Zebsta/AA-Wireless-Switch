# AI Context

This file captures the implementation context needed to continue development in
another environment.

## Product Goal

`AA Wireless Switch` is a personal Android app that toggles Android Auto wireless
mode without manually navigating through Android Auto developer settings. It
provides:

- a single switch in the main app;
- a home-screen widget;
- a Quick Settings tile.

## Current Implementation

The app uses `AaWirelessAccessibilityService` to automate Android Auto settings.
The expected flow is:

1. A user action calls `AutomationController.markToggleRequested(...)`.
2. Android Auto settings are opened.
3. Accessibility opens the three-dot overflow menu.
4. Accessibility selects `Developer settings` / `Для разработчиков`.
5. Accessibility finds `Wireless Android Auto` /
   `Беспроводная связь с Android Auto`.
6. Accessibility toggles the switch if needed.
7. Accessibility returns to the home screen.

The service intentionally always goes through the Android Auto developer menu.
Do not add logic that toggles from the Android Auto main settings screen.

## Known Platform Constraint

Direct component toggling was tested and blocked by Android:

```text
pm enable --user 0 com.google.android.projection.gearhead/com.google.android.apps.auto.wireless.bluetooth.WifiBluetoothReceiver
pm disable-user --user 0 com.google.android.projection.gearhead/com.google.android.apps.auto.wireless.bluetooth.WifiBluetoothReceiver
```

Both failed with:

```text
java.lang.SecurityException: Shell cannot change component state
```

Because of that, a normal app, `adb shell`, and Shizuku-style shell privileges
are not a reliable implementation path for this component. Root/system
privileges were intentionally abandoned.

## Important Files

- `app/src/main/java/dev/local/aawirelessswitch/AaWirelessAccessibilityService.java`
  - Android Auto UI automation.
- `app/src/main/java/dev/local/aawirelessswitch/AutomationController.java`
  - shared state and entry points for main app, widget, and tile.
- `app/src/main/java/dev/local/aawirelessswitch/AaWirelessTileService.java`
  - Quick Settings tile.
- `app/src/main/java/dev/local/aawirelessswitch/AaWirelessWidgetProvider.java`
  - home-screen widget.
- `app/src/main/res/xml/aawireless_accessibility_service.xml`
  - AccessibilityService declaration.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
  - adaptive launcher icon.
- `reference/aa-wireless-logo-original.png`
  - original logo reference, not included in APK.

## Build

Use JDK 17, Android SDK platform 35, and Gradle 8.10.2 or compatible.

```bash
gradle assembleDebug
```

Expected APK path:

```text
app/build/outputs/apk/debug/AA Wireless Switch-debug.apk
```

## UI/Asset Notes

The app icon uses adaptive-icon background plus a constrained foreground so the
logo fits inside launcher masks. The Quick Settings tile uses a separate
`aa_logo_black_tile` bitmap set because it needs tighter visual bounds than the
launcher foreground.

The widget intentionally does not show the logo: adding it previously compressed
the widget layout.
