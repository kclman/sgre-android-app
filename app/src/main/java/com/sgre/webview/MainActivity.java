package com.sgre.webview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_EXPORT_DEVICES = 2401;
    private static final int REQ_IMPORT_DEVICES = 2402;
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
        header.setPadding(dp(18), getStatusBarHeight() + dp(2), dp(18), dp(8));
        header.setBackgroundColor(blue);

        TextView spacer = new TextView(this);
        header.addView(spacer, new LinearLayout.LayoutParams(dp(46), dp(46)));

        TextView title = new TextView(this);
        title.setText("ESP設備管理");
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
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
        listLayout.setPadding(dp(14), dp(4), dp(14), dp(18));
        scroll.addView(listLayout);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        renderDevices();
    }

    private void showQuickActionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("設備管理")
                .setItems(new String[]{"新增設備", "搜尋區網", "導出設備", "匯入設備"}, (dialog, which) -> {
                    if (which == 0) showDeviceDialog(null);
                    if (which == 1) startActivity(new Intent(this, ScanActivity.class));
                    if (which == 2) showExportDevicesDialog();
                    if (which == 3) showImportDevicesDialog();
                })
                .show();
    }


    private void showExportDevicesDialog() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "sgre_devices_backup.json");
            startActivityForResult(intent, REQ_EXPORT_DEVICES);
        } catch (Exception e) {
            Toast.makeText(this, "無法開啟檔案選擇器", Toast.LENGTH_SHORT).show();
        }
    }

    private void showImportDevicesDialog() {
        new AlertDialog.Builder(this)
                .setTitle("匯入設備")
                .setMessage("請選擇之前導出的 JSON 備份檔。匯入後會覆蓋目前設備清單，建議先導出備份。")
                .setPositiveButton("選擇檔案", (dialog, which) -> openImportFilePicker())
                .setNegativeButton("取消", null)
                .show();
    }

    private void openImportFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/json", "text/plain", "application/octet-stream"});
            startActivityForResult(intent, REQ_IMPORT_DEVICES);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, REQ_IMPORT_DEVICES);
            } catch (Exception ex) {
                Toast.makeText(this, "無法開啟檔案選擇器", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT_DEVICES) {
            exportDevicesToUri(uri);
        } else if (requestCode == REQ_IMPORT_DEVICES) {
            importDevicesFromUri(uri);
        }
    }

    private void exportDevicesToUri(Uri uri) {
        try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
            if (os == null) throw new Exception("openOutputStream failed");
            os.write(DeviceStore.exportJson(this).getBytes("UTF-8"));
            os.flush();
            Toast.makeText(this, "設備備份已導出", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("導出失敗")
                    .setMessage("無法寫入備份檔，請重新選擇儲存位置。")
                    .setPositiveButton("確定", null)
                    .show();
        }
    }

    private void importDevicesFromUri(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) throw new Exception("openInputStream failed");
            byte[] buf = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = is.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, "UTF-8"));
            }
            boolean ok = DeviceStore.importJson(this, sb.toString());
            if (ok) {
                renderDevices();
                new AlertDialog.Builder(this)
                        .setTitle("匯入完成")
                        .setMessage("設備清單已由檔案更新。")
                        .setPositiveButton("確定", null)
                        .show();
            } else {
                showImportFailedDialog();
            }
        } catch (Exception e) {
            showImportFailedDialog();
        }
    }

    private void showImportFailedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("匯入失敗")
                .setMessage("檔案格式不正確，請確認選擇的是完整設備 JSON 備份檔。")
                .setPositiveButton("確定", null)
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
            empty.setPadding(dp(25), dp(72), dp(25), dp(20));
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
        box.setPadding(dp(16), dp(10), dp(16), dp(10));
        box.setBackground(bg(Color.rgb(238, 241, 245), 22));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(d.name.length() > 0 ? d.name : "未命名設備");
        name.setTextColor(Color.rgb(42, 54, 68));
        name.setTextSize(19);
        name.setTypeface(null, Typeface.BOLD);
        name.setSingleLine(true);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

        CheckBox def = new CheckBox(this);
        def.setChecked(d.isDefault);
        def.setText("預設");
        def.setTextSize(13);
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
        grid.setPadding(0, dp(4), 0, dp(2));

        TextView voltage = metric("電壓", "");
        TextView power = metric("功率", "");
        TextView energy = metric("電量", "");
        TextView load = metric("負載", "");

        grid.addView(voltage);
        grid.addView(power);
        grid.addView(energy);
        grid.addView(load);

        box.addView(grid);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);

        TextView url = new TextView(this);
        url.setText("內網 " + shortUrl(d.localUrl));
        url.setTextColor(Color.rgb(120, 130, 135));
        url.setTextSize(13);
        url.setSingleLine(true);
        bottom.addView(url, new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(30);
        arrow.setTextColor(Color.rgb(185, 195, 204));
        arrow.setGravity(Gravity.CENTER);
        bottom.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(34)));

        box.addView(bottom);

        box.setOnClickListener(v -> openDevice(d));
        box.setOnLongClickListener(v -> {
            showDeviceActionDialog(d);
            return true;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        listLayout.addView(box, lp);

        fetchSummary(d, box, voltage, power, energy, load);
    }

    private TextView metric(String label, String value) {
        TextView t = new TextView(this);
        t.setText(metricLine(label, value));
        t.setTextColor(Color.rgb(58, 70, 84));
        t.setTextSize(14);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(4), dp(3), dp(4), dp(3));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(0, 0, dp(8), dp(1));
        t.setLayoutParams(lp);
        return t;
    }

    private String metricLine(String label, String value) {
        String v = value == null ? "" : value.trim();
        return v.length() == 0 ? label : (label + " " + v);
    }

    private String shortUrl(String raw) {
        if (raw == null || raw.trim().length() == 0) return "未設定";
        return raw.replace("http://", "").replace("https://", "");
    }

    private void fetchSummary(DeviceStore.Device d, LinearLayout card, TextView voltage, TextView power, TextView energy, TextView load) {
        new Thread(() -> {
            boolean online = false;
            String v = "";
            String p = "--";
            String e = "--";
            String l = "--";

            if ("SGRE".equals(d.type)) {
                String alarm = fetch(apiBase(d) + "/api/alarm", 900, 1200);
                if (alarm.length() == 0 && d.remoteUrl != null && d.remoteUrl.trim().length() > 0) {
                    alarm = fetch(originOnly(d.remoteUrl) + "/api/alarm", 1200, 1600);
                }
                if (alarm.length() > 0) {
                    online = true;
                    String bv = num(alarm, "batt_v");
                    if (bv.length() == 0) bv = num(alarm, "battery_v");
                    if (bv.length() > 0) v = cleanNumber(bv, 1) + "V";
                    String soc = num(alarm, "batt_soc");
                    if (soc.length() == 0) soc = num(alarm, "battery_soc");
                    if (soc.length() == 0) soc = num(alarm, "soc");
                    if (soc.length() > 0) e = whole(soc) + "%";
                }

                String live = fetch(apiBase(d) + "/api/live", 1000, 1600);
                if (live.length() == 0 && d.remoteUrl != null && d.remoteUrl.trim().length() > 0) {
                    live = fetch(originOnly(d.remoteUrl) + "/api/live", 1200, 1800);
                }
                if (live.length() > 0) {
                    online = true;
                    String pv = liveVal(live, "v_pv_total_power");
                    String soc = liveVal(live, "v_batt_soc");
                    if (soc.length() == 0) soc = liveVal(live, "v_battery_soc");
                    if (soc.length() == 0) soc = liveVal(live, "batt_soc");
                    if (soc.length() == 0) soc = liveVal(live, "battery_soc");
                    if (soc.length() == 0) soc = liveVal(live, "soc");
                    String loadPct = liveVal(live, "v_load_percent_total");
                    if (loadPct.length() == 0) loadPct = liveVal(live, "load_percent_total");
                    if (pv.length() > 0) p = whole(pv) + "W";
                    if (soc.length() > 0) e = whole(soc) + "%";
                    if (loadPct.length() > 0) l = whole(loadPct) + "%";
                }
            } else {
                String body = fetch(firstUrl(d), 1000, 1400);
                online = body.length() > 0;
                v = online ? "可連線" : "";
                p = "";
                e = "";
                l = "";
            }

            final boolean ok = online;
            final String fv = v;
            final String fp = p;
            final String fe = e;
            final String fl = l;

            runOnUiThread(() -> {
                voltage.setText(metricLine("電壓", fv));
                power.setText(metricLine("功率", fp));
                energy.setText(metricLine("電量", fe));
                load.setText(metricLine("負載", fl));
                if (!ok) {
                    card.setAlpha(0.55f);
                    card.setBackground(bg(Color.rgb(210, 215, 222), 22));
                } else {
                    card.setAlpha(1f);
                    card.setBackground(bg(Color.rgb(238, 241, 245), 22));
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
        new AlertDialog.Builder(this)
                .setTitle(d.name)
                .setItems(new String[]{"編輯", "設為預設", "刪除"}, (dialog, which) -> {
                    if (which == 0) showDeviceDialog(d);
                    if (which == 1) {
                        DeviceStore.setDefault(this, d.id);
                        renderDevices();
                    }
                    if (which == 2) {
                        DeviceStore.delete(this, d.id);
                        renderDevices();
                    }
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
        InputStream is = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(cto);
            conn.setReadTimeout(rto);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) return "";
            is = conn.getInputStream();
            byte[] buf = new byte[4096];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = is.read(buf)) > 0) {
                sb.append(new String(buf, 0, n));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        } finally {
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private String num(String json, String key) {
        try {
            String mark = "\"" + key + "\":";
            int s = json.indexOf(mark);
            if (s < 0) return "";
            s += mark.length();
            int e = s;
            while (e < json.length() && "-0123456789.".indexOf(json.charAt(e)) >= 0) e++;
            return json.substring(s, e);
        } catch (Exception e) {
            return "";
        }
    }

    private String liveVal(String json, String id) {
        try {
            String mark = "\"id\":\"" + id + "\"";
            int s = json.indexOf(mark);
            if (s < 0) return "";
            int v = json.indexOf("\"value\":", s);
            if (v < 0) return "";
            v += 8;
            int e = v;
            while (e < json.length() && "-0123456789.".indexOf(json.charAt(e)) >= 0) e++;
            return json.substring(v, e);
        } catch (Exception e) {
            return "";
        }
    }

    private String whole(String raw) {
        try {
            return String.valueOf(Math.round(Double.parseDouble(raw)));
        } catch (Exception e) {
            return raw == null ? "" : raw;
        }
    }

    private String cleanNumber(String raw, int digits) {
        try {
            double v = Double.parseDouble(raw);
            if (digits <= 0) return String.valueOf(Math.round(v));
            String out = String.format(java.util.Locale.US, "% .".replace(" ", "") + digits + "f", v);
            while (out.contains(".") && out.endsWith("0")) out = out.substring(0, out.length() - 1);
            if (out.endsWith(".")) out = out.substring(0, out.length() - 1);
            return out;
        } catch (Exception e) {
            return raw == null ? "" : raw;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
