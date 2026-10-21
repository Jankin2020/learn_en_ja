package com.xyb.voxcoach;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Random;

/** Minimal-pair listening discrimination + production check, weighted towards the learner's weak sound categories. */
public class PairsActivity extends Activity {

    private String lang;
    private boolean ja;
    private Speaker sp;
    private Rec rec;
    private final Handler h = new Handler();
    private Content.Pair pair;
    private boolean targetA, answered;
    private int right, total;

    private TextView catText, statText, optA, optB, feedback, micBtn, micStatus, sayLabel, nextBtn;
    private LinearLayout speakBox;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        lang = Store.lang(this);
        ja = "ja".equals(lang);
        LinearLayout body = UI.screen(this, "听辨训练", null);

        LinearLayout card = UI.card(this);
        LinearLayout top = UI.hbox(this);
        catText = UI.text(this, "", 13, UI.PRIMARY, true);
        catText.setBackground(UI.round(this, UI.LIGHT, 10));
        catText.setPadding(UI.dp(this, 10), UI.dp(this, 4), UI.dp(this, 10), UI.dp(this, 4));
        top.addView(catText);
        View f = new View(this);
        top.addView(f, new LinearLayout.LayoutParams(0, 1, 1));
        statText = UI.text(this, "", 12, UI.SUB, false);
        top.addView(statText);
        card.addView(top);

        TextView q = UI.text(this, "听到的是哪一个？", 18, UI.TEXT, true);
        q.setGravity(Gravity.CENTER);
        card.addView(q, UI.matchWrap(this, 0, 16, 0, 12));
        LinearLayout pr = UI.hbox(this);
        pr.setGravity(Gravity.CENTER);
        TextView play = UI.micButton(this, 64);
        play.setText("▶");
        play.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { playTarget(); } });
        pr.addView(play, new LinearLayout.LayoutParams(UI.dp(this, 64), UI.dp(this, 64)));
        card.addView(pr, UI.matchWrap(this, 0, 0, 0, 16));

        LinearLayout opts = UI.hbox(this);
        optA = option();
        optB = option();
        optA.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { answer(true); } });
        optB.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { answer(false); } });
        opts.addView(optA, UI.lpw(this, 1, 5));
        opts.addView(optB, UI.lpw(this, 1, 5));
        card.addView(opts, UI.matchWrap(this, -5, 0, -5, 0));

        feedback = UI.text(this, "", 14, UI.TEXT, false);
        card.addView(feedback, UI.matchWrap(this, 0, 12, 0, 0));
        body.addView(card, UI.matchWrap(this, 0, 4, 0, 12));

        speakBox = UI.card(this);
        speakBox.setGravity(Gravity.CENTER_HORIZONTAL);
        sayLabel = UI.text(this, "", 16, UI.TEXT, true);
        sayLabel.setGravity(Gravity.CENTER);
        speakBox.addView(sayLabel);
        micBtn = UI.micButton(this, 64);
        micBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { speak(); } });
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(UI.dp(this, 64), UI.dp(this, 64));
        mp.setMargins(0, UI.dp(this, 12), 0, 0);
        speakBox.addView(micBtn, mp);
        micStatus = UI.text(this, "", 13, UI.SUB, false);
        micStatus.setGravity(Gravity.CENTER);
        speakBox.addView(micStatus, UI.matchWrap(this, 0, 8, 0, 0));
        speakBox.setVisibility(View.GONE);
        body.addView(speakBox, UI.matchWrap(this, 0, 0, 0, 12));

        nextBtn = UI.button(this, "下一组 →", true);
        nextBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { next(); } });
        nextBtn.setVisibility(View.GONE);
        body.addView(nextBtn);

        sp = new Speaker(this, lang);
        rec = new Rec(this);
        next();
    }

    private TextView option() {
        TextView t = UI.text(this, "", 20, UI.TEXT, true);
        t.setGravity(Gravity.CENTER);
        int p = UI.dp(this, 16);
        t.setPadding(p, p, p, p);
        t.setBackground(UI.ripple(this, UI.roundStroke(this, UI.CARD, 16, UI.BORDER)));
        t.setClickable(true);
        return t;
    }

    private void next() {
        stop();
        answered = false;
        pair = Store.nextPair(this, lang, pair);
        targetA = new Random().nextBoolean();
        catText.setText(pair.cat);
        int[] st = Store.pairStat(this, lang, pair.cat);
        statText.setText(st[1] == 0 ? "新类别" : "该类正确率 " + (100 * st[0] / st[1]) + "%");
        optA.setText(pair.a + "\n" + pair.aZh);
        optB.setText(pair.b + "\n" + pair.bZh);
        optA.setBackground(UI.ripple(this, UI.roundStroke(this, UI.CARD, 16, UI.BORDER)));
        optB.setBackground(UI.ripple(this, UI.roundStroke(this, UI.CARD, 16, UI.BORDER)));
        feedback.setText(total == 0 ? "" : "本次 " + right + "/" + total);
        speakBox.setVisibility(View.GONE);
        nextBtn.setVisibility(View.GONE);
        h.postDelayed(new Runnable() { public void run() { playTarget(); } }, 400);
    }

    private void playTarget() {
        if (rec.listening) rec.cancel();
        sp.speak(targetA ? pair.aSay : pair.bSay, 0.85f, null);
    }

    private void answer(boolean choseA) {
        if (answered) {
            // after answering, tapping an option plays that word for comparison
            sp.speak(choseA ? pair.aSay : pair.bSay, 0.85f, null);
            return;
        }
        answered = true;
        boolean ok = choseA == targetA;
        total++;
        if (ok) right++;
        Store.pairResult(this, lang, pair.cat, ok);
        TextView correct = targetA ? optA : optB;
        TextView chosen = choseA ? optA : optB;
        correct.setBackground(UI.roundStroke(this, 0xFFE7F7EF, 16, UI.GREEN));
        if (!ok) chosen.setBackground(UI.roundStroke(this, 0xFFFDECEC, 16, UI.RED));
        feedback.setText((ok ? "✅ 正确！" : "❌ 听成了另一个。") + "  本次 " + right + "/" + total
                + "\n" + Tips.of(pair.cat) + "\n（点两个选项可以对比着听）");
        String word = targetA ? pair.a : pair.b;
        sayLabel.setText("现在说一说：「" + word.split(" ")[0] + "」");
        micStatus.setText("点麦克风，看看能不能被准确识别");
        speakBox.setVisibility(View.VISIBLE);
        nextBtn.setVisibility(View.VISIBLE);
    }

    private static String normJa(String s) {
        return Scorer.toHiragana(s.replaceAll("[\\s、。！？!?.,]", ""));
    }

    private boolean matches(String heard, String display, String say) {
        if (ja) {
            String h = normJa(heard);
            return h.equals(normJa(say)) || h.equals(normJa(display.split(" ")[0]));
        }
        return Scorer.enNorm(heard).equals(Scorer.enNorm(say));
    }

    private void speak() {
        if (rec.listening) { rec.stop(); return; }
        if (!UI.ensureMic(this)) return;
        sp.stop();
        UI.micActive(this, micBtn, true);
        micStatus.setText("请说…");
        rec.start(lang, new Rec.Cb() {
            public void onReady() { micStatus.setText("请说…"); }
            public void onPartial(String text) { micStatus.setText("“" + text + "”"); }
            public void onRms(float db) {
                float s = 1f + Math.max(0f, Math.min(10f, db)) / 45f;
                micBtn.setScaleX(s); micBtn.setScaleY(s);
            }
            public void onResult(List<String> c, float conf, long dur) {
                UI.micActive(PairsActivity.this, micBtn, false);
                String tDisp = targetA ? pair.a : pair.b, tSay = targetA ? pair.aSay : pair.bSay;
                String oDisp = targetA ? pair.b : pair.a, oSay = targetA ? pair.bSay : pair.aSay;
                String top = c.get(0);
                boolean hitT = false, hitO = false;
                for (int i = 0; i < Math.min(2, c.size()); i++) {
                    if (matches(c.get(i), tDisp, tSay)) { hitT = true; if (i == 0) break; }
                    if (i == 0 && matches(c.get(i), oDisp, oSay)) hitO = true;
                }
                if (hitT && !hitO) {
                    micStatus.setText("✅ 识别为「" + top + "」，发音到位！");
                    micStatus.setTextColor(UI.GREEN);
                } else if (hitO) {
                    micStatus.setText("❌ 听起来像「" + oDisp.split(" ")[0] + "」。" + Tips.of(pair.cat));
                    micStatus.setTextColor(UI.RED);
                    java.util.ArrayList<String> l = new java.util.ArrayList<String>();
                    l.add(pair.cat);
                    Store.addIssues(PairsActivity.this, lang, l);
                } else {
                    micStatus.setText("🤔 识别为「" + top + "」，再清楚一点试试");
                    micStatus.setTextColor(UI.ORANGE);
                }
            }
            public void onError(String msg) {
                UI.micActive(PairsActivity.this, micBtn, false);
                micStatus.setTextColor(UI.SUB);
                micStatus.setText(msg.length() == 0 ? "点麦克风再试一次" : msg);
            }
        });
        micStatus.setTextColor(UI.SUB);
    }

    private void stop() {
        h.removeCallbacksAndMessages(null);
        if (rec != null && rec.listening) rec.cancel();
        if (sp != null) sp.stop();
        if (micBtn != null) UI.micActive(this, micBtn, false);
        if (micStatus != null) micStatus.setTextColor(UI.SUB);
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
