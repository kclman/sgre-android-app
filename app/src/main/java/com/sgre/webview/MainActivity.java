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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_IMPORT_DEVICES_FILE = 7101;
    private LinearLayout listLayout;
    private boolean autoOpened = false;

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
    }

    @Override
    protected void onPause() {
        super.onPause();
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
        String data = DeviceStore.exportJson(this);

        EditText output = new EditText(this);
        output.setText(data);
        output.setMinLines(8);
        output.setSelectAllOnFocus(true);

        new AlertDialog.Builder(this)
                .setTitle("導出設備檔案")
                .setMessage("以下是設備備份資料，可複製保存為 sgre_devices_backup.json。")
                .setView(output)
                .setPositiveButton("複製", (dialog, which) -> {
                    try {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("SGRE devices", DeviceStore.exportJson(this)));
                        }
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton("關閉", null)
                .show();
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
                    .setMessage("檔案選擇器無法開啟時，可重新安裝檔案管理器，或先用舊版貼上匯入。")
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
                    .setMessage("無法讀取檔案，請確認檔案可開啟，或改用貼上匯入。")
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
        sb.append("錯誤：").append(p.getString("last_error", "")).append("\n");
        sb.append("最後恢復：").append(fmtTime(p.getLong("last_recovery_wall", 0))).append("\n");
        sb.append("恢復訊息：").append(p.getString("last_recovery_msg", "")).append("\n\n");
        sb.append("最後通知：").append(fmtTime(p.getLong("last_notify_wall", 0))).append("\n");
        sb.append("通知標題：").append(p.getString("last_notify_title", "")).append("\n");
        sb.append("通知內容：").append(p.getString("last_notify_text", "")).append("\n\n");
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

        for (DeviceStore.Device d : devices) {
            addDeviceCard(d);
        }
    }

    private void addDeviceCard(DeviceStore.Device d) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        // Compact card layout: keep the device list dense like the earlier working version.
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(bg(Color.rgb(248, 250, 252), 22));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(d.name.length() > 0 ? d.name : "未命名設備");
        name.setTextColor(Color.rgb(42, 54, 68));
        name.setTextSize(22);
        name.setIncludeFontPadding(false);
        name.setTypeface(null, Typeface.BOLD);
        name.setSingleLine(true);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

        CheckBox def = new CheckBox(this);
        def.setChecked(d.isDefault);
        def.setText("預設");
        def.setTextSize(14);
        def.setIncludeFontPadding(false);
        def.setOnClickListener(v -> {
            if (((CheckBox) v).isChecked()) {
                DeviceStore.setDefault(this, d.id);
            } else {
                DeviceStore.clearDefault(this);
            }
            renderDevices();
        });
        row.addView(def);

        box.addView(row);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setPadding(0, dp(8), 0, dp(4));

        TextView voltage = metric("電壓", "--");
        TextView power = metric("功率", "--");
        TextView energy = metric("電量", "--");
        TextView load = metric("負載", "--");

        grid.addView(voltage);
        grid.addView(power);
        grid.addView(energy);
        grid.addView(load);

        box.addView(grid);

        box.setOnClickListener(v -> openDevice(d));
        box.setOnLongClickListener(v -> {
            showDeviceActionDialog(d);
            return true;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        listLayout.addView(box, lp);

        fetchSummary(d, box, voltage, power, energy, load, null);
    }

    private TextView metric(String label, String value) {
        TextView t = new TextView(this);
        setMetricText(t, label, value);
        t.setTextColor(Color.rgb(58, 70, 84));
        t.setTextSize(16);
        t.setTypeface(null, Typeface.BOLD);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setPadding(dp(2), dp(2), dp(3), dp(2));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(0, 0, dp(4), dp(5));
        t.setLayoutParams(lp);
        return t;
    }

    private void setMetricText(TextView target, String label, String value) {
        if (value == null || value.length() == 0) {
            target.setText("");
        } else if ("可連線".equals(value)) {
            target.setText(value);
        } else {
            target.setText(label + " " + value);
        }
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

    private void fetchSummary(DeviceStore.Device d, LinearLayout card, TextView voltage, TextView power, TextView energy, TextView load, TextView urlLabel) {
        new Thread(() -> {
            boolean online = false;
            String v = "--";
            String p = "--";
            String e = "--";
            String l = "--";
            String activeUrlLabel = "目前連線：未連線";

            if ("SGRE".equals(d.type)) {
                String localBase = apiBase(d);
                String remoteBase = originOnly(d.remoteUrl);
                boolean usingRemote = false;

                String alarm = fetch(localBase + "/api/alarm", 900, 1200);
                if (alarm.length() == 0 && remoteBase != null && remoteBase.trim().length() > 0) {
                    alarm = fetch(remoteBase + "/api/alarm", 1200, 1600);
                    if (alarm.length() > 0) usingRemote = true;
                }
                if (alarm.length() > 0) {
                    online = true;
                    String battVolt = num(alarm, "batt_v");
                    String alarmSoc = firstNonEmpty(
                            num(alarm, "batt_soc"),
                            num(alarm, "battery_soc"),
                            num(alarm, "soc"),
                            num(alarm, "battSoc"));
                    if (battVolt.length() > 0) v = battVolt + "V";
                    if (alarmSoc.length() > 0) e = intText(alarmSoc) + "%";
                }

                String live = fetch((usingRemote ? remoteBase : localBase) + "/api/live", 1000, 1600);
                if (live.length() == 0 && !usingRemote && remoteBase != null && remoteBase.trim().length() > 0) {
                    live = fetch(remoteBase + "/api/live", 1200, 1800);
                    if (live.length() > 0) usingRemote = true;
                }

                if (online || live.length() > 0) {
                    activeUrlLabel = usingRemote ? "目前外網：" + shortUrl(d.remoteUrl) : "目前內網：" + shortUrl(d.localUrl);
                }
                if (live.length() > 0) {
                    online = true;
                    String pv = firstNonEmpty(
                            liveVal(live, "v_pv_total_power"),
                            liveVal(live, "pv_total_power"),
                            liveVal(live, "v_pv_power"));
                    String soc = firstNonEmpty(
                            liveVal(live, "v_batt_soc"),
                            liveVal(live, "batt_soc"),
                            liveVal(live, "s_batt_soc"),
                            liveVal(live, "v_battery_soc"),
                            liveVal(live, "battery_soc"),
                            liveVal(live, "battery_percent"),
                            liveVal(live, "b_soc"),
                            liveVal(live, "soc"),
                            liveVal(live, "v_soc"));
                    String loadPct = firstNonEmpty(
                            liveVal(live, "v_load_percent_total"),
                            liveVal(live, "load_percent_total"),
                            liveVal(live, "v_load_pct"));
                    if (pv.length() > 0) p = intText(pv) + "W";
                    if (soc.length() > 0) e = intText(soc) + "%";
                    if (loadPct.length() > 0) l = intText(loadPct) + "%";
                }
            } else {
                String body = "";
                if (d.localUrl != null && d.localUrl.trim().length() > 0) {
                    body = fetch(DeviceStore.normalize(d.localUrl), 1000, 1400);
                    if (body.length() > 0) activeUrlLabel = "目前內網：" + shortUrl(d.localUrl);
                }
                if (body.length() == 0 && d.remoteUrl != null && d.remoteUrl.trim().length() > 0) {
                    body = fetch(DeviceStore.normalize(d.remoteUrl), 1200, 1600);
                    if (body.length() > 0) activeUrlLabel = "目前外網：" + shortUrl(d.remoteUrl);
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
            final String furl = activeUrlLabel;

            runOnUiThread(() -> {
                setMetricText(voltage, "電壓", fv);
                setMetricText(power, "功率", fp);
                setMetricText(energy, "電量", fe);
                setMetricText(load, "負載", fl);
                saveDeviceRuntime(d, ok ? furl : "目前連線：未連線");
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

    private String firstUrl(DeviceStore.Device d) {
        if (d.localUrl != null && d.localUrl.trim().length() > 0) return DeviceStore.normalize(d.localUrl);
        return DeviceStore.normalize(d.remoteUrl);
    }

    private String apiBase(DeviceStore.Device d) {
        return originOnly(firstUrl(d));
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

    private void openDevice(DeviceStore.Device d) {
        Intent i = new Intent(this, WebViewActivity.class);
        i.putExtra("id", d.id);
        startActivity(i);
    }

    private void showDeviceActionDialog(DeviceStore.Device d) {
        String local = (d.localUrl == null || d.localUrl.trim().length() == 0) ? "未設定" : d.localUrl;
        String remote = (d.remoteUrl == null || d.remoteUrl.trim().length() == 0) ? "未設定" : d.remoteUrl;
        String info = getDeviceRuntime(d)
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
