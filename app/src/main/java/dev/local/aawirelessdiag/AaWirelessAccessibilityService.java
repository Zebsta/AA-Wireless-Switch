package dev.local.aawirelessdiag;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.Locale;

public class AaWirelessAccessibilityService extends AccessibilityService {
    private static final long COMMAND_TTL_MS = 30_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int attempts;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (hasPendingCommand()) {
            scheduleAttempt(250);
        }
    }

    @Override
    public void onInterrupt() {
        saveResult("Accessibility service interrupted");
    }

    @Override
    protected void onServiceConnected() {
        saveResult("Accessibility service connected");
    }

    private boolean hasPendingCommand() {
        SharedPreferences prefs = AutomationController.prefs(this);
        if (!prefs.getBoolean(AutomationController.KEY_PENDING, false)) {
            attempts = 0;
            return false;
        }
        long commandTime = prefs.getLong(AutomationController.KEY_COMMAND_TIME, 0L);
        if (System.currentTimeMillis() - commandTime > COMMAND_TTL_MS) {
            prefs.edit()
                    .putBoolean(AutomationController.KEY_PENDING, false)
                    .putString(AutomationController.KEY_LAST_RESULT, "Command timed out")
                    .apply();
            AaWirelessWidgetProvider.updateAll(this);
            AaWirelessTileService.requestTileRefresh(this);
            attempts = 0;
            return false;
        }
        return true;
    }

    private void scheduleAttempt(long delayMillis) {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::tryToggle, delayMillis);
    }

    private void tryToggle() {
        if (!hasPendingCommand()) {
            return;
        }

        attempts++;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            retryOrFail("No active window");
            return;
        }

        SharedPreferences prefs = AutomationController.prefs(this);
        boolean desiredEnabled = prefs.getBoolean(AutomationController.KEY_DESIRED_ENABLED, true);
        ToggleTarget target = findToggleTarget(root);
        if (target == null || target.clickNode == null) {
            root.recycle();
            retryOrFail("Wireless Android Auto switch was not found");
            return;
        }

        if (target.checkedKnown && target.checked == desiredEnabled) {
            finish("Already " + (desiredEnabled ? "enabled" : "disabled"), desiredEnabled);
            root.recycle();
            return;
        }

        boolean clicked = target.clickNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        root.recycle();
        if (clicked) {
            finish("Clicked Wireless Android Auto switch to " + (desiredEnabled ? "enable" : "disable"), desiredEnabled);
        } else {
            retryOrFail("Switch click failed");
        }
    }

    private void retryOrFail(String reason) {
        if (attempts < 20) {
            scheduleAttempt(500);
            return;
        }
        fail(reason);
    }

    private void finish(String result, boolean enabled) {
        AutomationController.prefs(this)
                .edit()
                .putBoolean(AutomationController.KEY_PENDING, false)
                .apply();
        AutomationController.setLastKnownState(this, enabled, result);
        attempts = 0;
        Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
    }

    private void fail(String result) {
        AutomationController.prefs(this)
                .edit()
                .putBoolean(AutomationController.KEY_PENDING, false)
                .putString(AutomationController.KEY_LAST_RESULT, result)
                .apply();
        AaWirelessWidgetProvider.updateAll(this);
        AaWirelessTileService.requestTileRefresh(this);
        attempts = 0;
        Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
    }

    private void saveResult(String result) {
        AutomationController.prefs(this)
                .edit()
                .putString(AutomationController.KEY_LAST_RESULT, result)
                .apply();
    }

    private ToggleTarget findToggleTarget(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo label = findWirelessLabel(root);
        if (label == null) {
            return null;
        }

        AccessibilityNodeInfo row = findClickableAncestor(label);
        AccessibilityNodeInfo checkable = null;
        AccessibilityNodeInfo cursor = AccessibilityNodeInfo.obtain(label);
        for (int i = 0; i < 5 && cursor != null; i++) {
            checkable = findCheckableNode(cursor);
            if (checkable != null) {
                break;
            }
            AccessibilityNodeInfo parent = cursor.getParent();
            cursor.recycle();
            cursor = parent;
        }
        if (cursor != null) {
            cursor.recycle();
        }

        ToggleTarget target = new ToggleTarget();
        if (checkable != null) {
            target.clickNode = checkable.isClickable() ? checkable : findClickableAncestor(checkable);
            if (target.clickNode == null) {
                target.clickNode = row;
            }
            target.checkedKnown = true;
            target.checked = checkable.isChecked();
            checkable.recycle();
        } else {
            target.clickNode = row;
            target.checkedKnown = false;
        }
        label.recycle();
        return target.clickNode == null ? null : target;
    }

    private AccessibilityNodeInfo findWirelessLabel(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        if (matchesWirelessAndroidAuto(text) || matchesWirelessAndroidAuto(description)) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = findWirelessLabel(child);
            if (child != null) {
                child.recycle();
            }
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean matchesWirelessAndroidAuto(CharSequence value) {
        if (value == null) {
            return false;
        }
        String text = value.toString().toLowerCase(Locale.ROOT);
        boolean mentionsAndroidAuto = text.contains("android auto");
        boolean mentionsWireless = text.contains("wireless")
                || text.contains("беспровод")
                || text.contains("wi-fi")
                || text.contains("wifi");
        return mentionsAndroidAuto && mentionsWireless;
    }

    private AccessibilityNodeInfo findCheckableNode(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        if (node.isCheckable() || isSwitchClass(node)) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = findCheckableNode(child);
            if (child != null) {
                child.recycle();
            }
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean isSwitchClass(AccessibilityNodeInfo node) {
        CharSequence className = node.getClassName();
        if (className == null) {
            return false;
        }
        String value = className.toString().toLowerCase(Locale.ROOT);
        return value.contains("switch") || value.contains("checkbox");
    }

    private AccessibilityNodeInfo findClickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        for (int i = 0; i < 8 && current != null; i++) {
            if (current.isClickable() && current.isEnabled()) {
                return current;
            }
            AccessibilityNodeInfo parent = current.getParent();
            current.recycle();
            current = parent;
        }
        return null;
    }

    private static final class ToggleTarget {
        AccessibilityNodeInfo clickNode;
        boolean checkedKnown;
        boolean checked;
    }
}
