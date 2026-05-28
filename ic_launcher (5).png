package com.sgre.webview;

import android.app.Activity;
import android.content.SharedPreferences;
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
    private HistoryBridge historyBridge;
    private int lastTopInset = 0;
    private int lastBottomInset = 0;
    private String currentUrlForInsets = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupSystemBars();

        String id = getIntent().getStringExtra("id");
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

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                applyViewInsetForUrl(url);
                if (loadingText != null) loadingText.setVisibility(View.GONE);
                injectHistoryBridge();
                super.onPageFinished(view, url);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                tryRemoteFallback("");
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (Build.VERSION.SDK_INT >= 21 && request != null && request.isForMainFrame()) {
                    tryRemoteFallback("");
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

    private void loadBestAvailable() {
        final String local = DeviceStore.normalize(device.localUrl);
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
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
