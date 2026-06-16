package dev.local.aawirelessswitch;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class AaWirelessWidgetProvider extends AppWidgetProvider {
    static final String ACTION_TOGGLE = "dev.local.aawirelessswitch.action.WIDGET_TOGGLE";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, buildViews(context));
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            AutomationController.requestInverseToggleOrSetup(context);
            updateAll(context);
        }
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, AaWirelessWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) {
            manager.updateAppWidget(id, buildViews(context));
        }
    }

    private static RemoteViews buildViews(Context context) {
        SharedPreferences prefs = AutomationController.prefs(context);
        boolean known = prefs.getBoolean(AutomationController.KEY_LAST_KNOWN_VALID, false);
        boolean enabled = prefs.getBoolean(AutomationController.KEY_LAST_KNOWN_ENABLED, false);
        boolean pending = prefs.getBoolean(AutomationController.KEY_PENDING, false);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_aa_wireless);
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title));
        if (pending) {
            views.setTextViewText(R.id.widget_state, context.getString(R.string.widget_pending));
        } else if (!known) {
            views.setTextViewText(R.id.widget_state, context.getString(R.string.widget_unknown));
        } else {
            views.setTextViewText(R.id.widget_state, context.getString(enabled ? R.string.widget_on : R.string.widget_off));
        }
        views.setTextViewText(R.id.widget_toggle, context.getString(enabled ? R.string.widget_turn_off : R.string.widget_turn_on));

        Intent intent = new Intent(context, AaWirelessWidgetProvider.class);
        intent.setAction(ACTION_TOGGLE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                10,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_toggle, pendingIntent);
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);
        return views;
    }
}
