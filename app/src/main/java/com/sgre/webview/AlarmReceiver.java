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
    public static final String ACTION_CHECK_ALARM = "com.sgre.webview.CHECK_ALARM";
    public static final String DEBUG_PREF = "sgre_alarm_debug";
    private static final String CHANNEL_ID = "sgre_alarm_only";
    private static final int ALARM_ID = 3302;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_CHECK_ALARM.equals(intent.getAction())) return;
        new Thread(() -> runCheck(context, true, "background_receiver")).start();
    }

    public static String runDebugCheckNow(Context context, boolean notifyWhenAlarm) {
        return runCheck(context, notifyWhenAlarm, "manual_debug_check");
    }

    private static String runCheck(Context context, boolean notifyWhenAlarm, String source) {
        long started = System.currentTimeMillis();
        String urlUsed = "";
        String body = "";
        String error = "";
        boolean alarm = false;
        String msg = "";
        String code = "";
        long next = 60000L;

        try {
            DeviceStore.Device d = DeviceStore.getDefault(context);
            if (d == null) {
                error = "沒有預設設備";
            } else if (!"SGRE".equals(d.type)) {
                error = "預設設備不是 SGRE：" + d.type;
            } else {
                String localUrl = DeviceStore.normalize(d.localUrl) + "/api/alarm";
                FetchResult local = fetchWithStatus(localUrl);
                urlUsed = localUrl;
                body = local.body;
                error = local.error;

                if (body.length() == 0 && d.remoteUrl != null && d.remoteUrl.length() > 0) {
                    String remoteUrl = DeviceStore.normalize(d.remoteUrl) + "/api/alarm";
                    FetchResult remote = fetchWithStatus(remoteUrl);
                    urlUsed = remoteUrl;
                    body = remote.body;
                    error = remote.error;
                }

                alarm = body.contains("\"alarm\":true");
                msg = str(body, "msg");
                code = num(body, "code");
            }

            if (alarm) {
                next = 15000L;
                if (notifyWhenAlarm) {
                    showAlarmNotification(context,
                            msg.length() == 0 ? "SGRE 警報" : msg,
                            "警報代碼：" + (code.length() == 0 ? "-" : code));
                }
            }
        } catch (Exception e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        saveDebug(context, source, started, urlUsed, body, error, alarm, msg, code, next);
        scheduleNext(context, next);
        return alarm ? "alarm=true code=" + code + " msg=" + msg : "alarm=false " + (error.length() > 0 ? error : "正常");
    }

    public static void scheduleNext(Context context, long delayMs) {
        try {
            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.setAction(ACTION_CHECK_ALARM);
            PendingIntent pi = PendingIntent.getBroadcast(context, 3001, intent,
                    Build.VERSION.SDK_INT >= 23
                            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                            : PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                long at = SystemClock.elapsedRealtime() + delayMs;
                if (Build.VERSION.SDK_INT >= 23) {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
                } else {
                    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
                }
            }
            context.getSharedPreferences(DEBUG_PREF, Context.MODE_PRIVATE).edit()
                    .putLong("last_schedule_wall", System.currentTimeMillis())
                    .putLong("last_schedule_delay", delayMs)
                    .apply();
        } catch (Exception e) {
            context.getSharedPreferences(DEBUG_PREF, Context.MODE_PRIVATE).edit()
                    .putString("last_schedule_error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .apply();
        }
    }

    public static void cancel(Context context) {
        try {
            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.setAction(ACTION_CHECK_ALARM);
            PendingIntent pi = PendingIntent.getBroadcast(context, 3001, intent,
                    Build.VERSION.SDK_INT >= 23
                            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                            : PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pi);
            context.getSharedPreferences(DEBUG_PREF, Context.MODE_PRIVATE).edit()
                    .putLong("last_cancel_wall", System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public static void showAlarmNotification(Context context, String title, String text) {
        createChannel(context);
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, openIntent,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        b.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pi)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT >= 21) {
            b.setCategory(Notification.CATEGORY_ALARM);
            b.setVisibility(Notification.VISIBILITY_PUBLIC);
            b.setPriority(Notification.PRIORITY_HIGH);
        }
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(ALARM_ID, b.build());
            context.getSharedPreferences(DEBUG_PREF, Context.MODE_PRIVATE).edit()
                    .putLong("last_notify_wall", System.currentTimeMillis())
                    .putString("last_notify_title", title)
                    .putString("last_notify_text", text)
                    .apply();
        }
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "SGRE 警報通知", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("只有 SGRE 發生警報時才顯示通知");
            nm.createNotificationChannel(ch);
        }
    }

    private static class FetchResult {
        String body = "";
        String error = "";
    }

    private static FetchResult fetchWithStatus(String urlText) {
        FetchResult r = new FetchResult();
        if (urlText == null || urlText.length() == 0 || urlText.equals("/api/alarm")) {
            r.error = "URL 空白";
            return r;
        }
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlText);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("GET");
            int rc = conn.getResponseCode();
            if (rc != 200) {
                r.error = "HTTP " + rc;
                return r;
            }
            InputStream is = conn.getInputStream();
            byte[] buf = new byte[768];
            int n = is.read(buf);
            if (n <= 0) {
                r.error = "空回應";
                return r;
            }
            r.body = new String(buf, 0, n);
            r.error = "";
            return r;
        } catch (Exception e) {
            r.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            return r;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void saveDebug(Context context, String source, long started, String url, String body, String error,
                                  boolean alarm, String msg, String code, long next) {
        String shortBody = body == null ? "" : body;
        if (shortBody.length() > 600) shortBody = shortBody.substring(0, 600);
        context.getSharedPreferences(DEBUG_PREF, Context.MODE_PRIVATE).edit()
                .putLong("last_poll_wall", System.currentTimeMillis())
                .putLong("last_poll_started", started)
                .putString("last_source", source == null ? "" : source)
                .putString("last_url", url == null ? "" : url)
                .putString("last_body", shortBody)
                .putString("last_error", error == null ? "" : error)
                .putBoolean("last_alarm", alarm)
                .putString("last_msg", msg == null ? "" : msg)
                .putString("last_code", code == null ? "" : code)
                .putLong("last_next_delay", next)
                .apply();
    }

    private static String str(String json, String key) {
        try {
            String mark = "\"" + key + "\":\"";
            int s = json.indexOf(mark);
            if (s < 0) return "";
            s += mark.length();
            int e = json.indexOf("\"", s);
            return e > s ? json.substring(s, e) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String num(String json, String key) {
        try {
            String mark = "\"" + key + "\":";
            int s = json.indexOf(mark);
            if (s < 0) return "";
            s += mark.length();
            int e = s;
            while (e < json.length() && "-0123456789".indexOf(json.charAt(e)) >= 0) e++;
            return json.substring(s, e);
        } catch (Exception e) {
            return "";
        }
    }
}
