package com.xyb.voxcoach;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {

    private EditText base, key, model;
    private TextView testResult;
    private EditText packUrl;

    private static final String[][] PRESETS = {
            {"DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"},
            {"通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"},
            {"Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k"},
            {"智谱", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"},
            {"OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"},
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        build();
    }

    private void build() {
        LinearLayout body = UI.screen(this, "设置", null);

        // ---------------- AI
        LinearLayout ai = UI.card(this);
        ai.addView(UI.text(this, "🤖 AI 接口（OpenAI 兼容）", 16, UI.TEXT, true));
        ai.addView(UI.text(this, "用于情景对话、AI 发音诊断、按弱项出题。Key 只保存在本机。", 12, UI.SUB, false), UI.matchWrap(this, 0, 4, 0, 8));
        LinearLayout pr = UI.hbox(this);
        LinearLayout pr2 = UI.hbox(this);
        for (int i = 0; i < PRESETS.length; i++) {
            final String[] p = PRESETS[i];
            TextView c = UI.chip(this, p[0], p[1].equals(Store.apiBase(this)));
            c.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    base.setText(p[1]);
                    model.setText(p[2]);
                    UI.toast(SettingsActivity.this, "已填入 " + p[0] + " 的地址和默认模型，模型名可自行修改");
                }
            });
            (i < 3 ? pr : pr2).addView(c, UI.lpw(this, 1, 3));
        }
        pr2.addView(new View(this), UI.lpw(this, 1, 3));
        ai.addView(pr, UI.matchWrap(this, -3, 0, -3, 0));
        ai.addView(pr2, UI.matchWrap(this, -3, 0, -3, 8));
        ai.addView(UI.text(this, "Base URL", 12, UI.SUB, false));
        base = UI.input(this, "https://api.deepseek.com/v1");
        base.setText(Store.apiBase(this));
        base.setSingleLine(true);
        ai.addView(base, UI.matchWrap(this, 0, 4, 0, 8));
        ai.addView(UI.text(this, "API Key", 12, UI.SUB, false));
        key = UI.input(this, "sk-...");
        key.setText(Store.apiKey(this));
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        ai.addView(key, UI.matchWrap(this, 0, 4, 0, 8));
        ai.addView(UI.text(this, "模型名", 12, UI.SUB, false));
        model = UI.input(this, "deepseek-chat");
        model.setText(Store.apiModel(this));
        model.setSingleLine(true);
        ai.addView(model, UI.matchWrap(this, 0, 4, 0, 10));
        LinearLayout br = UI.hbox(this);
        TextView save = UI.button(this, "保存", true);
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { saveApi(); UI.toast(SettingsActivity.this, "已保存"); }
        });
        TextView test = UI.button(this, "测试连接", false);
        test.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { testApi(); } });
        br.addView(test, UI.lpw(this, 1, 3));
        br.addView(save, UI.lpw(this, 1, 3));
        ai.addView(br, UI.matchWrap(this, -3, 0, -3, 0));
        testResult = UI.text(this, "", 13, UI.SUB, false);
        ai.addView(testResult, UI.matchWrap(this, 0, 6, 0, 0));
        body.addView(ai, UI.matchWrap(this, 0, 4, 0, 12));

        // ---------------- learning
        LinearLayout le = UI.card(this);
        le.addView(UI.text(this, "📈 学习设置", 16, UI.TEXT, true));
        le.addView(UI.text(this, "英语等级（会根据得分自动调整）", 12, UI.SUB, false), UI.matchWrap(this, 0, 10, 0, 4));
        le.addView(levelRow("en"));
        le.addView(UI.text(this, "日语等级（会根据得分自动调整）", 12, UI.SUB, false), UI.matchWrap(this, 0, 10, 0, 4));
        le.addView(levelRow("ja"));

        le.addView(UI.text(this, "每日目标（题数）", 12, UI.SUB, false), UI.matchWrap(this, 0, 10, 0, 4));
        final int[] goals = {10, 20, 30, 50};
        final List<TextView> gChips = new ArrayList<TextView>();
        LinearLayout gr = UI.hbox(this);
        for (final int g : goals) {
            final TextView c = UI.chip(this, String.valueOf(g), Store.goal(this) == g);
            gChips.add(c);
            c.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Store.setGoal(SettingsActivity.this, g);
                    for (TextView x : gChips) UI.setChip(SettingsActivity.this, x, x == c);
                }
            });
            gr.addView(c, UI.lpw(this, 1, 3));
        }
        le.addView(gr, UI.matchWrap(this, -3, 0, -3, 0));

        le.addView(UI.text(this, "英语口音（发音示范 + 识别）", 12, UI.SUB, false), UI.matchWrap(this, 0, 10, 0, 4));
        LinearLayout ac = UI.hbox(this);
        final TextView us = UI.chip(this, "🇺🇸 美音", "US".equals(Store.accent(this)));
        final TextView uk = UI.chip(this, "🇬🇧 英音", "UK".equals(Store.accent(this)));
        us.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Store.setAccent(SettingsActivity.this, "US"); UI.setChip(SettingsActivity.this, us, true); UI.setChip(SettingsActivity.this, uk, false); }
        });
        uk.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Store.setAccent(SettingsActivity.this, "UK"); UI.setChip(SettingsActivity.this, uk, true); UI.setChip(SettingsActivity.this, us, false); }
        });
        ac.addView(us, UI.lpw(this, 1, 3));
        ac.addView(uk, UI.lpw(this, 1, 3));
        le.addView(ac, UI.matchWrap(this, -3, 0, -3, 0));

        le.addView(UI.text(this, "示范语速（跟读 / 对话的基础语速）", 12, UI.SUB, false), UI.matchWrap(this, 0, 10, 0, 4));
        final float[] rates = {0.8f, 0.9f, 0.95f, 1.0f, 1.1f};
        final List<TextView> rChips = new ArrayList<TextView>();
        LinearLayout rr = UI.hbox(this);
        for (final float r : rates) {
            final TextView c = UI.chip(this, r + "x", Math.abs(Store.ttsRate(this) - r) < 0.01f);
            rChips.add(c);
            c.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Store.setTtsRate(SettingsActivity.this, r);
                    for (TextView x : rChips) UI.setChip(SettingsActivity.this, x, x == c);
                }
            });
            rr.addView(c, UI.lpw(this, 1, 2));
        }
        le.addView(rr, UI.matchWrap(this, -2, 0, -2, 0));

        final TextView zh = UI.chip(this, Store.showZh(this) ? "✓ 默认显示中文翻译" : "默认隐藏中文翻译", Store.showZh(this));
        zh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean n = !Store.showZh(SettingsActivity.this);
                Store.setShowZh(SettingsActivity.this, n);
                zh.setText(n ? "✓ 默认显示中文翻译" : "默认隐藏中文翻译");
                UI.setChip(SettingsActivity.this, zh, n);
            }
        });
        le.addView(zh, UI.matchWrap(this, 0, 12, 0, 0));
        body.addView(le, UI.matchWrap(this, 0, 0, 0, 12));

        // ---------------- voice
        LinearLayout vc = UI.card(this);
        vc.addView(UI.text(this, "🔊 语音环境", 16, UI.TEXT, true));
        String tm = "azure".equals(Store.ttsMode(this)) ? "微软 Azure · " + Store.azureVoice(this, Store.lang(this)) : "cloud".equals(Store.ttsMode(this)) ? "在线 TTS" : (Speaker.engine(this) == null ? "系统默认引擎" : Speaker.engine(this));
        String am = "cloud".equals(Store.asrMode(this)) ? "在线识别" : (Rec.available(this) ? "系统识别" : "❌ 不可用");
        vc.addView(UI.text(this, "合成：" + tm + "\n识别：" + am, 13, UI.SUB, false), UI.matchWrap(this, 0, 6, 0, 8));
        TextView openVoice = UI.button(this, "语音引擎 / 日语语音包 / 在线语音 ›", false);
        openVoice.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, VoiceActivity.class)); }
        });
        vc.addView(openVoice);
        body.addView(vc, UI.matchWrap(this, 0, 0, 0, 12));

        // ---------------- content packs
        LinearLayout cp = UI.card(this);
        cp.addView(UI.text(this, "📦 内容更新（内容包）", 16, UI.TEXT, true));
        cp.addView(UI.text(this, "内置题库随 App 版本更新；额外的句子、对立词、对话场景可以用 JSON 内容包导入。填一个链接（如 Gitee / GitHub raw 地址）以后点「更新」即可拉取新版，也可以从手机文件导入。格式见项目里的 README 和 content_packs/ 示例。",
                12, UI.SUB, false), UI.matchWrap(this, 0, 4, 0, 8));
        packUrl = UI.input(this, "https://…/my_pack.json");
        packUrl.setSingleLine(true);
        packUrl.setText(Store.packUrl(this));
        cp.addView(packUrl, UI.matchWrap(this, 0, 0, 0, 8));
        LinearLayout pr3 = UI.hbox(this);
        TextView dl = UI.button(this, "⬇ 从链接更新", true);
        dl.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { downloadPack(); } });
        TextView imp = UI.button(this, "📂 从文件导入", false);
        imp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, 41);
                } catch (Exception e) { UI.toast(SettingsActivity.this, "无法打开文件选择器"); }
            }
        });
        pr3.addView(dl, UI.lpw(this, 1, 3));
        pr3.addView(imp, UI.lpw(this, 1, 3));
        cp.addView(pr3, UI.matchWrap(this, -3, 0, -3, 4));
        List<org.json.JSONObject> packs = Store.packs(this);
        if (packs.isEmpty()) cp.addView(UI.text(this, "尚未安装内容包", 12, UI.HINT, false), UI.matchWrap(this, 0, 6, 0, 0));
        for (final org.json.JSONObject p : packs) {
            LinearLayout r = UI.hbox(this);
            r.setBackground(UI.round(this, UI.BG, 12));
            int pd = UI.dp(this, 10);
            r.setPadding(pd, pd, pd, pd);
            int ns = p.optJSONArray("sentences") == null ? 0 : p.optJSONArray("sentences").length();
            int np = p.optJSONArray("pairs") == null ? 0 : p.optJSONArray("pairs").length();
            int nc = p.optJSONArray("scenarios") == null ? 0 : p.optJSONArray("scenarios").length();
            r.addView(UI.text(this, p.optString("name", p.optString("id")) + "  v" + p.optInt("version", 1)
                    + "\n句子 " + ns + " · 对立词 " + np + " · 场景 " + nc, 13, UI.TEXT, false),
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView del = UI.text(this, "删除", 13, UI.RED, true);
            del.setPadding(pd, pd, 0, pd);
            del.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    new AlertDialog.Builder(SettingsActivity.this).setMessage("删除内容包「" + p.optString("name") + "」？")
                            .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface d, int w) { Store.removePack(SettingsActivity.this, p.optString("id")); build(); }
                            }).setNegativeButton("取消", null).show();
                }
            });
            r.addView(del);
            cp.addView(r, UI.matchWrap(this, 0, 6, 0, 0));
        }
        body.addView(cp, UI.matchWrap(this, 0, 0, 0, 12));

        // ---------------- data
        LinearLayout dc = UI.card(this);
        dc.addView(UI.text(this, "🗂 数据", 16, UI.TEXT, true));
        dc.addView(UI.text(this, "累计练习 " + Store.totalCount(this) + " 次 · AI 生成题目 英语 " + Store.aiSentences(this, "en").size()
                + " 句 / 日语 " + Store.aiSentences(this, "ja").size() + " 句", 13, UI.SUB, false), UI.matchWrap(this, 0, 6, 0, 8));
        TextView reset = UI.button(this, "重置学习进度", false);
        reset.setTextColor(UI.RED);
        reset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this).setTitle("重置学习进度？")
                        .setMessage("得分记录、弱项统计、连续天数和 AI 生成的题目都会清空（AI 接口配置会保留）。")
                        .setPositiveButton("重置", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) { Store.resetProgress(SettingsActivity.this); build(); }
                        })
                        .setNegativeButton("取消", null).show();
            }
        });
        dc.addView(reset);
        body.addView(dc, UI.matchWrap(this, 0, 0, 0, 12));
        body.addView(UI.text(this, "VoxCoach 1.0 · 发音评分基于系统语音识别的逐词比对，仅供练习参考", 11, UI.HINT, false), UI.matchWrap(this, 4, 4, 4, 0));
    }

    private View levelRow(final String lang) {
        LinearLayout r = UI.hbox(this);
        final List<TextView> chips = new ArrayList<TextView>();
        for (int i = 1; i <= 4; i++) {
            final int lv = i;
            final TextView c = UI.chip(this, Content.levelName(lang, i).split(" ")[0], Store.level(this, lang) == i);
            chips.add(c);
            c.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Store.setLevel(SettingsActivity.this, lang, lv);
                    for (TextView x : chips) UI.setChip(SettingsActivity.this, x, x == c);
                }
            });
            r.addView(c, UI.lpw(this, 1, 3));
        }
        LinearLayout wrap = UI.vbox(this);
        wrap.addView(r, UI.matchWrap(this, -3, 0, -3, 0));
        return wrap;
    }

    private void downloadPack() {
        final String url = packUrl.getText().toString().trim();
        if (!url.startsWith("http")) { UI.toast(this, "请填写 http(s) 开头的内容包链接"); return; }
        Store.setPackUrl(this, url);
        UI.toast(this, "下载中…");
        final android.os.Handler h = new android.os.Handler();
        new Thread(new Runnable() {
            public void run() {
                String msg;
                try {
                    java.net.HttpURLConnection con = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    con.setConnectTimeout(15000);
                    con.setReadTimeout(30000);
                    int code = con.getResponseCode();
                    if (code >= 400) throw new Exception("HTTP " + code);
                    String text = CloudAudio.readString(con.getInputStream());
                    con.disconnect();
                    msg = Store.installPacks(SettingsActivity.this, text);
                } catch (Exception e) {
                    msg = "❌ 更新失败：" + (e.getMessage() != null ? e.getMessage() : e.toString());
                }
                final String fm = msg;
                h.post(new Runnable() {
                    public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        new AlertDialog.Builder(SettingsActivity.this).setTitle("内容更新").setMessage(fm).setPositiveButton("好", null).show();
                        build();
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 41 || res != RESULT_OK || data == null || data.getData() == null) return;
        String msg;
        try {
            msg = Store.installPacks(this, CloudAudio.readString(getContentResolver().openInputStream(data.getData())));
        } catch (Exception e) {
            msg = "❌ 导入失败：" + e.getMessage();
        }
        new AlertDialog.Builder(this).setTitle("导入内容包").setMessage(msg).setPositiveButton("好", null).show();
        build();
    }

    private void saveApi() {
        Store.setApi(this, base.getText().toString(), key.getText().toString(), model.getText().toString());
    }

    private void testApi() {
        saveApi();
        testResult.setTextColor(UI.SUB);
        testResult.setText("连接中…");
        JSONArray m = new JSONArray();
        m.put(AiClient.msg("user", "Reply with exactly: OK"));
        AiClient.chat(this, m, 0, new AiClient.Cb() {
            public void ok(String content) {
                testResult.setTextColor(UI.GREEN);
                testResult.setText("✅ 连接成功：" + (content.length() > 60 ? content.substring(0, 60) : content));
            }
            public void fail(String error) {
                testResult.setTextColor(UI.RED);
                testResult.setText("❌ " + error);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveApi();
        if (packUrl != null) Store.setPackUrl(this, packUrl.getText().toString());
    }
}
