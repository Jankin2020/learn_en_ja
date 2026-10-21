package com.xyb.voxcoach;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Weak sentences, weak words and pronunciation-issue statistics. */
public class ReviewActivity extends Activity {

    private String lang;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        lang = Store.lang(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        LinearLayout body = UI.screen(this, "弱项复习", null);
        boolean ja = "ja".equals(lang);

        // issues
        List<String[]> issues = Store.issues(this, lang);
        LinearLayout ic = UI.card(this);
        ic.addView(UI.text(this, "🎯 发音问题统计", 16, UI.TEXT, true));
        if (issues.isEmpty()) {
            ic.addView(UI.text(this, "还没有数据。去做几句跟读，系统会根据识别结果自动归类你的发音问题。", 13, UI.SUB, false), UI.matchWrap(this, 0, 6, 0, 0));
        } else {
            int max = Integer.parseInt(issues.get(0)[1]);
            for (int i = 0; i < Math.min(6, issues.size()); i++) {
                String[] it = issues.get(i);
                LinearLayout r = UI.hbox(this);
                r.addView(UI.text(this, it[0], 14, UI.TEXT, true), new LinearLayout.LayoutParams(UI.dp(this, 92), ViewGroup.LayoutParams.WRAP_CONTENT));
                r.addView(UI.bar(this, max, Integer.parseInt(it[1]), i == 0 ? UI.RED : UI.ORANGE), new LinearLayout.LayoutParams(0, UI.dp(this, 8), 1));
                TextView n = UI.text(this, "×" + it[1], 13, UI.SUB, false);
                n.setPadding(UI.dp(this, 8), 0, 0, 0);
                r.addView(n);
                ic.addView(r, UI.matchWrap(this, 0, 10, 0, 0));
                if (i < 2) ic.addView(UI.text(this, Tips.of(it[0]), 12, UI.SUB, false), UI.matchWrap(this, 0, 2, 0, 0));
            }
            TextView go = UI.button(this, "👂 针对性听辨训练", false);
            go.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { startActivity(new Intent(ReviewActivity.this, PairsActivity.class)); }
            });
            ic.addView(go, UI.matchWrap(this, 0, 12, 0, 0));
        }
        body.addView(ic, UI.matchWrap(this, 0, 4, 0, 12));

        // weak sentences
        List<Content.Sentence> weak = Store.weakSentences(this, lang);
        LinearLayout sc = UI.card(this);
        sc.addView(UI.text(this, "📚 待复习句子（最近一次 < 75 分）· " + weak.size(), 16, UI.TEXT, true));
        if (weak.isEmpty()) {
            sc.addView(UI.text(this, "暂时没有，继续保持！", 13, UI.SUB, false), UI.matchWrap(this, 0, 6, 0, 0));
        } else {
            TextView all = UI.button(this, "全部复习", true);
            all.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent i = new Intent(ReviewActivity.this, PracticeActivity.class);
                    i.putExtra("mode", "review");
                    startActivity(i);
                }
            });
            sc.addView(all, UI.matchWrap(this, 0, 10, 0, 6));
            for (int i = 0; i < Math.min(30, weak.size()); i++) {
                final Content.Sentence s = weak.get(i);
                int last = Store.lastScore(this, lang, s.id);
                LinearLayout r = UI.hbox(this);
                r.setBackground(UI.ripple(this, UI.round(this, UI.BG, 12)));
                int p = UI.dp(this, 10);
                r.setPadding(p, p, p, p);
                TextView t = UI.text(this, s.text, 14, UI.TEXT, false);
                r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                TextView sv = UI.text(this, String.valueOf(last), 16, UI.scoreColor(last), true);
                sv.setPadding(UI.dp(this, 10), 0, 0, 0);
                r.addView(sv);
                r.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Intent in = new Intent(ReviewActivity.this, PracticeActivity.class);
                        in.putExtra("id", s.id);
                        startActivity(in);
                    }
                });
                sc.addView(r, UI.matchWrap(this, 0, 6, 0, 0));
            }
        }
        body.addView(sc, UI.matchWrap(this, 0, 0, 0, 12));

        // weak words (English)
        if (!ja) {
            List<String[]> words = Store.weakWords(this, lang);
            LinearLayout wc = UI.card(this);
            wc.addView(UI.text(this, "🔤 易错单词 · 点击单独纠音，长按移除", 16, UI.TEXT, true));
            if (words.isEmpty()) {
                wc.addView(UI.text(this, "暂无。读错的单词会自动收集到这里，读对后次数会递减。", 13, UI.SUB, false), UI.matchWrap(this, 0, 6, 0, 0));
            } else {
                LinearLayout row = null;
                for (int i = 0; i < Math.min(40, words.size()); i++) {
                    if (i % 3 == 0) {
                        row = UI.hbox(this);
                        wc.addView(row, UI.matchWrap(this, 0, 8, 0, 0));
                    }
                    final String w = words.get(i)[0];
                    TextView c = UI.chip(this, w + " ×" + words.get(i)[1], false);
                    c.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            Intent in = new Intent(ReviewActivity.this, PracticeActivity.class);
                            in.putExtra("mode", "word");
                            in.putExtra("text", w);
                            startActivity(in);
                        }
                    });
                    c.setOnLongClickListener(new View.OnLongClickListener() {
                        public boolean onLongClick(View v) {
                            Store.removeWeakWord(ReviewActivity.this, lang, w);
                            build();
                            return true;
                        }
                    });
                    row.addView(c, UI.lpw(this, 1, 3));
                }
                int shown = Math.min(40, words.size());
                if (shown % 3 != 0 && row != null) {
                    for (int k = shown % 3; k < 3; k++) row.addView(new View(this), UI.lpw(this, 1, 3));
                }
            }
            body.addView(wc, UI.matchWrap(this, 0, 0, 0, 12));
        }
    }
}
