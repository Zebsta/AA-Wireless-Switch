package dev.local.aawirelessswitch;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
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
    private static final long CLOSE_SETTINGS_BACK_STEP_MS = 450L;
    private static final long CLOSE_SETTINGS_HOME_STEP_MS = 650L;
    private static final int OPEN_DEVELOPER_NOT_FOUND = 0;
    private static final int OPEN_DEVELOPER_CLICKED = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int attempts;
    private long activeCommandTime;
    private boolean overflowMenuOpened;
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
            overflowMenuOpened = false;
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
            overflowMenuOpened = false;
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
                overflowMenuOpened = false;
                root.recycle();
                scheduleAttempt(800);
                return;
            }

            if (!overflowMenuOpened) {
                if (openOverflowMenu(root)) {
                    overflowMenuOpened = true;
                    root.recycle();
                    scheduleAttempt(700);
                    return;
                }
                root.recycle();
                retryOrFail("Android Auto overflow menu was not found");
                return;
            }

            overflowMenuOpened = false;
            root.recycle();
            retryOrFail("Android Auto overflow menu did not open");
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
        overflowMenuOpened = false;
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
        overflowMenuOpened = false;
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
        handler.postDelayed(() -> {
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            handler.postDelayed(() -> {
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                handler.postDelayed(
                        () -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME),
                        CLOSE_SETTINGS_HOME_STEP_MS
                );
            }, CLOSE_SETTINGS_BACK_STEP_MS);
        }, CLOSE_SETTINGS_DELAY_MS);
    }

    private boolean openOverflowMenu(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo button = findOverflowMenuNode(root);
        if (button != null) {
            AccessibilityNodeInfo clickable = findClickableAncestor(button);
            boolean clicked;
            if (clickable != null) {
                clicked = performClick(clickable);
                clickable.recycle();
            } else {
                clicked = tapNodeCenter(button);
            }
            button.recycle();
            if (clicked) {
                return true;
            }
        }
        AccessibilityNodeInfo topRightButton = findTopRightClickableNode(root);
        if (topRightButton != null) {
            boolean clicked = performClick(topRightButton);
            topRightButton.recycle();
            if (clicked) {
                return true;
            }
        }
        return tapTopRight(root);
    }

    private int openDeveloperMenu(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo label = findDeveloperMenuLabel(root);
        if (label == null) {
            return OPEN_DEVELOPER_NOT_FOUND;
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

    private AccessibilityNodeInfo findOverflowMenuNode(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        if (matchesOverflowMenu(text) || matchesOverflowMenu(description)) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = findOverflowMenuNode(child);
            if (child != null) {
                child.recycle();
            }
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findTopRightClickableNode(AccessibilityNodeInfo root) {
        Rect rootBounds = new Rect();
        root.getBoundsInScreen(rootBounds);
        if (rootBounds.isEmpty()) {
            return null;
        }
        TopRightCandidate candidate = new TopRightCandidate(rootBounds);
        collectTopRightClickableNode(root, candidate);
        return candidate.node;
    }

    private void collectTopRightClickableNode(AccessibilityNodeInfo node, TopRightCandidate candidate) {
        if (node == null) {
            return;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (node.isClickable() && node.isEnabled() && candidate.isBetter(bounds)) {
            if (candidate.node != null) {
                candidate.node.recycle();
            }
            candidate.node = AccessibilityNodeInfo.obtain(node);
            candidate.bounds.set(bounds);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            collectTopRightClickableNode(child, candidate);
            if (child != null) {
                child.recycle();
            }
        }
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

    private boolean matchesOverflowMenu(CharSequence value) {
        if (value == null) {
            return false;
        }
        String text = value.toString().toLowerCase(Locale.ROOT);
        return text.contains("ещё")
                || text.contains("еще")
                || text.contains("more options")
                || text.equals("more")
                || text.contains("overflow")
                || text.contains("menu");
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

    private boolean tapTopRight(AccessibilityNodeInfo root) {
        if (root == null || Build.VERSION.SDK_INT < 24) {
            return false;
        }
        Rect bounds = new Rect();
        root.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) {
            return false;
        }
        float x = bounds.right - 28f * getResources().getDisplayMetrics().density;
        float y = bounds.top + 28f * getResources().getDisplayMetrics().density;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 80))
                .build();
        return dispatchGesture(gesture, null, null);
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

    private static final class TopRightCandidate {
        final Rect rootBounds;
        final Rect bounds = new Rect();
        AccessibilityNodeInfo node;

        TopRightCandidate(Rect rootBounds) {
            this.rootBounds = rootBounds;
        }

        boolean isBetter(Rect candidateBounds) {
            if (candidateBounds.isEmpty()) {
                return false;
            }
            int rootWidth = rootBounds.width();
            int rootHeight = rootBounds.height();
            boolean inTopRight = candidateBounds.centerX() > rootBounds.left + rootWidth * 2 / 3
                    && candidateBounds.centerY() < rootBounds.top + rootHeight / 4;
            if (!inTopRight) {
                return false;
            }
            int width = candidateBounds.width();
            int height = candidateBounds.height();
            if (width <= 0 || height <= 0 || width > rootWidth / 3 || height > rootHeight / 5) {
                return false;
            }
            if (node == null) {
                return true;
            }
            if (candidateBounds.right != bounds.right) {
                return candidateBounds.right > bounds.right;
            }
            return candidateBounds.top < bounds.top;
        }
    }
}
