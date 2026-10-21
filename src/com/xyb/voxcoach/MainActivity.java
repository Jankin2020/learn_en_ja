package com.xyb.voxcoach;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends Activity {

    private int obLevel = 1;
    private String obLang = "en";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        UI.ensureMicSilently(this);
    }

    private static boolean packCheckStarted;

    /** Once a day: silently update installed content packs from the subscribed catalog. */
    private void autoUpdatePacks() {
        if (packCheckStarted || !Store.onboarded(this) || !Store.autoUpdatePacks(this)) return;
        if (Store.day(0).equals(Store.lastPackCheck(this))) return;
        packCheckStarted = true;
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                final String msg = PackSync.autoUpdate(app);
                if (msg != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                        public void run() { UI.toast(app, msg); }
                    });
                }
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        autoUpdatePacks();
        if (!Store.onboarded(this)) buildOnboarding();
        else buildHome();
    }

    // ------------------------------------------------------------------ onboarding

    private void buildOnboarding() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(UI.BG);
        final LinearLayout body = UI.vbox(this);
        int p = UI.dp(this, 22);
        body.setPadding(p, UI.dp(this, 48), p, p);
        sv.addView(body);
        setContentView(sv);

        body.addView(UI.text(this, "👋 欢迎使用 VoxCoach", 26, UI.TEXT, true));
        TextView sub = UI.text(this, "你的英语 / 日语 AI 口语陪练：跟读打分、逐词纠音、自适应听力、AI 情景对话。", 15, UI.SUB, false);
        body.addView(sub, UI.matchWrap(this, 0, 8, 0, 24));

        body.addView(UI.text(this, "先练哪门语言？", 17, UI.TEXT, true));
        LinearLayout langRow = UI.hbox(this);
        final TextView en = UI.chip(this, "🇺🇸 English", true);
        final TextView ja = UI.chip(this, "🇯🇵 日本語", false);
        langRow.addView(en, UI.lpw(this, 1, 4));
        langRow.addView(ja, UI.lpw(this, 1, 4));
        body.addView(langRow, UI.matchWrap(this, 0, 8, 0, 20));

        body.addView(UI.text(this, "你现在的水平？", 17, UI.TEXT, true));
        final LinearLayout levels = UI.vbox(this);
        body.addView(levels, UI.matchWrap(this, 0, 8, 0, 20));
        final Runnable[] drawLevels = new Runnable[1];
        drawLevels[0] = new Runnable() {
            public void run() {
                levels.removeAllViews();
                String[] desc = "ja".equals(obLang)
                        ? new String[]{"刚开始学，会五十音", "能进行简单日常对话", "能看懂一般文章、日常交流无压力", "能在工作中使用日语"}
                        : new String[]{"只会简单单词和短句", "能进行基本日常交流", "能聊大部分日常话题", "能在工作中流利使用"};
                for (int i = 1; i <= 4; i++) {
                    final int lv = i;
                    TextView c = UI.chip(MainActivity.this, Content.levelName(obLang, i) + " · " + desc[i - 1], obLevel == i);
                    c.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    c.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) { obLevel = lv; drawLevels[0].run(); }
                    });
                    levels.addView(c, UI.matchWrap(MainActivity.this, 0, 4, 0, 4));
                }
            }
        };
        drawLevels[0].run();
        en.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                obLang = "en"; UI.setChip(MainActivity.this, en, true); UI.setChip(MainActivity.this, ja, false); drawLevels[0].run();
            }
        });
        ja.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                obLang = "ja"; UI.setChip(MainActivity.this, ja, true); UI.setChip(MainActivity.this, en, false); drawLevels[0].run();
            }
        });

        TextView tip = UI.text(this, "之后 App 会根据你每次的发音得分自动升降难度，也可以在设置里手动调整。两门语言可以随时在首页切换。", 13, UI.SUB, false);
        body.addView(tip, UI.matchWrap(this, 0, 4, 0, 20));

        TextView go = UI.button(this, "开始练习", true);
        go.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Store.setLang(MainActivity.this, obLang);
                Store.setLevel(MainActivity.this, obLang, obLevel);
                Store.setOnboarded(MainActivity.this);
                UI.ensureMic(MainActivity.this);
                buildHome();
            }
        });
        body.addView(go, UI.matchWrap(this, 0, 8, 0, 0));
    }

    // ------------------------------------------------------------------ home

    private void buildHome() {
        final String lang = Store.lang(this);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(UI.BG);
        LinearLayout body = UI.vbox(this);
        int p = UI.dp(this, 16);
        body.setPadding(p, UI.dp(this, 20), p, UI.dp(this, 28));
        sv.addView(body);
        setContentView(sv);

        // hero
        LinearLayout hero = UI.vbox(this);
        hero.setBackground(UI.gradient(this, UI.PRIMARY, UI.ACCENT, 24));
        int hp = UI.dp(this, 20);
        hero.setPadding(hp, hp, hp, hp);
        LinearLayout titleRow = UI.hbox(this);
        titleRow.addView(UI.text(this, "VoxCoach", 26, Color.WHITE, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView gear = UI.text(this, "⚙️", 22, Color.WHITE, false);
        gear.setPadding(UI.dp(this, 8), 0, 0, 0);
        gear.setOnClickListener(go(SettingsActivity.class));
        titleRow.addView(gear);
        hero.addView(titleRow);
        hero.addView(UI.text(this, "ja".equals(lang) ? "今日も話してみましょう！" : "Let's speak a little today!", 15, 0xDDFFFFFF, false));
        LinearLayout langRow = UI.hbox(this);
        langRow.addView(langPill("🇺🇸 English", "en", lang), UI.lpw(this, 1, 4));
        langRow.addView(langPill("🇯🇵 日本語", "ja", lang), UI.lpw(this, 1, 4));
        hero.addView(langRow, UI.matchWrap(this, -4, 14, -4, 0));
        body.addView(hero, UI.matchWrap(this, 0, 0, 0, 14));

        // stats
        LinearLayout stats = UI.card(this);
        LinearLayout row = UI.hbox(this);
        row.addView(stat("🔥 " + Store.streak(this), "连续天数"), UI.lpw(this, 1, 0));
        row.addView(stat(Store.todayCount(this) + "/" + Store.goal(this), "今日练习"), UI.lpw(this, 1, 0));
        row.addView(stat(Content.levelName(lang, Store.level(this, lang)).split(" ")[0], "当前等级"), UI.lpw(this, 1, 0));
        row.addView(stat(String.format("%.2fx", Store.listenSpeed(this, lang)), "听力语速"), UI.lpw(this, 1, 0));
        stats.addView(row);
        stats.addView(UI.bar(this, Store.goal(this), Math.min(Store.goal(this), Store.todayCount(this)), UI.GREEN),
                UI.matchWrap(this, 0, 12, 0, 0));
        body.addView(stats, UI.matchWrap(this, 0, 0, 0, 14));

        // weak points
        List<String[]> issues = Store.issues(this, lang);
        LinearLayout weak = UI.card(this);
        weak.addView(UI.text(this, "🎯 你的发音重点", 16, UI.TEXT, true));
        if (issues.isEmpty()) {
            weak.addView(UI.text(this, "多练几句后，这里会根据你的识别结果分析出发音弱项（如 L/R、TH、长音、促音），并自动加强相关训练。", 13, UI.SUB, false),
                    UI.matchWrap(this, 0, 6, 0, 0));
        } else {
            LinearLayout chips = UI.hbox(this);
            for (int i = 0; i < Math.min(3, issues.size()); i++) {
                TextView c = UI.chip(this, issues.get(i)[0] + " ×" + issues.get(i)[1], i == 0);
                chips.addView(c, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                ((LinearLayout.LayoutParams) c.getLayoutParams()).setMargins(0, 0, UI.dp(this, 8), 0);
            }
            weak.addView(chips, UI.matchWrap(this, 0, 8, 0, 0));
            weak.addView(UI.text(this, Tips.of(issues.get(0)[0]), 13, UI.SUB, false), UI.matchWrap(this, 0, 8, 0, 0));
        }
        weak.setOnClickListener(go(ReviewActivity.class));
        body.addView(weak, UI.matchWrap(this, 0, 0, 0, 14));

        // menu grid
        int weakCount = Store.weakSentences(this, lang).size();
        addRow(body,
                tile("🎯", "发音跟读", "逐词打分 · 自动纠音", PracticeActivity.class),
                tile("🎧", "听写训练", "语速自适应 " + String.format("%.2fx", Store.listenSpeed(this, lang)), DictationActivity.class));
        addRow(body,
                tile("👂", "听辨训练", "ja".equals(lang) ? "长音 · 促音 · 清浊音" : "L/R · TH · 长短元音", PairsActivity.class),
                tile("💬", "AI 情景对话", "角色扮演 · 实时纠错", ChatActivity.class));
        View review = tile("📚", "弱项复习", weakCount > 0 ? weakCount + " 句待复习" : "暂无待复习", ReviewActivity.class);
        if ("ja".equals(lang)) {
            addRow(body, tile("あ", "五十音", "假名表 · 认读 · 听音 · 跟读", KanaActivity.class), review);
            addRow(body, tile("🔊", "语音环境", "引擎 / 日语语音包 / 在线语音", VoiceActivity.class),
                    tile("⚙️", "设置", Store.aiReady(this) ? "AI 已连接 · " + Store.apiModel(this) : "配置 AI 接口", SettingsActivity.class));
        } else {
            addRow(body, review,
                    tile("⚙️", "设置", Store.aiReady(this) ? "AI 已连接 · " + Store.apiModel(this) : "配置 AI 接口", SettingsActivity.class));
        }

        if (!Rec.available(this)) {
            LinearLayout warn = UI.card(this);
            warn.addView(UI.text(this, "⚠️ 没有检测到系统语音识别服务", 15, UI.RED, true));
            warn.addView(UI.text(this, "跟读评分需要语音识别：安装「Google」App，或在「语音环境」里开启在线识别（国内直连）。点这里去设置。", 13, UI.SUB, false),
                    UI.matchWrap(this, 0, 6, 0, 0));
            warn.setOnClickListener(go(VoiceActivity.class));
            body.addView(warn, UI.matchWrap(this, 0, 6, 0, 0));
        } else if ("ja".equals(lang) && "system".equals(Store.ttsMode(this)) && !Speaker.installed(this, Speaker.GOOGLE_TTS)) {
            LinearLayout warn = UI.card(this);
            warn.addView(UI.text(this, "🔊 日语示范发音可能无法播放", 15, UI.ORANGE, true));
            warn.addView(UI.text(this, "没有检测到 Google 语音服务（国行手机自带引擎一般不支持日语）。点这里：安装 Google 语音服务并下载日语包，或切换到在线 TTS。", 13, UI.SUB, false),
                    UI.matchWrap(this, 0, 6, 0, 0));
            warn.setOnClickListener(go(VoiceActivity.class));
            body.addView(warn, UI.matchWrap(this, 0, 6, 0, 0));
        } else if (!Store.aiReady(this)) {
            LinearLayout hint = UI.card(this);
            hint.addView(UI.text(this, "💡 填入 AI 接口即可解锁：情景对话、AI 发音诊断、按弱项自动出题", 14, UI.TEXT, true));
            hint.addView(UI.text(this, "支持任意 OpenAI 兼容接口：DeepSeek、通义千问、Kimi、智谱、OpenAI…", 13, UI.SUB, false),
                    UI.matchWrap(this, 0, 6, 0, 0));
            hint.setOnClickListener(go(SettingsActivity.class));
            body.addView(hint, UI.matchWrap(this, 0, 6, 0, 0));
        }
    }

    private TextView langPill(String label, final String code, String current) {
        boolean on = code.equals(current);
        TextView t = UI.text(this, label, 15, on ? UI.PRIMARY : Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        int pv = UI.dp(this, 9);
        t.setPadding(0, pv, 0, pv);
        t.setBackground(UI.ripple(this, UI.round(this, on ? Color.WHITE : 0x33FFFFFF, 20)));
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Store.setLang(MainActivity.this, code);
                buildHome();
            }
        });
        return t;
    }

    private View stat(String big, String label) {
        LinearLayout l = UI.vbox(this);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView a = UI.text(this, big, 18, UI.TEXT, true);
        a.setGravity(Gravity.CENTER);
        l.addView(a);
        TextView b = UI.text(this, label, 12, UI.SUB, false);
        b.setGravity(Gravity.CENTER);
        l.addView(b);
        return l;
    }

    private View tile(String icon, String title, String sub, Class<?> target) {
        LinearLayout t = UI.card(this);
        t.setMinimumHeight(UI.dp(this, 112));
        t.setBackground(UI.ripple(this, UI.round(this, UI.CARD, 18)));
        t.addView(UI.text(this, icon, 26, UI.TEXT, false));
        t.addView(UI.text(this, title, 16, UI.TEXT, true), UI.matchWrap(this, 0, 6, 0, 0));
        t.addView(UI.text(this, sub, 12, UI.SUB, false), UI.matchWrap(this, 0, 2, 0, 0));
        t.setOnClickListener(go(target));
        return t;
    }

    private void addRow(LinearLayout body, View a, View b) {
        LinearLayout row = UI.hbox(this);
        row.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams pa = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        pa.setMargins(0, 0, UI.dp(this, 6), 0);
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        pb.setMargins(UI.dp(this, 6), 0, 0, 0);
        row.addView(a, pa);
        row.addView(b, pb);
        body.addView(row, UI.matchWrap(this, 0, 0, 0, 12));
    }

    private View.OnClickListener go(final Class<?> target) {
        return new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, target)); }
        };
    }
}
