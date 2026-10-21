package com.xyb.voxcoach;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Local persistence + adaptive learning logic. */
public final class Store {

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("vox", Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ basic settings

    public static String lang(Context c) { return sp(c).getString("lang", "en"); }
    public static void setLang(Context c, String l) { sp(c).edit().putString("lang", l).apply(); }
    public static boolean ja(Context c) { return "ja".equals(lang(c)); }

    public static boolean onboarded(Context c) { return sp(c).getBoolean("onboarded", false); }
    public static void setOnboarded(Context c) { sp(c).edit().putBoolean("onboarded", true).apply(); }

    public static int level(Context c, String lang) { return sp(c).getInt("level_" + lang, 1); }
    public static void setLevel(Context c, String lang, int lv) {
        sp(c).edit().putInt("level_" + lang, Math.max(1, Math.min(4, lv))).putString("recent_" + lang, "[]").apply();
    }

    public static int goal(Context c) { return sp(c).getInt("goal", 20); }
    public static void setGoal(Context c, int g) { sp(c).edit().putInt("goal", g).apply(); }

    public static boolean showZh(Context c) { return sp(c).getBoolean("show_zh", true); }
    public static void setShowZh(Context c, boolean b) { sp(c).edit().putBoolean("show_zh", b).apply(); }

    public static String accent(Context c) { return sp(c).getString("accent", "US"); }
    public static void setAccent(Context c, String a) { sp(c).edit().putString("accent", a).apply(); }

    public static float ttsRate(Context c) { return sp(c).getFloat("tts_rate", 0.95f); }
    public static void setTtsRate(Context c, float r) { sp(c).edit().putFloat("tts_rate", r).apply(); }

    // AI (OpenAI-compatible)
    public static String apiBase(Context c) { return sp(c).getString("api_base", "https://api.deepseek.com/v1"); }
    public static String apiKey(Context c) { return sp(c).getString("api_key", ""); }
    public static String apiModel(Context c) { return sp(c).getString("api_model", "deepseek-chat"); }
    public static void setApi(Context c, String base, String key, String model) {
        sp(c).edit().putString("api_base", base.trim()).putString("api_key", key.trim()).putString("api_model", model.trim()).apply();
    }
    public static boolean aiReady(Context c) { return apiKey(c).length() > 0 && apiBase(c).length() > 0; }

    // ------------------------------------------------------------------ voice engines

    /** "system" (Android TTS engine) or "cloud" (OpenAI-compatible /audio/speech). */
    public static String ttsMode(Context c) { return sp(c).getString("tts_mode", "system"); }
    public static void setTtsMode(Context c, String m) { sp(c).edit().putString("tts_mode", m).apply(); }
    /** preferred TTS engine package; "" = auto (prefer Google). */
    public static String ttsEngine(Context c) { return sp(c).getString("tts_engine", ""); }
    public static void setTtsEngine(Context c, String e) { sp(c).edit().putString("tts_engine", e).apply(); }
    /** "system" (Android SpeechRecognizer) or "cloud" (OpenAI-compatible /audio/transcriptions). */
    public static String asrMode(Context c) { return sp(c).getString("asr_mode", "system"); }
    public static void setAsrMode(Context c, String m) { sp(c).edit().putString("asr_mode", m).apply(); }
    /** preferred recognition service (flattened ComponentName); "" = auto (prefer Google). */
    public static String asrService(Context c) { return sp(c).getString("asr_service", ""); }
    public static void setAsrService(Context c, String s) { sp(c).edit().putString("asr_service", s).apply(); }

    public static String audioBase(Context c) { return sp(c).getString("audio_base", "https://api.siliconflow.cn/v1"); }
    public static String audioKeyRaw(Context c) { return sp(c).getString("audio_key", ""); }
    /** falls back to the chat API key when empty */
    public static String audioKey(Context c) { String k = audioKeyRaw(c); return k.length() > 0 ? k : apiKey(c); }
    public static String ttsModel(Context c) { return sp(c).getString("tts_model", "FunAudioLLM/CosyVoice2-0.5B"); }
    public static String ttsVoice(Context c, String lang) {
        return sp(c).getString("tts_voice_" + lang, "ja".equals(lang) ? "FunAudioLLM/CosyVoice2-0.5B:bella" : "FunAudioLLM/CosyVoice2-0.5B:anna");
    }
    public static String asrModel(Context c) { return sp(c).getString("asr_model", "FunAudioLLM/SenseVoiceSmall"); }
    public static void setAudio(Context c, String base, String key, String ttsModel, String voiceEn, String voiceJa, String asrModel) {
        sp(c).edit().putString("audio_base", base.trim()).putString("audio_key", key.trim()).putString("tts_model", ttsModel.trim())
                .putString("tts_voice_en", voiceEn.trim()).putString("tts_voice_ja", voiceJa.trim()).putString("asr_model", asrModel.trim()).apply();
    }
    public static boolean audioReady(Context c) { return audioKey(c).length() > 0 && audioBase(c).length() > 0; }

    // Microsoft Azure neural TTS
    public static String azureKey(Context c) { return sp(c).getString("azure_key", ""); }
    public static String azureRegion(Context c) { return sp(c).getString("azure_region", "japaneast"); }
    public static String azureVoice(Context c, String lang) {
        return sp(c).getString("azure_voice_" + lang, "ja".equals(lang) ? "ja-JP-NanamiNeural" : "en-US-AvaMultilingualNeural");
    }
    public static void setAzure(Context c, String key, String region) {
        sp(c).edit().putString("azure_key", key.trim()).putString("azure_region", region.trim()).apply();
    }
    public static void setAzureVoice(Context c, String lang, String v) { sp(c).edit().putString("azure_voice_" + lang, v.trim()).apply(); }

    /** chosen system (Google) voice name per language; "" = auto */
    public static String sysVoice(Context c, String lang) { return sp(c).getString("sys_voice_" + lang, ""); }
    public static void setSysVoice(Context c, String lang, String v) { sp(c).edit().putString("sys_voice_" + lang, v).apply(); }

    // ------------------------------------------------------------------ content packs & custom scenarios

    public static String packUrl(Context c) {
        String u = sp(c).getString("pack_url", "");
        return u.length() > 0 ? u : PackSync.JSDELIVR;
    }
    public static boolean autoUpdatePacks(Context c) { return sp(c).getBoolean("pack_auto", true); }
    public static void setAutoUpdatePacks(Context c, boolean b) { sp(c).edit().putBoolean("pack_auto", b).apply(); }
    public static String lastPackCheck(Context c) { return sp(c).getString("pack_check_day", ""); }
    public static void setLastPackCheck(Context c, String d) { sp(c).edit().putString("pack_check_day", d).apply(); }
    /** installed version of a pack, or -1 */
    public static int installedVersion(Context c, String id) {
        for (JSONObject p : packs(c)) if (id.equals(p.optString("id"))) return p.optInt("version", 1);
        return -1;
    }
    public static void setPackUrl(Context c, String u) { sp(c).edit().putString("pack_url", u.trim()).apply(); }

    public static List<JSONObject> packs(Context c) {
        List<JSONObject> out = new ArrayList<JSONObject>();
        JSONArray a = arr(c, "packs");
        for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i) != null) out.add(a.optJSONObject(i));
        return out;
    }

    /** Installs (or replaces, by id) one or more packs from JSON text. Returns a human-readable summary. */
    public static String installPacks(Context c, String json) throws Exception {
        String t = json.trim();
        List<JSONObject> incoming = new ArrayList<JSONObject>();
        if (t.startsWith("[")) {
            JSONArray a = new JSONArray(t);
            for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i) != null) incoming.add(a.optJSONObject(i));
        } else {
            JSONObject o = new JSONObject(t);
            JSONArray many = o.optJSONArray("packs");
            if (many != null) { for (int i = 0; i < many.length(); i++) if (many.optJSONObject(i) != null) incoming.add(many.optJSONObject(i)); }
            else incoming.add(o);
        }
        if (incoming.isEmpty()) throw new Exception("没有找到内容包");
        JSONArray cur = arr(c, "packs");
        StringBuilder sb = new StringBuilder();
        for (JSONObject p : incoming) {
            String id = p.optString("id", "");
            if (id.length() == 0) id = "pack_" + Math.abs(p.optString("name", "x").hashCode());
            p.put("id", id);
            int ns = p.optJSONArray("sentences") == null ? 0 : p.optJSONArray("sentences").length();
            int np = p.optJSONArray("pairs") == null ? 0 : p.optJSONArray("pairs").length();
            int nc = p.optJSONArray("scenarios") == null ? 0 : p.optJSONArray("scenarios").length();
            if (ns + np + nc == 0) throw new Exception("内容包「" + p.optString("name", id) + "」是空的");
            JSONArray next = new JSONArray();
            boolean replaced = false;
            for (int i = 0; i < cur.length(); i++) {
                JSONObject old = cur.optJSONObject(i);
                if (old != null && id.equals(old.optString("id"))) { next.put(p); replaced = true; }
                else if (old != null) next.put(old);
            }
            if (!replaced) next.put(p);
            cur = next;
            sb.append(replaced ? "已更新「" : "已安装「").append(p.optString("name", id)).append("」v").append(p.optInt("version", 1))
                    .append("：句子 ").append(ns).append("，对立词 ").append(np).append("，场景 ").append(nc).append("\n");
        }
        put(c, "packs", cur);
        return sb.toString().trim();
    }

    public static void removePack(Context c, String id) {
        JSONArray cur = arr(c, "packs"), next = new JSONArray();
        for (int i = 0; i < cur.length(); i++) {
            JSONObject o = cur.optJSONObject(i);
            if (o != null && !id.equals(o.optString("id"))) next.put(o);
        }
        put(c, "packs", next);
    }

    public static List<Content.Sentence> packSentences(Context c, String lang) {
        List<Content.Sentence> out = new ArrayList<Content.Sentence>();
        for (JSONObject p : packs(c)) {
            JSONArray a = p.optJSONArray("sentences");
            if (a == null) continue;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || !lang.equals(o.optString("lang", "en")) || o.optString("text").trim().length() == 0) continue;
                // stable id (survives pack updates that reorder sentences) so scores stay attached
                String sid = o.optString("id", "");
                if (sid.length() == 0) sid = Integer.toHexString(o.optString("text").trim().hashCode());
                out.add(new Content.Sentence(lang, "pk_" + p.optString("id") + "_" + sid, Math.max(1, Math.min(4, o.optInt("level", 1))),
                        o.optString("text").trim(), o.optString("zh", "")));
            }
        }
        return out;
    }

    public static List<Content.Pair> pairs(Context c, String lang) {
        List<Content.Pair> out = new ArrayList<Content.Pair>(Content.pairs(lang));
        for (JSONObject p : packs(c)) {
            JSONArray a = p.optJSONArray("pairs");
            if (a == null) continue;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || !lang.equals(o.optString("lang", "en"))) continue;
                String aw = o.optString("a").trim(), bw = o.optString("b").trim();
                if (aw.length() == 0 || bw.length() == 0) continue;
                out.add(new Content.Pair(o.optString("cat", "其他"), aw, o.optString("a_say", aw.split(" ")[aw.split(" ").length - 1]),
                        o.optString("a_zh", ""), bw, o.optString("b_say", bw.split(" ")[bw.split(" ").length - 1]), o.optString("b_zh", "")));
            }
        }
        return out;
    }

    public static List<Content.Scenario> scenarios(Context c, String lang) {
        List<Content.Scenario> out = new ArrayList<Content.Scenario>(Content.scenarios(lang));
        for (JSONObject p : packs(c)) {
            JSONArray a = p.optJSONArray("scenarios");
            if (a == null) continue;
            for (int i = 0; i < a.length(); i++) {
                Content.Scenario s = scenarioFrom(a.optJSONObject(i), lang, "pk_" + p.optString("id") + "_" + i);
                if (s != null) { s.source = p.optString("name", "内容包"); out.add(s); }
            }
        }
        JSONArray mine = arr(c, "my_scen");
        for (int i = 0; i < mine.length(); i++) {
            JSONObject o = mine.optJSONObject(i);
            Content.Scenario s = scenarioFrom(o, lang, o == null ? "" : o.optString("id"));
            if (s != null) { s.source = "我的"; s.custom = true; out.add(s); }
        }
        return out;
    }

    private static Content.Scenario scenarioFrom(JSONObject o, String lang, String id) {
        if (o == null || !lang.equals(o.optString("lang", "en"))) return null;
        String role = o.optString("role", "").trim();
        if (role.length() == 0) return null;
        String opener = o.optString("opener", "").trim();
        if (opener.length() == 0) opener = "ja".equals(lang) ? "こんにちは！では、始めましょうか。" : "Hi! Shall we get started?";
        return new Content.Scenario(id, o.optString("icon", "💬"), o.optString("title", "自定义场景"), o.optString("sub", ""), role, opener);
    }

    public static void addCustomScenario(Context c, String lang, String icon, String title, String sub, String role, String opener) {
        JSONArray a = arr(c, "my_scen");
        try {
            JSONObject o = new JSONObject();
            o.put("id", "my_" + new Date().getTime());
            o.put("lang", lang);
            o.put("icon", icon.length() == 0 ? "💬" : icon);
            o.put("title", title);
            o.put("sub", sub);
            o.put("role", role);
            o.put("opener", opener);
            a.put(o);
        } catch (Exception ignored) { }
        put(c, "my_scen", a);
    }

    public static void removeCustomScenario(Context c, String id) {
        JSONArray a = arr(c, "my_scen"), next = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && !id.equals(o.optString("id"))) next.put(o);
        }
        put(c, "my_scen", next);
    }

    // ------------------------------------------------------------------ kana stats

    /** returns {right, total, streak} for a kana key like "h:か" / "k:か" / "s:か" (speaking) */
    public static int[] kanaStat(Context c, String key) {
        JSONArray a = obj(c, "kana").optJSONArray(key);
        if (a == null) return new int[]{0, 0, 0};
        return new int[]{a.optInt(0), a.optInt(1), a.optInt(2)};
    }

    public static void kanaResult(Context c, String key, boolean ok) {
        JSONObject o = obj(c, "kana");
        try {
            int[] s = kanaStat(c, key);
            JSONArray a = new JSONArray();
            a.put(s[0] + (ok ? 1 : 0));
            a.put(s[1] + 1);
            a.put(ok ? s[2] + 1 : 0);
            o.put(key, a);
        } catch (Exception ignored) { }
        put(c, "kana", o);
        tick(c);
    }

    // ------------------------------------------------------------------ JSON helpers

    private static JSONObject obj(Context c, String key) {
        try { return new JSONObject(sp(c).getString(key, "{}")); } catch (Exception e) { return new JSONObject(); }
    }
    private static JSONArray arr(Context c, String key) {
        try { return new JSONArray(sp(c).getString(key, "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    private static void put(Context c, String key, Object v) { sp(c).edit().putString(key, v.toString()).apply(); }

    // ------------------------------------------------------------------ daily stats & streak

    static String day(int offset) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, offset);
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.getTime());
    }

    public static int todayCount(Context c) {
        return day(0).equals(sp(c).getString("today_day", "")) ? sp(c).getInt("today_count", 0) : 0;
    }

    public static int streak(Context c) {
        String last = sp(c).getString("last_day", "");
        if (last.equals(day(0)) || last.equals(day(-1))) return sp(c).getInt("streak", 0);
        return 0;
    }

    public static int totalCount(Context c) { return sp(c).getInt("total_count", 0); }

    /** Call after every exercise item. */
    public static void tick(Context c) {
        SharedPreferences p = sp(c);
        SharedPreferences.Editor e = p.edit();
        String today = day(0);
        int count = today.equals(p.getString("today_day", "")) ? p.getInt("today_count", 0) : 0;
        e.putString("today_day", today).putInt("today_count", count + 1);
        String last = p.getString("last_day", "");
        if (!last.equals(today)) {
            int st = last.equals(day(-1)) ? p.getInt("streak", 0) + 1 : 1;
            e.putInt("streak", st).putString("last_day", today);
        }
        e.putInt("total_count", p.getInt("total_count", 0) + 1);
        e.apply();
    }

    // ------------------------------------------------------------------ sentence scores & adaptivity

    public static int bestScore(Context c, String lang, String id) {
        return obj(c, "best_" + lang).optInt(id, -1);
    }

    public static int lastScore(Context c, String lang, String id) {
        return obj(c, "last_" + lang).optInt(id, -1);
    }

    /**
     * Records a pronunciation result. Returns a message when the level changed (adaptive difficulty), else null.
     */
    public static String recordSentence(Context c, String lang, String id, int score) {
        JSONObject best = obj(c, "best_" + lang);
        JSONObject last = obj(c, "last_" + lang);
        try {
            if (score > best.optInt(id, -1)) best.put(id, score);
            last.put(id, score);
        } catch (Exception ignored) { }
        put(c, "best_" + lang, best);
        put(c, "last_" + lang, last);
        tick(c);

        JSONArray recent = arr(c, "recent_" + lang);
        recent.put(score);
        while (recent.length() > 6) recent.remove(0);
        put(c, "recent_" + lang, recent);
        if (recent.length() >= 5) {
            double sum = 0;
            for (int i = 0; i < recent.length(); i++) sum += recent.optInt(i);
            double avg = sum / recent.length();
            int lv = level(c, lang);
            if (avg >= 86 && lv < 4) {
                setLevel(c, lang, lv + 1);
                return "最近平均 " + Math.round(avg) + " 分，已自动升级到 " + Content.levelName(lang, lv + 1) + " 🎉";
            }
            if (avg < 55 && lv > 1) {
                setLevel(c, lang, lv - 1);
                return "最近有点吃力（平均 " + Math.round(avg) + " 分），先回到 " + Content.levelName(lang, lv - 1) + " 打好基础";
            }
        }
        return null;
    }

    /** All sentences (built-in + AI generated) for a language. */
    public static List<Content.Sentence> allSentences(Context c, String lang) {
        List<Content.Sentence> list = new ArrayList<Content.Sentence>(Content.sentences(lang));
        list.addAll(packSentences(c, lang));
        list.addAll(aiSentences(c, lang));
        return list;
    }

    /**
     * Adaptive picker: ~30% review of weak sentences, otherwise new / low-score sentences at the current level.
     */
    public static Content.Sentence nextSentence(Context c, String lang, String excludeId) {
        Random rnd = new Random();
        int lv = level(c, lang);
        List<Content.Sentence> all = allSentences(c, lang);
        List<Content.Sentence> weak = new ArrayList<Content.Sentence>();
        List<Content.Sentence> fresh = new ArrayList<Content.Sentence>();
        List<Content.Sentence> atLevel = new ArrayList<Content.Sentence>();
        for (Content.Sentence s : all) {
            if (s.id.equals(excludeId)) continue;
            int last = lastScore(c, lang, s.id);
            if (last >= 0 && last < 75) weak.add(s);
            if (s.level == lv) {
                atLevel.add(s);
                if (last < 0) fresh.add(s);
            }
        }
        if (!weak.isEmpty() && (rnd.nextInt(100) < 30 || fresh.isEmpty())) return weak.get(rnd.nextInt(weak.size()));
        if (!fresh.isEmpty()) return fresh.get(rnd.nextInt(fresh.size()));
        if (!atLevel.isEmpty()) {
            // all seen: lean towards the lowest scores
            Content.Sentence pick = atLevel.get(rnd.nextInt(atLevel.size()));
            int worst = 101;
            for (Content.Sentence s : atLevel) {
                int sc = lastScore(c, lang, s.id);
                if (sc < worst && rnd.nextInt(100) < 60) { worst = sc; pick = s; }
            }
            return pick;
        }
        return all.get(rnd.nextInt(all.size()));
    }

    public static List<Content.Sentence> weakSentences(Context c, String lang) {
        List<Content.Sentence> out = new ArrayList<Content.Sentence>();
        for (Content.Sentence s : allSentences(c, lang)) {
            int last = lastScore(c, lang, s.id);
            if (last >= 0 && last < 75) out.add(s);
        }
        return out;
    }

    public static Content.Sentence findSentence(Context c, String lang, String id) {
        for (Content.Sentence s : allSentences(c, lang)) if (s.id.equals(id)) return s;
        return null;
    }

    // ------------------------------------------------------------------ weak words & issue categories

    public static void updateWords(Context c, String lang, List<String> bad, List<String> good) {
        if ("ja".equals(lang)) return; // per-kana stats aren't useful; Japanese uses issue categories instead
        JSONObject w = obj(c, "weak_" + lang);
        try {
            for (String b : bad) if (b.length() > 1) w.put(b, w.optInt(b, 0) + 1);
            for (String g : good) {
                int v = w.optInt(g, 0);
                if (v > 1) w.put(g, v - 1);
                else if (v == 1) w.remove(g);
            }
        } catch (Exception ignored) { }
        put(c, "weak_" + lang, w);
    }

    public static List<String[]> weakWords(Context c, String lang) {
        return sortedCounts(obj(c, "weak_" + lang));
    }

    public static void addIssues(Context c, String lang, List<String> issues) {
        if (issues.isEmpty()) return;
        JSONObject w = obj(c, "issue_" + lang);
        try { for (String i : issues) w.put(i, w.optInt(i, 0) + 1); } catch (Exception ignored) { }
        put(c, "issue_" + lang, w);
    }

    public static List<String[]> issues(Context c, String lang) {
        return sortedCounts(obj(c, "issue_" + lang));
    }

    private static List<String[]> sortedCounts(JSONObject o) {
        List<String[]> out = new ArrayList<String[]>();
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            out.add(new String[]{k, String.valueOf(o.optInt(k))});
        }
        Collections.sort(out, new Comparator<String[]>() {
            public int compare(String[] a, String[] b) { return Integer.parseInt(b[1]) - Integer.parseInt(a[1]); }
        });
        return out;
    }

    public static void removeWeakWord(Context c, String lang, String w) {
        JSONObject o = obj(c, "weak_" + lang);
        o.remove(w);
        put(c, "weak_" + lang, o);
    }

    // ------------------------------------------------------------------ minimal pairs

    public static void pairResult(Context c, String lang, String cat, boolean correct) {
        JSONObject o = obj(c, "pair_" + lang);
        try {
            JSONArray a = o.optJSONArray(cat);
            if (a == null) { a = new JSONArray(); a.put(0); a.put(0); }
            a.put(0, a.optInt(0) + (correct ? 1 : 0));
            a.put(1, a.optInt(1) + 1);
            o.put(cat, a);
        } catch (Exception ignored) { }
        put(c, "pair_" + lang, o);
        tick(c);
    }

    /** returns {right,total} */
    public static int[] pairStat(Context c, String lang, String cat) {
        JSONArray a = obj(c, "pair_" + lang).optJSONArray(cat);
        if (a == null) return new int[]{0, 0};
        return new int[]{a.optInt(0), a.optInt(1)};
    }

    /** Weighted pick: categories with more mistakes (listening or speaking) come up more often. */
    public static Content.Pair nextPair(Context c, String lang, Content.Pair prev) {
        List<Content.Pair> all = pairs(c, lang);
        JSONObject issues = obj(c, "issue_" + lang);
        double[] w = new double[all.size()];
        double sum = 0;
        for (int i = 0; i < all.size(); i++) {
            Content.Pair p = all.get(i);
            int[] st = pairStat(c, lang, p.cat);
            double err = st[1] == 0 ? 0.5 : 1.0 - (double) st[0] / st[1];
            double weight = 1 + err * 3 + Math.min(3, issues.optInt(p.cat, 0) * 0.3);
            // Content.pairs() builds new objects on every call, so compare by value, not reference
            if (prev != null && p.cat.equals(prev.cat) && p.a.equals(prev.a) && p.b.equals(prev.b)) weight *= 0.1;
            w[i] = weight;
            sum += weight;
        }
        double r = new Random().nextDouble() * sum;
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r <= 0) return all.get(i);
        }
        return all.get(all.size() - 1);
    }

    // ------------------------------------------------------------------ listening (dictation) speed

    public static float listenSpeed(Context c, String lang) { return sp(c).getFloat("listen_" + lang, 0.85f); }

    /** Adapts the dictation playback speed to the learner's accuracy. Returns new speed. */
    public static float adaptListen(Context c, String lang, int score) {
        float s = listenSpeed(c, lang);
        if (score >= 90) s += 0.05f;
        else if (score < 60) s -= 0.1f;
        s = Math.max(0.6f, Math.min(1.3f, Math.round(s * 100) / 100f));
        sp(c).edit().putFloat("listen_" + lang, s).apply();
        tick(c);
        return s;
    }

    // ------------------------------------------------------------------ AI generated sentences

    public static List<Content.Sentence> aiSentences(Context c, String lang) {
        List<Content.Sentence> out = new ArrayList<Content.Sentence>();
        JSONArray a = arr(c, "ai_" + lang);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            out.add(new Content.Sentence(lang, o.optString("id"), o.optInt("level", 1), o.optString("text"), o.optString("zh")));
        }
        return out;
    }

    public static List<Content.Sentence> addAiSentences(Context c, String lang, JSONArray items, int level) {
        JSONArray a = arr(c, "ai_" + lang);
        List<Content.Sentence> added = new ArrayList<Content.Sentence>();
        long ts = new Date().getTime();
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) continue;
            String text = o.optString("text", "").trim();
            if (text.length() == 0) continue;
            try {
                JSONObject n = new JSONObject();
                n.put("id", "ai_" + lang + "_" + ts + "_" + i);
                n.put("text", text);
                n.put("zh", o.optString("zh", ""));
                n.put("level", level);
                a.put(n);
                added.add(new Content.Sentence(lang, n.getString("id"), level, text, n.getString("zh")));
            } catch (Exception ignored) { }
        }
        while (a.length() > 200) a.remove(0);
        put(c, "ai_" + lang, a);
        return added;
    }

    /** Clears learning progress but keeps settings, API config, content packs and custom scenarios. */
    public static void resetProgress(Context c) {
        SharedPreferences p = sp(c);
        String[] prefixes = {"best_", "last_", "recent_", "weak_", "issue_", "pair_", "listen_", "ai_", "kana", "streak",
                "last_day", "today_", "total_count", "level_"};
        SharedPreferences.Editor e = p.edit();
        for (String k : new ArrayList<String>(p.getAll().keySet())) {
            for (String pre : prefixes) if (k.startsWith(pre)) { e.remove(k); break; }
        }
        e.apply();
    }
}
