package com.xyb.voxcoach;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;

/** Content-pack catalog (index.json) download / install / auto-update. Blocking methods: call off the main thread. */
public final class PackSync {

    public static final String JSDELIVR = "https://cdn.jsdelivr.net/gh/Jankin2020/learn_en_ja@main/content_packs/index.json";
    public static final String GITHUB_RAW = "https://raw.githubusercontent.com/Jankin2020/learn_en_ja/main/content_packs/index.json";

    public static String fetch(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);
        con.setRequestProperty("Accept", "application/json");
        int code = con.getResponseCode();
        if (code >= 400) {
            con.disconnect();
            throw new Exception("HTTP " + code + (code == 404 ? "（地址不对，或仓库里还没有这个文件）" : ""));
        }
        String s = CloudAudio.readString(con.getInputStream());
        con.disconnect();
        return s;
    }

    public static String resolve(String base, String rel) throws Exception {
        return new URL(new URL(base), rel).toString();
    }

    /** Returns the catalog entries if the JSON is an index (packs with "url"), otherwise null (it is a pack itself). */
    public static JSONArray catalogEntries(String json) {
        try {
            String t = json.trim();
            if (!t.startsWith("{")) return null;
            JSONArray p = new JSONObject(t).optJSONArray("packs");
            if (p == null) return null;
            for (int i = 0; i < p.length(); i++) {
                JSONObject e = p.optJSONObject(i);
                if (e != null && e.optString("url", "").length() > 0) return p;
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Downloads and installs one catalog entry; returns the summary line. */
    public static String installEntry(Context c, String catalogUrl, JSONObject entry) throws Exception {
        String json = fetch(resolve(catalogUrl, entry.getString("url")));
        return Store.installPacks(c, json);
    }

    /** Updates installed packs that have a newer version in the catalog. Returns a message, or null when nothing changed. */
    public static String autoUpdate(Context c) {
        String url = Store.packUrl(c);
        if (url.length() == 0 || Store.packs(c).isEmpty()) return null;
        try {
            JSONArray entries = catalogEntries(fetch(url));
            if (entries == null) return null;
            StringBuilder sb = new StringBuilder();
            int fresh = 0;
            for (int i = 0; i < entries.length(); i++) {
                JSONObject e = entries.optJSONObject(i);
                if (e == null) continue;
                int have = Store.installedVersion(c, e.optString("id"));
                if (have < 0) { fresh++; continue; }
                if (e.optInt("version", 1) > have) {
                    installEntry(c, url, e);
                    sb.append(sb.length() > 0 ? "、" : "").append(e.optString("name", e.optString("id")));
                }
            }
            Store.setLastPackCheck(c, Store.day(0));
            if (sb.length() > 0) return "内容包已自动更新：" + sb + (fresh > 0 ? "（另有 " + fresh + " 个新包可在内容库安装）" : "");
            if (fresh > 0) return "内容库有 " + fresh + " 个新内容包，可在 设置 → 内容库 安装";
        } catch (Exception ignored) { }
        return null;
    }
}
