package dev.local.aawirelessdiag;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends Activity {
    private static final String PREFS = "snapshots";
    private static final String KEY_BEFORE = "before";
    private static final String KEY_AFTER = "after";

    private TextView output;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(buildView());
        showInstructions();
    }

    private View buildView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(12));
        root.setBackgroundColor(0xFFF7F8FA);

        TextView title = new TextView(this);
        title.setText("AA Wireless Diagnostic");
        title.setTextSize(22);
        title.setTextColor(0xFF17211E);
        title.setGravity(Gravity.START);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Read-only диагностика. Системные настройки не изменяются.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFF52615D);
        subtitle.setPadding(0, dp(4), 0, dp(12));
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));

        addButton(buttons, "1. Снять ДО", v -> saveSnapshot(KEY_BEFORE));
        addButton(buttons, "2. Снять ПОСЛЕ", v -> saveSnapshot(KEY_AFTER));
        addButton(buttons, "Показать diff", v -> showDiff());
        addButton(buttons, "Текущий снимок", v -> showCurrentSnapshot());
        addButton(buttons, "Копировать отчет", v -> copyReport());
        addButton(buttons, "Отправить отчет", v -> shareReport());

        ScrollView scroll = new ScrollView(this);
        output = new TextView(this);
        output.setTextSize(12);
        output.setTextColor(0xFF1B1F1D);
        output.setTextIsSelectable(true);
        output.setPadding(0, dp(12), 0, 0);
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        return root;
    }

    private void addButton(LinearLayout parent, String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.setMargins(0, 0, 0, dp(8));
        parent.addView(button, lp);
    }

    private void showInstructions() {
        output.setText(
                "Как пользоваться:\n\n"
                        + "1. Установи APK и открой приложение.\n"
                        + "2. Выставь Android Auto Wireless в первом состоянии.\n"
                        + "3. Нажми \"1. Снять ДО\".\n"
                        + "4. Вручную переключи чекбокс \"Беспроводная связь с Android Auto\".\n"
                        + "5. Вернись сюда и нажми \"2. Снять ПОСЛЕ\".\n"
                        + "6. Нажми \"Показать diff\" и отправь отчет.\n\n"
                        + "Приложение не запрашивает опасные разрешения и не записывает системные настройки.\n"
        );
    }

    private void saveSnapshot(String key) {
        String snapshot = collectSnapshot();
        prefs.edit().putString(key, snapshot).apply();
        output.setText(snapshot);
        Toast.makeText(this, key.equals(KEY_BEFORE) ? "Снимок ДО сохранен" : "Снимок ПОСЛЕ сохранен", Toast.LENGTH_SHORT).show();
    }

    private void showCurrentSnapshot() {
        output.setText(collectSnapshot());
    }

    private void showDiff() {
        String before = prefs.getString(KEY_BEFORE, "");
        String after = prefs.getString(KEY_AFTER, "");
        if (before.isEmpty() || after.isEmpty()) {
            output.setText("Нужно сначала сделать оба снимка: ДО и ПОСЛЕ.");
            return;
        }
        output.setText(buildDiffReport(before, after));
    }

    private void copyReport() {
        String text = output.getText().toString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("AA Wireless Diagnostic", text));
        Toast.makeText(this, "Отчет скопирован", Toast.LENGTH_SHORT).show();
    }

    private void shareReport() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "AA Wireless Diagnostic report");
        intent.putExtra(Intent.EXTRA_TEXT, output.getText().toString());
        startActivity(Intent.createChooser(intent, "Отправить отчет"));
    }

    private String collectSnapshot() {
        StringBuilder sb = new StringBuilder();
        sb.append("AA Wireless Diagnostic snapshot\n");
        sb.append("time=").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date())).append('\n');
        sb.append("appVersion=0.1.0\n\n");

        appendDeviceInfo(sb);
        appendPackageInfo(sb, "com.google.android.projection.gearhead", "Android Auto");
        appendPackageInfo(sb, "com.google.android.gms", "Google Play Services");

        Map<String, String> all = new TreeMap<>();
        readSettingsTable("secure", Settings.Secure.CONTENT_URI, all);
        readSettingsTable("global", Settings.Global.CONTENT_URI, all);
        readSettingsTable("system", Settings.System.CONTENT_URI, all);

        sb.append("\n[interesting_keys]\n");
        int interestingCount = 0;
        for (Map.Entry<String, String> entry : all.entrySet()) {
            String lower = entry.getKey().toLowerCase(Locale.US);
            if (lower.contains("auto")
                    || lower.contains("car")
                    || lower.contains("wireless")
                    || lower.contains("wifi")
                    || lower.contains("projection")
                    || lower.contains("gearhead")
                    || lower.contains("android_auto")) {
                sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
                interestingCount++;
            }
        }
        if (interestingCount == 0) {
            sb.append("(none)\n");
        }

        sb.append("\n[all_settings]\n");
        for (Map.Entry<String, String> entry : all.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }

        return sb.toString();
    }

    private void appendDeviceInfo(StringBuilder sb) {
        sb.append("[device]\n");
        sb.append("sdk=").append(Build.VERSION.SDK_INT).append('\n');
        sb.append("release=").append(Build.VERSION.RELEASE).append('\n');
        sb.append("manufacturer=").append(Build.MANUFACTURER).append('\n');
        sb.append("brand=").append(Build.BRAND).append('\n');
        sb.append("model=").append(Build.MODEL).append('\n');
        sb.append("fingerprint=").append(Build.FINGERPRINT).append('\n');
    }

    private void appendPackageInfo(StringBuilder sb, String packageName, String label) {
        sb.append("\n[package:").append(packageName).append("]\n");
        try {
            PackageInfo info = getPackageManager().getPackageInfo(packageName, 0);
            sb.append("label=").append(label).append('\n');
            sb.append("versionName=").append(info.versionName).append('\n');
            if (Build.VERSION.SDK_INT >= 28) {
                sb.append("versionCode=").append(info.getLongVersionCode()).append('\n');
            } else {
                sb.append("versionCode=").append(info.versionCode).append('\n');
            }
        } catch (PackageManager.NameNotFoundException e) {
            sb.append("not_found=true\n");
        } catch (RuntimeException e) {
            sb.append("error=").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append('\n');
        }
    }

    private void readSettingsTable(String tableName, Uri uri, Map<String, String> out) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{"name", "value"}, null, null, null);
            if (cursor == null) {
                out.put(tableName + ".__query_result", "null");
                return;
            }
            int nameIndex = cursor.getColumnIndex("name");
            int valueIndex = cursor.getColumnIndex("value");
            while (cursor.moveToNext()) {
                String name = nameIndex >= 0 ? cursor.getString(nameIndex) : null;
                String value = valueIndex >= 0 ? cursor.getString(valueIndex) : null;
                if (name != null) {
                    out.put(tableName + "." + name, value == null ? "" : value);
                }
            }
            out.put(tableName + ".__row_count", String.valueOf(cursor.getCount()));
        } catch (SecurityException e) {
            out.put(tableName + ".__security_error", e.getMessage());
        } catch (RuntimeException e) {
            out.put(tableName + ".__error", e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String buildDiffReport(String before, String after) {
        Map<String, String> beforeMap = parseSettings(before);
        Map<String, String> afterMap = parseSettings(after);
        List<String> keys = new ArrayList<>();
        keys.addAll(beforeMap.keySet());
        for (String key : afterMap.keySet()) {
            if (!beforeMap.containsKey(key)) {
                keys.add(key);
            }
        }
        Collections.sort(keys);

        StringBuilder sb = new StringBuilder();
        sb.append("AA Wireless Diagnostic diff\n\n");
        sb.append("[changed]\n");
        int changed = 0;
        for (String key : keys) {
            String oldValue = beforeMap.get(key);
            String newValue = afterMap.get(key);
            if (oldValue == null || newValue == null || !oldValue.equals(newValue)) {
                sb.append(key).append('\n');
                sb.append("  before=").append(oldValue == null ? "(missing)" : oldValue).append('\n');
                sb.append("  after =").append(newValue == null ? "(missing)" : newValue).append("\n\n");
                changed++;
            }
        }
        if (changed == 0) {
            sb.append("(none)\n");
        }

        sb.append("\n[before_snapshot]\n").append(before);
        sb.append("\n[after_snapshot]\n").append(after);
        return sb.toString();
    }

    private Map<String, String> parseSettings(String snapshot) {
        Map<String, String> result = new LinkedHashMap<>();
        boolean inAllSettings = false;
        String[] lines = snapshot.split("\\r?\\n");
        for (String line : lines) {
            if ("[all_settings]".equals(line)) {
                inAllSettings = true;
                continue;
            }
            if (inAllSettings && line.startsWith("[") && line.endsWith("]")) {
                break;
            }
            if (!inAllSettings) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq > 0) {
                result.put(line.substring(0, eq), line.substring(eq + 1));
            }
        }
        return result;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
