package com.sgre.webview;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class DeviceStore {
    private static final String PREFS = "sgre_devices";
    private static final String KEY_LIST = "device_list";
    private static final String KEY_DEFAULT = "default_id";

    public static class Device {
        public String id = "";
        public String name = "";
        public String type = "SGRE";
        public String localUrl = "";
        public String remoteUrl = "";
        public boolean isDefault = false;

        public String bestUrl() {
            if (localUrl != null && localUrl.trim().length() > 0) return normalize(localUrl);
            if (remoteUrl != null && remoteUrl.trim().length() > 0) return normalize(remoteUrl);
            return "";
        }
    }

    public static List<Device> load(Context ctx) {
        ArrayList<Device> list = new ArrayList<>();
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY_LIST, "[]");
        String def = sp.getString(KEY_DEFAULT, "");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Device d = new Device();
                d.id = o.optString("id", "");
                d.name = o.optString("name", "");
                d.type = o.optString("type", "SGRE");
                d.localUrl = o.optString("local", "");
                d.remoteUrl = o.optString("remote", "");
                d.isDefault = d.id.equals(def);
                if (d.id.length() > 0) list.add(d);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static void save(Context ctx, List<Device> list) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray arr = new JSONArray();
        try {
            for (Device d : list) {
                JSONObject o = new JSONObject();
                o.put("id", d.id);
                o.put("name", d.name);
                o.put("type", d.type);
                o.put("local", d.localUrl);
                o.put("remote", d.remoteUrl);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        sp.edit().putString(KEY_LIST, arr.toString()).apply();
    }

    public static Device get(Context ctx, String id) {
        List<Device> list = load(ctx);
        for (Device d : list) {
            if (d.id.equals(id)) return d;
        }
        return null;
    }

    public static Device getDefault(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String def = sp.getString(KEY_DEFAULT, "");
        List<Device> list = load(ctx);
        for (Device d : list) {
            if (d.id.equals(def)) {
                d.isDefault = true;
                return d;
            }
        }
        return null;
    }

    public static void setDefault(Context ctx, String id) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_DEFAULT, id).apply();
    }

    public static void clearDefault(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_DEFAULT).apply();
    }

    public static void upsert(Context ctx, Device d, boolean makeDefault) {
        List<Device> list = load(ctx);
        if (d.id == null || d.id.length() == 0) d.id = "dev_" + System.currentTimeMillis();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(d.id)) {
                list.set(i, d);
                found = true;
                break;
            }
        }
        if (!found) list.add(d);
        save(ctx, list);
        if (makeDefault) setDefault(ctx, d.id);
    }

    public static void delete(Context ctx, String id) {
        List<Device> list = load(ctx);
        ArrayList<Device> out = new ArrayList<>();
        for (Device d : list) {
            if (!d.id.equals(id)) out.add(d);
        }
        save(ctx, out);
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (id.equals(sp.getString(KEY_DEFAULT, ""))) clearDefault(ctx);
    }

    public static String exportJson(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("default_id", sp.getString(KEY_DEFAULT, ""));
            root.put("devices", new JSONArray(sp.getString(KEY_LIST, "[]")));
            return root.toString(2);
        } catch (Exception e) {
            return "{\"version\":1,\"default_id\":\"\",\"devices\":[]}";
        }
    }

    public static boolean importJson(Context ctx, String raw) {
        if (raw == null) return false;
        try {
            String clean = raw.trim();
            JSONArray arr;
            String def = "";

            if (clean.startsWith("{")) {
                JSONObject root = new JSONObject(clean);
                arr = root.optJSONArray("devices");
                def = root.optString("default_id", "");
                if (arr == null) return false;
            } else {
                arr = new JSONArray(clean);
            }

            ArrayList<Device> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Device d = new Device();
                d.id = o.optString("id", "");
                if (d.id.length() == 0) d.id = "dev_" + System.currentTimeMillis() + "_" + i;
                d.name = o.optString("name", "");
                d.type = o.optString("type", "SGRE");
                d.localUrl = normalize(o.optString("local", o.optString("localUrl", "")));
                d.remoteUrl = normalize(o.optString("remote", o.optString("remoteUrl", "")));
                if (d.name.length() == 0) d.name = d.type + " 設備";
                list.add(d);
            }

            save(ctx, list);
            if (def.length() > 0) setDefault(ctx, def);
            else clearDefault(ctx);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String url = raw.trim();
        if (url.length() == 0) return "";
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }
}
