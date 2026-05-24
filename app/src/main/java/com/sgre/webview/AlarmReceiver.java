package com.sgre.webview;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class AlarmReceiver extends BroadcastReceiver {
    public static final String ACTION_CHECK_ALARM="com.sgre.webview.CHECK_ALARM";
    private static final String CHANNEL_ID="sgre_alarm_only";
    private static final int ALARM_ID=3302;
    @Override public void onReceive(Context context, Intent intent){ if(intent==null||!ACTION_CHECK_ALARM.equals(intent.getAction()))return; new Thread(()->{ DeviceStore.Device d=DeviceStore.getDefault(context); boolean alarm=false; String msg=""; String code=""; long next=60000L; if(d!=null&&"SGRE".equals(d.type)){ String body=fetch(DeviceStore.normalize(d.localUrl)+"/api/alarm"); if(body.length()==0&&d.remoteUrl!=null&&d.remoteUrl.length()>0)body=fetch(DeviceStore.normalize(d.remoteUrl)+"/api/alarm"); alarm=body.contains("\"alarm\":true"); msg=str(body,"msg"); code=num(body,"code"); } if(alarm){ next=15000L; showAlarmNotification(context,msg.length()==0?"SGRE 警報":msg,"警報代碼："+(code.length()==0?"-":code)); } scheduleNext(context,next); }).start(); }
    public static void scheduleNext(Context context,long delayMs){ try{ Intent intent=new Intent(context,AlarmReceiver.class); intent.setAction(ACTION_CHECK_ALARM); PendingIntent pi=PendingIntent.getBroadcast(context,3001,intent,Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE:PendingIntent.FLAG_UPDATE_CURRENT); AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE); if(am!=null)am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+delayMs,pi);}catch(Exception ignored){} }
    public static void cancel(Context context){ try{ Intent intent=new Intent(context,AlarmReceiver.class); intent.setAction(ACTION_CHECK_ALARM); PendingIntent pi=PendingIntent.getBroadcast(context,3001,intent,Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE:PendingIntent.FLAG_UPDATE_CURRENT); AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE); if(am!=null)am.cancel(pi);}catch(Exception ignored){} }
    private static String fetch(String urlText){ if(urlText==null||urlText.length()==0||urlText.equals("/api/alarm"))return""; HttpURLConnection conn=null; try{URL url=new URL(urlText); conn=(HttpURLConnection)url.openConnection(); conn.setConnectTimeout(4000);conn.setReadTimeout(4000);conn.setRequestMethod("GET"); if(conn.getResponseCode()!=200)return""; InputStream is=conn.getInputStream(); byte[] buf=new byte[512]; int n=is.read(buf); if(n<=0)return""; return new String(buf,0,n);}catch(Exception e){return"";}finally{if(conn!=null)conn.disconnect();} }
    private static void showAlarmNotification(Context context,String title,String text){ createChannel(context); Intent openIntent=new Intent(context,MainActivity.class); openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP); PendingIntent pi=PendingIntent.getActivity(context,0,openIntent,Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0); Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,CHANNEL_ID):new Notification.Builder(context); b.setContentTitle(title).setContentText(text).setSmallIcon(android.R.drawable.ic_dialog_alert).setContentIntent(pi).setAutoCancel(true); if(Build.VERSION.SDK_INT>=21){b.setCategory(Notification.CATEGORY_ALARM);b.setVisibility(Notification.VISIBILITY_PUBLIC);b.setPriority(Notification.PRIORITY_HIGH);} NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE); if(nm!=null)nm.notify(ALARM_ID,b.build()); }
    private static void createChannel(Context context){ if(Build.VERSION.SDK_INT>=26){ NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE); if(nm==null)return; NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"SGRE 警報通知",NotificationManager.IMPORTANCE_HIGH); ch.setDescription("只有 SGRE 發生警報時才顯示通知"); nm.createNotificationChannel(ch);} }
    private static String str(String json,String key){ try{String mark="\""+key+"\":\"";int s=json.indexOf(mark);if(s<0)return"";s+=mark.length();int e=json.indexOf("\"",s);return e>s?json.substring(s,e):"";}catch(Exception e){return"";} }
    private static String num(String json,String key){ try{String mark="\""+key+"\":";int s=json.indexOf(mark);if(s<0)return"";s+=mark.length();int e=s;while(e<json.length()&&"-0123456789".indexOf(json.charAt(e))>=0)e++;return json.substring(s,e);}catch(Exception e){return"";} }
}
