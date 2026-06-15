package dev.local.aawirelessdiag;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.Locale;

public class AaWirelessAccessibilityService extends AccessibilityService {
    private static final long COMMAND_TTL_MS = 45_000L;
    private static final long CLOSE_SETTINGS_DELAY_MS = 700L;
    private static final int OPEN_DEVELOPER_NOT_FOUND = 0;
    private static final int OPEN_DEVELOPER_CLICKED = 1;
    private static final int OPEN_DEVELOPER_SCROLLED = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int attempts;
    private long activeCommandTime;
    private boolean developerMenuOpened;

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
        if (commandTime != activeCommandTime) {
            activeCommandTime = commandTime;
            attempts = 0;
            developerMenuOpened = false;
        }
        if (System.currentTimeMillis() - commandTime > COMMAND_TTL_MS) {
            prefs.edit()
                    .putBoolean(AutomationController.KEY_PENDING, false)
                    .putString(AutomationController.KEY_LAST_RESULT, "Command timed out")
                    .apply();
            AaWirelessWidgetProvider.updateAll(this);
            AaWirelessTileService.requestTileRefresh(this);
            attempts = 0;
            developerMenuOpened = false;
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
        if (!developerMenuOpened) {
            int developerAction = openDeveloperMenu(root);
            if (developerAction == OPEN_DEVELOPER_CLICKED) {
                developerMenuOpened = true;
                root.recycle();
                scheduleAttempt(800);
                return;
            }
            if (developerAction == OPEN_DEVELOPER_SCROLLED) {
                root.recycle();
                scheduleAttempt(700);
                return;
            }
            root.recycle();
            retryOrFail("Android Auto developer settings was not found");
            return;
        }

        ToggleTarget target = findToggleTarget(root);
        if (target == null || target.clickNode == null) {
            root.recycle();
            retryOrFail("Wireless Android Auto switch was not found");
            return;
        }

        if (target.checkedKnown && target.checked == desiredEnabled) {
            finish("Already " + (desiredEnabled ? "enabled" : "disabled"), desiredEnabled);
            closeSettingsAfterSuccess();
            root.recycle();
            return;
        }

        boolean clicked = performClick(target.clickNode);
        root.recycle();
        if (clicked) {
            finish("Clicked Wireless Android Auto switch to " + (desiredEnabled ? "enable" : "disable"), desiredEnabled);
            closeSettingsAfterSuccess();
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
        developerMenuOpened = false;
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
        developerMenuOpened = false;
        Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
    }

    private void saveResult(String result) {
        AutomationController.prefs(this)
                .edit()
                .putString(AutomationController.KEY_LAST_RESULT, result)
                .apply();
    }

    private void closeSettingsAfterSuccess() {
        handler.postDelayed(
                () -> {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                },
                CLOSE_SETTINGS_DELAY_MS
        );
    }

    private int openDeveloperMenu(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo label = findDeveloperMenuLabel(root);
        if (label == null) {
            return scrollForward(root) ? OPEN_DEVELOPER_SCROLLED : OPEN_DEVELOPER_NOT_FOUND;
        }
        AccessibilityNodeInfo clickable = findClickableAncestor(label);
        boolean clicked;
        if (clickable != null) {
            clicked = performClick(clickable);
            clickable.recycle();
        } else {
            clicked = tapNodeCenter(label);
        }
        label.recycle();
        return clicked ? OPEN_DEVELOPER_CLICKED : OPEN_DEVELOPER_NOT_FOUND;
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

    private AccessibilityNodeInfo findDeveloperMenuLabel(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        if (matchesDeveloperMenu(text) || matchesDeveloperMenu(description)) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = findDeveloperMenuLabel(child);
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

    private boolean matchesDeveloperMenu(CharSequence value) {
        if (value == null) {
            return false;
        }
        String text = value.toString().toLowerCase(Locale.ROOT);
        return text.contains("для разработ")
                || text.contains("разработчик")
                || text.contains("developer");
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

    private boolean performClick(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true;
        }
        return tapNodeCenter(node);
    }

    private boolean tapNodeCenter(AccessibilityNodeInfo node) {
        if (node == null || Build.VERSION.SDK_INT < 24) {
            return false;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) {
            return false;
        }
        Path path = new Path();
        path.moveTo(bounds.centerX(), bounds.centerY());
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 80))
                .build();
        return dispatchGesture(gesture, null, null);
    }

    private boolean scrollForward(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        if (node.isScrollable() && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            boolean scrolled = scrollForward(child);
            if (child != null) {
                child.recycle();
            }
            if (scrolled) {
                return true;
            }
        }
        return false;
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
