package com.xyb.voxcoach;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Listening dictation with adaptive playback speed. */
public class DictationActivity extends Activity {

    private String lang;
    private boolean ja;
    private Speaker sp;
    private final Handler h = new Handler();
    private Content.Sentence cur;
    private final List<String> recent = new ArrayList<String>();
    private int plays, hintLevel, n, sum;
    private boolean answered;

    private TextView speedChip, counter, playsText, resultTitle, resultBody, zhText, hintText, submit;
    private EditText input;
    private LinearLayout resultCard;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        lang = Store.lang(this);
        ja = "ja".equals(lang);
        speedChip = UI.text(this, "", 13, UI.PRIMARY, true);
        speedChip.setBackground(UI.round(this, UI.LIGHT, 10));
        speedChip.setPadding(UI.dp(this, 10), UI.dp(this, 4), UI.dp(this, 10), UI.dp(this, 4));
        LinearLayout body = UI.screen(this, "听写训练", speedChip);

        LinearLayout card = UI.card(this);
        counter = UI.text(this, "", 13, UI.SUB, false);
        card.addView(counter);
        card.addView(UI.text(this, "听音频，把听到的句子写下来", 17, UI.TEXT, true), UI.matchWrap(this, 0, 4, 0, 0));
        card.addView(UI.text(this, "语速会根据你的正确率自动调整：全对加速，错得多就放慢。", 12, UI.SUB, false), UI.matchWrap(this, 0, 4, 0, 0));

        LinearLayout ctr = UI.hbox(this);
        ctr.setGravity(Gravity.CENTER);
        TextView play = UI.micButton(this, 72);
        play.setText("▶");
        play.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { play(1f); } });
        ctr.addView(play, new LinearLayout.LayoutParams(UI.dp(this, 72), UI.dp(this, 72)));
        LinearLayout side = UI.vbox(this);
        TextView slow = UI.button(this, "🐢 0.7x 慢放", false);
        slow.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { play(0.7f / Store.listenSpeed(DictationActivity.this, lang)); } });
        side.addView(slow);
        playsText = UI.text(this, "", 12, UI.SUB, false);
        playsText.setGravity(Gravity.CENTER);
        side.addView(playsText, UI.matchWrap(this, 0, 6, 0, 0));
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp2.setMargins(UI.dp(this, 22), 0, 0, 0);
        ctr.addView(side, sp2);
        card.addView(ctr, UI.matchWrap(this, 0, 16, 0, 12));

        input = UI.input(this, ja ? "写下听到的日语（假名或汉字都可以）" : "Type what you hear…");
        input.setMinLines(2);
        input.setGravity(Gravity.TOP | Gravity.START);
        card.addView(input, UI.matchWrap(this, 0, 4, 0, 0));
        hintText = UI.text(this, "", 13, UI.ORANGE, false);
        hintText.setVisibility(View.GONE);
        card.addView(hintText, UI.matchWrap(this, 0, 8, 0, 0));

        LinearLayout btns = UI.hbox(this);
        TextView hint = UI.button(this, "💡 提示", false);
        hint.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { hint(); } });
        submit = UI.button(this, "提交", true);
        submit.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (answered) next();
                else check();
            }
        });
        btns.addView(hint, UI.lpw(this, 1, 3));
        btns.addView(submit, UI.lpw(this, 2, 3));
        card.addView(btns, UI.matchWrap(this, -3, 12, -3, 0));
        body.addView(card, UI.matchWrap(this, 0, 4, 0, 12));

        resultCard = UI.card(this);
        resultCard.setVisibility(View.GONE);
        resultTitle = UI.text(this, "", 18, UI.TEXT, true);
        resultCard.addView(resultTitle);
        resultBody = UI.text(this, "", 20, UI.TEXT, true);
        resultCard.addView(resultBody, UI.matchWrap(this, 0, 8, 0, 0));
        zhText = UI.text(this, "", 14, UI.SUB, false);
        resultCard.addView(zhText, UI.matchWrap(this, 0, 8, 0, 0));
        TextView shadow = UI.button(this, "🎯 去跟读这句", false);
        shadow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                android.content.Intent i = new android.content.Intent(DictationActivity.this, PracticeActivity.class);
                i.putExtra("id", cur.id);
                startActivity(i);
            }
        });
        resultCard.addView(shadow, UI.matchWrap(this, 0, 12, 0, 0));
        body.addView(resultCard);

        sp = new Speaker(this, lang);
        next();
    }

    private void updateChip() {
        speedChip.setText(String.format("语速 %.2fx", Store.listenSpeed(this, lang)));
    }

    private void next() {
        answered = false;
        plays = 0;
        hintLevel = 0;
        input.setText("");
        input.setEnabled(true);
        hintText.setVisibility(View.GONE);
        resultCard.setVisibility(View.GONE);
        submit.setText("提交");
        int lv = Store.level(this, lang);
        List<Content.Sentence> pool = new ArrayList<Content.Sentence>();
        for (Content.Sentence s : Store.allSentences(this, lang)) {
            if (s.level == lv && !recent.contains(s.id)) pool.add(s);
        }
        if (pool.isEmpty()) {
            recent.clear();
            for (Content.Sentence s : Store.allSentences(this, lang)) if (s.level == lv) pool.add(s);
        }
        cur = pool.get(new Random().nextInt(pool.size()));
        recent.add(cur.id);
        if (recent.size() > 10) recent.remove(0);
        counter.setText("第 " + (n + 1) + " 题 · " + Content.levelName(lang, lv) + (n > 0 ? " · 平均 " + (sum / n) : ""));
        updateChip();
        playsText.setText("点 ▶ 播放");
        h.postDelayed(new Runnable() { public void run() { play(1f); } }, 400);
    }

    private void play(float mult) {
        plays++;
        playsText.setText("已播放 " + plays + " 次");
        sp.speak(cur.text, Store.listenSpeed(this, lang) * mult, null);
    }

    private void hint() {
        if (answered) return;
        hintLevel++;
        StringBuilder b = new StringBuilder();
        if (hintLevel == 1) {
            if (ja) b.append("共 ").append(cur.reading.replaceAll("[、。！？\\s]", "").length()).append(" 个假名");
            else b.append("共 ").append(cur.text.trim().split("\\s+").length).append(" 个单词");
            b.append(" · 中文：").append(cur.zh);
        } else {
            if (ja) {
                String r = cur.reading;
                for (int i = 0; i < r.length(); i++) b.append(i % 3 == 0 ? r.charAt(i) : '＿');
            } else {
                for (String w : cur.text.trim().split("\\s+")) {
                    b.append(w.charAt(0));
                    for (int i = 1; i < w.length(); i++) b.append(Character.isLetter(w.charAt(i)) ? '_' : w.charAt(i));
                    b.append(' ');
                }
            }
        }
        hintText.setText("💡 " + b.toString().trim());
        hintText.setVisibility(View.VISIBLE);
    }

    private void check() {
        String ans = input.getText().toString().trim();
        if (ans.length() == 0) { UI.toast(this, "先写下你听到的内容"); return; }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        answered = true;
        input.setEnabled(false);
        Scorer.Result r = Scorer.scoreTyped(cur.text, cur.reading, cur.ruby, ja, ans);
        int score = Math.max(0, r.overall - Math.max(0, plays - 3) * 3 - hintLevel * 8);
        n++;
        sum += score;
        float before = Store.listenSpeed(this, lang);
        float after = Store.adaptListen(this, lang, score);
        updateChip();

        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < r.display.size(); i++) {
            if (!ja && i > 0) sb.append(" ");
            int st = sb.length();
            sb.append(r.display.get(i));
            int s = r.status.get(i);
            int c = s == Scorer.OK ? UI.GREEN : s == Scorer.NEAR ? UI.ORANGE : s == Scorer.SKIP ? UI.TEXT : UI.RED;
            sb.setSpan(new ForegroundColorSpan(c), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (ja && r.byReading) sb.insert(0, cur.text + "\n");
        resultBody.setText(sb);
        String speedMsg = after > before ? String.format("  ·  语速提升到 %.2fx ⬆", after)
                : after < before ? String.format("  ·  语速降到 %.2fx ⬇", after) : "";
        resultTitle.setText(score + " 分 · " + UI.scoreWord(score) + speedMsg);
        resultTitle.setTextColor(UI.scoreColor(score));
        zhText.setText("你的答案：" + ans + "\n中文：" + cur.zh
                + (plays > 3 || hintLevel > 0 ? "\n（播放超过 3 次或使用提示会少量扣分）" : ""));
        resultCard.setVisibility(View.VISIBLE);
        submit.setText("下一题 →");
        sp.speak(cur.text, Store.listenSpeed(this, lang), null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacksAndMessages(null);
        sp.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sp.shutdown();
    }
}
