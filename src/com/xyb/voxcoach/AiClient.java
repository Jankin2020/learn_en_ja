package com.xyb.voxcoach;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Minimal OpenAI-compatible /chat/completions client (DeepSeek, Qwen/DashScope, Kimi, OpenAI, ...). */
public final class AiClient {

    public interface Cb {
        void ok(String content);
        void fail(String error);
    }

    public static String endpoint(String base) {
        String b = base.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (b.endsWith("/chat/completions")) return b;
        return b + "/chat/completions";
    }

    public static void chat(Context c, final JSONArray messages, final double temperature, final Cb cb) {
        final String base = Store.apiBase(c), key = Store.apiKey(c), model = Store.apiModel(c);
        final Handler h = new Handler(Looper.getMainLooper());
        if (key.length() == 0) {
            cb.fail("还没有配置 AI 接口：请到「设置」里填写 Base URL、API Key 和模型名");
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                String result = null, err = null;
                HttpURLConnection con = null;
                try {
                    con = (HttpURLConnection) new URL(endpoint(base)).openConnection();
                    con.setRequestMethod("POST");
                    con.setDoOutput(true);
                    con.setConnectTimeout(20000);
                    con.setReadTimeout(90000);
                    con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    con.setRequestProperty("Authorization", "Bearer " + key);
                    JSONObject body = new JSONObject();
                    body.put("model", model);
                    body.put("messages", messages);
                    body.put("temperature", temperature);
                    body.put("stream", false);
                    byte[] data = body.toString().getBytes("UTF-8");
                    OutputStream os = con.getOutputStream();
                    os.write(data);
                    os.close();
                    int code = con.getResponseCode();
                    InputStream is = code >= 400 ? con.getErrorStream() : con.getInputStream();
                    String text = read(is);
                    if (code >= 400) {
                        String m = text;
                        try {
                            JSONObject e = new JSONObject(text).optJSONObject("error");
                            if (e != null) m = e.optString("message", text);
                        } catch (Exception ignored) { }
                        if (m.length() > 300) m = m.substring(0, 300);
                        err = "HTTP " + code + "：" + m;
                    } else {
                        JSONObject r = new JSONObject(text);
                        String content = r.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "");
                        // strip reasoning blocks some models emit
                        int t = content.indexOf("</think>");
                        if (t >= 0) content = content.substring(t + 8);
                        result = content.trim();
                    }
                } catch (Exception e) {
                    err = e.getClass().getSimpleName() + "：" + e.getMessage();
                } finally {
                    if (con != null) con.disconnect();
                }
                final String fr = result, fe = err;
                h.post(new Runnable() {
                    public void run() {
                        if (fr != null) cb.ok(fr);
                        else cb.fail(fe == null ? "未知错误" : fe);
                    }
                });
            }
        }).start();
    }

    private static String read(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return bo.toString("UTF-8");
    }

    public static JSONObject json(String s) {
        if (s == null) return null;
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        if (a < 0 || b <= a) return null;
        try { return new JSONObject(s.substring(a, b + 1)); } catch (Exception e) { return null; }
    }

    public static JSONArray jsonArray(String s) {
        if (s == null) return null;
        int a = s.indexOf('['), b = s.lastIndexOf(']');
        if (a < 0 || b <= a) return null;
        try { return new JSONArray(s.substring(a, b + 1)); } catch (Exception e) { return null; }
    }

    public static JSONObject msg(String role, String content) {
        JSONObject o = new JSONObject();
        try { o.put("role", role); o.put("content", content); } catch (Exception ignored) { }
        return o;
    }
}
