package com.sgre.webview;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int REQ_IMPORT_DEVICES_FILE = 7101;
    private static final int REQ_EXPORT_DEVICES_FILE = 7102;
    private static final long HOME_SGRE_REFRESH_MS = 10000L;
    private LinearLayout listLayout;
    private boolean autoOpened = false;
    private boolean homeVisible = false;
    private final Handler homeRefreshHandler = new Handler(Looper.getMainLooper());
    private final Map<String, CardWidgets> cardWidgets = new HashMap<>();
    private final Runnable homeRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!homeVisible) return;
            refreshHomeSgreCards();
            homeRefreshHandler.postDelayed(this, HOME_SGRE_REFRESH_MS);
        }
    };

    private static class CardWidgets {
        final DeviceStore.Device device;
        final LinearLayout card;
        final TextView voltage;
        final TextView power;
        final TextView energy;
        final TextView load;
        final TextView alarmStatus;

        CardWidgets(DeviceStore.Device device, LinearLayout card, TextView voltage, TextView power, TextView energy, TextView load, TextView alarmStatus) {
            this.device = device;
            this.card = card;
            this.voltage = voltage;
            this.power = power;
            this.energy = energy;
            this.load = load;
            this.alarmStatus = alarmStatus;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupSystemBars();
        requestNotificationPermissionIfNeeded();
        buildHome();

        DeviceStore.Device def = DeviceStore.getDefault(this);
        if (def != null && !autoOpened) {
            autoOpened = true;
            openDevice(def);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupSystemBars();
        AlarmReceiver.cancel(this);
        buildHome();
        startHomeRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopHomeRefresh();
        AlarmReceiver.scheduleNext(this, 60000L);
    }

    private boolean isDarkMode() {
        int flags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return flags == Configuration.UI_MODE_NIGHT_YES;
    }

    private void setupSystemBars() {
        try {
            Window w = getWindow();
            if (Build.VERSION.SDK_INT >= 21) {
                w.setStatusBarColor(Color.rgb(31, 70, 132));
                w.setNavigationBarColor(isDarkMode() ? Color.BLACK : Color.rgb(225, 231, 240));
            }
            if (Build.VERSION.SDK_INT >= 23) {
                w.getDecorView().setSystemUiVisibility(0);
            }
        } catch (Exception ignored) {
        }
    }

    private int getStatusBarHeight() {
        int result = 0;
        try {
            int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resId > 0) result = getResources().getDimensionPixelSize(resId);
        } catch (Exception ignored) {
        }
        return result;
    }

    private int getNavigationBarHeight() {
        int result = 0;
        try {
            int resId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (resId > 0) result = getResources().getDimensionPixelSize(resId);
        } catch (Exception ignored) {
        }
        return result;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private GradientDrawable bg(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp((int) radiusDp));
        return g;
    }

    private GradientDrawable strokeBg(int color, float radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable g = bg(color, radiusDp);
        g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private void buildHome() {
        int blue = Color.rgb(31, 70, 132);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(blue);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), getStatusBarHeight() + dp(6), dp(18), dp(10));
        header.setBackgroundColor(blue);

        TextView spacer = new TextView(this);
        header.addView(spacer, new LinearLayout.LayoutParams(dp(46), dp(46)));

        TextView title = new TextView(this);
        title.setText("ESP設備管理");
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setOnTouchListener(new View.OnTouchListener() {
            private final Handler handler = new Handler(Looper.getMainLooper());
            private boolean fired = false;
            private final Runnable openDebug = () -> {
                fired = true;
                showAlarmDebugDialog();
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    fired = false;
                    handler.postDelayed(openDebug, 3000);
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    handler.removeCallbacks(openDebug);
                    return fired;
                }
                return true;
            }
        });
        header.addView(title, new LinearLayout.LayoutParams(0, dp(46), 1));

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextSize(28);
        plus.setTypeface(null, Typeface.BOLD);
        plus.setTextColor(Color.WHITE);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(strokeBg(Color.TRANSPARENT, 23, Color.WHITE, 1));
        plus.setOnClickListener(v -> showQuickActionDialog());
        header.addView(plus, new LinearLayout.LayoutParams(dp(46), dp(46)));

        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(dp(14), dp(8), dp(14), getNavigationBarHeight() + dp(24));
        scroll.addView(listLayout);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        renderDevices();
    }

    private void startHomeRefresh() {
        homeVisible = true;
        homeRefreshHandler.removeCallbacks(homeRefreshRunnable);
        homeRefreshHandler.postDelayed(homeRefreshRunnable, HOME_SGRE_REFRESH_MS);
    }

    private void stopHomeRefresh() {
        homeVisible = false;
        homeRefreshHandler.removeCallbacks(homeRefreshRunnable);
    }

    private void refreshHomeSgreCards() {
        if (listLayout == null) return;
        List<DeviceStore.Device> devices = DeviceStore.load(this);
        for (DeviceStore.Device d : devices) {
            CardWidgets w = cardWidgets.get(d.id);
            if (w == null) continue;
            fetchSummary(d, w.card, w.voltage, w.power, w.energy, w.load, w.alarmStatus, null);
        }
    }

    private void showQuickActionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("設備管理")
                .setItems(new String[]{"新增設備", "搜尋設備", "導出設備檔案", "匯入設備檔案"}, (dialog, which) -> {
                    if (which == 0) showDeviceDialog(null);
                    if (which == 1) startActivity(new Intent(this, ScanActivity.class));
                    if (which == 2) showExportDevicesDialog();
                    if (which == 3) openImportFilePicker();
                })
                .show();
    }


    private void showExportDevicesDialog() {
        createExportFile();
    }

    private void createExportFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "sgre_devices_backup.sgre.json");
            startActivityForResult(intent, REQ_EXPORT_DEVICES_FILE);
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("無法建立設備檔案")
                    .setMessage("檔案選擇器無法開啟，請確認手機已安裝檔案管理器。")
                    .setPositiveButton("確定", null)
                    .show();
        }
    }

    private void exportDevicesToUri(Uri uri) {
        try {
            OutputStream os = getContentResolver().openOutputStream(uri, "wt");
            if (os == null) throw new Exception("openOutputStream failed");

            OutputStreamWriter writer = new OutputStreamWriter(os);
            writer.write(DeviceStore.exportJson(this));
            writer.flush();
            writer.close();

            new AlertDialog.Builder(this)
                    .setTitle("導出完成")
                    .setMessage("設備清單已存成檔案。之後可用「匯入設備檔案」選取此檔案還原。")
                    .setPositiveButton("確定", null)
                    .show();
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("導出失敗")
                    .setMessage("無法寫入設備檔案，請重新選擇儲存位置。")
                    .setPositiveButton("確定", null)
                    .show();
        }
    }


    private void openImportFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            String[] mimeTypes = new String[]{
                    "application/json",
                    "text/plain",
                    "application/octet-stream"
            };
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            startActivityForResult(intent, REQ_IMPORT_DEVICES_FILE);
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("無法開啟檔案選擇器")
                    .setMessage("檔案選擇器無法開啟，請確認手機已安裝檔案管理器。")
                    .setPositiveButton("確定", null)
                    .show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT_DEVICES_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            importDevicesFromUri(uri);
            return;
        }
        if (requestCode == REQ_EXPORT_DEVICES_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            exportDevicesToUri(uri);
        }
    }

    private void importDevicesFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) throw new Exception("openInputStream failed");

            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            br.close();

            boolean ok = DeviceStore.importJson(this, sb.toString());
            if (ok) {
                renderDevices();
                new AlertDialog.Builder(this)
                        .setTitle("匯入完成")
                        .setMessage("已從檔案匯入設備清單。")
                        .setPositiveButton("確定", null)
                        .show();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("匯入失敗")
                        .setMessage("檔案格式不正確，請確認是 SGRE 匯出的設備備份 JSON。")
                        .setPositiveButton("確定", null)
                        .show();
            }
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("匯入失敗")
                    .setMessage("無法讀取檔案，請確認檔案可開啟。")
                    .setPositiveButton("確定", null)
                    .show();
        }
    }


    private void showImportDevicesDialog() {
        EditText input = new EditText(this);
        input.setHint("請貼上導出的設備 JSON");
        input.setMinLines(8);

        new AlertDialog.Builder(this)
                .setTitle("匯入設備")
                .setMessage("匯入會覆蓋目前設備清單，請先確認已備份。")
                .setView(input)
                .setPositiveButton("匯入", (dialog, which) -> {
                    boolean ok = DeviceStore.importJson(this, input.getText().toString());
                    if (ok) {
                        renderDevices();
                        new AlertDialog.Builder(this)
                                .setTitle("匯入完成")
                                .setMessage("設備清單已更新。")
                                .setPositiveButton("確定", null)
                                .show();
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("匯入失敗")
                                .setMessage("格式不正確，請確認貼上的是完整設備 JSON。")
                                .setPositiveButton("確定", null)
                                .show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }



    private String fmtTime(long ms) {
        if (ms <= 0) return "尚無";
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.TAIWAN);
            return f.format(new java.util.Date(ms));
        } catch (Exception e) {
            return String.valueOf(ms);
        }
    }

    private String alarmHistoryText() {
        String h = AlarmReceiver.getHistory(this);
        if (h == null || h.trim().length() == 0) return "尚無警報紀錄";
        StringBuilder out = new StringBuilder();
        String[] lines = h.split("\n");
        for (String line : lines) {
            String[] parts = line.split("｜", -1);
            if (parts.length >= 4) {
                long t = 0;
                try { t = Long.parseLong(parts[0]); } catch (Exception ignored) {}
                out.append(fmtTime(t)).append("  ").append(parts[1]).append("\n");
                out.append(parts[2]).append("\n");
                out.append(parts[3]);
                if (parts.length >= 5 && parts[4].length() > 0) out.append("\n").append(parts[4]);
                out.append("\n\n");
            } else {
                out.append(line).append("\n");
            }
        }
        return out.toString().trim();
    }

    private boolean notificationsAllowed() {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= 24) {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                return nm == null || nm.areNotificationsEnabled();
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    private String alarmDebugText() {
        SharedPreferences p = getSharedPreferences(AlarmReceiver.DEBUG_PREF, MODE_PRIVATE);
        DeviceStore.Device d = DeviceStore.getDefault(this);
        StringBuilder sb = new StringBuilder();
        sb.append("通知權限：").append(notificationsAllowed() ? "允許" : "可能被關閉").append("\n");
        sb.append("預設設備：").append(d == null ? "無" : (d.name + " / " + d.type)).append("\n\n");
        sb.append("最後排程：").append(fmtTime(p.getLong("last_schedule_wall", 0))).append("\n");
        sb.append("排程延遲：").append(p.getLong("last_schedule_delay", 0) / 1000).append(" 秒\n");
        sb.append("最後取消：").append(fmtTime(p.getLong("last_cancel_wall", 0))).append("\n");
        sb.append("最後輪詢：").append(fmtTime(p.getLong("last_poll_wall", 0))).append("\n");
        sb.append("來源：").append(p.getString("last_source", "")).append("\n");
        sb.append("網址：").append(p.getString("last_url", "")).append("\n");
        sb.append("結果：").append(p.getBoolean("last_alarm", false) ? "alarm=true" : "alarm=false").append("\n");
        sb.append("code：").append(p.getString("last_code", "")).append("\n");
        sb.append("alarm key：").append(p.getString("last_alarm_key", "")).append("\n");
        sb.append("msg：").append(p.getString("last_msg", "")).append("\n");
        sb.append("主要告警：").append(p.getString("last_main", "")).append("\n");
        sb.append("分類/等級：").append(p.getString("last_category", "")).append(" / ").append(p.getString("last_level_text", "")).append("\n");
        sb.append("摘要：").append(p.getString("last_summary", "")).append("\n");
        String raw = p.getString("last_raw", "");
        if (raw.length() > 0) sb.append("RAW：").append(raw).append("\n");
        sb.append("錯誤：").append(p.getString("last_error", "")).append("\n");
        sb.append("最後恢復：").append(fmtTime(p.getLong("last_recovery_wall", 0))).append("\n");
        sb.append("恢復訊息：").append(p.getString("last_recovery_msg", "")).append("\n\n");
        sb.append("最後通知：").append(fmtTime(p.getLong("last_notify_wall", 0))).append("\n");
        sb.append("通知標題：").append(p.getString("last_notify_title", "")).append("\n");
        sb.append("通知內容：").append(p.getString("last_notify_text", "")).append("\n");
        sb.append("去重抑制次數：").append(p.getInt("alarm_suppressed_count", 0)).append("\n");
        sb.append("最後去重：").append(fmtTime(p.getLong("last_suppressed_wall", 0))).append("\n");
        sb.append("去重 key：").append(p.getString("last_suppressed_key", "")).append("\n\n");
        sb.append("警報紀錄：\n").append(alarmHistoryText()).append("\n\n");
        sb.append("最後回傳：\n").append(p.getString("last_body", ""));
        return sb.toString();
    }

    private void showAlarmDebugDialog() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(4), dp(12), 0);

        TextView info = new TextView(this);
        info.setText(alarmDebugText());
        info.setTextSize(14);
        info.setTextColor(Color.rgb(38, 50, 64));
        info.setPadding(0, 0, 0, dp(8));
        panel.addView(info);

        Button manual = new Button(this);
        manual.setText("立即檢查 /api/alarm");
        manual.setOnClickListener(v -> new Thread(() -> {
            String result = AlarmReceiver.runDebugCheckNow(this, true);
            runOnUiThread(() -> {
                info.setText(alarmDebugText() + "\n\n手動檢查結果：" + result);
            });
        }).start());
        panel.addView(manual);

        Button testNotify = new Button(this);
        testNotify.setText("發送本機測試通知");
        testNotify.setOnClickListener(v -> {
            AlarmReceiver.showAlarmNotification(this, "SGRE 本機測試通知", "如果看到這則通知，代表 Android 通知權限正常。");
            info.setText(alarmDebugText());
        });
        panel.addView(testNotify);

        Button schedule = new Button(this);
        schedule.setText("排程 60 秒後背景檢查");
        schedule.setOnClickListener(v -> {
            AlarmReceiver.scheduleNext(this, 60000L);
            info.setText(alarmDebugText());
        });
        panel.addView(schedule);

        Button clearHistory = new Button(this);
        clearHistory.setText("清除警報紀錄 / 去重統計");
        clearHistory.setOnClickListener(v -> {
            AlarmReceiver.clearHistory(this);
            info.setText(alarmDebugText());
        });
        panel.addView(clearHistory);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(panel);

        new AlertDialog.Builder(this)
                .setTitle("警報監控除錯")
                .setView(scroll)
                .setPositiveButton("重新整理", (dialog, which) -> showAlarmDebugDialog())
                .setNegativeButton("關閉", null)
                .show();
    }

    private void renderDevices() {
        if (listLayout == null) return;
        listLayout.removeAllViews();
        cardWidgets.clear();

        List<DeviceStore.Device> devices = DeviceStore.load(this);
        if (devices.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("尚未新增設備\n請按右上角 + 新增或搜尋區網。");
            empty.setTextSize(18);
            empty.setTextColor(Color.WHITE);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(25), dp(90), dp(25), dp(20));
            listLayout.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        LinearLayout currentRow = null;
        int col = 0;
        for (DeviceStore.Device d : devices) {
            if (col == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                rowLp.setMargins(0, 0, 0, dp(12));
                listLayout.addView(currentRow, rowLp);
            }

            LinearLayout card = createDeviceCard(d);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, -2, 1);
            if (col == 0) {
                cardLp.setMargins(0, 0, dp(6), 0);
            } else {
                cardLp.setMargins(dp(6), 0, 0, 0);
            }
            currentRow.addView(card, cardLp);

            col++;
            if (col >= 2) col = 0;
        }

        if (col == 1 && currentRow != null) {
            TextView placeholder = new TextView(this);
            placeholder.setVisibility(View.INVISIBLE);
            LinearLayout.LayoutParams phLp = new LinearLayout.LayoutParams(0, 1, 1);
            phLp.setMargins(dp(6), 0, 0, 0);
            currentRow.addView(placeholder, phLp);
        }
    }

    private LinearLayout createDeviceCard(DeviceStore.Device d) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        // Compact card layout: keep the device list dense like the earlier working version.
        box.setPadding(dp(10), dp(10), dp(10), dp(10));
        // Stage15_5: keep every home card visually the same size, even cards that only show "可連線".
        box.setMinimumHeight(dp(154));
        box.setBackground(bg(Color.rgb(248, 250, 252), 22));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(d.name.length() > 0 ? d.name : "未命名設備");
        name.setTextColor(Color.rgb(42, 54, 68));
        name.setTextSize(17);
        name.setIncludeFontPadding(false);
        name.setTypeface(null, Typeface.BOLD);
        name.setSingleLine(true);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

        box.addView(row);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setPadding(0, dp(8), 0, dp(4));

        TextView power = metric("功率", "--", Color.rgb(255, 178, 45));
        TextView energy = metric("SOC", "--", Color.rgb(76, 195, 112));
        TextView voltage = metric("電壓", "--", Color.rgb(87, 155, 255));
        TextView load = metric("負載", "--", Color.rgb(250, 103, 92));

        grid.addView(power);
        grid.addView(energy);
        grid.addView(voltage);
        grid.addView(load);

        box.addView(grid);

        TextView alarmStatus = new TextView(this);
        alarmStatus.setTextColor(Color.rgb(214, 65, 65));
        alarmStatus.setTextSize(12);
        alarmStatus.setTypeface(null, Typeface.BOLD);
        alarmStatus.setIncludeFontPadding(false);
        alarmStatus.setSingleLine(true);
        alarmStatus.setPadding(dp(2), dp(0), dp(2), dp(0));
        alarmStatus.setVisibility(View.GONE);
        box.addView(alarmStatus);

        box.setOnClickListener(v -> openDevice(d));
        box.setOnLongClickListener(v -> {
            showDeviceActionDialog(d);
            return true;
        });

        if (d.id != null && d.id.length() > 0) {
            cardWidgets.put(d.id, new CardWidgets(d, box, voltage, power, energy, load, alarmStatus));
        }

        fetchSummary(d, box, voltage, power, energy, load, alarmStatus, null);
        return box;
    }

    private TextView metric(String label, String value, int dotColor) {
        TextView t = new TextView(this);
        t.setTag(Integer.valueOf(dotColor));
        t.setTextColor(Color.rgb(58, 70, 84));
        t.setTextSize(12);
        t.setTypeface(null, Typeface.BOLD);
        t.setIncludeFontPadding(false);
        t.setSingleLine(false);
        t.setMaxLines(2);
        t.setMinHeight(dp(52));
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(0f, 0.91f);
        t.setPadding(dp(1), dp(1), dp(2), dp(4));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(0, 0, dp(4), dp(5));
        t.setLayoutParams(lp);
        setMetricText(t, label, value);
        return t;
    }

    private void setMetricText(TextView target, String label, String value) {
        applyMetricText(target, label, value, true);
    }

    private void applyMetricText(TextView target, String label, String value, boolean scheduleAfterLayout) {
        if (value == null || value.length() == 0) {
            target.setText("");
        } else if ("可連線".equals(value)) {
            target.setText(value);
        } else {
            // Stage15_7: remove color dots so label/value stay visually centered; value text auto-fits each metric cell.
            // Keep label/value stacked, keep value on one visual line, and use the largest safe font size.
            String safeLabel = noBreakLabel(label);
            String safeValue = noBreakValue(value);
            String text = safeLabel + "\n" + safeValue;
            SpannableString s = new SpannableString(text);
            int labelEnd = Math.max(0, text.indexOf('\n'));
            int valueStart = Math.min(text.length(), labelEnd + 1);
            if (labelEnd > 0) {
                // Stage15_8: keep labels visually consistent. Use a fixed preferred size and only shrink
                // when the label is too long for the metric cell, so "280 SOC" and "314 SOC" stay identical.
                float labelSp = fitMetricLabelSp(target, label, 13.2f, 10.5f);
                s.setSpan(new AbsoluteSizeSpan(Math.round(labelSp), true), 0, labelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                s.setSpan(new StyleSpan(Typeface.BOLD), 0, labelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (valueStart < text.length()) {
                float valueSp = fitMetricTextSp(target, value, 17.2f, 9.5f);
                s.setSpan(new AbsoluteSizeSpan(Math.round(valueSp), true), valueStart, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                s.setSpan(new StyleSpan(Typeface.BOLD), valueStart, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            target.setText(s);

            if (scheduleAfterLayout && target.getWidth() <= 0) {
                final String flabel = label;
                final String fvalue = value;
                target.post(() -> applyMetricText(target, flabel, fvalue, false));
            }
        }
    }

    private String noBreakLabel(String label) {
        if (label == null) return "";
        return label.replace(" ", "\u00A0");
    }

    private String noBreakValue(String value) {
        if (value == null || value.length() <= 1) return value == null ? "" : value;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            if (i > 0) sb.append('\u2060');
            sb.append(value.charAt(i));
        }
        return sb.toString();
    }

    private float fitMetricTextSp(TextView target, String value, float maxSp, float minSp) {
        int available = target.getWidth() - target.getPaddingLeft() - target.getPaddingRight();
        if (available <= dp(12)) return maxSp;
        Paint p = new Paint(target.getPaint());
        for (float sp = maxSp; sp >= minSp; sp -= 0.5f) {
            p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics()));
            if (p.measureText(value == null ? "" : value) <= available) return sp;
        }
        return minSp;
    }

    private float fitMetricLabelSp(TextView target, String label, float preferredSp, float minSp) {
        int available = target.getWidth() - target.getPaddingLeft() - target.getPaddingRight();
        if (available <= dp(12)) return preferredSp;
        Paint p = new Paint(target.getPaint());
        String text = label == null ? "" : label;
        p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, preferredSp, getResources().getDisplayMetrics()));
        if (p.measureText(text) <= available) return preferredSp;
        for (float sp = preferredSp - 0.5f; sp >= minSp; sp -= 0.5f) {
            p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics()));
            if (p.measureText(text) <= available) return sp;
        }
        return minSp;
    }

    private boolean hasValue(String value) {
        return value != null && value.length() > 0 && !"--".equals(value);
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && value.length() > 0) return value;
        }
        return "";
    }

    private String shortUrl(String raw) {
        if (raw == null || raw.trim().length() == 0) return "未設定";
        return raw.replace("http://", "").replace("https://", "");
    }

    private String intText(String raw) {
        try {
            if (raw == null || raw.length() == 0) return "";
            return String.valueOf(Math.round(Float.parseFloat(raw)));
        } catch (Exception e) {
            return raw == null ? "" : raw;
        }
    }

    private String oneDecimalText(String raw) {
        try {
            if (raw == null || raw.length() == 0) return "";
            float v = Float.parseFloat(raw);
            if (Math.abs(v - Math.round(v)) < 0.05f) return String.valueOf(Math.round(v));
            return String.format(java.util.Locale.US, "%.1f", v);
        } catch (Exception e) {
            return raw == null ? "" : raw;
        }
    }

    private String twoDecimalText(String raw) {
        try {
            if (raw == null || raw.length() == 0) return "";
            float val = Float.parseFloat(raw);
            if (Math.abs(val - Math.round(val)) < 0.005f) return String.valueOf(Math.round(val));
            return String.format(java.util.Locale.US, "%.2f", val);
        } catch (Exception e) {
            return raw == null ? "" : raw;
        }
    }

    private String formatCardValue(String label, String value, String unit) {
        if (value == null || value.length() == 0) return "";
        String u = unit == null ? "" : unit;
        if ("W".equals(u)) return intText(value) + u;
        if ("V".equals(u)) return twoDecimalText(value) + u;
        if ("%".equals(u)) return oneDecimalText(value) + u;
        if ("台".equals(u)) return intText(value) + u;
        return value + u;
    }

    private float parseNumber(String raw) throws Exception {
        if (raw == null || raw.length() == 0) throw new Exception("empty");
        return Float.parseFloat(raw);
    }

    private String sumAbsNumbers(String... values) {
        float sum = 0f;
        boolean found = false;
        for (String value : values) {
            try {
                if (value == null || value.length() == 0) continue;
                sum += Math.abs(parseNumber(value));
                found = true;
            } catch (Exception ignored) {
            }
        }
        if (!found) return "";
        return String.valueOf(sum);
    }

    private void saveDeviceRuntime(DeviceStore.Device d, String status) {
        try {
            if (d == null || d.id == null) return;
            getSharedPreferences("sgre_device_runtime", MODE_PRIVATE).edit()
                    .putString(d.id + "_status", status == null ? "" : status)
                    .putLong(d.id + "_time", System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private String getDeviceRuntime(DeviceStore.Device d) {
        try {
            if (d == null || d.id == null) return "目前連線：尚未檢查";
            SharedPreferences p = getSharedPreferences("sgre_device_runtime", MODE_PRIVATE);
            String s = p.getString(d.id + "_status", "");
            long t = p.getLong(d.id + "_time", 0);
            if (s == null || s.length() == 0) return "目前連線：尚未檢查";
            if (t > 0) return s + "\n更新時間：" + fmtTime(t);
            return s;
        } catch (Exception e) {
            return "目前連線：尚未檢查";
        }
    }

    private void saveDeviceRuntime(DeviceStore.Device d, String status, String openUrl) {
        try {
            if (d == null || d.id == null) return;
            SharedPreferences.Editor e = getSharedPreferences("sgre_device_runtime", MODE_PRIVATE).edit()
                    .putString(d.id + "_status", status == null ? "" : status)
                    .putLong(d.id + "_time", System.currentTimeMillis());
            if (openUrl != null && openUrl.trim().length() > 0) {
                e.putString(d.id + "_open_url", openUrl.trim());
            }
            e.apply();
        } catch (Exception ignored) {
        }
    }

    private String getDeviceOpenUrl(DeviceStore.Device d) {
        try {
            if (d == null || d.id == null) return "";
            SharedPreferences p = getSharedPreferences("sgre_device_runtime", MODE_PRIVATE);
            String u = p.getString(d.id + "_open_url", "");
            return u == null ? "" : u;
        } catch (Exception e) {
            return "";
        }
    }

    private String openPageUrl(String base, String rawPreferred) {
        try {
            String b = originOnly(base);
            if (b == null || b.trim().length() == 0) return "";
            String path = "/phone";
            if (rawPreferred != null && rawPreferred.trim().length() > 0) {
                URL u = new URL(DeviceStore.normalize(rawPreferred));
                String p = u.getPath();
                if (p != null && p.trim().length() > 0 && !"/".equals(p.trim())) {
                    path = p.trim();
                }
            }
            if (!path.startsWith("/")) path = "/" + path;
            return b + path;
        } catch (Exception e) {
            String b = originOnly(base);
            if (b == null || b.trim().length() == 0) return "";
            return b + "/phone";
        }
    }

    private void fetchSummary(DeviceStore.Device d, LinearLayout card, TextView voltage, TextView power, TextView energy, TextView load, TextView alarmStatus, TextView urlLabel) {
        new Thread(() -> {
            boolean online = false;
            String v = "--";
            String p = "--";
            String e = "--";
            String l = "--";
            String labelP = "功率";
            String labelE = "SOC";
            String labelV = "電壓";
            String labelL = "負載";
            String alarmLine = "";
            String activeUrlLabel = "目前連線：未連線";
            String activeOpenUrl = "";

            if ("SGRE".equals(d.type)) {
                String localBase = apiBase(d);
                String remoteBase = originOnly(d.remoteUrl);
                boolean usingRemote = false;
                String liveOpenUrl = "";

                // Stage APP Live-First: 首頁卡片先抓 /api/live，讓功率/SOC/電壓/負載先更新；
                // 告警改成第二階段背景補上，避免 /api/alarm 拖慢卡片數字。
                String live = fetch(localBase + "/api/live", 900, 1300);
                if (live.length() > 0) {
                    liveOpenUrl = openPageUrl(localBase, d.localUrl);
                }
                if (live.length() == 0 && remoteBase != null && remoteBase.trim().length() > 0) {
                    live = fetch(remoteBase + "/api/live", 1000, 1600);
                    if (live.length() > 0) {
                        usingRemote = true;
                        liveOpenUrl = openPageUrl(remoteBase, d.remoteUrl);
                    }
                }

                if (live.length() > 0) {
                    online = true;
                    activeUrlLabel = usingRemote ? "目前外網：" + shortUrl(d.remoteUrl) : "目前內網：" + shortUrl(d.localUrl);

                    String pv = firstNonEmpty(
                            liveVal(live, "v_pv_total_power"),
                            liveVal(live, "pv_total_power"),
                            liveVal(live, "v_pv_power"),
                            liveVal(live, "PV_Vsum"),
                            liveVal(live, "pv_sum_power"),
                            liveVal(live, "v_pv_sum_power"));

                    String batterySoc = firstNonEmpty(
                            liveVal(live, "v_battery_soc"),
                            liveVal(live, "battery_soc"),
                            liveVal(live, "v_batt_soc"),
                            liveVal(live, "batt_soc"),
                            liveVal(live, "v_soc"),
                            liveVal(live, "soc"),
                            liveVal(live, "SOC"),
                            liveVal(live, "BattSoc"),
                            liveVal(live, "v_batt_state_of_charge"),
                            liveVal(live, "batt_state_of_charge"),
                            liveVal(live, "state_of_charge"),
                            liveVal(live, "7532"),
                            liveVal(live, "v_7532"));

                    String battVoltLive = firstNonEmpty(
                            liveVal(live, "v_batt_voltage"),
                            liveVal(live, "batt_voltage"),
                            liveVal(live, "v_battery_voltage"),
                            liveVal(live, "battery_voltage"),
                            liveVal(live, "v_batt_v"),
                            liveVal(live, "batt_v"),
                            liveVal(live, "BattVolt"),
                            liveVal(live, "7530"),
                            liveVal(live, "v_7530"));

                    String upsLoad = firstNonEmpty(
                            liveVal(live, "v_ac_out_sum"),
                            liveVal(live, "ac_out_sum"),
                            liveVal(live, "AC_Out_Sum"),
                            liveVal(live, "v_output_total_power"),
                            liveVal(live, "output_total_power"),
                            liveVal(live, "v_out_total_power"),
                            liveVal(live, "out_total_power"),
                            liveVal(live, "v_backup_load_power"),
                            liveVal(live, "backup_load_power"),
                            liveVal(live, "v_ups_load_power"),
                            liveVal(live, "ups_load_power"));

                    String gridLoad = firstNonEmpty(
                            liveVal(live, "v_grid_load_power"),
                            liveVal(live, "grid_load_power"),
                            liveVal(live, "v_normal_load_power"),
                            liveVal(live, "normal_load_power"),
                            liveVal(live, "v_ct_sum"),
                            liveVal(live, "ct_sum"),
                            liveVal(live, "CT_sum"),
                            liveVal(live, "v_grid_total_power"),
                            liveVal(live, "grid_total_power"),
                            liveVal(live, "v_line_sum_power"),
                            liveVal(live, "line_sum_power"),
                            liveVal(live, "v_line_total_power"),
                            liveVal(live, "line_total_power"));

                    String totalLoad = sumAbsNumbers(gridLoad, upsLoad);

                    if (pv.length() > 0) p = intText(pv) + "W";
                    if (batterySoc.length() > 0) e = intText(batterySoc) + "%";
                    if (battVoltLive.length() > 0) v = oneDecimalText(battVoltLive) + "V";
                    if (totalLoad.length() > 0) l = intText(totalLoad) + "W";

                    final String liveV = v;
                    final String liveP = p;
                    final String liveE = e;
                    final String liveL = l;
                    final String liveLabelP = labelP;
                    final String liveLabelE = labelE;
                    final String liveLabelV = labelV;
                    final String liveLabelL = labelL;
                    final String liveUrl = activeUrlLabel;
                    final String liveOpen = liveOpenUrl;
                    runOnUiThread(() -> {
                        setMetricText(power, liveLabelP, liveP);
                        setMetricText(energy, liveLabelE, liveE);
                        setMetricText(voltage, liveLabelV, liveV);
                        setMetricText(load, liveLabelL, liveL);
                        saveDeviceRuntime(d, liveUrl, liveOpen);
                        if (urlLabel != null) urlLabel.setText("");
                        card.setAlpha(1f);
                        card.setBackground(bg(Color.rgb(248, 250, 252), 22));
                    });
                }

                // 第二階段才抓告警。若 /api/live 已成功，告警只走同一條連線路徑，避免多敲一次外網。
                String alarm = "";
                if (online) {
                    alarm = fetch((usingRemote ? remoteBase : localBase) + "/api/alarm", 700, 1100);
                } else {
                    alarm = fetch(localBase + "/api/alarm", 800, 1100);
                    if (alarm.length() == 0 && remoteBase != null && remoteBase.trim().length() > 0) {
                        alarm = fetch(remoteBase + "/api/alarm", 1000, 1500);
                        if (alarm.length() > 0) {
                            usingRemote = true;
                            liveOpenUrl = openPageUrl(remoteBase, d.remoteUrl);
                        }
                    } else if (alarm.length() > 0) {
                        liveOpenUrl = openPageUrl(localBase, d.localUrl);
                    }
                }

                if (alarm.length() > 0) {
                    online = true;
                    activeUrlLabel = usingRemote ? "目前外網：" + shortUrl(d.remoteUrl) : "目前內網：" + shortUrl(d.localUrl);
                    String battVolt = num(alarm, "batt_v");
                    if (!hasValue(v) && battVolt.length() > 0) v = oneDecimalText(battVolt) + "V";
                    if (jsonBool(alarm, "alarm")) {
                        String levelText = jsonString(alarm, "level_text");
                        String mainText = firstNonEmpty(jsonString(alarm, "main"), jsonString(alarm, "msg"));
                        if (levelText.length() == 0 || "正常".equals(levelText)) levelText = "警告";
                        if (mainText.length() == 0 || "無告警".equals(mainText) || "正常".equals(mainText)) {
                            mainText = firstNonEmpty(jsonString(alarm, "summary"), "未知告警");
                        }
                        if (mainText.startsWith(levelText + "｜")) {
                            alarmLine = mainText;
                        } else {
                            alarmLine = levelText + "｜" + mainText;
                        }
                    }
                    if (liveOpenUrl != null && liveOpenUrl.trim().length() > 0) {
                        saveDeviceRuntime(d, activeUrlLabel, liveOpenUrl);
                    }
                }

                // Stage14.3: connection status must mean the device page is reachable, not only that /api/live or /api/alarm returns JSON.
                // Some external/WAN devices can open normally but expose the API through another path or block API probing.
                // In that case keep the card active and show 「可連線」 instead of marking it gray.
                if (!online) {
                    String body = "";
                    if (d.localUrl != null && d.localUrl.trim().length() > 0) {
                        body = fetch(DeviceStore.normalize(d.localUrl), 1000, 1500);
                        if (body.length() > 0) {
                            activeUrlLabel = "目前內網：" + shortUrl(d.localUrl);
                            liveOpenUrl = DeviceStore.normalize(d.localUrl);
                        }
                    }
                    if (body.length() == 0 && d.remoteUrl != null && d.remoteUrl.trim().length() > 0) {
                        body = fetch(DeviceStore.normalize(d.remoteUrl), 1400, 2400);
                        if (body.length() > 0) {
                            activeUrlLabel = "目前外網：" + shortUrl(d.remoteUrl);
                            liveOpenUrl = DeviceStore.normalize(d.remoteUrl);
                        }
                    }
                    if (body.length() > 0) {
                        online = true;
                        v = "可連線";
                        p = "";
                        e = "";
                        l = "";
                        saveDeviceRuntime(d, activeUrlLabel, liveOpenUrl);
                    }
                }

            } else {
                String live = "";
                String liveSource = "";
                for (String u : liveApiCandidates(d, true)) {
                    live = fetch(u, 1000, 1800);
                    if (live.length() > 0) {
                        liveSource = u;
                        activeUrlLabel = "目前內網：" + shortUrl(u);
                        activeOpenUrl = openUrlFromLiveSource(u, d.localUrl);
                        break;
                    }
                }
                if (live.length() == 0) {
                    for (String u : liveApiCandidates(d, false)) {
                        live = fetch(u, 1200, 2200);
                        if (live.length() > 0) {
                            liveSource = u;
                            activeUrlLabel = "目前外網：" + shortUrl(u);
                            activeOpenUrl = openUrlFromLiveSource(u, d.remoteUrl);
                            break;
                        }
                    }
                }

                if (live.length() > 0 && hasGenericCardItems(live)) {
                    online = true;
                    if (isSelposLive(live)) {
                        int packCount = selposPackCount(live);
                        String soc280 = itemNumberByName(live, "280", "soc");
                        String soc314 = itemNumberByName(live, "314", "soc");
                        boolean dualSelpos = packCount >= 2 || (soc280.length() > 0 && soc314.length() > 0);
                        if (dualSelpos) {
                            labelP = "280 SOC";
                            labelE = "314 SOC";
                            labelV = "總功率";
                            labelL = "電壓";
                            p = soc280.length() > 0 ? oneDecimalText(soc280) + "%" : "--";
                            e = soc314.length() > 0 ? oneDecimalText(soc314) + "%" : "--";
                            v = cardItemDisplay(live, "總功率");
                            l = cardItemDisplay(live, "電壓");
                        } else {
                            labelP = "功率";
                            labelE = "SOC";
                            labelV = "電壓";
                            labelL = "電流";
                            String power = firstPackNumber(live, "power");
                            String soc = firstPackNumber(live, "soc");
                            String voltage = firstPackNumber(live, "voltage");
                            String current = firstPackNumber(live, "current");
                            if (power.length() == 0) power = cardItemNumber(live, "總功率");
                            if (soc.length() == 0) soc = cardItemNumber(live, "平均SOC");
                            if (voltage.length() == 0) voltage = cardItemNumber(live, "電壓");
                            p = power.length() > 0 ? intText(power) + "W" : "--";
                            e = soc.length() > 0 ? oneDecimalText(soc) + "%" : "--";
                            v = voltage.length() > 0 ? twoDecimalText(voltage) + "V" : "--";
                            l = current.length() > 0 ? oneDecimalText(current) + "A" : "--";
                        }
                    } else {
                        labelP = "在線";
                        labelE = "平均SOC";
                        labelV = "總功率";
                        labelL = "電壓";
                        p = cardItemDisplay(live, "在線");
                        e = cardItemDisplay(live, "平均SOC");
                        v = cardItemDisplay(live, "總功率");
                        l = cardItemDisplay(live, "電壓");
                        if (p.length() == 0 || e.length() == 0 || v.length() == 0 || l.length() == 0) {
                            String[] generic = firstCardItems(live, 4);
                            if (p.length() == 0 && generic.length > 0) { labelP = generic[0]; p = generic.length > 1 ? generic[1] : ""; }
                            if (e.length() == 0 && generic.length > 2) { labelE = generic[2]; e = generic.length > 3 ? generic[3] : ""; }
                            if (v.length() == 0 && generic.length > 4) { labelV = generic[4]; v = generic.length > 5 ? generic[5] : ""; }
                            if (l.length() == 0 && generic.length > 6) { labelL = generic[6]; l = generic.length > 7 ? generic[7] : ""; }
                        }
                    }
                } else {
                    String body = "";
                    if (d.localUrl != null && d.localUrl.trim().length() > 0) {
                        body = fetch(DeviceStore.normalize(d.localUrl), 1000, 1400);
                        if (body.length() > 0) {
                            activeUrlLabel = "目前內網：" + shortUrl(d.localUrl);
                            activeOpenUrl = DeviceStore.normalize(d.localUrl);
                        }
                    }
                    if (body.length() == 0 && d.remoteUrl != null && d.remoteUrl.trim().length() > 0) {
                        body = fetch(DeviceStore.normalize(d.remoteUrl), 1200, 1600);
                        if (body.length() > 0) {
                            activeUrlLabel = "目前外網：" + shortUrl(d.remoteUrl);
                            activeOpenUrl = DeviceStore.normalize(d.remoteUrl);
                        }
                    }
                    online = body.length() > 0;
                    if (online) {
                        v = "可連線";
                        p = "";
                        e = "";
                        l = "";
                    } else {
                        v = "--";
                    }
                }
            }

            if (online && !hasValue(v) && !hasValue(p) && !hasValue(e) && !hasValue(l)) {
                v = "可連線";
                p = "";
                e = "";
                l = "";
            }

            final boolean ok = online;
            final String fv = v;
            final String fp = p;
            final String fe = e;
            final String fl = l;
            final String flabelP = labelP;
            final String flabelE = labelE;
            final String flabelV = labelV;
            final String flabelL = labelL;
            final String fa = alarmLine;
            final String furl = activeUrlLabel;
            final String fopenUrl = activeOpenUrl;

            runOnUiThread(() -> {
                setMetricText(power, flabelP, fp);
                setMetricText(energy, flabelE, fe);
                setMetricText(voltage, flabelV, fv);
                setMetricText(load, flabelL, fl);
                if (alarmStatus != null) {
                    if (fa != null && fa.length() > 0) {
                        alarmStatus.setText(fa);
                        alarmStatus.setVisibility(View.VISIBLE);
                    } else {
                        alarmStatus.setText("");
                        alarmStatus.setVisibility(View.GONE);
                    }
                }
                if (ok && fopenUrl != null && fopenUrl.trim().length() > 0) {
                    saveDeviceRuntime(d, furl, fopenUrl);
                } else {
                    saveDeviceRuntime(d, ok ? furl : "目前連線：未連線");
                }
                if (urlLabel != null) urlLabel.setText("");
                if (!ok) {
                    card.setAlpha(0.55f);
                    card.setBackground(bg(Color.rgb(210, 215, 222), 22));
                } else {
                    card.setAlpha(1f);
                    card.setBackground(bg(Color.rgb(248, 250, 252), 22));
                }
            });
        }).start();
    }

    private String openUrlFromLiveSource(String liveSource, String preferredUrl) {
        try {
            // Stage16-H4-FIX2H Selpos open fix:
            // Use the actual successful /api/live source as the page base.
            // If Selpos was detected through http://IP:81/api/live, opening the saved preferred URL
            // like http://IP would hit ESPHome built-in port 80 and can trigger JSON document overflow.
            String source = DeviceStore.normalize(liveSource);
            if (source.endsWith("/api/live")) {
                return source.substring(0, source.length() - "/api/live".length());
            }
            String preferred = DeviceStore.normalize(preferredUrl);
            if (preferred.length() > 0 && !preferred.endsWith("/api/live")) {
                return preferred;
            }
            return originOnly(source);
        } catch (Exception e) {
            try { return originOnly(liveSource); } catch (Exception ignored) { return ""; }
        }
    }

    private String firstUrl(DeviceStore.Device d) {
        if (d.localUrl != null && d.localUrl.trim().length() > 0) return DeviceStore.normalize(d.localUrl);
        return DeviceStore.normalize(d.remoteUrl);
    }

    private String apiBase(DeviceStore.Device d) {
        return originOnly(firstUrl(d));
    }

    private List<String> liveApiCandidates(DeviceStore.Device d, boolean local) {
        ArrayList<String> out = new ArrayList<>();
        String raw = local ? d.localUrl : d.remoteUrl;
        String origin = originOnly(raw);

        // Stage13: generic ESP/Selpos cards should not hit ESPHome's built-in port 80 first.
        // Some ESPHome web_server JSON endpoints can log "JSON document overflow" when probed.
        // If the local URL has no explicit port, try our custom UI/API port 81 first and skip port 80 probing.
        if (local && !hasExplicitPort(origin)) {
            addLiveCandidate(out, forcePort81(origin));
            return out;
        }

        addLiveCandidate(out, origin);
        addLiveCandidate(out, DeviceStore.normalize(raw));
        if (local) {
            String port81 = forcePort81(origin);
            addLiveCandidate(out, port81);
        }
        return out;
    }

    private boolean hasExplicitPort(String url) {
        try {
            if (url == null || url.trim().length() == 0) return false;
            URL u = new URL(DeviceStore.normalize(url));
            return u.getPort() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void addLiveCandidate(ArrayList<String> out, String base) {
        try {
            if (base == null) return;
            String b = DeviceStore.normalize(base);
            if (b.length() == 0) return;
            if (b.endsWith("/api/live")) {
                if (!out.contains(b)) out.add(b);
            } else {
                String u = b + "/api/live";
                if (!out.contains(u)) out.add(u);
            }
        } catch (Exception ignored) {
        }
    }

    private String forcePort81(String url) {
        try {
            if (url == null || url.trim().length() == 0) return "";
            URL u = new URL(DeviceStore.normalize(url));
            if (u.getPort() > 0) return "";
            return u.getProtocol() + "://" + u.getHost() + ":81";
        } catch (Exception e) {
            return "";
        }
    }

    private String originOnly(String url) {
        try {
            String u = DeviceStore.normalize(url);
            int scheme = u.indexOf("://");
            int start = scheme >= 0 ? scheme + 3 : 0;
            int slash = u.indexOf("/", start);
            if (slash > 0) return u.substring(0, slash);
            return u;
        } catch (Exception e) {
            return url;
        }
    }

    private boolean shouldPreferPort81Open(DeviceStore.Device d) {
        if (d == null) return false;
        String type = d.type == null ? "" : d.type.toUpperCase(java.util.Locale.US);
        String name = d.name == null ? "" : d.name.toUpperCase(java.util.Locale.US);
        return type.contains("BMS") || name.contains("SELPOS") || name.contains("SEPLOS") || name.contains("BMS");
    }

    private boolean isPrivateHostUrl(String url) {
        try {
            URL u = new URL(DeviceStore.normalize(url));
            String h = u.getHost();
            if (h == null) return false;
            h = h.toLowerCase(java.util.Locale.US);
            if (h.equals("localhost") || h.endsWith(".local")) return true;
            if (h.startsWith("192.168.")) return true;
            if (h.startsWith("10.")) return true;
            if (h.startsWith("172.")) {
                String[] p = h.split("\\.");
                if (p.length > 1) {
                    int n = Integer.parseInt(p[1]);
                    return n >= 16 && n <= 31;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private String normalizeOpenUrlForDevice(DeviceStore.Device d, String url) {
        try {
            String u = DeviceStore.normalize(url);
            if (u.length() == 0) return "";
            if (shouldPreferPort81Open(d) && isPrivateHostUrl(u) && !hasExplicitPort(originOnly(u))) {
                String p81 = forcePort81(originOnly(u));
                if (p81.length() > 0) return p81;
            }
            return u;
        } catch (Exception e) {
            return DeviceStore.normalize(url);
        }
    }

    private void openDevice(DeviceStore.Device d) {
        Intent i = new Intent(this, WebViewActivity.class);
        i.putExtra("id", d.id);

        // Stage APP Click-Single-URL: 卡片點擊只開最近一次成功來源，避免 WebView 先內網後外網造成 HTML 串流被取消。
        String openUrl = getDeviceOpenUrl(d);
        if (openUrl == null || openUrl.trim().length() == 0) {
            if (d.localUrl != null && d.localUrl.trim().length() > 0) {
                openUrl = DeviceStore.normalize(d.localUrl);
            } else {
                openUrl = DeviceStore.normalize(d.remoteUrl);
            }
        }
        if (openUrl != null && openUrl.trim().length() > 0) {
            openUrl = normalizeOpenUrlForDevice(d, openUrl);
            i.putExtra("url", openUrl.trim());
        }

        startActivity(i);
    }

    private void showDeviceActionDialog(DeviceStore.Device d) {
        String local = (d.localUrl == null || d.localUrl.trim().length() == 0) ? "未設定" : d.localUrl;
        String remote = (d.remoteUrl == null || d.remoteUrl.trim().length() == 0) ? "未設定" : d.remoteUrl;
        String info = (d.isDefault ? "目前狀態：預設設備\n\n" : "")
                + getDeviceRuntime(d)
                + "\n\n設定內網：" + local
                + "\n設定外網：" + remote;

        new AlertDialog.Builder(this)
                .setTitle(d.name)
                .setMessage(info)
                .setPositiveButton("編輯", (dialog, which) -> showDeviceDialog(d))
                .setNeutralButton("設為預設", (dialog, which) -> {
                    DeviceStore.setDefault(this, d.id);
                    renderDevices();
                })
                .setNegativeButton("刪除", (dialog, which) -> {
                    DeviceStore.delete(this, d.id);
                    renderDevices();
                })
                .show();
    }

    private void showDeviceDialog(DeviceStore.Device editing) {
        boolean isEdit = editing != null;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);

        EditText name = new EditText(this);
        name.setHint("設備名稱，例如 晟格瑞混網機");
        name.setSingleLine(true);
        form.addView(name);

        Spinner type = new Spinner(this);
        String[] types = new String[]{"SGRE", "BMS", "WEB"};
        type.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, types));
        form.addView(type);

        EditText local = new EditText(this);
        local.setHint("內網網址，例如 http://192.168.31.201:81/phone");
        local.setSingleLine(true);
        form.addView(local);

        EditText remote = new EditText(this);
        remote.setHint("外網網址，可空白");
        remote.setSingleLine(true);
        form.addView(remote);

        CheckBox def = new CheckBox(this);
        def.setText("設為預設開啟");
        form.addView(def);

        if (isEdit) {
            name.setText(editing.name);
            local.setText(editing.localUrl);
            remote.setText(editing.remoteUrl);
            def.setChecked(editing.isDefault);
            for (int i = 0; i < types.length; i++) {
                if (types[i].equals(editing.type)) type.setSelection(i);
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(isEdit ? "編輯設備" : "新增設備")
                .setView(form)
                .setPositiveButton("儲存", (dialog, which) -> {
                    DeviceStore.Device d = isEdit ? editing : new DeviceStore.Device();
                    if (!isEdit) d.id = "dev_" + System.currentTimeMillis();
                    d.name = name.getText().toString().trim();
                    d.type = type.getSelectedItem().toString();
                    d.localUrl = DeviceStore.normalize(local.getText().toString());
                    d.remoteUrl = DeviceStore.normalize(remote.getText().toString());
                    if (d.name.length() == 0) d.name = d.type + " 設備";
                    DeviceStore.upsert(this, d, def.isChecked());
                    renderDevices();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String fetch(String url, int cto, int rto) {
        if (url == null || url.length() == 0) return "";
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(cto);
            conn.setReadTimeout(rto);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) return "";
            InputStream is = conn.getInputStream();
            byte[] buf = new byte[4096];
            int n = is.read(buf);
            if (n <= 0) return "";
            return new String(buf, 0, n);
        } catch (Exception e) {
            return "";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private boolean jsonBool(String json, String key) {
        try {
            String mark = "\"" + key + "\"";
            int s = json.indexOf(mark);
            if (s < 0) return false;
            int colon = json.indexOf(":", s + mark.length());
            if (colon < 0) return false;
            int v = colon + 1;
            while (v < json.length() && json.charAt(v) == ' ') v++;
            return json.startsWith("true", v) || json.startsWith("1", v);
        } catch (Exception e) {
            return false;
        }
    }

    private String jsonString(String json, String key) {
        try {
            String mark = "\"" + key + "\"";
            int s = json.indexOf(mark);
            if (s < 0) return "";
            int colon = json.indexOf(":", s + mark.length());
            if (colon < 0) return "";
            int q1 = json.indexOf("\"", colon + 1);
            if (q1 < 0) return "";
            StringBuilder out = new StringBuilder();
            boolean esc = false;
            for (int i = q1 + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (esc) {
                    if (c == 'n') out.append(' ');
                    else if (c == 'r') out.append(' ');
                    else if (c == 't') out.append(' ');
                    else out.append(c);
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '\"') {
                    break;
                } else {
                    out.append(c);
                }
            }
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String num(String json, String key) {
        try {
            String mark = "\"" + key + "\"";
            int s = json.indexOf(mark);
            if (s < 0) return "";
            int colon = json.indexOf(":", s + mark.length());
            if (colon < 0) return "";
            int v = colon + 1;
            while (v < json.length() && (json.charAt(v) == ' ' || json.charAt(v) == '\"')) v++;
            int e = v;
            while (e < json.length() && "-0123456789.".indexOf(json.charAt(e)) >= 0) e++;
            return json.substring(v, e);
        } catch (Exception e) {
            return "";
        }
    }

    private boolean hasGenericCardItems(String json) {
        try {
            return json != null && json.indexOf("\"card\"") >= 0 && json.indexOf("\"items\"") >= 0 && json.indexOf("\"label\"") >= 0 && json.indexOf("\"value\"") >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String cardItemDisplay(String json, String label) {
        String value = cardItemNumber(json, label);
        if (value.length() == 0) return "";
        String unit = cardItemUnit(json, label);
        return formatCardValue(label, value, unit);
    }
    private boolean isSelposLive(String json) {
        try {
            String device = jsonString(json, "device").toUpperCase(java.util.Locale.US);
            String name = jsonString(json, "name").toUpperCase(java.util.Locale.US);
            String title = jsonString(json, "title").toUpperCase(java.util.Locale.US);
            return device.contains("SELPOS") || device.contains("SEPLOS")
                    || name.contains("SELPOS") || name.contains("SEPLOS")
                    || title.contains("SELPOS") || title.contains("SEPLOS");
        } catch (Exception e) {
            return false;
        }
    }

    private String itemNumberByName(String json, String namePart, String key) {
        try {
            String mark = "\"name\"";
            int searchFrom = 0;
            while (true) {
                int s = json.indexOf(mark, searchFrom);
                if (s < 0) return "";
                int colon = json.indexOf(":", s + mark.length());
                if (colon < 0) return "";
                int q1 = json.indexOf("\"", colon + 1);
                if (q1 < 0) return "";
                int q2 = json.indexOf("\"", q1 + 1);
                if (q2 < 0) return "";
                String found = json.substring(q1 + 1, q2);
                if (found.contains(namePart)) {
                    int objEnd = json.indexOf("}", q2);
                    if (objEnd < 0) objEnd = Math.min(json.length(), q2 + 220);
                    String item = json.substring(q2, Math.min(json.length(), objEnd + 1));
                    return num(item, key);
                }
                searchFrom = q2 + 1;
            }
        } catch (Exception e) {
            return "";
        }
    }

    private int selposPackCount(String json) {
        try {
            org.json.JSONObject root = new org.json.JSONObject(json);
            org.json.JSONArray arr = root.optJSONArray("items");
            if (arr == null) return 0;
            int count = 0;
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                if (item.has("soc") || item.has("voltage") || item.has("current") || item.has("power")) count++;
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    private String firstPackNumber(String json, String key) {
        try {
            org.json.JSONObject root = new org.json.JSONObject(json);
            org.json.JSONArray arr = root.optJSONArray("items");
            if (arr == null || arr.length() == 0) return "";
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject item = arr.optJSONObject(i);
                if (item != null && item.has(key)) return String.valueOf(item.optDouble(key));
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }


    private String cardItemNumber(String json, String label) {
        try {
            String mark = "\"label\"";
            int searchFrom = 0;
            while (true) {
                int s = json.indexOf(mark, searchFrom);
                if (s < 0) return "";
                int colon = json.indexOf(":", s + mark.length());
                if (colon < 0) return "";
                int q1 = json.indexOf("\"", colon + 1);
                if (q1 < 0) return "";
                int q2 = json.indexOf("\"", q1 + 1);
                if (q2 < 0) return "";
                String found = json.substring(q1 + 1, q2);
                if (label.equals(found)) {
                    int value = json.indexOf("\"value\"", q2);
                    if (value < 0) return "";
                    int vc = json.indexOf(":", value);
                    if (vc < 0) return "";
                    int start = vc + 1;
                    while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\"')) start++;
                    int end = start;
                    while (end < json.length() && "-0123456789.".indexOf(json.charAt(end)) >= 0) end++;
                    return json.substring(start, end);
                }
                searchFrom = q2 + 1;
            }
        } catch (Exception e) {
            return "";
        }
    }

    private String cardItemUnit(String json, String label) {
        try {
            String mark = "\"label\"";
            int searchFrom = 0;
            while (true) {
                int s = json.indexOf(mark, searchFrom);
                if (s < 0) return "";
                int colon = json.indexOf(":", s + mark.length());
                if (colon < 0) return "";
                int q1 = json.indexOf("\"", colon + 1);
                if (q1 < 0) return "";
                int q2 = json.indexOf("\"", q1 + 1);
                if (q2 < 0) return "";
                String found = json.substring(q1 + 1, q2);
                if (label.equals(found)) {
                    int unit = json.indexOf("\"unit\"", q2);
                    if (unit < 0) return "";
                    int uc = json.indexOf(":", unit);
                    if (uc < 0) return "";
                    int u1 = json.indexOf("\"", uc + 1);
                    if (u1 < 0) return "";
                    int u2 = json.indexOf("\"", u1 + 1);
                    if (u2 < 0) return "";
                    return json.substring(u1 + 1, u2);
                }
                searchFrom = q2 + 1;
            }
        } catch (Exception e) {
            return "";
        }
    }

    private String[] firstCardItems(String json, int maxItems) {
        try {
            java.util.ArrayList<String> out = new java.util.ArrayList<>();
            String mark = "\"label\"";
            int searchFrom = 0;
            while (out.size() < maxItems * 2) {
                int s = json.indexOf(mark, searchFrom);
                if (s < 0) break;
                int colon = json.indexOf(":", s + mark.length());
                if (colon < 0) break;
                int q1 = json.indexOf("\"", colon + 1);
                if (q1 < 0) break;
                int q2 = json.indexOf("\"", q1 + 1);
                if (q2 < 0) break;
                String label = json.substring(q1 + 1, q2);
                String value = cardItemDisplay(json.substring(s), label);
                if (label.length() > 0 && value.length() > 0) {
                    out.add(label);
                    out.add(value);
                }
                searchFrom = q2 + 1;
            }
            return out.toArray(new String[0]);
        } catch (Exception e) {
            return new String[0];
        }
    }

    private String liveVal(String json, String id) {
        try {
            String mark = "\"id\"";
            int searchFrom = 0;
            while (true) {
                int s = json.indexOf(mark, searchFrom);
                if (s < 0) return "";
                int colon = json.indexOf(":", s + mark.length());
                if (colon < 0) return "";
                int idStart = json.indexOf("\"", colon + 1);
                if (idStart < 0) return "";
                int idEnd = json.indexOf("\"", idStart + 1);
                if (idEnd < 0) return "";
                String foundId = json.substring(idStart + 1, idEnd);
                if (id.equals(foundId)) {
                    int v = json.indexOf("\"value\"", idEnd);
                    if (v < 0) return "";
                    int vc = json.indexOf(":", v);
                    if (vc < 0) return "";
                    int valueStart = vc + 1;
                    while (valueStart < json.length() && (json.charAt(valueStart) == ' ' || json.charAt(valueStart) == '\"')) valueStart++;
                    int e = valueStart;
                    while (e < json.length() && "-0123456789.".indexOf(json.charAt(e)) >= 0) e++;
                    return json.substring(valueStart, e);
                }
                searchFrom = idEnd + 1;
            }
        } catch (Exception e) {
            return "";
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
