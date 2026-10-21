package com.xyb.voxcoach;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.ComponentName;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Voice environment: choose/diagnose TTS engine and speech recognizer, or switch to cloud voice. */
public class VoiceActivity extends Activity {

    private final Handler h = new Handler();
    private boolean destroyed;
    private int gen;
    private Rec rec;
    private Speaker testSpeaker;
    private MediaPlayer player;
    private EditText aBase, aKey, tModel, vEn, vJa, sModel;
    private TextView asrResult, cloudResult, azResult;
    private EditText azRegion, azKey;
    private TextToSpeech voiceTts;

    private static final String[][] AZ_JA = {
            {"ja-JP-NanamiNeural", "七海 Nanami 女·推荐"}, {"ja-JP-KeitaNeural", "圭太 Keita 男"},
            {"ja-JP-AoiNeural", "碧衣 Aoi 女·活泼"}, {"ja-JP-DaichiNeural", "大智 Daichi 男·沉稳"},
            {"ja-JP-MayuNeural", "真夕 Mayu 女"}, {"ja-JP-NaokiNeural", "直紀 Naoki 男"},
            {"ja-JP-ShioriNeural", "志織 Shiori 女"}, {"ja-JP-MasaruMultilingualNeural", "Masaru 男·多语"}};
    private static final String[][] AZ_EN = {
            {"en-US-AvaMultilingualNeural", "Ava 美音女·推荐"}, {"en-US-AndrewMultilingualNeural", "Andrew 美音男"},
            {"en-US-EmmaMultilingualNeural", "Emma 美音女"}, {"en-US-BrianMultilingualNeural", "Brian 美音男"},
            {"en-US-JennyNeural", "Jenny 美音女"}, {"en-GB-SoniaNeural", "Sonia 英音女"},
            {"en-GB-RyanNeural", "Ryan 英音男"}, {"en-US-Ava:DragonHDLatestNeural", "Ava HD 更真人*"}};
    private final List<TextToSpeech> probes = new ArrayList<TextToSpeech>();

    private static final String[][] PRESETS = {
            {"硅基流动", "https://api.siliconflow.cn/v1", "FunAudioLLM/CosyVoice2-0.5B", "FunAudioLLM/CosyVoice2-0.5B:anna",
                    "FunAudioLLM/CosyVoice2-0.5B:bella", "FunAudioLLM/SenseVoiceSmall"},
            {"OpenAI", "https://api.openai.com/v1", "tts-1", "alloy", "nova", "whisper-1"},
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        rec = new Rec(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build(); // rebuild: the user may have just installed Google TTS / voice data
    }

    private void build() {
        gen++;
        shutdownProbes();
        if (testSpeaker != null) { testSpeaker.shutdown(); testSpeaker = null; }
        LinearLayout body = UI.screen(this, "语音环境", null);

        // ---------------- guide
        LinearLayout g = UI.card(this);
        g.addView(UI.text(this, "📱 国行手机（小米 / 华为 / OPPO…）怎么配", 16, UI.TEXT, true));
        g.addView(UI.text(this,
                "方案 A（免费、离线可用）：开代理 → Google Play 安装「Google 语音服务（Speech Services by Google）」和「Google」App "
                        + "→ 回到这里点「下载 Google 日语语音包」，选 日本語 下载 → 下面引擎选 Google。\n"
                        + "方案 B（无需代理）：在下方「在线语音」填一个硅基流动（国内直连）或 OpenAI 的 Key，把合成和识别都切到「在线」。\n"
                        + "想要更像真人的示范：用下方「微软 Azure 神经语音」（每月有免费额度），或在 Google 音色里挑「高音质 · 联网」的音色。\n"
                        + "这些方案可以混用：比如合成走 Azure，识别走 Google。", 13, UI.SUB, false), UI.matchWrap(this, 0, 6, 0, 0));
        body.addView(g, UI.matchWrap(this, 0, 4, 0, 12));

        // ---------------- TTS
        LinearLayout t = UI.card(this);
        t.addView(UI.text(this, "🔊 语音合成（示范发音）", 16, UI.TEXT, true));
        t.addView(modeRow(Store.ttsMode(this), new String[]{"system", "cloud", "azure"}, new String[]{"系统引擎", "在线(兼容)", "微软 Azure"}, true),
                UI.matchWrap(this, -3, 8, -3, 4));
        boolean googleTts = Speaker.installed(this, Speaker.GOOGLE_TTS);
        t.addView(UI.text(this, "Google 语音服务：" + (googleTts ? "✅ 已安装" : "❌ 未安装（日语示范需要它，或改用在线 TTS）"), 13,
                googleTts ? UI.GREEN : UI.RED, false), UI.matchWrap(this, 0, 6, 0, 6));
        t.addView(UI.text(this, "已安装的引擎（点击选用，正在检测英/日语支持…）", 12, UI.SUB, false));
        final LinearLayout engines = UI.vbox(this);
        t.addView(engines, UI.matchWrap(this, 0, 4, 0, 8));
        probeEngines(engines);

        LinearLayout r1 = UI.hbox(this);
        TextView tryEn = UI.button(this, "▶ 试听英语", false);
        tryEn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { trySpeak("en", "Hello! Nice to meet you."); } });
        TextView tryJa = UI.button(this, "▶ 试听日语", false);
        tryJa.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { trySpeak("ja", "こんにちは。はじめまして、よろしくお願いします。"); } });
        r1.addView(tryEn, UI.lpw(this, 1, 3));
        r1.addView(tryJa, UI.lpw(this, 1, 3));
        t.addView(r1, UI.matchWrap(this, -3, 0, -3, 0));
        LinearLayout r2 = UI.hbox(this);
        TextView dl = UI.button(this, googleTts ? "下载 Google 日语语音包" : "安装 Google 语音服务", true);
        dl.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (Speaker.installed(VoiceActivity.this, Speaker.GOOGLE_TTS)) openGoogleVoiceData();
                else openStore(Speaker.GOOGLE_TTS);
            }
        });
        r2.addView(dl, UI.lpw(this, 1, 3));
        t.addView(r2, UI.matchWrap(this, -3, 0, -3, 0));
        t.addView(UI.text(this, "系统音色（引擎里可能有多个音色，「联网」音色通常更自然）", 12, UI.SUB, false), UI.matchWrap(this, 0, 10, 0, 2));
        LinearLayout r5 = UI.hbox(this);
        TextView pvEn = UI.button(this, "🎚 选英语音色", false);
        pvEn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickSysVoice("en"); } });
        TextView pvJa = UI.button(this, "🎚 选日语音色", false);
        pvJa.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickSysVoice("ja"); } });
        r5.addView(pvEn, UI.lpw(this, 1, 3));
        r5.addView(pvJa, UI.lpw(this, 1, 3));
        t.addView(r5, UI.matchWrap(this, -3, 0, -3, 0));
        body.addView(t, UI.matchWrap(this, 0, 0, 0, 12));

        // ---------------- ASR
        LinearLayout a = UI.card(this);
        a.addView(UI.text(this, "🎤 语音识别（跟读评分 / 对话）", 16, UI.TEXT, true));
        a.addView(modeRow(Store.asrMode(this), new String[]{"system", "cloud"}, new String[]{"系统识别", "在线识别"}, false),
                UI.matchWrap(this, -3, 8, -3, 4));
        boolean gApp = Speaker.installed(this, "com.google.android.googlequicksearchbox");
        a.addView(UI.text(this, "Google App：" + (gApp ? "✅ 已安装" : "❌ 未安装（系统识别推荐用它，识别日语更准）"), 13,
                gApp ? UI.GREEN : UI.RED, false), UI.matchWrap(this, 0, 6, 0, 6));
        a.addView(UI.text(this, "识别服务（点击选用）", 12, UI.SUB, false));
        LinearLayout svc = UI.vbox(this);
        a.addView(svc, UI.matchWrap(this, 0, 4, 0, 8));
        renderServices(svc);
        a.addView(UI.text(this, "系统识别能给出多个候选和置信度，评分更细；在线识别只返回一个结果，但不依赖 Google。", 12, UI.SUB, false),
                UI.matchWrap(this, 0, 0, 0, 8));
        LinearLayout r3 = UI.hbox(this);
        TextView tEn = UI.button(this, "🎤 测英语", false);
        tEn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { testAsr("en"); } });
        TextView tJa = UI.button(this, "🎤 测日语", false);
        tJa.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { testAsr("ja"); } });
        r3.addView(tEn, UI.lpw(this, 1, 3));
        r3.addView(tJa, UI.lpw(this, 1, 3));
        if (!gApp) {
            TextView ig = UI.button(this, "装 Google App", false);
            ig.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openStore("com.google.android.googlequicksearchbox"); } });
            r3.addView(ig, UI.lpw(this, 1.2f, 3));
        }
        a.addView(r3, UI.matchWrap(this, -3, 0, -3, 0));
        asrResult = UI.text(this, "", 13, UI.SUB, false);
        a.addView(asrResult, UI.matchWrap(this, 0, 6, 0, 0));
        body.addView(a, UI.matchWrap(this, 0, 0, 0, 12));

        // ---------------- cloud
        LinearLayout c = UI.card(this);
        c.addView(UI.text(this, "☁️ 在线语音（OpenAI 兼容 /audio 接口）", 16, UI.TEXT, true));
        c.addView(UI.text(this, "硅基流动：CosyVoice2 合成（中英日）+ SenseVoice 识别，国内直连。API Key 留空则用「AI 接口」里的 Key。合成结果会缓存，同一句不会重复计费。", 12, UI.SUB, false),
                UI.matchWrap(this, 0, 4, 0, 8));
        LinearLayout pr = UI.hbox(this);
        for (final String[] p : PRESETS) {
            TextView chip = UI.chip(this, p[0], p[1].equals(Store.audioBase(this)));
            chip.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    aBase.setText(p[1]); tModel.setText(p[2]); vEn.setText(p[3]); vJa.setText(p[4]); sModel.setText(p[5]);
                }
            });
            pr.addView(chip, UI.lpw(this, 1, 3));
        }
        c.addView(pr, UI.matchWrap(this, -3, 0, -3, 6));
        aBase = field(c, "Base URL", Store.audioBase(this));
        aKey = field(c, "API Key（可留空）", Store.audioKeyRaw(this));
        aKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tModel = field(c, "合成模型", Store.ttsModel(this));
        vEn = field(c, "英语音色", Store.ttsVoice(this, "en"));
        vJa = field(c, "日语音色", Store.ttsVoice(this, "ja"));
        c.addView(UI.text(this, "CosyVoice2 可选音色后缀：alex / benjamin / charles / david（男）· anna / bella / claire / diana（女）", 11, UI.HINT, false));
        sModel = field(c, "识别模型", Store.asrModel(this));
        LinearLayout r4 = UI.hbox(this);
        TextView save = UI.button(this, "保存", true);
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { saveCloud(); UI.toast(VoiceActivity.this, "已保存"); } });
        TextView test = UI.button(this, "测试在线合成（日语）", false);
        test.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { testCloudTts(); } });
        r4.addView(test, UI.lpw(this, 1.4f, 3));
        r4.addView(save, UI.lpw(this, 1, 3));
        c.addView(r4, UI.matchWrap(this, -3, 8, -3, 0));
        cloudResult = UI.text(this, "", 13, UI.SUB, false);
        c.addView(cloudResult, UI.matchWrap(this, 0, 6, 0, 0));
        body.addView(c, UI.matchWrap(this, 0, 0, 0, 12));

        // ---------------- Azure
        LinearLayout az = UI.card(this);
        az.addView(UI.text(this, "🗣 微软 Azure 神经语音（最接近真人）", 16, UI.TEXT, true));
        az.addView(UI.text(this, "免费档（F0）每月有 50 万字符免费额度，日语七海 Nanami、英语 Ava 等音色自然度很高。在 portal.azure.com 创建「语音服务（Speech）」资源"
                + "（区域建议选 Japan East），把 Key 和区域填到这里，然后把上面的「语音合成」切到「微软 Azure」。示范音频会缓存，同一句只请求一次。", 12, UI.SUB, false),
                UI.matchWrap(this, 0, 4, 0, 8));
        azRegion = field(az, "区域（如 japaneast / eastasia / eastus，也可填完整 endpoint）", Store.azureRegion(this));
        azKey = field(az, "Key", Store.azureKey(this));
        azKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        az.addView(UI.text(this, "日语音色（点一下即试听并选用）", 12, UI.SUB, false), UI.matchWrap(this, 0, 8, 0, 4));
        az.addView(voiceGrid("ja", AZ_JA));
        az.addView(UI.text(this, "英语音色", 12, UI.SUB, false), UI.matchWrap(this, 0, 8, 0, 4));
        az.addView(voiceGrid("en", AZ_EN));
        azResult = UI.text(this, "", 13, UI.SUB, false);
        az.addView(azResult, UI.matchWrap(this, 0, 6, 0, 0));
        body.addView(az, UI.matchWrap(this, 0, 0, 0, 12));
    }

    private EditText field(LinearLayout parent, String label, String value) {
        parent.addView(UI.text(this, label, 12, UI.SUB, false), UI.matchWrap(this, 0, 4, 0, 2));
        EditText e = UI.input(this, label);
        e.setSingleLine(true);
        e.setText(value);
        parent.addView(e, UI.matchWrap(this, 0, 0, 0, 4));
        return e;
    }

    private View modeRow(String cur, final String[] vals, String[] labels, final boolean tts) {
        LinearLayout r = UI.hbox(this);
        final List<TextView> chips = new ArrayList<TextView>();
        for (int i = 0; i < vals.length; i++) {
            final String v = vals[i];
            final TextView c = UI.chip(this, labels[i], v.equals(cur));
            chips.add(c);
            c.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    if ("cloud".equals(v)) saveCloud();
                    if ("azure".equals(v)) saveAzure();
                    if ("cloud".equals(v) && !Store.audioReady(VoiceActivity.this))
                        UI.toast(VoiceActivity.this, "先在下方填好在线语音接口（或 AI 接口的 Key）");
                    if ("azure".equals(v) && Store.azureKey(VoiceActivity.this).length() == 0)
                        UI.toast(VoiceActivity.this, "先在下方「微软 Azure」里填好 Key 和区域");
                    if (tts) Store.setTtsMode(VoiceActivity.this, v);
                    else { Store.setAsrMode(VoiceActivity.this, v); rec.destroy(); }
                    for (TextView x : chips) UI.setChip(VoiceActivity.this, x, x == c);
                }
            });
            r.addView(c, UI.lpw(this, 1, 3));
        }
        return r;
    }

    // ------------------------------------------------------------------ TTS engines

    private void probeEngines(final LinearLayout box) {
        box.removeAllViews();
        final String chosen = Store.ttsEngine(this);
        final String effective = Speaker.engine(this);
        addEngineRow(box, "", "自动（优先 Google）", effective == null ? "→ 当前：系统默认引擎" : "→ 当前：" + effective, chosen.length() == 0);
        final int myGen = gen;
        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            public void onInit(int status) {
                h.post(new Runnable() {
                    public void run() {
                        final TextToSpeech tt = holder[0];
                        if (destroyed || tt == null || myGen != gen) return;
                        List<TextToSpeech.EngineInfo> list = tt.getEngines();
                        try { tt.shutdown(); } catch (Exception ignored) { }
                        if (list == null || list.isEmpty()) {
                            box.addView(UI.text(VoiceActivity.this, "没有找到任何语音引擎", 13, UI.RED, false));
                            return;
                        }
                        List<TextView> rows = new ArrayList<TextView>();
                        for (TextToSpeech.EngineInfo e : list) rows.add(addEngineRow(box, e.name, e.label, "检测中…", e.name.equals(chosen)));
                        probe(list, rows, 0, myGen);
                    }
                });
            }
        });
        probes.add(holder[0]);
    }

    private TextView addEngineRow(final LinearLayout box, final String pkg, String label, String status, boolean on) {
        final TextView row = UI.chip(this, (on ? "◉ " : "○ ") + label + "\n" + status, on);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setTag(label);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Store.setTtsEngine(VoiceActivity.this, pkg);
                UI.toast(VoiceActivity.this, pkg.length() == 0 ? "已设为自动" : "已选用：" + row.getTag());
                build();
            }
        });
        box.addView(row, UI.matchWrap(this, 0, 3, 0, 3));
        return row;
    }

    private void probe(final List<TextToSpeech.EngineInfo> list, final List<TextView> rows, final int i, final int myGen) {
        if (destroyed || i >= list.size() || myGen != gen) return;
        final TextToSpeech.EngineInfo e = list.get(i);
        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            public void onInit(final int status) {
                h.post(new Runnable() {
                    public void run() {
                        TextToSpeech tt = holder[0];
                        if (destroyed || tt == null || myGen != gen) return;
                        String st;
                        if (status != TextToSpeech.SUCCESS) st = "无法启动";
                        else st = "英语 " + mark(tt.isLanguageAvailable(Locale.US)) + "   日语 " + mark(tt.isLanguageAvailable(Locale.JAPAN));
                        try { tt.shutdown(); } catch (Exception ignored) { }
                        probes.remove(tt);
                        TextView row = rows.get(i);
                        boolean on = e.name.equals(Store.ttsEngine(VoiceActivity.this));
                        row.setText((on ? "◉ " : "○ ") + e.label + "\n" + e.name + " · " + st);
                        probe(list, rows, i + 1, myGen);
                    }
                });
            }
        }, e.name);
        probes.add(holder[0]);
    }

    private static String mark(int r) {
        if (r >= TextToSpeech.LANG_AVAILABLE) return "✅";
        if (r == TextToSpeech.LANG_MISSING_DATA) return "⬇需下载";
        return "❌";
    }

    private void shutdownProbes() {
        for (TextToSpeech t : probes) { try { t.shutdown(); } catch (Exception ignored) { } }
        probes.clear();
    }

    private void trySpeak(String lang, String text) {
        if (testSpeaker != null) testSpeaker.shutdown();
        testSpeaker = new Speaker(this, lang);
        testSpeaker.speak(text, 1f, null);
    }

    private void openGoogleVoiceData() {
        try {
            Intent i = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            i.setPackage(Speaker.GOOGLE_TTS);
            startActivity(i);
            UI.toast(this, "在列表里找到「日语 / 日本語」点下载，下载完回到这里");
        } catch (Exception e) {
            try {
                Intent d = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + Speaker.GOOGLE_TTS));
                startActivity(d);
                UI.toast(this, "请在 Google 语音服务的设置里下载日语语音");
            } catch (Exception e2) {
                UI.toast(this, "无法打开 Google 语音服务");
            }
        }
    }

    private void openStore(String pkg) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)).setPackage("com.android.vending"));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
            } catch (Exception e2) {
                UI.toast(this, "无法打开应用商店");
            }
        }
    }

    // ------------------------------------------------------------------ ASR

    private void renderServices(final LinearLayout box) {
        box.removeAllViews();
        List<String[]> all = Rec.services(this);
        String chosen = Store.asrService(this);
        ComponentName eff = Rec.pickService(this);
        addSvcRow(box, "", "自动（优先 Google）" + (eff == null ? "\n→ 当前：系统默认" : "\n→ 当前：" + eff.getPackageName()), chosen.length() == 0);
        if (all.isEmpty()) box.addView(UI.text(this, "没有找到语音识别服务", 13, UI.RED, false));
        for (String[] s : all) addSvcRow(box, s[0], s[1], s[0].equals(chosen));
    }

    private void addSvcRow(final LinearLayout box, final String id, String label, boolean on) {
        TextView row = UI.chip(this, (on ? "◉ " : "○ ") + label, on);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Store.setAsrService(VoiceActivity.this, id);
                rec.destroy();
                renderServices(box);
            }
        });
        box.addView(row, UI.matchWrap(this, 0, 3, 0, 3));
    }

    private void testAsr(final String lang) {
        if (rec.listening) { rec.stop(); return; }
        if (!UI.ensureMic(this)) return;
        if ("cloud".equals(Store.asrMode(this))) saveCloud();
        asrResult.setTextColor(UI.SUB);
        asrResult.setText("ja".equals(lang) ? "请说一句日语，例如「こんにちは」" : "Please say something, e.g. \"Good morning\"");
        rec.start(lang, new Rec.Cb() {
            public void onReady() { asrResult.setText("正在听…"); }
            public void onPartial(String text) { asrResult.setText("“" + text + "”"); }
            public void onRms(float db) { }
            public void onResult(List<String> c, float conf, long dur) {
                asrResult.setTextColor(UI.GREEN);
                asrResult.setText("✅ 识别结果：" + c.get(0) + (c.size() > 1 ? "\n候选：" + c.subList(1, Math.min(4, c.size())) : "")
                        + (conf > 0 ? String.format("\n置信度 %.2f", conf) : ""));
            }
            public void onError(String msg) {
                if (msg.length() == 0) return;
                asrResult.setTextColor(UI.RED);
                asrResult.setText("❌ " + msg);
            }
        });
    }

    // ------------------------------------------------------------------ cloud

    private void saveCloud() {
        if (aBase == null) return;
        Store.setAudio(this, aBase.getText().toString(), aKey.getText().toString(), tModel.getText().toString(),
                vEn.getText().toString(), vJa.getText().toString(), sModel.getText().toString());
    }

    private void testCloudTts() {
        saveCloud();
        cloudResult.setTextColor(UI.SUB);
        cloudResult.setText("请求中…");
        new Thread(new Runnable() {
            public void run() {
                String err = null;
                File f = null;
                try { f = CloudAudio.tts(VoiceActivity.this, "こんにちは。日本語の発音練習を始めましょう。", "ja", 1f); }
                catch (Exception e) { err = e.getMessage() != null ? e.getMessage() : e.toString(); }
                final String fe = err;
                final File ff = f;
                h.post(new Runnable() {
                    public void run() {
                        if (destroyed) return;
                        if (fe != null) { cloudResult.setTextColor(UI.RED); cloudResult.setText("❌ " + fe); return; }
                        cloudResult.setTextColor(UI.GREEN);
                        cloudResult.setText("✅ 合成成功，正在播放。满意的话把上面的「语音合成」切到「在线 TTS」。");
                        playFile(ff);
                    }
                });
            }
        }).start();
    }

    private void playFile(File f) {
        releasePlayer();
        try {
            player = new MediaPlayer();
            player.setDataSource(f.getAbsolutePath());
            player.prepare();
            player.start();
        } catch (Exception e) {
            releasePlayer();
            UI.toast(this, "播放失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ Azure

    private void saveAzure() {
        if (azKey == null) return;
        Store.setAzure(this, azKey.getText().toString(), azRegion.getText().toString());
    }

    private View voiceGrid(final String lang, final String[][] voices) {
        final LinearLayout box = UI.vbox(this);
        final List<TextView> chips = new ArrayList<TextView>();
        LinearLayout row = null;
        String cur = Store.azureVoice(this, lang);
        for (int i = 0; i < voices.length; i++) {
            if (i % 2 == 0) { row = UI.hbox(this); box.addView(row, UI.matchWrap(this, -3, 0, -3, 0)); }
            final String id = voices[i][0];
            final TextView c = UI.chip(this, voices[i][1], id.equals(cur));
            c.setTextSize(13);
            chips.add(c);
            c.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Store.setAzureVoice(VoiceActivity.this, lang, id);
                    for (TextView x : chips) UI.setChip(VoiceActivity.this, x, x == c);
                    previewAzure(lang, id);
                }
            });
            row.addView(c, UI.lpw(this, 1, 3));
        }
        return box;
    }

    private void previewAzure(final String lang, final String voice) {
        saveAzure();
        azResult.setTextColor(UI.SUB);
        azResult.setText("正在合成试听…");
        final String text = "ja".equals(lang) ? "こんにちは。一緒に日本語の発音を練習しましょう。" : "Hi! I'm your speaking coach. Let's practice together.";
        new Thread(new Runnable() {
            public void run() {
                String err = null;
                File f = null;
                try { f = CloudAudio.azureTts(VoiceActivity.this, text, lang, 1f, voice); }
                catch (Exception e) { err = e.getMessage() == null ? e.toString() : e.getMessage(); }
                final String fe = err;
                final File ff = f;
                h.post(new Runnable() {
                    public void run() {
                        if (destroyed) return;
                        if (fe != null || ff == null) {
                            azResult.setTextColor(UI.RED);
                            azResult.setText("❌ " + fe + (voice.contains("DragonHD") ? "\n* HD 音色只在部分区域可用（如 eastus / westeurope / southeastasia）" : ""));
                            return;
                        }
                        azResult.setTextColor(UI.GREEN);
                        azResult.setText("✅ " + voice + (Store.ttsMode(VoiceActivity.this).equals("azure") ? "" : "\n满意的话把上面的「语音合成」切到「微软 Azure」"));
                        playFile(ff);
                    }
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ system voices

    private void pickSysVoice(final String lang) {
        UI.toast(this, "正在读取音色…");
        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            public void onInit(final int status) {
                h.post(new Runnable() {
                    public void run() {
                        final TextToSpeech tt = holder[0];
                        if (destroyed || tt == null) return;
                        if (status != TextToSpeech.SUCCESS) { UI.toast(VoiceActivity.this, "语音引擎启动失败"); return; }
                        if (voiceTts != null && voiceTts != tt) { try { voiceTts.shutdown(); } catch (Exception ignored) { } }
                        voiceTts = tt;
                        showVoiceDialog(lang, tt);
                    }
                });
            }
        }, Speaker.engine(this));
    }

    private void showVoiceDialog(final String lang, final TextToSpeech tt) {
        final List<android.speech.tts.Voice> list = new ArrayList<android.speech.tts.Voice>();
        java.util.Set<android.speech.tts.Voice> vs = null;
        try { vs = tt.getVoices(); } catch (Exception ignored) { }
        if (vs != null) {
            for (android.speech.tts.Voice v : vs) {
                Locale l = v.getLocale();
                if (l != null && l.getLanguage().equals("ja".equals(lang) ? "ja" : "en")) list.add(v);
            }
        }
        if (list.isEmpty()) {
            UI.toast(this, "当前引擎没有" + ("ja".equals(lang) ? "日语" : "英语") + "音色：请先安装 Google 语音服务并下载语音包");
            return;
        }
        java.util.Collections.sort(list, new java.util.Comparator<android.speech.tts.Voice>() {
            public int compare(android.speech.tts.Voice a, android.speech.tts.Voice b) {
                if (a.getQuality() != b.getQuality()) return b.getQuality() - a.getQuality();
                return a.getName().compareTo(b.getName());
            }
        });
        String cur = Store.sysVoice(this, lang);
        String[] labels = new String[list.size() + 1];
        labels[0] = "自动（离线、音质最高的那个）";
        int checked = 0;
        for (int i = 0; i < list.size(); i++) {
            android.speech.tts.Voice v = list.get(i);
            int q = v.getQuality();
            java.util.Set<String> f = v.getFeatures();
            boolean notInstalled = f != null && f.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED);
            labels[i + 1] = v.getName() + "\n" + v.getLocale().getCountry() + " · " + (q >= 400 ? "高音质" : q >= 300 ? "普通" : "低音质")
                    + (v.isNetworkConnectionRequired() ? " · 联网" : " · 离线") + (notInstalled ? " · 未下载" : "");
            if (v.getName().equals(cur)) checked = i + 1;
        }
        final int[] sel = {checked};
        final String sample = "ja".equals(lang) ? "こんにちは。一緒に日本語の発音を練習しましょう。" : "Hi! I'm your speaking coach. Let's practice together.";
        new AlertDialog.Builder(this).setTitle("ja".equals(lang) ? "选择日语音色（点一下试听）" : "选择英语音色（点一下试听）")
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        sel[0] = which;
                        if (which == 0) return;
                        try {
                            tt.setVoice(list.get(which - 1));
                            tt.setSpeechRate(Store.ttsRate(VoiceActivity.this));
                            tt.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "preview");
                        } catch (Exception ignored) { }
                    }
                })
                .setPositiveButton("使用", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try { tt.stop(); } catch (Exception ignored) { }
                        Store.setSysVoice(VoiceActivity.this, lang, sel[0] == 0 ? "" : list.get(sel[0] - 1).getName());
                        UI.toast(VoiceActivity.this, sel[0] == 0 ? "已设为自动" : "已选用：" + list.get(sel[0] - 1).getName()
                                + (list.get(sel[0] - 1).isNetworkConnectionRequired() ? "（联网音色，需要能访问 Google）" : ""));
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { try { tt.stop(); } catch (Exception ignored) { } }
                }).show();
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCloud();
        saveAzure();
        if (rec.listening) rec.cancel();
        if (testSpeaker != null) testSpeaker.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
        shutdownProbes();
        if (voiceTts != null) { try { voiceTts.shutdown(); } catch (Exception ignored) { } }
        rec.destroy();
        releasePlayer();
        if (testSpeaker != null) testSpeaker.shutdown();
    }
}
