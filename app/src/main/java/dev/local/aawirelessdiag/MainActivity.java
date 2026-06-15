package dev.local.aawirelessdiag;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
    private ImageView controllerIcon;
    private Switch mainSwitch;
    private TextView status;
    private boolean binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private LinearLayout buildView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextAppearance(android.R.style.TextAppearance_Material_Headline);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        controllerIcon = new ImageView(this);
        controllerIcon.setAdjustViewBounds(true);
        controllerIcon.setContentDescription(getString(R.string.main_switch_label));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(220), dp(220));
        iconParams.setMargins(0, dp(28), 0, dp(10));
        root.addView(controllerIcon, iconParams);

        mainSwitch = new Switch(this);
        mainSwitch.setText(R.string.main_switch_label);
        mainSwitch.setTextAppearance(android.R.style.TextAppearance_Material_Title);
        mainSwitch.setGravity(Gravity.CENTER);
        mainSwitch.setTextSize(22);
        mainSwitch.setScaleX(1.45f);
        mainSwitch.setScaleY(1.45f);
        mainSwitch.setMinHeight(dp(64));
        mainSwitch.setPadding(0, dp(18), 0, dp(22));
        mainSwitch.setOnCheckedChangeListener(this::onSwitchChanged);
        root.addView(mainSwitch, new LinearLayout.LayoutParams(-2, -2));

        status = new TextView(this);
        status.setTextAppearance(android.R.style.TextAppearance_Material_Body1);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        return root;
    }

    private void onSwitchChanged(CompoundButton button, boolean checked) {
        if (binding) {
            return;
        }
        if (!AutomationController.isAccessibilityServiceEnabled(this)) {
            AutomationController.openAccessibilitySettings(this);
            refresh();
            return;
        }
        AutomationController.requestToggle(this, checked);
        refresh();
    }

    private void refresh() {
        SharedPreferences prefs = AutomationController.prefs(this);
        boolean known = prefs.getBoolean(AutomationController.KEY_LAST_KNOWN_VALID, false);
        boolean enabled = prefs.getBoolean(AutomationController.KEY_LAST_KNOWN_ENABLED, false);
        boolean pending = prefs.getBoolean(AutomationController.KEY_PENDING, false);
        boolean serviceEnabled = AutomationController.isAccessibilityServiceEnabled(this);

        binding = true;
        mainSwitch.setChecked(enabled);
        mainSwitch.setEnabled(true);
        controllerIcon.setImageResource(enabled ? R.drawable.controller_on : R.drawable.controller_off);
        binding = false;

        if (!serviceEnabled) {
            status.setText(R.string.status_accessibility_required);
        } else if (pending) {
            status.setText(R.string.status_pending);
        } else if (!known) {
            status.setText(R.string.status_unknown);
        } else {
            status.setText(enabled ? R.string.status_enabled : R.string.status_disabled);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
