package com.xyb.voxcoach;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Role-play conversation with an OpenAI-compatible LLM, with per-turn corrections. */
public class ChatActivity extends Activity {

    private String lang;
    private boolean ja;
    private Speaker sp;
    private Rec rec;
    private final Handler h = new Handler();
    private Content.Scenario sc;
    private JSONArray history;
    private LinearLayout msgs;
    private ScrollView scroll;
    private EditText input;
    private TextView mic, status, subChip;
    private boolean subtitles = true, busy, inChat;
    private int turns, scoreSum;

    private static class AiMsg { TextView text, zh; String reply, replyZh; }
    private final List<AiMsg> aiMsgs = new ArrayList<AiMsg>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        lang = Store.lang(this);
        ja = "ja".equals(lang);
        sp = new Speaker(this, lang);
        rec = new Rec(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!inChat) showScenarios();
    }

    // ------------------------------------------------------------------ scenario list

    private void showScenarios() {
        inChat = false;
        LinearLayout body = UI.screen(this, "AI 情景对话", null);
        if (!Store.aiReady(this)) {
            LinearLayout c = UI.card(this);
            c.addView(UI.text(this, "需要先配置 AI 接口", 16, UI.TEXT, true));
            c.addView(UI.text(this, "情景对话使用 OpenAI 兼容接口（DeepSeek / 通义千问 / Kimi / 智谱 / OpenAI 等）。填好 Base URL、API Key 和模型名即可。", 13, UI.SUB, false),
                    UI.matchWrap(this, 0, 6, 0, 10));
            TextView go = UI.button(this, "去设置", true);
            go.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { startActivity(new Intent(ChatActivity.this, SettingsActivity.class)); }
            });
            c.addView(go);
            body.addView(c, UI.matchWrap(this, 0, 4, 0, 14));
        }
        body.addView(UI.text(this, "选一个场景，用" + (ja ? "日语" : "英语") + "开口聊。AI 会按你的水平（"
                + Content.levelName(lang, Store.level(this, lang)) + "）回复，并纠正你的每一句话。", 13, UI.SUB, false), UI.matchWrap(this, 2, 4, 2, 12));
        LinearLayout add = UI.hbox(this);
        TextView mine = UI.button(this, "＋ 自定义", false);
        mine.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { customDialog(); } });
        TextView aiGen = UI.button(this, "✨ AI 生成", false);
        aiGen.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { aiScenarioDialog(); } });
        TextView lib = UI.button(this, "📦 场景库", false);
        lib.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(ChatActivity.this, PacksActivity.class)); }
        });
        add.addView(mine, UI.lpw(this, 1, 3));
        add.addView(aiGen, UI.lpw(this, 1, 3));
        add.addView(lib, UI.lpw(this, 0.9f, 3));
        body.addView(add, UI.matchWrap(this, -3, 0, -3, 10));
        for (final Content.Scenario s : Store.scenarios(this, lang)) {
            LinearLayout row = UI.hbox(this);
            row.setBackground(UI.ripple(this, UI.round(this, UI.CARD, 18)));
            int p = UI.dp(this, 16);
            row.setPadding(p, p, p, p);
            row.addView(UI.text(this, s.icon, 28, UI.TEXT, false));
            LinearLayout t = UI.vbox(this);
            t.addView(UI.text(this, s.title, 16, UI.TEXT, true));
            t.addView(UI.text(this, s.sub + (s.source != null ? "  · " + s.source : ""), 13, UI.SUB, false));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            tp.setMargins(UI.dp(this, 14), 0, 0, 0);
            row.addView(t, tp);
            row.addView(UI.text(this, "›", 24, UI.HINT, false));
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (!Store.aiReady(ChatActivity.this)) { UI.toast(ChatActivity.this, "请先配置 AI 接口"); return; }
                    startChat(s);
                }
            });
            if (s.custom) {
                row.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View v) {
                        new AlertDialog.Builder(ChatActivity.this).setMessage("删除场景「" + s.title + "」？")
                                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface d, int w) { Store.removeCustomScenario(ChatActivity.this, s.id); showScenarios(); }
                                }).setNegativeButton("取消", null).show();
                        return true;
                    }
                });
            }
            body.addView(row, UI.matchWrap(this, 0, 0, 0, 10));
        }
        body.addView(UI.text(this, "长按「我的」场景可删除；更多场景在「📦 场景库」里一键安装和更新。", 11, UI.HINT, false), UI.matchWrap(this, 2, 2, 2, 0));
    }

    private EditText dlgField(LinearLayout box, String label, String hint, boolean multi) {
        box.addView(UI.text(this, label, 12, UI.SUB, false), UI.matchWrap(this, 0, 8, 0, 2));
        EditText e = UI.input(this, hint);
        if (multi) { e.setMinLines(3); e.setGravity(Gravity.TOP | Gravity.START); } else e.setSingleLine(true);
        box.addView(e);
        return e;
    }

    private void customDialog() {
        LinearLayout box = UI.vbox(this);
        int p = UI.dp(this, 20);
        box.setPadding(p, UI.dp(this, 8), p, 0);
        final EditText title = dlgField(box, "场景名称", "例如：看牙医", false);
        final EditText role = dlgField(box, "AI 扮演谁 / 场景描述（中文也可以）", "例如：你是东京一家牙科诊所的前台，我第一次来看牙，需要填问诊表、约时间", true);
        final EditText opener = dlgField(box, "AI 的第一句话（可留空）", ja ? "例如：こんにちは。初診の方ですか？" : "e.g. Hi, is this your first visit?", false);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);
        new AlertDialog.Builder(this).setTitle("自定义场景").setView(sv)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String t = title.getText().toString().trim(), r = role.getText().toString().trim();
                        if (t.length() == 0 || r.length() == 0) { UI.toast(ChatActivity.this, "场景名称和描述都要填"); return; }
                        Store.addCustomScenario(ChatActivity.this, lang, "💬", t, ja ? "自定义（日语）" : "Custom", r, opener.getText().toString().trim());
                        showScenarios();
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void aiScenarioDialog() {
        if (!Store.aiReady(this)) { UI.toast(this, "请先配置 AI 接口"); return; }
        LinearLayout box = UI.vbox(this);
        int p = UI.dp(this, 20);
        box.setPadding(p, UI.dp(this, 8), p, 0);
        final EditText desc = dlgField(box, "一句话描述想练的场景", "例如：在日本租房，跟中介看房谈条件", true);
        new AlertDialog.Builder(this).setTitle("AI 生成场景").setView(box)
                .setPositiveButton("生成", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String q = desc.getText().toString().trim();
                        if (q.length() > 0) generateScenario(q);
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void generateScenario(String desc) {
        JSONArray m = new JSONArray();
        m.put(AiClient.msg("system", "You design role-play scenarios for a language-learning app. Output strictly valid JSON only."));
        m.put(AiClient.msg("user", "Target language: " + (ja ? "Japanese" : "English") + ". Learner level: "
                + Content.levelName(lang, Store.level(this, lang)) + ". The learner wants to practise: " + desc
                + "\nReturn JSON: {\"icon\": \"one emoji\", \"title\": \"简短中文标题(<=8字)\", \"sub\": \"short subtitle in the target language\", "
                + "\"role\": \"English instructions describing who the AI plays, the setting, and what the learner should accomplish\", "
                + "\"opener\": \"the AI's natural first line in the target language\"}"));
        UI.toast(this, "AI 正在设计场景…");
        AiClient.chat(this, m, 0.8, new AiClient.Cb() {
            public void ok(String content) {
                if (isFinishing() || isDestroyed()) return;
                JSONObject o = AiClient.json(content);
                if (o == null || o.optString("role").length() == 0) { UI.toast(ChatActivity.this, "生成失败，请换个描述再试"); return; }
                Store.addCustomScenario(ChatActivity.this, lang, o.optString("icon", "💬"), o.optString("title", "AI 场景"),
                        o.optString("sub", ""), o.optString("role"), o.optString("opener", ""));
                UI.toast(ChatActivity.this, "已添加场景「" + o.optString("title") + "」");
                if (!inChat) showScenarios();
            }
            public void fail(String error) { UI.toast(ChatActivity.this, "生成失败：" + error); }
        });
    }

    // ------------------------------------------------------------------ chat

    private String systemPrompt() {
        int lv = Store.level(this, lang);
        String level = ja ? "JLPT N" + (6 - lv) : new String[]{"", "CEFR A1 (beginner)", "CEFR A2", "CEFR B1", "CEFR B2"}[lv];
        return "You are a role-play partner inside a language-learning app. Target language: " + (ja ? "Japanese" : "English")
                + ". Learner level: " + level + ". Learner's native language: Chinese.\n"
                + "Scenario: " + sc.role + "\n"
                + "Rules:\n- Stay in character and keep the conversation going. Reply ONLY in the target language, 1-3 short spoken-style sentences, "
                + "vocabulary suited to the learner's level. Usually end with a question so the learner keeps speaking.\n"
                + "- The learner's messages come from speech recognition, so ignore punctuation/capitalization. If a word looks mis-recognized, "
                + "it may be a pronunciation problem: mention it in feedback.\n"
                + "- Evaluate the learner's LAST message.\n"
                + "Return ONLY a JSON object, no markdown:\n"
                + "{\"reply\": \"your in-character reply\", \"reply_zh\": \"简体中文翻译\", "
                + "\"feedback\": \"用简体中文写 1-2 句：上一句的语法/用词/发音问题；没问题就留空字符串\", "
                + "\"better\": \"a more natural way to say the learner's last message in the target language; empty if already natural\", "
                + "\"score\": 0-100 integer for how correct and natural the learner's last message was}";
    }

    private void startChat(Content.Scenario s) {
        inChat = true;
        sc = s;
        busy = false; // a reply still in flight belongs to the previous chat and is dropped in send()
        turns = 0;
        scoreSum = 0;
        aiMsgs.clear();
        history = new JSONArray();
        history.put(AiClient.msg("system", systemPrompt()));
        history.put(AiClient.msg("assistant", s.opener));

        LinearLayout root = UI.vbox(this);
        root.setBackgroundColor(UI.BG);
        LinearLayout bar = UI.hbox(this);
        bar.setPadding(UI.dp(this, 8), UI.dp(this, 6), UI.dp(this, 12), UI.dp(this, 6));
        TextView back = UI.text(this, "‹", 30, UI.TEXT, false);
        back.setPadding(UI.dp(this, 10), 0, UI.dp(this, 14), UI.dp(this, 4));
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { leaveChat(); } });
        bar.addView(back);
        bar.addView(UI.text(this, s.icon + " " + s.title, 17, UI.TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        subChip = UI.chip(this, "字幕 开", true);
        subChip.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleSubs(); } });
        bar.addView(subChip);
        TextView rev = UI.chip(this, "点评", false);
        rev.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { summary(); } });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(UI.dp(this, 6), 0, 0, 0);
        bar.addView(rev, rp);
        root.addView(bar);

        scroll = new ScrollView(this);
        msgs = UI.vbox(this);
        msgs.setPadding(UI.dp(this, 12), UI.dp(this, 4), UI.dp(this, 12), UI.dp(this, 12));
        scroll.addView(msgs);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bottom = UI.vbox(this);
        bottom.setBackgroundColor(UI.CARD);
        bottom.setPadding(UI.dp(this, 12), UI.dp(this, 8), UI.dp(this, 12), UI.dp(this, 10));
        status = UI.text(this, "点麦克风说话，或者打字发送", 12, UI.SUB, false);
        status.setGravity(Gravity.CENTER);
        bottom.addView(status, UI.matchWrap(this, 0, 0, 0, 6));
        LinearLayout row = UI.hbox(this);
        input = UI.input(this, ja ? "日本語で入力…" : "Type in English…");
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) { sendInput(); return true; }
        });
        row.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView send = UI.button(this, "发送", false);
        send.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { sendInput(); } });
        LinearLayout.LayoutParams spp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spp.setMargins(UI.dp(this, 8), 0, UI.dp(this, 8), 0);
        row.addView(send, spp);
        mic = UI.micButton(this, 54);
        mic.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { onMic(); } });
        row.addView(mic, new LinearLayout.LayoutParams(UI.dp(this, 54), UI.dp(this, 54)));
        bottom.addView(row);
        root.addView(bottom);
        setContentView(root);

        addAi(s.opener, "");
        sp.speak(s.opener, 1f, null);
    }

    private void leaveChat() {
        stop();
        if (turns >= 3) {
            new AlertDialog.Builder(this).setMessage("要先让 AI 点评这次对话吗？")
                    .setPositiveButton("点评", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { summary(); }
                    })
                    .setNegativeButton("直接退出", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { showScenarios(); }
                    }).show();
        } else showScenarios();
    }

    @Override
    public void onBackPressed() {
        if (inChat) leaveChat();
        else super.onBackPressed();
    }

    private int maxBubble() {
        return (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
    }

    private void addAi(final String reply, String zh) {
        final AiMsg m = new AiMsg();
        m.reply = reply;
        m.replyZh = zh;
        LinearLayout row = UI.hbox(this);
        row.setGravity(Gravity.START);
        LinearLayout bub = UI.vbox(this);
        bub.setBackground(UI.ripple(this, UI.round(this, UI.CARD, 16)));
        int p = UI.dp(this, 12);
        bub.setPadding(p, UI.dp(this, 10), p, UI.dp(this, 10));
        m.text = UI.text(this, "", 16, UI.TEXT, false);
        m.text.setMaxWidth(maxBubble());
        bub.addView(m.text);
        m.zh = UI.text(this, "", 13, UI.SUB, false);
        m.zh.setMaxWidth(maxBubble());
        bub.addView(m.zh, UI.matchWrap(this, 0, 4, 0, 0));
        bub.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { sp.speak(reply, 1f, null); }
        });
        bub.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { sp.speak(reply, 0.7f, null); UI.toast(ChatActivity.this, "慢速重播"); return true; }
        });
        row.addView(bub);
        msgs.addView(row, UI.matchWrap(this, 0, 6, 40, 6));
        aiMsgs.add(m);
        renderAi(m);
        scrollDown();
    }

    private void renderAi(AiMsg m) {
        if (subtitles) {
            m.text.setText(m.reply);
            m.zh.setText(m.replyZh == null ? "" : m.replyZh);
            m.zh.setVisibility(m.replyZh != null && m.replyZh.length() > 0 && Store.showZh(this) ? View.VISIBLE : View.GONE);
        } else {
            m.text.setText("🔊 点击重听 · 长按慢放");
            m.zh.setVisibility(View.GONE);
        }
    }

    private void toggleSubs() {
        subtitles = !subtitles;
        subChip.setText(subtitles ? "字幕 开" : "字幕 关");
        UI.setChip(this, subChip, subtitles);
        for (AiMsg m : aiMsgs) renderAi(m);
        UI.toast(this, subtitles ? "已显示字幕" : "已关闭字幕：只靠听力理解 AI 的话");
    }

    private TextView addUser(final String text) {
        LinearLayout row = UI.vbox(this);
        row.setGravity(Gravity.END);
        TextView t = UI.text(this, text, 16, Color.WHITE, false);
        t.setMaxWidth(maxBubble());
        t.setBackground(UI.round(this, UI.PRIMARY, 16));
        int p = UI.dp(this, 12);
        t.setPadding(p, UI.dp(this, 10), p, UI.dp(this, 10));
        row.addView(t);
        TextView fb = UI.text(this, "", 13, UI.TEXT, false);
        fb.setMaxWidth(maxBubble());
        fb.setBackground(UI.round(this, 0xFFFFF6E5, 12));
        fb.setPadding(p, UI.dp(this, 8), p, UI.dp(this, 8));
        fb.setVisibility(View.GONE);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.setMargins(0, UI.dp(this, 4), 0, 0);
        row.addView(fb, fp);
        msgs.addView(row, UI.matchWrap(this, 40, 6, 0, 6));
        scrollDown();
        return fb;
    }

    private void scrollDown() {
        scroll.post(new Runnable() { public void run() { scroll.fullScroll(View.FOCUS_DOWN); } });
    }

    private void sendInput() {
        String t = input.getText().toString().trim();
        if (t.length() == 0) return;
        input.setText("");
        send(t);
    }

    private void send(final String text) {
        if (busy) { UI.toast(this, "AI 还在回复中…"); return; }
        busy = true;
        sp.stop();
        final TextView fb = addUser(text);
        final JSONArray hist = history;
        hist.put(AiClient.msg("user", text));
        status.setText("AI 思考中…");
        // send a snapshot so the worker thread never serialises an array the UI thread is mutating
        JSONArray snapshot;
        try { snapshot = new JSONArray(hist.toString()); } catch (Exception e) { snapshot = hist; }
        AiClient.chat(this, snapshot, 0.8, new AiClient.Cb() {
            public void ok(String content) {
                if (hist != history || !inChat) return; // user left / started another chat meanwhile
                busy = false;
                status.setText("点麦克风说话，或者打字发送");
                JSONObject o = AiClient.json(content);
                String reply = o != null ? o.optString("reply", "") : content;
                if (reply.length() == 0) reply = content;
                String zh = o != null ? o.optString("reply_zh", "") : "";
                hist.put(AiClient.msg("assistant", reply));
                if (o != null) {
                    String f = o.optString("feedback", "").trim();
                    String better = o.optString("better", "").trim();
                    int score = o.optInt("score", -1);
                    StringBuilder sb = new StringBuilder();
                    if (score >= 0) {
                        sb.append(score >= 85 ? "✅ " : score >= 60 ? "🟡 " : "🔴 ").append(score).append(" 分");
                        turns++;
                        scoreSum += score;
                        Store.tick(ChatActivity.this);
                    }
                    if (f.length() > 0) sb.append(sb.length() > 0 ? "  " : "").append(f);
                    if (better.length() > 0 && !better.equalsIgnoreCase(text)) sb.append("\n💬 更自然：").append(better).append("  🔊");
                    if (sb.length() > 0) {
                        fb.setText(sb.toString());
                        fb.setVisibility(View.VISIBLE);
                        final String say = better.length() > 0 ? better : text;
                        fb.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) { sp.speak(say, 0.9f, null); }
                        });
                    }
                }
                addAi(reply, zh);
                sp.speak(reply, 1f, null);
            }

            public void fail(String error) {
                if (hist != history || !inChat) return;
                busy = false;
                if (hist.length() > 0) hist.remove(hist.length() - 1);
                status.setText("发送失败，点上面的提示可重试");
                fb.setText("⚠️ " + error + "\n（点这里重新发送）");
                fb.setVisibility(View.VISIBLE);
                fb.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        ((ViewGroup) fb.getParent().getParent()).removeView((View) fb.getParent());
                        send(text);
                    }
                });
            }
        });
    }

    private void onMic() {
        if (rec.listening) { rec.stop(); return; }
        if (busy) { UI.toast(this, "等 AI 回复完再说哦"); return; }
        if (!UI.ensureMic(this)) return;
        sp.stop();
        UI.micActive(this, mic, true);
        status.setText("请说" + (ja ? "日语" : "英语") + "…");
        rec.start(lang, new Rec.Cb() {
            public void onReady() { status.setText("正在听…说完会自动发送"); }
            public void onPartial(String t) { status.setText("“" + t + "”"); }
            public void onRms(float db) {
                float s = 1f + Math.max(0f, Math.min(10f, db)) / 45f;
                mic.setScaleX(s); mic.setScaleY(s);
            }
            public void onResult(List<String> c, float conf, long dur) {
                UI.micActive(ChatActivity.this, mic, false);
                status.setText(conf > 0 && conf < 0.5f ? "识别置信度较低，注意发音清晰度" : "点麦克风说话，或者打字发送");
                send(c.get(0));
            }
            public void onError(String msg) {
                UI.micActive(ChatActivity.this, mic, false);
                status.setText(msg.length() == 0 ? "点麦克风说话，或者打字发送" : msg);
            }
        });
    }

    // ------------------------------------------------------------------ summary

    private void summary() {
        if (history == null || turns < 1) { UI.toast(this, "多聊几句再让 AI 点评吧"); return; }
        stop();
        StringBuilder tr = new StringBuilder();
        for (int i = 1; i < history.length(); i++) {
            JSONObject m = history.optJSONObject(i);
            if (m == null) continue;
            tr.append("assistant".equals(m.optString("role")) ? "AI: " : "Learner: ").append(m.optString("content")).append("\n");
        }
        String fmt = ja ? "phrases 里的 text 必须把含汉字的词写成 {漢字|かな} 注音格式，例如 {切手|きって}を{三枚|さんまい}ください。" : "";
        JSONArray m = new JSONArray();
        m.put(AiClient.msg("system", "You are a supportive but honest " + (ja ? "Japanese" : "English")
                + " speaking coach for a Chinese-speaking learner. Output strictly valid JSON."));
        m.put(AiClient.msg("user", "下面是一段情景对话（学习者的话来自语音识别）：\n" + tr
                + "\n请返回 JSON：{\"summary\": \"用简体中文点评：整体表现（1句）、最重要的 3 个问题及改正（每条一行，附正确说法）、下次练习建议（1句）\", "
                + "\"phrases\": [{\"text\": \"这个场景里值得掌握的地道表达（3-5 条，目标语言）\", \"zh\": \"中文\"}]}。" + fmt));
        final AlertDialog wait = new AlertDialog.Builder(this).setMessage("AI 正在点评这次对话…").setCancelable(true).show();
        AiClient.chat(this, m, 0.5, new AiClient.Cb() {
            public void ok(String content) {
                // the request can take up to ~90 s; the activity may be gone -> BadTokenException on show()
                if (isFinishing() || isDestroyed()) return;
                try { wait.dismiss(); } catch (Exception ignored) { }
                JSONObject o = AiClient.json(content);
                String sum = o != null ? o.optString("summary", content) : content;
                final JSONArray phrases = o != null ? o.optJSONArray("phrases") : null;
                StringBuilder sb = new StringBuilder();
                if (turns > 0) sb.append("本次对话 ").append(turns).append(" 轮 · 平均 ").append(scoreSum / turns).append(" 分\n\n");
                sb.append(sum.replace("**", ""));
                if (phrases != null && phrases.length() > 0) {
                    sb.append("\n\n📌 推荐表达：");
                    for (int i = 0; i < phrases.length(); i++) {
                        JSONObject p = phrases.optJSONObject(i);
                        if (p == null) continue;
                        String t = p.optString("text").replaceAll("\\{([^|}]*)\\|[^}]*\\}", "$1");
                        sb.append("\n• ").append(t).append("  ").append(p.optString("zh"));
                    }
                }
                AlertDialog.Builder bd = new AlertDialog.Builder(ChatActivity.this).setTitle("对话点评").setMessage(sb.toString())
                        .setNegativeButton("关闭", null);
                if (phrases != null && phrases.length() > 0) {
                    bd.setPositiveButton("加入跟读题库", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            List<Content.Sentence> added = Store.addAiSentences(ChatActivity.this, lang, phrases, Store.level(ChatActivity.this, lang));
                            UI.toast(ChatActivity.this, "已加入 " + added.size() + " 句，去「发音跟读」练习吧");
                        }
                    });
                }
                bd.show();
            }

            public void fail(String error) {
                if (isFinishing() || isDestroyed()) return;
                try { wait.dismiss(); } catch (Exception ignored) { }
                UI.toast(ChatActivity.this, "点评失败：" + error);
            }
        });
    }

    private void stop() {
        if (rec != null && rec.listening) rec.cancel();
        if (sp != null) sp.stop();
        if (mic != null) UI.micActive(this, mic, false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sp.shutdown();
        rec.destroy();
    }
}
