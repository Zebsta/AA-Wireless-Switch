package dev.local.aawirelessdiag;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;

final class AutomationController {
    static final String ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead";
    static final String ANDROID_AUTO_SETTINGS_ACTION = "com.google.android.projection.gearhead.SETTINGS";

    static final String PREFS = "aa_wireless_accessibility";
    static final String KEY_PENDING = "pending";
    static final String KEY_DESIRED_ENABLED = "desired_enabled";
    static final String KEY_LAST_KNOWN_ENABLED = "last_known_enabled";
    static final String KEY_LAST_KNOWN_VALID = "last_known_valid";
    static final String KEY_LAST_RESULT = "last_result";
    static final String KEY_COMMAND_TIME = "command_time";

    private AutomationController() {
    }

    static boolean isAccessibilityServiceEnabled(Context context) {
        ComponentName expected = new ComponentName(context, AaWirelessAccessibilityService.class);
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabled == null) {
            return false;
        }
        String expectedShort = expected.flattenToShortString();
        String expectedLong = expected.flattenToString();
        String[] services = enabled.split(":");
        for (String service : services) {
            if (expectedShort.equalsIgnoreCase(service) || expectedLong.equalsIgnoreCase(service)) {
                return true;
            }
        }
        return false;
    }

    static void openAccessibilitySettings(Context context) {
        context.startActivity(accessibilitySettingsIntent());
    }

    static void requestToggle(Context context, boolean desiredEnabled) {
        markToggleRequested(context, desiredEnabled);
        openAndroidAutoSettings(context);
        AaWirelessWidgetProvider.updateAll(context);
        AaWirelessTileService.requestTileRefresh(context);
    }

    static void markToggleRequested(Context context, boolean desiredEnabled) {
        prefs(context)
                .edit()
                .putBoolean(KEY_PENDING, true)
                .putBoolean(KEY_DESIRED_ENABLED, desiredEnabled)
                .putLong(KEY_COMMAND_TIME, System.currentTimeMillis())
                .putString(KEY_LAST_RESULT, "Opening Android Auto settings")
                .apply();
    }

    static void requestToggleOrSetup(Context context, boolean desiredEnabled) {
        if (!isAccessibilityServiceEnabled(context)) {
            openAccessibilitySettings(context);
            return;
        }
        requestToggle(context, desiredEnabled);
    }

    static void requestInverseToggleOrSetup(Context context) {
        SharedPreferences prefs = prefs(context);
        boolean current = prefs.getBoolean(KEY_LAST_KNOWN_ENABLED, false);
        requestToggleOrSetup(context, !current);
    }

    static void setLastKnownState(Context context, boolean enabled, String result) {
        prefs(context)
                .edit()
                .putBoolean(KEY_LAST_KNOWN_ENABLED, enabled)
                .putBoolean(KEY_LAST_KNOWN_VALID, true)
                .putString(KEY_LAST_RESULT, result)
                .apply();
        AaWirelessWidgetProvider.updateAll(context);
        AaWirelessTileService.requestTileRefresh(context);
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Intent accessibilitySettingsIntent() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    static Intent androidAutoSettingsIntent() {
        Intent intent = new Intent(ANDROID_AUTO_SETTINGS_ACTION);
        intent.setPackage(ANDROID_AUTO_PACKAGE);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private static Intent androidAutoPreferencesIntent() {
        Intent preferences = new Intent(Intent.ACTION_APPLICATION_PREFERENCES);
        preferences.setPackage(ANDROID_AUTO_PACKAGE);
        preferences.addCategory(Intent.CATEGORY_DEFAULT);
        preferences.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return preferences;
    }

    private static Intent androidAutoDetailsIntent() {
        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        details.setData(Uri.parse("package:" + ANDROID_AUTO_PACKAGE));
        details.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return details;
    }

    private static void openAndroidAutoSettings(Context context) {
        Intent intent = androidAutoSettingsIntent();
        try {
            context.startActivity(intent);
            return;
        } catch (ActivityNotFoundException ignored) {
            // Fall through to the standard app preferences action.
        }

        Intent preferences = androidAutoPreferencesIntent();
        try {
            context.startActivity(preferences);
            return;
        } catch (ActivityNotFoundException ignored) {
            // Fall through to app details as a last visible fallback.
        }

        context.startActivity(androidAutoDetailsIntent());
    }
}
