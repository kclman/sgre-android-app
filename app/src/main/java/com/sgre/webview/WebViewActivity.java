package com.sgre.webview;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

public class WebViewActivity extends Activity {
    private WebView webView;
    private DeviceStore.Device device;
    private boolean triedRemote = false;
    @Override protected void onCreate(Bundle b){ super.onCreate(b); setupSystemBars(); String id=getIntent().getStringExtra("id"); device=DeviceStore.get(this,id); if(device==null){Toast.makeText(this,"找不到設備",Toast.LENGTH_SHORT).show();finish();return;} buildWebView(); loadPrimary(); }
    private boolean isDarkMode(){ return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES; }
    private void setupSystemBars(){ try{boolean dark=isDarkMode(); Window w=getWindow(); if(Build.VERSION.SDK_INT>=21){w.setStatusBarColor(dark?Color.rgb(17,24,39):Color.WHITE);w.setNavigationBarColor(dark?Color.rgb(17,24,39):Color.rgb(243,244,246));} if(Build.VERSION.SDK_INT>=23)w.getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);}catch(Exception ignored){} }
    private int getStatusBarHeight(){ int result=0; try{ int resId=getResources().getIdentifier("status_bar_height","dimen","android"); if(resId>0)result=getResources().getDimensionPixelSize(resId); }catch(Exception ignored){} return result; }
    private boolean needsWebInset(String url){
        String u = url == null ? "" : url.toLowerCase(java.util.Locale.US);
        return u.contains("/view") || u.endsWith("/view") || u.contains("view?");
    }
    private String primaryUrl(){
        String url=DeviceStore.normalize(device.localUrl);
        if(url.length()==0)url=DeviceStore.normalize(device.remoteUrl);
        return url;
    }
    private void applyWebInset(String url){
        if(webView==null)return;
        View parent=(View)webView.getParent();
        if(parent!=null)parent.setPadding(0,needsWebInset(url)?getStatusBarHeight():0,0,0);
    }
    private void buildWebView(){ FrameLayout root=new FrameLayout(this); root.setBackgroundColor(isDarkMode()?Color.rgb(17,24,39):Color.WHITE); root.setPadding(0,0,0,0); webView=new WebView(this); WebSettings s=webView.getSettings(); s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setTextZoom(100);s.setLoadWithOverviewMode(false);s.setUseWideViewPort(false);s.setSupportZoom(false);s.setBuiltInZoomControls(false);s.setDisplayZoomControls(false);s.setAllowFileAccess(false);s.setAllowContentAccess(true);s.setMediaPlaybackRequiresUserGesture(false); if(Build.VERSION.SDK_INT>=21)s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); webView.setWebChromeClient(new WebChromeClient()); webView.setWebViewClient(new WebViewClient(){ @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon){applyWebInset(url);} @Override public void onReceivedError(WebView view,int errorCode,String description,String failingUrl){tryRemoteFallback();} @Override public void onReceivedError(WebView view,WebResourceRequest request,android.webkit.WebResourceError error){ if(Build.VERSION.SDK_INT>=21 && request!=null && request.isForMainFrame())tryRemoteFallback(); }}); root.addView(webView,new FrameLayout.LayoutParams(-1,-1)); setContentView(root); }
    private void loadPrimary(){ triedRemote=false; String url=primaryUrl(); if(url.length()==0){Toast.makeText(this,"設備沒有設定網址",Toast.LENGTH_SHORT).show();finish();return;} applyWebInset(url); webView.loadUrl(url); }
    private void tryRemoteFallback(){ if(triedRemote)return; String remote=DeviceStore.normalize(device.remoteUrl); if(remote.length()==0)return; triedRemote=true; runOnUiThread(()->{Toast.makeText(this,"內網無法開啟，切換外網",Toast.LENGTH_SHORT).show();applyWebInset(remote);webView.loadUrl(remote);}); }
    @Override protected void onPause(){ super.onPause(); if(webView!=null){try{webView.onPause();webView.pauseTimers();}catch(Exception ignored){}} AlarmReceiver.scheduleNext(this,60000L); }
    @Override protected void onResume(){ super.onResume(); AlarmReceiver.cancel(this); if(webView!=null){try{webView.resumeTimers();webView.onResume();}catch(Exception ignored){}} }
    @Override public void onBackPressed(){ if(webView!=null && webView.canGoBack())webView.goBack(); else super.onBackPressed(); }
}
