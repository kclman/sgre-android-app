package com.sgre.webview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ScanActivity extends Activity {
    private LinearLayout listLayout; private TextView statusText; private Button scanButton; private final List<Result> results=Collections.synchronizedList(new ArrayList<>());
    static class Result{String ip="";boolean port6053=false;boolean sgre=false;String webUrl="";String summary="";}
    @Override protected void onCreate(Bundle b){super.onCreate(b);setupSystemBars();buildLayout();}
    private boolean isDarkMode(){return(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
    private void setupSystemBars(){try{boolean dark=isDarkMode();Window w=getWindow();if(Build.VERSION.SDK_INT>=21){w.setStatusBarColor(dark?Color.rgb(9,9,9):Color.WHITE);w.setNavigationBarColor(dark?Color.rgb(9,9,9):Color.rgb(243,244,246));}if(Build.VERSION.SDK_INT>=23)w.getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);}catch(Exception ignored){}}
    private void buildLayout(){boolean dark=isDarkMode();int bg=dark?Color.BLACK:Color.rgb(245,246,250);int card=dark?Color.rgb(18,18,18):Color.WHITE;int text=dark?Color.rgb(255,226,0):Color.rgb(20,24,31);int sub=dark?Color.rgb(180,160,40):Color.rgb(92,99,112);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(22),dp(34),dp(22),dp(18));TextView back=new TextView(this);back.setText("‹");back.setTextSize(38);back.setTextColor(sub);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(46),dp(48)));TextView title=new TextView(this);title.setText("掃描裝置");title.setTextSize(28);title.setTextColor(text);title.setTypeface(null,1);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);LinearLayout scanBox=new LinearLayout(this);scanBox.setGravity(Gravity.CENTER_VERTICAL);scanBox.setPadding(dp(22),dp(14),dp(22),dp(14));scanBox.setBackgroundColor(card);statusText=new TextView(this);statusText.setText("點擊掃描以搜尋區網裝置");statusText.setTextSize(16);statusText.setTextColor(sub);scanBox.addView(statusText,new LinearLayout.LayoutParams(0,-2,1));scanButton=new Button(this);scanButton.setText("掃描");scanButton.setTextColor(Color.BLACK);scanButton.setBackgroundColor(Color.rgb(255,226,0));scanButton.setOnClickListener(v->startScan());scanBox.addView(scanButton,new LinearLayout.LayoutParams(dp(128),dp(56)));LinearLayout.LayoutParams scanLp=new LinearLayout.LayoutParams(-1,-2);scanLp.setMargins(dp(22),dp(8),dp(22),dp(18));root.addView(scanBox,scanLp);ScrollView scroll=new ScrollView(this);listLayout=new LinearLayout(this);listLayout.setOrientation(LinearLayout.VERTICAL);listLayout.setPadding(dp(22),0,dp(22),dp(22));scroll.addView(listLayout);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);}
    private void startScan(){scanButton.setEnabled(false);results.clear();listLayout.removeAllViews();statusText.setText("搜尋中：6053 + 81/80/1314...");new Thread(()->{String prefix=getLocalSubnetPrefix();ExecutorService pool=Executors.newFixedThreadPool(48);for(int i=1;i<=254;i++){final String ip=prefix+i;pool.execute(()->scanHost(ip));}pool.shutdown();try{pool.awaitTermination(14,TimeUnit.SECONDS);}catch(Exception ignored){}runOnUiThread(()->{scanButton.setEnabled(true);renderResults();});}).start();}
    private void scanHost(String ip){Result r=new Result();r.ip=ip;r.port6053=isPortOpen(ip,6053,280);int[] ports=new int[]{81,80,1314};for(int p:ports){String base="http://"+ip+":"+p;String alarm=fetch(base+"/api/alarm",520,760);if(alarm.contains("\"device\":\"SGRE\"")){r.sgre=true;r.webUrl=base;r.summary=str(alarm,"msg")+"｜V="+num(alarm,"batt_v")+" SOC="+num(alarm,"soc")+"%";break;}String live=fetch(base+"/api/live",520,760);if(live.contains("v_pv_total_power")||live.contains("d_battery_voltage")){r.sgre=true;r.webUrl=base;r.summary="已偵測 SGRE /api/live";break;}}if(r.port6053||r.sgre){synchronized(results){results.add(r);}}}
    private void renderResults(){listLayout.removeAllViews();ArrayList<Result> copy; synchronized(results){copy=new ArrayList<>(results);} if(copy.isEmpty()){statusText.setText("沒有找到裝置。請確認同一個 Wi-Fi / 熱點。");return;} statusText.setText("發現 "+copy.size()+" 個裝置"); Collections.sort(copy,(a,b)->{if(a.sgre!=b.sgre)return a.sgre?-1:1;return a.ip.compareTo(b.ip);}); for(Result r:copy)addCard(r);}
    private void addCard(Result r){boolean dark=isDarkMode();int card=dark?Color.rgb(18,18,18):Color.WHITE;int title=dark?Color.rgb(255,226,0):Color.rgb(20,24,31);int sub=dark?Color.rgb(180,160,40):Color.rgb(92,99,112);LinearLayout box=new LinearLayout(this);box.setGravity(Gravity.CENTER_VERTICAL);box.setPadding(dp(18),dp(16),dp(14),dp(16));box.setBackgroundColor(card);LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);TextView t=new TextView(this);t.setText(r.sgre?"晟格瑞混網機":"ESPHome 裝置");t.setTextSize(20);t.setTypeface(null,1);t.setTextColor(title);info.addView(t);TextView s=new TextView(this);String line=r.ip+(r.port6053?":6053":"");if(r.webUrl.length()>0)line+="\n"+r.webUrl;if(r.summary.length()>0)line+="\n"+r.summary;s.setText(line);s.setTextSize(14);s.setTextColor(sub);s.setPadding(0,dp(6),0,0);info.addView(s);box.addView(info,new LinearLayout.LayoutParams(0,-2,1));Button add=new Button(this);add.setText("新增");add.setTextColor(Color.BLACK);add.setBackgroundColor(Color.rgb(255,226,0));add.setOnClickListener(v->showAddDialog(r));box.addView(add,new LinearLayout.LayoutParams(dp(110),dp(54)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(14));listLayout.addView(box,lp);}
    private void showAddDialog(Result r){LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(18),dp(8),dp(18),0);EditText name=new EditText(this);name.setHint("設備名稱");name.setSingleLine(true);name.setText(r.sgre?"晟格瑞混網機":"ESPHome 裝置");form.addView(name);EditText local=new EditText(this);local.setHint("內網網址");local.setSingleLine(true);local.setText(r.webUrl.length()>0?r.webUrl:"http://"+r.ip+":81");form.addView(local);EditText remote=new EditText(this);remote.setHint("外網網址，可空白");remote.setSingleLine(true);form.addView(remote);new AlertDialog.Builder(this).setTitle("新增設備").setView(form).setPositiveButton("儲存",(d,which)->{DeviceStore.Device dev=new DeviceStore.Device();dev.id="dev_"+System.currentTimeMillis();dev.name=name.getText().toString().trim();dev.type=r.sgre?"SGRE":"WEB";dev.localUrl=DeviceStore.normalize(local.getText().toString());dev.remoteUrl=DeviceStore.normalize(remote.getText().toString());if(dev.name.length()==0)dev.name=dev.type+" 設備";DeviceStore.upsert(this,dev,false);finish();}).setNegativeButton("取消",null).show();}
    private String getLocalSubnetPrefix(){try{Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();for(NetworkInterface nif:Collections.list(interfaces)){if(!nif.isUp()||nif.isLoopback())continue;Enumeration<java.net.InetAddress> addrs=nif.getInetAddresses();for(java.net.InetAddress addr:Collections.list(addrs)){if(addr instanceof Inet4Address&&!addr.isLoopbackAddress()){String ip=addr.getHostAddress();int dot=ip.lastIndexOf('.');if(dot>0)return ip.substring(0,dot+1);}}}}catch(Exception ignored){}return"192.168.31.";}
    private boolean isPortOpen(String ip,int port,int timeoutMs){Socket socket=null;try{socket=new Socket();socket.connect(new InetSocketAddress(ip,port),timeoutMs);return true;}catch(Exception e){return false;}finally{try{if(socket!=null)socket.close();}catch(Exception ignored){}}}
    private String fetch(String urlText,int cto,int rto){HttpURLConnection conn=null;try{conn=(HttpURLConnection)new URL(urlText).openConnection();conn.setConnectTimeout(cto);conn.setReadTimeout(rto);conn.setRequestMethod("GET");if(conn.getResponseCode()!=200)return"";InputStream is=conn.getInputStream();byte[] buf=new byte[800];int n=is.read(buf);if(n<=0)return"";return new String(buf,0,n);}catch(Exception e){return"";}finally{if(conn!=null)conn.disconnect();}}
    private String str(String json,String key){try{String mark="\""+key+"\":\"";int s=json.indexOf(mark);if(s<0)return"";s+=mark.length();int e=json.indexOf("\"",s);return e>s?json.substring(s,e):"";}catch(Exception e){return"";}}
    private String num(String json,String key){try{String mark="\""+key+"\":";int s=json.indexOf(mark);if(s<0)return"-";s+=mark.length();int e=s;while(e<json.length()&&"-0123456789.".indexOf(json.charAt(e))>=0)e++;return json.substring(s,e);}catch(Exception e){return"-";}}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);} }
