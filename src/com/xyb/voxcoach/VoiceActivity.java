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
                    "FunAudioLLM/CosyVoice2-0.5B:claire", "FunAudioLLM/SenseVoiceSmall"},
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

    /** Sections the user has expanded (kept across rebuilds while the app is running). */
    private static final java.util.Set<String> OPEN = new java.util.HashSet<String>();

    private interface Filler { void fill(LinearLayout box); }

    private void build() {
        gen++;
        shutdownProbes();
        if (testSpeaker != null) { testSpeaker.shutdown(); testSpeaker = null; }
        saveCloud(); // keep anything typed into fields before they are rebuilt
        saveAzure();
        aBase = null;
        azKey = null;
        LinearLayout body = UI.screen(this, "语音环境", null);

        // ---------------- what is in effect right now
        LinearLayout cur = UI.card(this);
        cur.addView(UI.text(this, "当前生效", 16, UI.TEXT, true));
        cur.addView(statusLine("🔊 示范发音", ttsSummary()), UI.matchWrap(this, 0, 8, 0, 0));
        cur.addView(statusLine("🎤 语音识别", asrSummary()), UI.matchWrap(this, 0, 6, 0, 0));
        LinearLayout tr = UI.hbox(this);
        tr.addView(small("▶ 听英语", new View.OnClickListener() { public void onClick(View v) { trySpeak("en", "Hello! Nice to meet you."); } }), UI.lpw(this, 1, 3));
        tr.addView(small("▶ 听日语", new View.OnClickListener() { public void onClick(View v) { trySpeak("ja", "こんにちは。はじめまして、よろしくお願いします。"); } }), UI.lpw(this, 1, 3));
        tr.addView(small("🎤 测英语", new View.OnClickListener() { public void onClick(View v) { testAsr("en"); } }), UI.lpw(this, 1, 3));
        tr.addView(small("🎤 测日语", new View.OnClickListener() { public void onClick(View v) { testAsr("ja"); } }), UI.lpw(this, 1, 3));
        cur.addView(tr, UI.matchWrap(this, -3, 10, -3, 0));
        asrResult = UI.text(this, "", 13, UI.SUB, false);
        cur.addView(asrResult, UI.matchWrap(this, 0, 4, 0, 0));
        body.addView(cur, UI.matchWrap(this, 0, 4, 0, 12));

        body.addView(UI.text(this, "点下面的分组展开详细设置", 12, UI.HINT, false), UI.matchWrap(this, 4, 0, 0, 6));
        section(body, "tts", "🔊 示范发音用什么", ttsSummary(), new Filler() { public void fill(LinearLayout box) { fillTts(box); } });
        section(body, "asr", "🎤 语音识别用什么", asrSummary(), new Filler() { public void fill(LinearLayout box) { fillAsr(box); } });
        section(body, "cloud", "☁️ 在线语音接口（硅基流动 / OpenAI）", cloudSummary(), new Filler() { public void fill(LinearLayout box) { fillCloud(box); } });
        section(body, "azure", "🗣 微软 Azure 神经语音", azureSummary(), new Filler() { public void fill(LinearLayout box) { fillAzure(box); } });
        section(body, "guide", "📱 国行手机怎么配？", "不开代理也能用的推荐配置", new Filler() { public void fill(LinearLayout box) { fillGuide(box); } });
    }

    // ------------------------------------------------------------------ building blocks

    private View statusLine(String label, String value) {
        LinearLayout r = UI.hbox(this);
        r.addView(UI.text(this, label, 14, UI.SUB, false), new LinearLayout.LayoutParams(UI.dp(this, 96), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView v = UI.text(this, "✓ " + value, 14, UI.GREEN, true);
        r.addView(v, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return r;
    }

    private TextView small(String label, View.OnClickListener l) {
        TextView b = UI.button(this, label, false);
        b.setTextSize(12);
        b.setPadding(UI.dp(this, 4), UI.dp(this, 9), UI.dp(this, 4), UI.dp(this, 9));
        b.setOnClickListener(l);
        return b;
    }

    /** Collapsible card: header with title + current choice; content filled lazily when opened. */
    private void section(LinearLayout body, final String key, String title, String summary, final Filler filler) {
        final LinearLayout card = UI.card(this);
        card.setPadding(0, 0, 0, 0);
        final LinearLayout head = UI.hbox(this);
        int p = UI.dp(this, 16);
        head.setPadding(p, UI.dp(this, 14), p, UI.dp(this, 14));
        head.setBackground(UI.ripple(this, UI.round(this, UI.CARD, 18)));
        LinearLayout t = UI.vbox(this);
        t.addView(UI.text(this, title, 15, UI.TEXT, true));
        t.addView(UI.text(this, summary, 12, summary.startsWith("未") ? UI.ORANGE : UI.GREEN, false), UI.matchWrap(this, 0, 3, 0, 0));
        head.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        final TextView arrow = UI.text(this, OPEN.contains(key) ? "▾" : "▸", 20, UI.HINT, false);
        head.addView(arrow);
        card.addView(head);
        final LinearLayout box = UI.vbox(this);
        box.setPadding(p, 0, p, p);
        card.addView(box);
        if (OPEN.contains(key)) filler.fill(box);
        else box.setVisibility(View.GONE);
        head.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (OPEN.contains(key)) {
                    if ("cloud".equals(key)) saveCloud();
                    if ("azure".equals(key)) saveAzure();
                    OPEN.remove(key);
                    box.setVisibility(View.GONE);
                    box.removeAllViews();
                    arrow.setText("▸");
                } else {
                    OPEN.add(key);
                    box.removeAllViews();
                    filler.fill(box);
                    box.setVisibility(View.VISIBLE);
                    arrow.setText("▾");
                }
            }
        });
        body.addView(card, UI.matchWrap(this, 0, 0, 0, 10));
    }

    /** Radio-style option row; the selected one is highlighted and marked ✓. descOut[0] receives the description view. */
    private LinearLayout radio(String title, String desc, boolean on, View.OnClickListener l, TextView[] descOut) {
        LinearLayout r = UI.hbox(this);
        int p = UI.dp(this, 12);
        r.setPadding(p, UI.dp(this, 10), p, UI.dp(this, 10));
        r.setBackground(UI.ripple(this, on ? UI.roundStroke(this, 0xFFEAF7F0, 12, UI.GREEN) : UI.roundStroke(this, UI.CARD, 12, UI.BORDER)));
        TextView mark = UI.text(this, on ? "✓" : "○", 18, on ? UI.GREEN : UI.HINT, true);
        mark.setGravity(Gravity.CENTER);
        r.addView(mark, new LinearLayout.LayoutParams(UI.dp(this, 28), LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout t = UI.vbox(this);
        t.addView(UI.text(this, title, 14, on ? 0xFF14794A : UI.TEXT, true));
        TextView d = UI.text(this, desc, 12, UI.SUB, false);
        if (desc.length() > 0) t.addView(d, UI.matchWrap(this, 0, 2, 0, 0));
        if (descOut != null) descOut[0] = d;
        r.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        if (on) r.addView(UI.text(this, "使用中", 11, UI.GREEN, true));
        if (l != null) r.setOnClickListener(l);
        return r;
    }

    private void addRadio(LinearLayout box, String title, String desc, boolean on, View.OnClickListener l) {
        box.addView(radio(title, desc, on, l, null), UI.matchWrap(this, 0, 4, 0, 4));
    }

    private EditText field(LinearLayout parent, String label, String value) {
        parent.addView(UI.text(this, label, 12, UI.SUB, false), UI.matchWrap(this, 0, 6, 0, 2));
        EditText e = UI.input(this, label);
        e.setSingleLine(true);
        e.setTextSize(14);
        e.setText(value);
        parent.addView(e, UI.matchWrap(this, 0, 0, 0, 2));
        return e;
    }

    private void subTitle(LinearLayout box, String s) {
        box.addView(UI.text(this, s, 13, UI.TEXT, true), UI.matchWrap(this, 0, 12, 0, 4));
    }

    private void openSection(String key) {
        OPEN.add(key);
        build();
    }

    // ------------------------------------------------------------------ summaries

    private static String host(String url) {
        try { return new java.net.URL(url).getHost(); } catch (Exception e) { return url; }
    }

    private String fallbackName(String f) {
        return "cloud".equals(f) ? "在线接口" : "azure".equals(f) ? "Azure" : "系统引擎";
    }

    private String ttsSummary() {
        String m = Store.ttsMode(this);
        if ("bundled".equals(m)) return "内置离线音（AI 回复等用" + fallbackName(Store.ttsFallback(this)) + "）";
        if ("cloud".equals(m)) return "在线接口 · " + Store.ttsModel(this);
        if ("azure".equals(m)) return "微软 Azure · " + Store.azureVoice(this, Store.lang(this));
        String e = Speaker.engine(this);
        return "系统引擎 · " + (e == null ? "系统默认" : e);
    }

    private String asrSummary() {
        if ("cloud".equals(Store.asrMode(this))) return "在线识别 · " + Store.asrModel(this);
        if (Store.asrService(this).length() > 0) {
            ComponentName cn = ComponentName.unflattenFromString(Store.asrService(this));
            return "系统识别 · 指定 " + (cn == null ? Store.asrService(this) : cn.getPackageName());
        }
        ComponentName eff = Rec.pickService(this);
        return "系统识别 · 自动（" + (eff == null ? "系统默认" : eff.getPackageName()) + "）";
    }

    private String cloudSummary() {
        if (Store.audioKeyRaw(this).length() > 0) return "已配置 · " + host(Store.audioBase(this));
        return "未配置（需要时填 Key）";
    }

    private String azureSummary() {
        if (Store.azureKey(this).length() > 0) return "已配置 · " + Store.azureRegion(this);
        return "未配置（可选）";
    }

    // ------------------------------------------------------------------ section contents

    private void fillTts(LinearLayout box) {
        String m = Store.ttsMode(this);
        addRadio(box, "内置离线音（推荐）", "App 自带 " + AudioBank.assetCount(this) + " 条英/日录音，覆盖内置句子、场景包、五十音、单词；免代理、免 Key、可断网",
                "bundled".equals(m), ttsMode("bundled"));
        String e = Speaker.engine(this);
        addRadio(box, "系统引擎", "用手机里的语音引擎朗读 · 当前：" + (e == null ? "系统默认" : e), "system".equals(m), ttsMode("system"));
        addRadio(box, "在线接口（硅基流动 / OpenAI）", cloudSummary(), "cloud".equals(m), ttsMode("cloud"));
        addRadio(box, "微软 Azure", azureSummary() + " · 最接近真人", "azure".equals(m), ttsMode("azure"));

        if ("bundled".equals(m)) {
            subTitle(box, "没有内置录音的内容（AI 对话回复、AI 出题）用：");
            String f = Store.ttsFallback(this);
            addRadio(box, "系统引擎", "小米等国行引擎一般不会读日语", "system".equals(f), fallback("system"));
            addRadio(box, "在线接口", cloudSummary(), "cloud".equals(f), fallback("cloud"));
            addRadio(box, "微软 Azure", azureSummary(), "azure".equals(f), fallback("azure"));
        } else if ("system".equals(m)) {
            subTitle(box, "选择引擎（会检测是否支持英语 / 日语）");
            LinearLayout engines = UI.vbox(this);
            box.addView(engines);
            probeEngines(engines);
            subTitle(box, "引擎里的具体音色");
            LinearLayout r5 = UI.hbox(this);
            r5.addView(small("🎚 选英语音色", new View.OnClickListener() { public void onClick(View v) { pickSysVoice("en"); } }), UI.lpw(this, 1, 3));
            r5.addView(small("🎚 选日语音色", new View.OnClickListener() { public void onClick(View v) { pickSysVoice("ja"); } }), UI.lpw(this, 1, 3));
            box.addView(r5, UI.matchWrap(this, -3, 0, -3, 0));
            boolean googleTts = Speaker.installed(this, Speaker.GOOGLE_TTS);
            box.addView(small(googleTts ? "下载 Google 日语语音包（需代理）" : "安装 Google 语音服务（需代理）", new View.OnClickListener() {
                public void onClick(View v) {
                    if (Speaker.installed(VoiceActivity.this, Speaker.GOOGLE_TTS)) openGoogleVoiceData();
                    else openStore(Speaker.GOOGLE_TTS);
                }
            }), UI.matchWrap(this, 0, 6, 0, 0));
        } else {
            final String k = "cloud".equals(m) ? "cloud" : "azure";
            box.addView(small("去设置" + ("cloud".equals(m) ? "在线接口" : " Azure") + "（Key、音色）›", new View.OnClickListener() {
                public void onClick(View v) { openSection(k); }
            }), UI.matchWrap(this, 0, 10, 0, 0));
        }
    }

    private View.OnClickListener ttsMode(final String v) {
        return new View.OnClickListener() {
            public void onClick(View view) {
                Store.setTtsMode(VoiceActivity.this, v);
                if ("cloud".equals(v) && Store.audioKeyRaw(VoiceActivity.this).length() == 0) {
                    UI.toast(VoiceActivity.this, "已切换。还需要在「在线语音接口」里填 Key");
                    OPEN.add("cloud");
                }
                if ("azure".equals(v) && Store.azureKey(VoiceActivity.this).length() == 0) {
                    UI.toast(VoiceActivity.this, "已切换。还需要在「微软 Azure」里填 Key");
                    OPEN.add("azure");
                }
                build();
            }
        };
    }

    private View.OnClickListener fallback(final String v) {
        return new View.OnClickListener() {
            public void onClick(View view) {
                Store.setTtsFallback(VoiceActivity.this, v);
                if ("cloud".equals(v) && Store.audioKeyRaw(VoiceActivity.this).length() == 0) OPEN.add("cloud");
                if ("azure".equals(v) && Store.azureKey(VoiceActivity.this).length() == 0) OPEN.add("azure");
                build();
            }
        };
    }

    private void fillAsr(LinearLayout box) {
        final boolean cloud = "cloud".equals(Store.asrMode(this));
        addRadio(box, "系统识别", "用手机里的识别服务，评分更细；不支持某语言时自动换服务", !cloud, asrMode("system"));
        addRadio(box, "在线识别（硅基流动 SenseVoice 等）", cloudSummary() + " · 不需要代理", cloud, asrMode("cloud"));
        if (!cloud) {
            subTitle(box, "识别服务");
            LinearLayout svc = UI.vbox(this);
            box.addView(svc);
            renderServices(svc);
            if (!Speaker.installed(this, "com.google.android.googlequicksearchbox")) {
                box.addView(small("安装 Google App（识别更准，需代理）", new View.OnClickListener() {
                    public void onClick(View v) { openStore("com.google.android.googlequicksearchbox"); }
                }), UI.matchWrap(this, 0, 6, 0, 0));
            }
        } else if (Store.audioKeyRaw(this).length() == 0) {
            box.addView(small("去填在线接口的 Key ›", new View.OnClickListener() { public void onClick(View v) { openSection("cloud"); } }),
                    UI.matchWrap(this, 0, 10, 0, 0));
        }
    }

    private View.OnClickListener asrMode(final String v) {
        return new View.OnClickListener() {
            public void onClick(View view) {
                Store.setAsrMode(VoiceActivity.this, v);
                Rec.resetFallback();
                rec.destroy();
                if ("cloud".equals(v) && Store.audioKeyRaw(VoiceActivity.this).length() == 0) OPEN.add("cloud");
                build();
            }
        };
    }

    private void fillCloud(LinearLayout box) {
        box.addView(UI.text(this, "示范发音的「在线接口」和「在线识别」都用这里的配置。硅基流动国内直连，手机号注册送额度；合成结果会缓存，同一句不重复计费。", 12, UI.SUB, false),
                UI.matchWrap(this, 0, 0, 0, 4));
        subTitle(box, "服务商");
        final String base = Store.audioBase(this);
        for (final String[] p : PRESETS) {
            addRadio(box, p[0], p[1], p[1].equals(base), new View.OnClickListener() {
                public void onClick(View v) {
                    aBase.setText(p[1]); tModel.setText(p[2]); vEn.setText(p[3]); vJa.setText(p[4]); sModel.setText(p[5]);
                    build(); // build() saves the fields first
                }
            });
        }
        aBase = field(box, "Base URL", base);
        aKey = field(box, "API Key", Store.audioKeyRaw(this));
        aKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tModel = field(box, "合成模型", Store.ttsModel(this));
        vEn = field(box, "英语音色", Store.ttsVoice(this, "en"));
        vJa = field(box, "日语音色", Store.ttsVoice(this, "ja"));
        sModel = field(box, "识别模型", Store.asrModel(this));
        if (base.contains("siliconflow")) {
            subTitle(box, "英语音色（点一下选用）");
            box.addView(cosyGrid("en"));
            subTitle(box, "日语音色（点一下选用）");
            box.addView(cosyGrid("ja"));
        }
        LinearLayout r4 = UI.hbox(this);
        TextView test = UI.button(this, "测试合成（日语）", false);
        test.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { testCloudTts(); } });
        TextView save = UI.button(this, "保存", true);
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { saveCloud(); UI.toast(VoiceActivity.this, "已保存"); build(); } });
        r4.addView(test, UI.lpw(this, 1.4f, 3));
        r4.addView(save, UI.lpw(this, 1, 3));
        box.addView(r4, UI.matchWrap(this, -3, 10, -3, 0));
        cloudResult = UI.text(this, "", 13, UI.SUB, false);
        box.addView(cloudResult, UI.matchWrap(this, 0, 6, 0, 0));
    }

    private static final String[][] COSY = {
            {"anna", "anna 沉稳女·英语推荐"}, {"claire", "claire 温柔女·日语推荐"}, {"alex", "alex 沉稳男"}, {"benjamin", "benjamin 低沉男"},
            {"charles", "charles 磁性男"}, {"david", "david 欢快男"}, {"bella", "bella 激情女"}, {"diana", "diana 欢快女"}};

    private View cosyGrid(final String lang) {
        LinearLayout grid = UI.vbox(this);
        String cur = Store.ttsVoice(this, lang);
        LinearLayout row = null;
        for (int i = 0; i < COSY.length; i++) {
            if (i % 2 == 0) { row = UI.hbox(this); grid.addView(row, UI.matchWrap(this, -3, 0, -3, 0)); }
            final String id = "FunAudioLLM/CosyVoice2-0.5B:" + COSY[i][0];
            boolean on = id.equals(cur);
            TextView c = UI.chip(this, (on ? "✓ " : "") + COSY[i][1], on);
            c.setTextSize(12);
            c.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    ("ja".equals(lang) ? vJa : vEn).setText(id);
                    saveCloud();
                    build();
                    previewCloud(lang);
                }
            });
            row.addView(c, UI.lpw(this, 1, 3));
        }
        return grid;
    }

    private void fillAzure(LinearLayout box) {
        box.addView(UI.text(this, "免费档（F0）每月 50 万字符。在 portal.azure.com 创建「语音服务（Speech）」，区域选 Japan East，把「密钥 1」和区域填到这里。"
                + "填好后到「示范发音用什么」选「微软 Azure」，或在内置离线音下把兜底选为 Azure。", 12, UI.SUB, false), UI.matchWrap(this, 0, 0, 0, 4));
        azRegion = field(box, "区域（如 japaneast / eastasia / eastus）", Store.azureRegion(this));
        azKey = field(box, "Key", Store.azureKey(this));
        azKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        subTitle(box, "日语音色（点一下试听并选用）");
        box.addView(voiceGrid("ja", AZ_JA));
        subTitle(box, "英语音色");
        box.addView(voiceGrid("en", AZ_EN));
        TextView save = UI.button(this, "保存", true);
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { saveAzure(); UI.toast(VoiceActivity.this, "已保存"); build(); } });
        box.addView(save, UI.matchWrap(this, 0, 10, 0, 0));
        azResult = UI.text(this, "", 13, UI.SUB, false);
        box.addView(azResult, UI.matchWrap(this, 0, 6, 0, 0));
    }

    private void fillGuide(LinearLayout box) {
        box.addView(UI.text(this,
                "推荐（不开代理）：\n"
                        + "① 示范发音：内置离线音（默认就是）。\n"
                        + "② 语音识别：在线识别 + 硅基流动 Key（手机号注册即可）。\n"
                        + "③ AI 回复的朗读：兜底选「在线接口」或「Azure」。\n\n"
                        + "能开代理时：安装 Google 语音服务和 Google App，系统识别会更准（多候选、置信度）；"
                        + "在「系统引擎」里还能挑 Google 的联网高音质音色。", 13, UI.SUB, false));
    }

    // ------------------------------------------------------------------ TTS engines

    private void probeEngines(final LinearLayout box) {
        box.removeAllViews();
        final String chosen = Store.ttsEngine(this);
        final String effective = Speaker.engine(this);
        addEngineRow(box, "", "自动（优先 Google）", effective == null ? "实际使用：系统默认引擎" : "实际使用：" + effective, chosen.length() == 0);
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

    private TextView addEngineRow(final LinearLayout box, final String pkg, final String label, String status, boolean on) {
        TextView[] d = new TextView[1];
        box.addView(radio(label, status, on, new View.OnClickListener() {
            public void onClick(View v) {
                Store.setTtsEngine(VoiceActivity.this, pkg);
                UI.toast(VoiceActivity.this, pkg.length() == 0 ? "已设为自动" : "已选用：" + label);
                build();
            }
        }, d), UI.matchWrap(this, 0, 4, 0, 4));
        return d[0];
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
                        rows.get(i).setText(e.name + " · " + st);
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
        addSvcRow(box, "", "自动（推荐）", "实际使用：" + (eff == null ? "系统默认" : eff.getPackageName()) + "；不可用时自动换下一个", chosen.length() == 0);
        if (all.isEmpty()) box.addView(UI.text(this, "没有找到语音识别服务", 13, UI.RED, false));
        for (String[] s : all) {
            String[] parts = s[1].split("\n");
            addSvcRow(box, s[0], parts[0], parts.length > 1 ? parts[1] : "", s[0].equals(chosen));
        }
    }

    private void addSvcRow(final LinearLayout box, final String id, String label, String desc, boolean on) {
        box.addView(radio(label, desc, on, new View.OnClickListener() {
            public void onClick(View v) {
                Store.setAsrService(VoiceActivity.this, id);
                Rec.resetFallback();
                rec.destroy();
                build();
            }
        }, null), UI.matchWrap(this, 0, 4, 0, 4));
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
                try { f = CloudAudio.openaiTts(VoiceActivity.this, "こんにちは。日本語の発音練習を始めましょう。", "ja", 1f); }
                catch (Exception e) { err = e.getMessage() != null ? e.getMessage() : e.toString(); }
                final String fe = err;
                final File ff = f;
                h.post(new Runnable() {
                    public void run() {
                        if (destroyed) return;
                        if (fe != null) { cloudResult.setTextColor(UI.RED); cloudResult.setText("❌ " + fe); return; }
                        cloudResult.setTextColor(UI.GREEN);
                        cloudResult.setText("✅ 合成成功，正在播放");
                        playFile(ff);
                    }
                });
            }
        }).start();
    }

    private void previewCloud(final String lang) {
        if (cloudResult == null) return;
        cloudResult.setTextColor(UI.SUB);
        cloudResult.setText("正在合成试听…");
        final String text = "ja".equals(lang) ? "こんにちは。一緒に日本語の発音を練習しましょう。" : "Hi! I'm your speaking coach. Let's practice together.";
        new Thread(new Runnable() {
            public void run() {
                String err = null;
                File f = null;
                try { f = CloudAudio.openaiTts(VoiceActivity.this, text, lang, 1f); }
                catch (Exception e) { err = e.getMessage() != null ? e.getMessage() : e.toString(); }
                final String fe = err;
                final File ff = f;
                h.post(new Runnable() {
                    public void run() {
                        if (destroyed || cloudResult == null) return;
                        if (fe != null) { cloudResult.setTextColor(UI.RED); cloudResult.setText("❌ " + fe); return; }
                        cloudResult.setTextColor(UI.GREEN);
                        cloudResult.setText("✅ 已选用并试听：" + Store.ttsVoice(VoiceActivity.this, lang));
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
            final String label = voices[i][1];
            final TextView c = UI.chip(this, (id.equals(cur) ? "✓ " : "") + label, id.equals(cur));
            c.setTag(label);
            c.setTextSize(12);
            chips.add(c);
            c.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Store.setAzureVoice(VoiceActivity.this, lang, id);
                    for (TextView x : chips) {
                        UI.setChip(VoiceActivity.this, x, x == c);
                        x.setText((x == c ? "✓ " : "") + x.getTag());
                    }
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
                        azResult.setText("✅ " + voice + ("azure".equals(Store.cloudProvider(VoiceActivity.this)) ? "" : "\n满意的话把上面的「语音合成」切到「Azure」，或在内置离线音下把兜底选为 Azure"));
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
