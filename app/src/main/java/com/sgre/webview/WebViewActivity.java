package com.sgre.webview;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
import android.webkit.ValueCallback;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;

public class WebViewActivity extends Activity {
    private FrameLayout rootLayout;
    private WebView webView;
    private TextView loadingText;
    private DeviceStore.Device device;
    private boolean triedRemote = false;
    private boolean mainFrameLoadError = false;
    private HistoryBridge historyBridge;
    private int lastTopInset = 0;
    private int lastBottomInset = 0;
    private String currentUrlForInsets = "";
    private static final int REQ_AUTO_RULE_EXPORT = 8201;
    private static final int REQ_AUTO_RULE_IMPORT = 8202;
    private static final int REQ_WEB_FILE_CHOOSER = 8203;
    private String pendingAutoRuleExportName = "";
    private String pendingAutoRuleExportJson = "";
    private ValueCallback<Uri[]> webFilePathCallback;
    private String explicitOpenUrl = "";
    private boolean singleUrlMode = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupSystemBars();

        String id = getIntent().getStringExtra("id");
        explicitOpenUrl = DeviceStore.normalize(getIntent().getStringExtra("url"));
        singleUrlMode = explicitOpenUrl != null && explicitOpenUrl.trim().length() > 0;
        device = DeviceStore.get(this, id);
        if (device == null) {
            Toast.makeText(this, "找不到設備", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        buildWebView();
        loadBestAvailable();
    }

    private boolean isDarkMode() {
        int flags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return flags == Configuration.UI_MODE_NIGHT_YES;
    }

    private void setupSystemBars() {
        try {
            boolean dark = isDarkMode();
            Window w = getWindow();

            // V11：把手機原生狀態列 / 三鍵導航列視為 APP 禁區。
            // 不走 edge-to-edge，不讓 WebView 畫到導航列底下。
            if (Build.VERSION.SDK_INT >= 30) {
                w.setDecorFitsSystemWindows(true);
            }

            if (Build.VERSION.SDK_INT >= 21) {
                w.setStatusBarColor(dark ? Color.rgb(17, 24, 39) : Color.WHITE);
                w.setNavigationBarColor(dark ? Color.rgb(17, 24, 39) : Color.rgb(243, 244, 246));
            }
            if (Build.VERSION.SDK_INT >= 29) {
                w.setNavigationBarContrastEnforced(true);
            }
            if (Build.VERSION.SDK_INT >= 23) {
                int flags = dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (!dark && Build.VERSION.SDK_INT >= 26) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                w.getDecorView().setSystemUiVisibility(flags);
            }
        } catch (Exception ignored) {
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
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

    private void applyViewInsetForUrl(String url) {
        if (rootLayout == null) return;
        try {
            currentUrlForInsets = url == null ? "" : url;
            String u = currentUrlForInsets.toLowerCase();

            // V11：Native 系統列禁區法。
            // 上方狀態列與下方三鍵/手勢列都交給 Android 原生當禁區。
            // WebView 只畫在安全內容區；網頁 CSS 不補全域 body padding。
            boolean isPhonePage = u.contains("/phone");

            int top = 0;
            int bottom = lastBottomInset;
            if (bottom <= 0 && Build.VERSION.SDK_INT < 30) {
                bottom = getNavigationBarHeight();
            }
            if (bottom < 0) bottom = 0;

            rootLayout.setPadding(0, top, 0, bottom);
            injectSafeAreaCss(0, isPhonePage);
        } catch (Exception ignored) {
        }
    }

    private void installInsetListener() {
        if (rootLayout == null || Build.VERSION.SDK_INT < 20) return;
        try {
            rootLayout.setOnApplyWindowInsetsListener((v, insets) -> {
                try {
                    lastTopInset = insets.getSystemWindowInsetTop();
                    lastBottomInset = insets.getSystemWindowInsetBottom();
                    applyViewInsetForUrl(currentUrlForInsets);
                } catch (Exception ignored) {
                }
                return insets;
            });
            rootLayout.requestApplyInsets();
        } catch (Exception ignored) {
        }
    }

    private void injectSafeAreaCss(int bottomPx, boolean isPhonePage) {
        if (webView == null) return;
        // V10：Web 頁面不再注入底部 padding，避免不同頁面重複計算造成大空白或覆蓋。
        // 底部禁區統一由 rootLayout padding / Android WindowInsets 處理。
        String js = "(function(){try{"
                + "document.documentElement.style.setProperty('--sgre-app-bottom-inset','0px');"
                + "var s=document.getElementById('__sgre_app_safe_area_css__');"
                + "if(!s){s=document.createElement('style');s.id='__sgre_app_safe_area_css__';document.head.appendChild(s);}"
                + "s.textContent='html,body{box-sizing:border-box!important;}';"
                + "var old=document.getElementById('__sgre_app_bottom_safe_spacer__');"
                + "if(old&&old.parentNode){old.parentNode.removeChild(old);}"
                + "}catch(e){}})();";
        if (Build.VERSION.SDK_INT >= 19) {
            webView.evaluateJavascript(js, null);
        } else {
            webView.loadUrl("javascript:" + js);
        }
    }

    private void buildWebView() {
        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(isDarkMode() ? Color.rgb(17, 24, 39) : Color.WHITE);
        installInsetListener();

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setTextZoom(100);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        historyBridge = new HistoryBridge(getSharedPreferences("sgre_history_" + safeDeviceKey(), MODE_PRIVATE));
        webView.addJavascriptInterface(historyBridge, "SGREAppHistory");
        webView.addJavascriptInterface(new AutoRuleFileBridge(), "SGREAppFile");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                try {
                    if (webFilePathCallback != null) {
                        webFilePathCallback.onReceiveValue(null);
                    }
                    webFilePathCallback = filePathCallback;
                    Intent intent = fileChooserParams != null ? fileChooserParams.createIntent() : new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    if (intent.getType() == null || intent.getType().length() == 0) {
                        intent.setType("application/json");
                    }
                    startActivityForResult(intent, REQ_WEB_FILE_CHOOSER);
                    return true;
                } catch (Exception e) {
                    if (webFilePathCallback != null) {
                        webFilePathCallback.onReceiveValue(null);
                        webFilePathCallback = null;
                    }
                    Toast.makeText(WebViewActivity.this, "無法開啟檔案選擇器", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (handleSgreAppFileUrl(url)) return true;
                return super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= 21 && request != null && request.getUrl() != null) {
                    if (handleSgreAppFileUrl(request.getUrl().toString())) return true;
                }
                return super.shouldOverrideUrlLoading(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                mainFrameLoadError = false;
                if (loadingText != null) loadingText.setVisibility(View.GONE);
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                applyViewInsetForUrl(url);
                if (!mainFrameLoadError && loadingText != null) loadingText.setVisibility(View.GONE);
                if (!mainFrameLoadError) {
                    injectHistoryBridge();
                    injectAutoRuleFileBridge();
                }
                super.onPageFinished(view, url);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                showMainFrameLoadError(description, failingUrl);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (Build.VERSION.SDK_INT >= 21 && request != null && request.isForMainFrame()) {
                    String desc = "";
                    try { desc = error == null ? "" : String.valueOf(error.getDescription()); } catch (Exception ignored) {}
                    String url = "";
                    try { url = request.getUrl() == null ? "" : request.getUrl().toString(); } catch (Exception ignored) {}
                    showMainFrameLoadError(desc, url);
                }
            }
        });

        rootLayout.addView(webView, new FrameLayout.LayoutParams(-1, -1));

        loadingText = new TextView(this);
        loadingText.setText("");
        loadingText.setTextSize(18);
        loadingText.setTextColor(Color.rgb(90, 98, 108));
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setVisibility(View.GONE);
        rootLayout.addView(loadingText, new FrameLayout.LayoutParams(-1, -1));

        setContentView(rootLayout);
        if (Build.VERSION.SDK_INT >= 20) {
            try { rootLayout.requestApplyInsets(); } catch (Exception ignored) {}
        }
    }

    private String safeDeviceKey() {
        try {
            if (device != null && device.id != null && device.id.length() > 0) {
                return device.id.replaceAll("[^A-Za-z0-9_]", "_");
            }
        } catch (Exception ignored) {
        }
        return "default";
    }

    private void injectHistoryBridge() {
        if (webView == null) return;

        String js =
                "(function(){try{"
                + "if(window.__SGRE_APP_HISTORY_BRIDGE__)return;"
                + "window.__SGRE_APP_HISTORY_BRIDGE__=true;"
                + "function pull(k){try{var v=window.SGREAppHistory.getItem(k);var c=localStorage.getItem(k);"
                + "if(v&&(!c||c.length<v.length)){localStorage.setItem(k,v);}}catch(e){}}"
                + "pull('sgre_hist_date');pull('sgre_hist_data');"
                + "var oldSet=localStorage.setItem.bind(localStorage);"
                + "localStorage.setItem=function(k,v){oldSet(k,v);try{"
                + "if(k==='sgre_hist_date'||k==='sgre_hist_data'||String(k).indexOf('sgre_chart_')===0){"
                + "window.SGREAppHistory.setItem(String(k),String(v));}}catch(e){}};"
                + "['sgre_hist_date','sgre_hist_data'].forEach(function(k){try{var v=localStorage.getItem(k);"
                + "if(v)window.SGREAppHistory.setItem(k,v);}catch(e){}});"
                + "}catch(e){}})();";

        if (Build.VERSION.SDK_INT >= 19) {
            webView.evaluateJavascript(js, null);
        } else {
            webView.loadUrl("javascript:" + js);
        }
    }


    private void injectAutoRuleFileBridge() {
        if (webView == null) return;
        String js =
                "(function(){try{"
                + "if(!window.SGREAppFile)return;"
                + "window.__SGRE_APP_FILE_BRIDGE_READY__=true;window.__SGRE_APP_SCHEME_READY__=true;"
                + "window.__SGRE_APP_EXPORT_RULES__=function(fileName,jsonText){try{window.SGREAppFile.exportAutoRules(String(fileName||'sgre_auto_rules.json'),String(jsonText||''));return true;}catch(e){console.log(e);return false;}};"
                + "window.__SGRE_APP_IMPORT_RULES__=function(){try{window.SGREAppFile.importAutoRules();return true;}catch(e){console.log(e);return false;}};"
                + "}catch(e){}})();";
        if (Build.VERSION.SDK_INT >= 19) {
            webView.evaluateJavascript(js, null);
        } else {
            webView.loadUrl("javascript:" + js);
        }
    }

    private boolean handleSgreAppFileUrl(String url) {
        if (url == null) return false;
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            if (scheme == null || !scheme.equalsIgnoreCase("sgreapp")) return false;
            String host = uri.getHost() == null ? "" : uri.getHost();
            String path = uri.getPath() == null ? "" : uri.getPath();
            String action = (host + path).toLowerCase();

            if (action.contains("export")) {
                String name = uri.getQueryParameter("name");
                if (name == null || name.trim().length() == 0) name = "sgre_auto_rules.json";
                final String fileName = name;
                if (webView != null && Build.VERSION.SDK_INT >= 19) {
                    webView.evaluateJavascript("(function(){try{return String(window.__SGRE_PENDING_AUTO_RULE_EXPORT__||'');}catch(e){return '';}})();", value -> {
                        String txt = decodeJsString(value);
                        startAutoRuleExport(fileName, txt);
                    });
                } else {
                    startAutoRuleExport(fileName, "");
                }
                return true;
            }

            if (action.contains("import")) {
                startAutoRuleImport();
                return true;
            }
        } catch (Exception e) {
            Toast.makeText(this, "APP 檔案橋接失敗", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private String decodeJsString(String value) {
        try {
            if (value == null || value.equals("null")) return "";
            return new org.json.JSONArray("[" + value + "]").getString(0);
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    private void startAutoRuleExport(String fileName, String jsonText) {
        try {
            pendingAutoRuleExportName = (fileName == null || fileName.trim().length() == 0) ? "sgre_auto_rules.json" : fileName.trim();
            pendingAutoRuleExportJson = jsonText == null ? "" : jsonText;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, pendingAutoRuleExportName);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(Intent.createChooser(intent, "儲存自動化規則"), REQ_AUTO_RULE_EXPORT);
        } catch (Exception e) {
            Toast.makeText(WebViewActivity.this, "無法開啟規則備份儲存位置", Toast.LENGTH_LONG).show();
        }
    }

    private void startAutoRuleImport() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/json", "text/plain", "application/octet-stream"});
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(Intent.createChooser(intent, "選擇自動化規則備份檔"), REQ_AUTO_RULE_IMPORT);
        } catch (Exception e) {
            Toast.makeText(WebViewActivity.this, "無法開啟規則備份檔案", Toast.LENGTH_LONG).show();
        }
    }

    public class AutoRuleFileBridge {
        @JavascriptInterface
        public boolean isAvailable() {
            return true;
        }

        @JavascriptInterface
        public void exportAutoRules(String fileName, String jsonText) {
            runOnUiThread(() -> startAutoRuleExport(fileName, jsonText));
        }

        @JavascriptInterface
        public void importAutoRules() {
            runOnUiThread(() -> startAutoRuleImport());
        }
    }

    public static class HistoryBridge {
        private final SharedPreferences prefs;

        HistoryBridge(SharedPreferences prefs) {
            this.prefs = prefs;
        }

        @JavascriptInterface
        public String getItem(String key) {
            try {
                if (key == null) return "";
                return prefs.getString(key, "");
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void setItem(String key, String value) {
            try {
                if (key == null || value == null) return;
                if (!key.equals("sgre_hist_date") &&
                        !key.equals("sgre_hist_data") &&
                        !key.startsWith("sgre_chart_")) {
                    return;
                }
                prefs.edit().putString(key, value).apply();
            } catch (Exception ignored) {
            }
        }
    }

    private boolean shouldPreferPort81Open() {
        if (device == null) return false;
        String type = device.type == null ? "" : device.type.toUpperCase(java.util.Locale.US);
        String name = device.name == null ? "" : device.name.toUpperCase(java.util.Locale.US);
        return type.contains("BMS") || name.contains("SELPOS") || name.contains("SEPLOS") || name.contains("BMS");
    }

    private boolean hasExplicitPort(String url) {
        try {
            URL u = new URL(DeviceStore.normalize(url));
            return u.getPort() > 0;
        } catch (Exception e) {
            return false;
        }
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

    private String originOnly(String url) {
        try {
            String u = DeviceStore.normalize(url);
            int scheme = u.indexOf("://");
            int start = scheme >= 0 ? scheme + 3 : 0;
            int slash = u.indexOf("/", start);
            if (slash > 0) return u.substring(0, slash);
            return u;
        } catch (Exception e) {
            return url == null ? "" : url;
        }
    }

    private String forcePort81(String url) {
        try {
            if (url == null || url.trim().length() == 0) return "";
            URL u = new URL(DeviceStore.normalize(url));
            if (u.getPort() > 0) return DeviceStore.normalize(url);
            return u.getProtocol() + "://" + u.getHost() + ":81";
        } catch (Exception e) {
            return DeviceStore.normalize(url);
        }
    }

    private String normalizeOpenUrlForDevice(String url) {
        try {
            String u = DeviceStore.normalize(url);
            if (u.length() == 0) return "";
            if (shouldPreferPort81Open() && isPrivateHostUrl(u) && !hasExplicitPort(originOnly(u))) {
                String p81 = forcePort81(originOnly(u));
                if (p81.length() > 0) return p81;
            }
            return u;
        } catch (Exception e) {
            return DeviceStore.normalize(url);
        }
    }

    private void loadBestAvailable() {
        if (singleUrlMode && explicitOpenUrl != null && explicitOpenUrl.trim().length() > 0) {
            final String target = normalizeOpenUrlForDevice(explicitOpenUrl.trim());
            if (loadingText != null) loadingText.setVisibility(View.GONE);
            applyViewInsetForUrl(target);
            webView.loadUrl(target);
            return;
        }

        final String local = normalizeOpenUrlForDevice(DeviceStore.normalize(device.localUrl));
        final String remote = DeviceStore.normalize(device.remoteUrl);

        if (local.length() == 0 && remote.length() == 0) {
            Toast.makeText(this, "設備沒有設定網址", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (loadingText != null) loadingText.setVisibility(View.GONE);

        new Thread(() -> {
            String target = local;
            boolean localOk = false;

            if (local.length() > 0) {
                localOk = urlReachable(local, 1200, 1500);
            }

            if (!localOk && remote.length() > 0) {
                target = remote;
            }

            final String finalTarget = target;
            final boolean usedRemote = !localOk && remote.length() > 0;

            runOnUiThread(() -> {
                if (finalTarget == null || finalTarget.length() == 0) {
                    loadingText.setText("無法連線");
                    Toast.makeText(this, "內網與外網都未設定或無法開啟", Toast.LENGTH_LONG).show();
                    return;
                }
                if (usedRemote) {
                    triedRemote = true;
                }
                applyViewInsetForUrl(finalTarget);
                webView.loadUrl(finalTarget);
            });
        }).start();
    }

    private boolean urlReachable(String urlText, int connectMs, int readMs) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlText);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(connectMs);
            conn.setReadTimeout(readMs);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            return code >= 200 && code < 500;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void tryRemoteFallback(String toast) {
        if (singleUrlMode) return;
        if (triedRemote) return;
        String remote = DeviceStore.normalize(device.remoteUrl);
        if (remote.length() == 0) return;
        triedRemote = true;
        runOnUiThread(() -> {
            if (loadingText != null) {
                loadingText.setText("");
                loadingText.setVisibility(View.GONE);
            }
            applyViewInsetForUrl(remote);
            webView.loadUrl(remote);
        });
    }


    private void showMainFrameLoadError(String description, String failingUrl) {
        mainFrameLoadError = true;
        try { if (webView != null) webView.stopLoading(); } catch (Exception ignored) {}
        String url = failingUrl == null || failingUrl.length() == 0 ? "目前網址" : failingUrl;
        String desc = description == null || description.length() == 0 ? "連線失敗" : description;
        if (loadingText != null) {
            loadingText.setText("網頁暫時無法連線\n\n" + url + "\n\n" + desc + "\n\n請按返回鍵回首頁，或稍後再點卡片重試。首頁卡片資料會先保留上次成功快取。") ;
            loadingText.setVisibility(View.VISIBLE);
        }
    }

    private String readTextFromUri(Uri uri) throws Exception {
        InputStream is = getContentResolver().openInputStream(uri);
        if (is == null) throw new Exception("openInputStream failed");
        try {
            byte[] buf = new byte[4096];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            try { is.close(); } catch (Exception ignored) {}
        }
    }

    private void writeTextToUri(Uri uri, String text) throws Exception {
        OutputStream os = getContentResolver().openOutputStream(uri, "wt");
        if (os == null) throw new Exception("openOutputStream failed");
        OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
        try {
            writer.write(text == null ? "" : text);
            writer.flush();
        } finally {
            try { writer.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            if (requestCode == REQ_WEB_FILE_CHOOSER) {
                if (webFilePathCallback != null) {
                    Uri[] result = null;
                    if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                        result = new Uri[]{data.getData()};
                    }
                    webFilePathCallback.onReceiveValue(result);
                    webFilePathCallback = null;
                }
                return;
            }
            if (requestCode == REQ_AUTO_RULE_EXPORT) {
                if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                    writeTextToUri(data.getData(), pendingAutoRuleExportJson);
                    Toast.makeText(this, "自動化規則已匯出", Toast.LENGTH_SHORT).show();
                }
                pendingAutoRuleExportJson = "";
                pendingAutoRuleExportName = "";
                return;
            }
            if (requestCode == REQ_AUTO_RULE_IMPORT) {
                if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                    String txt = readTextFromUri(data.getData());
                    String js = "(function(){try{if(window.__SGRE_IMPORT_AUTO_RULES_TEXT__){window.__SGRE_IMPORT_AUTO_RULES_TEXT__(" + JSONObject.quote(txt) + ");}}catch(e){alert('還原失敗：APP 檔案讀取後無法傳給網頁');}})();";
                    if (Build.VERSION.SDK_INT >= 19) {
                        webView.evaluateJavascript(js, null);
                    } else {
                        webView.loadUrl("javascript:" + js);
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "自動化規則檔案處理失敗", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            try {
                webView.onPause();
                webView.pauseTimers();
            } catch (Exception ignored) {
            }
        }
        AlarmReceiver.scheduleNext(this, 60000L);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AlarmReceiver.cancel(this);
        if (webView != null) {
            try {
                webView.resumeTimers();
                webView.onResume();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mainFrameLoadError || singleUrlMode) {
            finish();
            return;
        }
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
