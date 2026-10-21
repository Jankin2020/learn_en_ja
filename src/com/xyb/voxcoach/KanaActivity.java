package com.xyb.voxcoach;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Gojūon trainer: chart, reading quiz, listening quiz and example-word shadowing, with adaptive item selection. */
public class KanaActivity extends Activity {

    private static final int TAB_CHART = 0, TAB_READ = 1, TAB_LISTEN = 2, TAB_SPEAK = 3;
    private static final String[] TABS = {"五十音表", "认读", "听音选字", "跟读"};
    private static final String[] SCRIPTS = {"h", "k", "mix"};
    private static final String[] SCRIPT_LABELS = {"平假名", "片假名", "混合"};
    private static final String[] GROUPS = {Kana.SEION, Kana.DAKUON, Kana.YOUON, "全部"};

    private int tab = TAB_CHART;
    private String script = "h", group = Kana.SEION;
    private final List<TextView> tabChips = new ArrayList<TextView>(), scriptChips = new ArrayList<TextView>(), groupChips = new ArrayList<TextView>();
    private LinearLayout content;
    private Speaker sp;
    private Rec rec;
    private final Handler h = new Handler();
    private final Random rnd = new Random();

    private Kana.K cur;
    private boolean curKata, answered;
    private int right, total, streak;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = new Speaker(this, "ja");
        rec = new Rec(this);
        LinearLayout body = UI.screen(this, "五十音", null);

        body.addView(chipRow(TABS, tabChips, new Pick() { public void pick(int i) { tab = i; refresh(); } }), UI.matchWrap(this, -3, 0, -3, 6));
        body.addView(chipRow(SCRIPT_LABELS, scriptChips, new Pick() { public void pick(int i) { script = SCRIPTS[i]; refresh(); } }), UI.matchWrap(this, -3, 0, -3, 6));
        body.addView(chipRow(GROUPS, groupChips, new Pick() { public void pick(int i) { group = GROUPS[i]; refresh(); } }), UI.matchWrap(this, -3, 0, -3, 10));
        content = UI.vbox(this);
        body.addView(content);
        refresh();
    }

    private interface Pick { void pick(int i); }

    private View chipRow(String[] labels, final List<TextView> store, final Pick pick) {
        LinearLayout r = UI.hbox(this);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView c = UI.chip(this, labels[i], false);
            c.setTextSize(13);
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pick.pick(idx); } });
            store.add(c);
            r.addView(c, UI.lpw(this, 1, 3));
        }
        return r;
    }

    private void refresh() {
        stop();
        for (int i = 0; i < tabChips.size(); i++) UI.setChip(this, tabChips.get(i), i == tab);
        for (int i = 0; i < scriptChips.size(); i++) UI.setChip(this, scriptChips.get(i), SCRIPTS[i].equals(script));
        for (int i = 0; i < groupChips.size(); i++) UI.setChip(this, groupChips.get(i), GROUPS[i].equals(group));
        content.removeAllViews();
        right = total = streak = 0;
        cur = null;
        if (tab == TAB_CHART) buildChart(null);
        else nextItem();
    }

    // ------------------------------------------------------------------ mastery

    private String keyPrefix() {
        if (tab == TAB_LISTEN) return "l:";
        if (tab == TAB_SPEAK) return "s:";
        return curKata ? "k:" : "h:";
    }

    /** 0 = new, 1 = learning, 2 = mastered, 3 = weak */
    private int mastery(String key) {
        int[] s = Store.kanaStat(this, key);
        if (s[1] == 0) return 0;
        if (s[2] >= 3 && s[0] >= s[1] * 0.7) return 2;
        if (s[1] - s[0] > s[0]) return 3;
        return 1;
    }

    private static int masteryColor(int m) {
        switch (m) {
            case 2: return 0xFFE3F6EC;
            case 1: return 0xFFFFF4DC;
            case 3: return 0xFFFDE8E8;
            default: return UI.CARD;
        }
    }

    // ------------------------------------------------------------------ chart

    private void buildChart(final Kana.K focus) {
        content.removeAllViews();
        int mastered = 0, count = 0;
        for (Kana.K k : Kana.list("全部")) {
            count++;
            if (mastery("h:" + k.h) == 2 && mastery("k:" + k.h) == 2) mastered++;
        }
        content.addView(UI.text(this, "平/片假名都掌握：" + mastered + " / " + count + "    颜色：绿=已掌握 黄=学习中 红=易错", 12, UI.SUB, false),
                UI.matchWrap(this, 2, 0, 2, 8));

        if (focus != null) content.addView(detailCard(focus), UI.matchWrap(this, 0, 0, 0, 12));

        String[] groups = "全部".equals(group) ? new String[]{Kana.SEION, Kana.DAKUON, Kana.YOUON} : new String[]{group};
        for (String g : groups) {
            content.addView(UI.text(this, g, 15, UI.TEXT, true), UI.matchWrap(this, 2, 4, 0, 6));
            LinearLayout card = UI.card(this);
            card.setPadding(UI.dp(this, 6), UI.dp(this, 6), UI.dp(this, 6), UI.dp(this, 6));
            for (List<Kana.K> line : Kana.grid(g)) {
                LinearLayout row = UI.hbox(this);
                for (final Kana.K k : line) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, UI.dp(this, 58), 1);
                    lp.setMargins(UI.dp(this, 3), UI.dp(this, 3), UI.dp(this, 3), UI.dp(this, 3));
                    if (k == null) { row.addView(new View(this), lp); continue; }
                    row.addView(cell(k), lp);
                }
                card.addView(row);
            }
            content.addView(card, UI.matchWrap(this, 0, 0, 0, 12));
        }
    }

    private View cell(final Kana.K k) {
        LinearLayout c = UI.vbox(this);
        c.setGravity(Gravity.CENTER);
        int m;
        String big;
        if ("k".equals(script)) { big = k.k(); m = mastery("k:" + k.h); }
        else if ("mix".equals(script)) { big = k.h + " " + k.k(); m = Math.min(mastery("h:" + k.h), mastery("k:" + k.h)); if (mastery("h:" + k.h) == 3 || mastery("k:" + k.h) == 3) m = 3; }
        else { big = k.h; m = mastery("h:" + k.h); }
        c.setBackground(UI.ripple(this, UI.roundStroke(this, masteryColor(m), 10, UI.BORDER)));
        TextView t = UI.text(this, big, "mix".equals(script) || k.h.length() > 1 ? 17 : 22, UI.TEXT, true);
        t.setGravity(Gravity.CENTER);
        c.addView(t);
        TextView r = UI.text(this, k.romaji, 10, UI.SUB, false);
        r.setGravity(Gravity.CENTER);
        c.addView(r);
        c.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sp.speak(k.h, 0.8f, null);
                buildChart(k);
            }
        });
        return c;
    }

    private View detailCard(final Kana.K k) {
        LinearLayout d = UI.card(this);
        LinearLayout top = UI.hbox(this);
        top.addView(UI.text(this, k.h + "  " + k.k(), 40, UI.PRIMARY, true));
        TextView ro = UI.text(this, "  " + k.romaji, 18, UI.SUB, false);
        top.addView(ro);
        d.addView(top);
        if (k.word.length() > 0) {
            final Content.Sentence w = word(k);
            d.addView(UI.text(this, "例词：" + w.text + "（" + w.reading + "）  " + k.wordZh, 15, UI.TEXT, false), UI.matchWrap(this, 0, 6, 0, 0));
        }
        LinearLayout br = UI.hbox(this);
        TextView a = UI.button(this, "▶ 发音", false);
        a.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { sp.speak(k.h, 0.8f, null); } });
        br.addView(a, UI.lpw(this, 1, 3));
        if (k.word.length() > 0) {
            TextView b = UI.button(this, "▶ 例词", false);
            b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { sp.speak(word(k).text, 0.85f, null); } });
            br.addView(b, UI.lpw(this, 1, 3));
        }
        TextView s = UI.button(this, "🎤 跟读", true);
        s.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                tab = TAB_SPEAK;
                for (int i = 0; i < tabChips.size(); i++) UI.setChip(KanaActivity.this, tabChips.get(i), i == tab);
                content.removeAllViews();
                right = total = streak = 0;
                showSpeak(k);
            }
        });
        br.addView(s, UI.lpw(this, 1, 3));
        d.addView(br, UI.matchWrap(this, -3, 10, -3, 0));
        return d;
    }

    private Content.Sentence word(Kana.K k) {
        String w = k.word.length() > 0 ? k.word : k.h;
        return new Content.Sentence("ja", "kana_" + k.h, 1, w, k.wordZh);
    }

    // ------------------------------------------------------------------ adaptive picking

    private Kana.K pick() {
        List<Kana.K> pool = Kana.list(group);
        if (tab == TAB_SPEAK) {
            // youon without an example word is still fine to practise alone
        }
        boolean kata = "k".equals(script) || ("mix".equals(script) && rnd.nextBoolean());
        curKata = kata;
        String pre = keyPrefix();
        double[] w = new double[pool.size()];
        double sum = 0;
        for (int i = 0; i < pool.size(); i++) {
            int[] s = Store.kanaStat(this, pre + pool.get(i).h);
            double x = 1 + (s[1] == 0 ? 1.5 : 0) + (s[1] - s[0]) * 1.5 - Math.min(s[2], 5) * 0.3;
            if (pool.get(i) == cur) x = 0.05;
            w[i] = Math.max(0.15, x);
            sum += w[i];
        }
        double r = rnd.nextDouble() * sum;
        for (int i = 0; i < w.length; i++) { r -= w[i]; if (r <= 0) return pool.get(i); }
        return pool.get(pool.size() - 1);
    }

    private void nextItem() {
        stop();
        answered = false;
        content.removeAllViews();
        Kana.K k = pick();
        if (tab == TAB_READ) showRead(k);
        else if (tab == TAB_LISTEN) showListen(k);
        else showSpeak(k);
    }

    private List<Kana.K> options(Kana.K k, List<Kana.K> similar, boolean byRomaji) {
        List<Kana.K> opts = new ArrayList<Kana.K>();
        opts.add(k);
        Collections.shuffle(similar);
        for (Kana.K s : similar) {
            if (opts.size() >= 3) break;
            if (!clash(opts, s, byRomaji)) opts.add(s);
        }
        List<Kana.K> pool = Kana.list("全部".equals(group) ? "全部" : k.group);
        Collections.shuffle(pool);
        for (Kana.K s : pool) {
            if (opts.size() >= 4) break;
            if (!clash(opts, s, byRomaji)) opts.add(s);
        }
        Collections.shuffle(opts);
        return opts;
    }

    private boolean clash(List<Kana.K> opts, Kana.K s, boolean byRomaji) {
        for (Kana.K o : opts) {
            if (o == s) return true;
            if (byRomaji && o.romaji.equals(s.romaji)) return true;
            // listening: homophones (じ/ぢ, ず/づ, お/を) can't be told apart by ear
            if (!byRomaji && sound(o.h).equals(sound(s.h))) return true;
        }
        return false;
    }

    private static String sound(String h) {
        return h.replace('ぢ', 'じ').replace('づ', 'ず').replace('を', 'お');
    }

    private String stats() {
        return total == 0 ? "" : "本轮 " + right + "/" + total + (streak >= 3 ? "  🔥连对 " + streak : "");
    }

    // ------------------------------------------------------------------ reading quiz

    private void showRead(final Kana.K k) {
        cur = k;
        LinearLayout card = UI.card(this);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(UI.text(this, "这个假名怎么读？", 15, UI.SUB, false));
        TextView big = UI.text(this, k.show(curKata), 76, UI.TEXT, true);
        big.setGravity(Gravity.CENTER);
        card.addView(big, UI.matchWrap(this, 0, 4, 0, 8));
        final List<Kana.K> opts = options(k, Kana.lookAlikes(k, curKata), true);
        final List<TextView> btns = new ArrayList<TextView>();
        LinearLayout grid = UI.vbox(this);
        LinearLayout row = null;
        for (int i = 0; i < opts.size(); i++) {
            if (i % 2 == 0) { row = UI.hbox(this); grid.addView(row); }
            final Kana.K o = opts.get(i);
            final TextView b = option(o.romaji);
            btns.add(b);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { answer(k, o, b, btns, opts, "k:".equals(keyPrefix()) ? "k:" : "h:"); }
            });
            row.addView(b, UI.lpw(this, 1, 5));
        }
        card.addView(grid, UI.matchWrap(this, -5, 0, -5, 0));
        content.addView(card);
        addFooter();
    }

    // ------------------------------------------------------------------ listening quiz

    private void showListen(final Kana.K k) {
        cur = k;
        LinearLayout card = UI.card(this);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(UI.text(this, "听到的是哪个假名？", 15, UI.SUB, false));
        TextView play = UI.micButton(this, 72);
        play.setText("▶");
        play.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { sp.speak(k.h, 0.8f, null); } });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(UI.dp(this, 72), UI.dp(this, 72));
        pl.setMargins(0, UI.dp(this, 12), 0, UI.dp(this, 16));
        card.addView(play, pl);
        final List<Kana.K> opts = options(k, Kana.soundAlikes(k), false);
        final List<TextView> btns = new ArrayList<TextView>();
        LinearLayout grid = UI.vbox(this);
        LinearLayout row = null;
        for (int i = 0; i < opts.size(); i++) {
            if (i % 2 == 0) { row = UI.hbox(this); grid.addView(row); }
            final Kana.K o = opts.get(i);
            final TextView b = option(o.show(curKata));
            b.setTextSize(28);
            btns.add(b);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { answer(k, o, b, btns, opts, "l:"); }
            });
            row.addView(b, UI.lpw(this, 1, 5));
        }
        card.addView(grid, UI.matchWrap(this, -5, 0, -5, 0));
        content.addView(card);
        addFooter();
        h.postDelayed(new Runnable() { public void run() { sp.speak(k.h, 0.8f, null); } }, 300);
    }

    private TextView option(String label) {
        TextView t = UI.text(this, label, 22, UI.TEXT, true);
        t.setGravity(Gravity.CENTER);
        int p = UI.dp(this, 14);
        t.setPadding(p, p, p, p);
        t.setBackground(UI.ripple(this, UI.roundStroke(this, UI.CARD, 14, UI.BORDER)));
        t.setClickable(true);
        return t;
    }

    private TextView feedback, nextBtn;

    private void addFooter() {
        feedback = UI.text(this, stats(), 14, UI.SUB, false);
        feedback.setGravity(Gravity.CENTER);
        content.addView(feedback, UI.matchWrap(this, 0, 12, 0, 8));
        nextBtn = UI.button(this, "下一个 →", true);
        nextBtn.setVisibility(View.GONE);
        nextBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { nextItem(); } });
        content.addView(nextBtn);
    }

    private void answer(Kana.K k, Kana.K chosen, TextView btn, List<TextView> btns, List<Kana.K> opts, String pre) {
        if (answered) {
            sp.speak(chosen.h, 0.8f, null); // after answering, tap options to compare sounds
            return;
        }
        answered = true;
        boolean ok = chosen == k;
        total++;
        if (ok) { right++; streak++; } else streak = 0;
        Store.kanaResult(this, pre + k.h, ok);
        for (int i = 0; i < opts.size(); i++) {
            if (opts.get(i) == k) btns.get(i).setBackground(UI.roundStroke(this, 0xFFE7F7EF, 14, UI.GREEN));
        }
        if (!ok) btn.setBackground(UI.roundStroke(this, 0xFFFDECEC, 14, UI.RED));
        sp.speak(k.h, 0.8f, null);
        String ex = k.word.length() > 0 ? "  例：" + word(k).text + "（" + word(k).reading + "）" + k.wordZh : "";
        if (ok) {
            feedback.setText("✅ " + k.h + " " + k.k() + " = " + k.romaji + ex + "\n" + stats());
            feedback.setTextColor(UI.GREEN);
            h.postDelayed(new Runnable() { public void run() { nextItem(); } }, 1300);
        } else {
            feedback.setText("❌ 正确答案：" + k.h + " " + k.k() + " = " + k.romaji + "（你选的是 " + chosen.show(curKata) + " " + chosen.romaji + "）"
                    + ex + "\n" + stats() + "\n点选项可以对比发音");
            feedback.setTextColor(UI.RED);
            nextBtn.setVisibility(View.VISIBLE);
        }
    }

    // ------------------------------------------------------------------ speaking

    private void showSpeak(final Kana.K k) {
        cur = k;
        final Content.Sentence w = word(k);
        LinearLayout card = UI.card(this);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView big = UI.text(this, k.h + "  " + k.k(), 56, UI.TEXT, true);
        big.setGravity(Gravity.CENTER);
        card.addView(big);
        card.addView(UI.text(this, k.romaji, 16, UI.SUB, false));
        final TextView target = UI.text(this, k.word.length() > 0 ? "跟读例词：" + w.text : "跟读：" + k.h, 20, UI.TEXT, true);
        target.setGravity(Gravity.CENTER);
        card.addView(target, UI.matchWrap(this, 0, 12, 0, 0));
        final TextView reading = UI.text(this, w.reading + (k.wordZh.length() > 0 ? "　" + k.wordZh : ""), 15, UI.SUB, false);
        reading.setGravity(Gravity.CENTER);
        card.addView(reading, UI.matchWrap(this, 0, 4, 0, 0));
        LinearLayout br = UI.hbox(this);
        TextView a = UI.button(this, "▶ 假名", false);
        a.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { sp.speak(k.h, 0.8f, null); } });
        TextView b = UI.button(this, "▶ 例词", false);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { sp.speak(w.text, 0.85f, null); } });
        br.addView(a, UI.lpw(this, 1, 3));
        if (k.word.length() > 0) br.addView(b, UI.lpw(this, 1, 3));
        card.addView(br, UI.matchWrap(this, -3, 12, -3, 0));
        final TextView mic = UI.micButton(this, 72);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(UI.dp(this, 72), UI.dp(this, 72));
        ml.setMargins(0, UI.dp(this, 16), 0, 0);
        card.addView(mic, ml);
        final TextView status = UI.text(this, "先听，再点麦克风跟读", 13, UI.SUB, false);
        status.setGravity(Gravity.CENTER);
        card.addView(status, UI.matchWrap(this, 0, 8, 0, 0));
        content.addView(card);
        addFooter();
        nextBtn.setVisibility(View.VISIBLE);
        mic.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (rec.listening) { rec.stop(); return; }
                if (!UI.ensureMic(KanaActivity.this)) return;
                sp.stop();
                UI.micActive(KanaActivity.this, mic, true);
                rec.start("ja", new Rec.Cb() {
                    public void onReady() { status.setText("请说…"); }
                    public void onPartial(String t) { status.setText("“" + t + "”"); }
                    public void onRms(float db) {
                        float s = 1f + Math.max(0f, Math.min(10f, db)) / 45f;
                        mic.setScaleX(s); mic.setScaleY(s);
                    }
                    public void onResult(List<String> c, float conf, long dur) {
                        UI.micActive(KanaActivity.this, mic, false);
                        Scorer.Result r = Scorer.scoreSpeech(w.text, w.reading, w.ruby, true, c, conf, dur);
                        boolean ok = r.overall >= 80;
                        total++;
                        if (ok) { right++; streak++; } else streak = 0;
                        Store.kanaResult(KanaActivity.this, "s:" + k.h, ok);
                        Store.addIssues(KanaActivity.this, "ja", Scorer.issues(r));
                        SpannableStringBuilder sb = new SpannableStringBuilder();
                        for (int i = 0; i < r.display.size(); i++) {
                            int st = sb.length();
                            sb.append(r.display.get(i));
                            int s = r.status.get(i);
                            int col = s == Scorer.OK ? UI.GREEN : s == Scorer.NEAR ? UI.ORANGE : s == Scorer.SKIP ? UI.TEXT : UI.RED;
                            sb.setSpan(new ForegroundColorSpan(col), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        reading.setText(sb);
                        status.setTextColor(UI.scoreColor(r.overall));
                        StringBuilder msg = new StringBuilder();
                        msg.append(r.overall).append(" 分 · ").append(UI.scoreWord(r.overall)).append("\n识别到：").append(r.heard);
                        for (String is : Scorer.issues(r)) msg.append("\n• ").append(is).append("：").append(Tips.of(is));
                        status.setText(msg.toString());
                        feedback.setText(stats());
                    }
                    public void onError(String msg) {
                        UI.micActive(KanaActivity.this, mic, false);
                        status.setTextColor(UI.SUB);
                        status.setText(msg.length() == 0 ? "点麦克风跟读" : msg);
                    }
                });
            }
        });
        h.postDelayed(new Runnable() { public void run() { sp.speak(k.word.length() > 0 ? w.text : k.h, 0.85f, null); } }, 300);
    }

    // ------------------------------------------------------------------ lifecycle

    private void stop() {
        h.removeCallbacksAndMessages(null);
        if (rec != null && rec.listening) rec.cancel();
        if (sp != null) sp.stop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stop();
        sp.shutdown();
        rec.destroy();
    }
}
