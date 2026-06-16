package dev.local.aawirelessdiag;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class AaWirelessTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        refreshTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!AutomationController.isAccessibilityServiceEnabled(this)) {
            launchAndCollapse(AutomationController.accessibilitySettingsIntent(), 30);
            return;
        }
        SharedPreferences prefs = AutomationController.prefs(this);
        boolean current = prefs.getBoolean(AutomationController.KEY_LAST_KNOWN_ENABLED, false);
        AutomationController.markToggleRequested(this, !current);
        launchAndCollapse(AutomationController.androidAutoSettingsIntent(), 31);
        AaWirelessWidgetProvider.updateAll(this);
        refreshTile();
    }

    private void refreshTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        SharedPreferences prefs = AutomationController.prefs(this);
        boolean known = prefs.getBoolean(AutomationController.KEY_LAST_KNOWN_VALID, false);
        boolean enabled = prefs.getBoolean(AutomationController.KEY_LAST_KNOWN_ENABLED, false);
        boolean pending = prefs.getBoolean(AutomationController.KEY_PENDING, false);

        tile.setLabel(getString(R.string.tile_label));
        tile.setIcon(Icon.createWithResource(this, R.drawable.aa_logo_black));
        if (Build.VERSION.SDK_INT >= 29) {
            if (pending) {
                tile.setSubtitle(getString(R.string.tile_pending));
            } else if (!AutomationController.isAccessibilityServiceEnabled(this)) {
                tile.setSubtitle(getString(R.string.tile_setup));
            } else if (!known) {
                tile.setSubtitle(getString(R.string.tile_unknown));
            } else {
                tile.setSubtitle(getString(enabled ? R.string.tile_on : R.string.tile_off));
            }
        }
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    static void requestTileRefresh(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            TileService.requestListeningState(
                    context,
                    new android.content.ComponentName(context, AaWirelessTileService.class)
            );
        }
    }

    private void launchAndCollapse(Intent intent, int requestCode) {
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
