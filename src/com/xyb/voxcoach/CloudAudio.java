package com.xyb.voxcoach;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;

/**
 * OpenAI-compatible audio endpoints (SiliconFlow CosyVoice/SenseVoice, OpenAI tts-1/whisper-1, ...).
 * All methods are blocking: call them off the main thread.
 */
public final class CloudAudio {

    private static String base(Context c) {
        String b = Store.audioBase(c).trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    /** Synthesizes text to an mp3 file with the provider chosen in settings (OpenAI-compatible or Azure). */
    public static File tts(Context c, String text, String lang, float speed) throws Exception {
        if ("azure".equals(Store.ttsMode(c))) return azureTts(c, text, lang, speed, Store.azureVoice(c, lang));
        return openaiTts(c, text, lang, speed);
    }

    private static float clampSpeed(float speed) {
        return Math.round(Math.max(0.25f, Math.min(4f, speed)) * 100) / 100f;
    }

    private static File cacheFile(Context c, String key) {
        File dir = new File(c.getCacheDir(), "tts");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, md5(key) + ".mp3");
    }

    private static boolean cached(File f) {
        if (f.exists() && f.length() > 0) { f.setLastModified(System.currentTimeMillis()); return true; } // LRU for trimCache
        return false;
    }

    /** OpenAI-compatible /audio/speech (SiliconFlow CosyVoice, OpenAI tts-1 / gpt-4o-mini-tts, ...). */
    public static File openaiTts(Context c, String text, String lang, float speed) throws Exception {
        String model = Store.ttsModel(c), voice = Store.ttsVoice(c, lang);
        float sp = clampSpeed(speed);
        File f = cacheFile(c, model + "|" + voice + "|" + sp + "|" + text);
        if (cached(f)) return f;
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("input", text);
        if (voice.length() > 0) body.put("voice", voice);
        body.put("response_format", "mp3");
        body.put("speed", sp);
        if (model.startsWith("gpt-4o")) {
            body.put("instructions", "ja".equals(lang)
                    ? "Speak natural standard Japanese (Tokyo accent) clearly, like a friendly native teacher."
                    : "Speak natural, clear " + ("UK".equals(Store.accent(c)) ? "British" : "American") + " English like a friendly native teacher.");
        }
        HttpURLConnection con = open(c, base(c) + "/audio/speech");
        con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        OutputStream os = con.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();
        return download(con, f, "在线 TTS");
    }

    /** Microsoft Azure neural TTS via REST + SSML. */
    public static File azureTts(Context c, String text, String lang, float speed, String voice) throws Exception {
        String key = Store.azureKey(c), region = Store.azureRegion(c).trim();
        if (key.length() == 0) throw new Exception("还没有填写 Azure 语音的 Key：设置 → 语音环境 → 微软 Azure");
        if (region.length() == 0) region = "japaneast";
        float sp = Math.max(0.5f, Math.min(2f, clampSpeed(speed)));
        File f = cacheFile(c, "azure|" + region + "|" + voice + "|" + sp + "|" + text);
        if (cached(f)) return f;
        String url = region.startsWith("http") ? region : "https://" + region + ".tts.speech.microsoft.com/cognitiveservices/v1";
        String xmlLang = voice.length() >= 5 && voice.charAt(2) == '-' ? voice.substring(0, 5) : ("ja".equals(lang) ? "ja-JP" : "en-US");
        int pct = Math.round((sp - 1f) * 100);
        String ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='" + xmlLang + "'>"
                + "<voice name='" + xmlEsc(voice) + "'><prosody rate='" + (pct >= 0 ? "+" : "") + pct + "%'>" + xmlEsc(text)
                + "</prosody></voice></speak>";
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setConnectTimeout(15000);
        con.setReadTimeout(60000);
        con.setRequestProperty("Ocp-Apim-Subscription-Key", key);
        con.setRequestProperty("Content-Type", "application/ssml+xml");
        con.setRequestProperty("X-Microsoft-OutputFormat", "audio-24khz-96kbitrate-mono-mp3");
        con.setRequestProperty("User-Agent", "VoxCoach");
        OutputStream os = con.getOutputStream();
        os.write(ssml.getBytes("UTF-8"));
        os.close();
        return download(con, f, "Azure 语音");
    }

    private static String xmlEsc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&apos;").replace("\"", "&quot;");
    }

    private static File download(HttpURLConnection con, File f, String label) throws Exception {
        int code = con.getResponseCode();
        String ct = con.getContentType() == null ? "" : con.getContentType();
        if (code >= 400 || ct.contains("json") || ct.contains("text/")) {
            String err = readString(code >= 400 ? con.getErrorStream() : con.getInputStream());
            con.disconnect();
            String hint = code == 401 ? "（Key 不对，或 Key 与区域不匹配）" : code == 400 ? "（音色名可能不存在或该区域不支持）" : "";
            throw new Exception(label + "失败（HTTP " + code + "）" + hint + "：" + shortErr(err));
        }
        File dir = f.getParentFile();
        File tmp = new File(dir, f.getName() + ".part");
        InputStream is = con.getInputStream();
        FileOutputStream fo = new FileOutputStream(tmp);
        byte[] buf = new byte[8192];
        int n;
        long total = 0;
        while ((n = is.read(buf)) > 0) { fo.write(buf, 0, n); total += n; }
        fo.close();
        is.close();
        con.disconnect();
        if (total < 200) { tmp.delete(); throw new Exception(label + "返回的音频为空"); }
        tmp.renameTo(f);
        trimCache(dir, 400);
        return f;
    }

    /** Transcribes a WAV file. Returns plain text. */
    public static String transcribe(Context c, byte[] wav, String lang) throws Exception {
        String model = Store.asrModel(c);
        String boundary = "----vox" + System.currentTimeMillis();
        HttpURLConnection con = open(c, base(c) + "/audio/transcriptions");
        con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        field(bo, boundary, "model", model);
        String m = model.toLowerCase();
        if (m.contains("whisper") || m.contains("transcribe")) field(bo, boundary, "language", "ja".equals(lang) ? "ja" : "en");
        bo.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n").getBytes("UTF-8"));
        bo.write(wav);
        bo.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
        OutputStream os = con.getOutputStream();
        bo.writeTo(os);
        os.close();
        int code = con.getResponseCode();
        String text = readString(code >= 400 ? con.getErrorStream() : con.getInputStream());
        con.disconnect();
        if (code >= 400) throw new Exception("在线识别失败（HTTP " + code + "）：" + shortErr(text));
        String t;
        try { t = new JSONObject(text).optString("text", ""); } catch (Exception e) { t = text; }
        // SenseVoice may emit tags like <|ja|><|NEUTRAL|><|Speech|>
        t = t.replaceAll("<\\|[^|>]*\\|>", "").trim();
        return t;
    }

    private static void field(ByteArrayOutputStream bo, String boundary, String name, String value) throws Exception {
        bo.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes("UTF-8"));
    }

    private static HttpURLConnection open(Context c, String url) throws Exception {
        String key = Store.audioKey(c);
        if (key.length() == 0) throw new Exception("还没有配置在线语音接口：设置 → 语音环境");
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setConnectTimeout(15000);
        con.setReadTimeout(60000);
        con.setRequestProperty("Authorization", "Bearer " + key);
        return con;
    }

    private static String shortErr(String s) {
        if (s == null) return "";
        try {
            JSONObject o = new JSONObject(s);
            JSONObject e = o.optJSONObject("error");
            if (e != null) s = e.optString("message", s);
            else if (o.has("message")) s = o.optString("message");
        } catch (Exception ignored) { }
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    static String readString(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return bo.toString("UTF-8");
    }

    static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return String.valueOf(Math.abs(s.hashCode()));
        }
    }

    private static void trimCache(File dir, int max) {
        File[] fs = dir.listFiles();
        if (fs == null || fs.length <= max) return;
        Arrays.sort(fs, new Comparator<File>() {
            public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); }
        });
        for (int i = 0; i < fs.length - max; i++) fs[i].delete();
    }

    /** Wraps 16-bit mono PCM into a WAV container. */
    public static byte[] wav(byte[] pcm, int sampleRate) {
        int dataLen = pcm.length;
        byte[] out = new byte[44 + dataLen];
        int byteRate = sampleRate * 2;
        writeStr(out, 0, "RIFF");
        writeInt(out, 4, 36 + dataLen);
        writeStr(out, 8, "WAVE");
        writeStr(out, 12, "fmt ");
        writeInt(out, 16, 16);
        writeShort(out, 20, 1);
        writeShort(out, 22, 1);
        writeInt(out, 24, sampleRate);
        writeInt(out, 28, byteRate);
        writeShort(out, 32, 2);
        writeShort(out, 34, 16);
        writeStr(out, 36, "data");
        writeInt(out, 40, dataLen);
        System.arraycopy(pcm, 0, out, 44, dataLen);
        return out;
    }

    private static void writeStr(byte[] b, int off, String s) { for (int i = 0; i < 4; i++) b[off + i] = (byte) s.charAt(i); }
    private static void writeInt(byte[] b, int off, int v) {
        b[off] = (byte) v; b[off + 1] = (byte) (v >> 8); b[off + 2] = (byte) (v >> 16); b[off + 3] = (byte) (v >> 24);
    }
    private static void writeShort(byte[] b, int off, int v) { b[off] = (byte) v; b[off + 1] = (byte) (v >> 8); }
}
