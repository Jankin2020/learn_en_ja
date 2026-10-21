package com.xyb.voxcoach;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shadowing / read-aloud with per-word scoring and adaptive coaching. */
public class PracticeActivity extends Activity {

    private String lang, mode;
    private boolean ja;
    private Speaker sp;
    private Rec rec;
    private final Handler h = new Handler();

    private Content.Sentence cur;
    private final List<Content.Sentence> queue = new ArrayList<Content.Sentence>();
    private final Map<String, Integer> fails = new HashMap<String, Integer>();
    private boolean slowMode;
    private int sessionCount, sessionSum;
    private Scorer.Result last;
    private boolean showZh;

    private TextView levelChip, sessionInfo, mainText, readingText, zhText, micBtn, micStatus, notice;
    private TextView scoreBig, scoreWord, heardText, issuesText, aiText, aiBtn, recBtn;
    private LinearLayout resultCard;
    private ProgressBar accBar, compBar, fluBar;
    private TextView accVal, compVal, fluVal;

    private MediaRecorder recorder;
    private MediaPlayer player;
    private File recFile;
    private boolean recording;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        lang = Store.lang(this);
        ja = "ja".equals(lang);
        mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "learn";
        showZh = Store.showZh(this);

        TextView gen = null;
        if (!"word".equals(mode)) {
            gen = UI.chip(this, "✨ AI 出题", false);
            gen.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { generate(); } });
        }
        String title = "review".equals(mode) ? "弱项复习" : ("word".equals(mode) ? "单词纠音" : "发音跟读");
        LinearLayout body = UI.screen(this, title, gen);

        // info row
        LinearLayout info = UI.hbox(this);
        levelChip = UI.text(this, "", 12, UI.PRIMARY, true);
        levelChip.setBackground(UI.round(this, UI.LIGHT, 10));
        levelChip.setPadding(UI.dp(this, 10), UI.dp(this, 4), UI.dp(this, 10), UI.dp(this, 4));
        info.addView(levelChip);
        View fill = new View(this);
        info.addView(fill, new LinearLayout.LayoutParams(0, 1, 1));
        sessionInfo = UI.text(this, "", 12, UI.SUB, false);
        info.addView(sessionInfo);
        body.addView(info, UI.matchWrap(this, 0, 4, 0, 10));

        // sentence card
        LinearLayout card = UI.card(this);
        mainText = UI.text(this, "", ja ? 22 : 23, UI.TEXT, true);
        mainText.setMovementMethod(LinkMovementMethod.getInstance());
        mainText.setHighlightColor(Color.TRANSPARENT);
        card.addView(mainText);
        readingText = UI.text(this, "", 15, UI.SUB, false);
        readingText.setMovementMethod(LinkMovementMethod.getInstance());
        readingText.setHighlightColor(Color.TRANSPARENT);
        if (ja) card.addView(readingText, UI.matchWrap(this, 0, 6, 0, 0));
        zhText = UI.text(this, "", 14, UI.SUB, false);
        zhText.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showZh = !showZh; renderZh(); }
        });
        card.addView(zhText, UI.matchWrap(this, 0, 10, 0, 0));
        card.addView(UI.text(this, ja ? "点击句子可慢速重听 · 点翻译可隐藏" : "点击任意单词单独听 · 点翻译可隐藏", 11, UI.HINT, false),
                UI.matchWrap(this, 0, 8, 0, 0));

        LinearLayout ctr = UI.hbox(this);
        TextView play = UI.button(this, "▶ 标准", false);
        play.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { playStd(1.0f); } });
        TextView slow = UI.button(this, "🐢 慢速", false);
        slow.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { playStd(0.65f); } });
        recBtn = UI.button(this, "🎙 录音对比", false);
        recBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleRecord(); } });
        ctr.addView(play, UI.lpw(this, 1, 3));
        ctr.addView(slow, UI.lpw(this, 1, 3));
        ctr.addView(recBtn, UI.lpw(this, 1.3f, 3));
        card.addView(ctr, UI.matchWrap(this, -3, 12, -3, 0));
        body.addView(card, UI.matchWrap(this, 0, 0, 0, 0));

        // mic
        LinearLayout micBox = UI.vbox(this);
        micBox.setGravity(Gravity.CENTER_HORIZONTAL);
        micBtn = UI.micButton(this, 84);
        micBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { onMic(); } });
        micBox.addView(micBtn, new LinearLayout.LayoutParams(UI.dp(this, 84), UI.dp(this, 84)));
        micStatus = UI.text(this, "点击麦克风，跟读上面的句子", 14, UI.SUB, false);
        micStatus.setGravity(Gravity.CENTER);
        micBox.addView(micStatus, UI.matchWrap(this, 0, 10, 0, 0));
        body.addView(micBox, UI.matchWrap(this, 0, 18, 0, 12));

        notice = UI.text(this, "", 13, UI.PRIMARY_DARK, false);
        notice.setBackground(UI.round(this, UI.LIGHT, 12));
        notice.setPadding(UI.dp(this, 12), UI.dp(this, 10), UI.dp(this, 12), UI.dp(this, 10));
        notice.setVisibility(View.GONE);
        body.addView(notice, UI.matchWrap(this, 0, 0, 0, 12));

        // result
        resultCard = UI.card(this);
        resultCard.setVisibility(View.GONE);
        LinearLayout top = UI.hbox(this);
        scoreBig = UI.text(this, "0", 46, UI.GREEN, true);
        top.addView(scoreBig);
        LinearLayout sw = UI.vbox(this);
        scoreWord = UI.text(this, "", 18, UI.TEXT, true);
        sw.addView(scoreWord);
        sw.addView(UI.text(this, "综合得分", 12, UI.SUB, false));
        LinearLayout.LayoutParams swp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swp.setMargins(UI.dp(this, 14), 0, 0, 0);
        top.addView(sw, swp);
        resultCard.addView(top);

        accBar = UI.bar(this, 100, 0, UI.GREEN); accVal = UI.text(this, "", 13, UI.TEXT, true);
        compBar = UI.bar(this, 100, 0, UI.GREEN); compVal = UI.text(this, "", 13, UI.TEXT, true);
        fluBar = UI.bar(this, 100, 0, UI.GREEN); fluVal = UI.text(this, "", 13, UI.TEXT, true);
        resultCard.addView(barRow("准确度", accBar, accVal), UI.matchWrap(this, 0, 8, 0, 0));
        resultCard.addView(barRow("完整度", compBar, compVal), UI.matchWrap(this, 0, 4, 0, 0));
        resultCard.addView(barRow("流利度", fluBar, fluVal), UI.matchWrap(this, 0, 4, 0, 0));

        heardText = UI.text(this, "", 13, UI.SUB, false);
        resultCard.addView(heardText, UI.matchWrap(this, 0, 12, 0, 0));
        issuesText = UI.text(this, "", 14, UI.TEXT, false);
        resultCard.addView(issuesText, UI.matchWrap(this, 0, 8, 0, 0));
        aiText = UI.text(this, "", 14, UI.TEXT, false);
        aiText.setBackground(UI.round(this, 0xFFF7F3FF, 12));
        aiText.setPadding(UI.dp(this, 12), UI.dp(this, 10), UI.dp(this, 12), UI.dp(this, 10));
        aiText.setVisibility(View.GONE);
        aiText.setTextIsSelectable(true);
        resultCard.addView(aiText, UI.matchWrap(this, 0, 10, 0, 0));

        LinearLayout btns = UI.hbox(this);
        TextView again = UI.button(this, "🔁 再读", false);
        again.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { onMic(); } });
        aiBtn = UI.button(this, "🤖 AI 诊断", false);
        aiBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { diagnose(); } });
        TextView next = UI.button(this, "word".equals(mode) ? "完成" : "下一句 →", true);
        next.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if ("word".equals(mode)) finish();
                else next();
            }
        });
        btns.addView(again, UI.lpw(this, 1, 3));
        btns.addView(aiBtn, UI.lpw(this, 1.2f, 3));
        btns.addView(next, UI.lpw(this, 1.2f, 3));
        resultCard.addView(btns, UI.matchWrap(this, -3, 12, -3, 0));
        body.addView(resultCard, UI.matchWrap(this, 0, 0, 0, 0));

        sp = new Speaker(this, lang);
        rec = new Rec(this);
        recFile = new File(getCacheDir(), "my_voice.m4a");

        // initial queue
        if ("review".equals(mode)) {
            queue.addAll(Store.weakSentences(this, lang));
            Collections.shuffle(queue);
            if (queue.isEmpty()) UI.toast(this, "暂时没有低于 75 分的句子，先做常规练习吧");
        } else if ("word".equals(mode)) {
            String w = getIntent().getStringExtra("text");
            queue.add(new Content.Sentence(lang, "word", Store.level(this, lang), w == null ? "hello" : w, "单词纠音"));
        } else if (getIntent().getStringExtra("id") != null) {
            Content.Sentence s = Store.findSentence(this, lang, getIntent().getStringExtra("id"));
            if (s != null) queue.add(s);
        }
        next();
    }

    private View barRow(String label, ProgressBar bar, TextView val) {
        LinearLayout r = UI.hbox(this);
        TextView l = UI.text(this, label, 13, UI.SUB, false);
        r.addView(l, new LinearLayout.LayoutParams(UI.dp(this, 56), ViewGroup.LayoutParams.WRAP_CONTENT));
        r.addView(bar, new LinearLayout.LayoutParams(0, UI.dp(this, 8), 1));
        val.setGravity(Gravity.END);
        r.addView(val, new LinearLayout.LayoutParams(UI.dp(this, 40), ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    // ------------------------------------------------------------------ flow

    private void next() {
        stopAll();
        String prev = cur == null ? null : cur.id;
        if (!queue.isEmpty()) cur = queue.remove(0);
        else if ("word".equals(mode) && cur != null) { /* keep */ }
        else cur = Store.nextSentence(this, lang, prev);
        last = null;
        resultCard.setVisibility(View.GONE);
        aiText.setVisibility(View.GONE);
        render(null);
        int best = Store.bestScore(this, lang, cur.id);
        String src = cur.id.startsWith("ai_") ? " · AI 题" : "";
        levelChip.setText(Content.levelName(lang, Math.max(1, Math.min(4, cur.level))) + src + (best >= 0 ? " · 最佳 " + best : ""));
        updateSession();
        micStatus.setText("先听一遍，再点麦克风跟读");
        h.postDelayed(new Runnable() { public void run() { playStd(1.0f); } }, 350);
    }

    private void updateSession() {
        sessionInfo.setText(sessionCount == 0 ? "今日 " + Store.todayCount(this) + "/" + Store.goal(this)
                : "本次 " + sessionCount + " 句 · 平均 " + (sessionSum / sessionCount));
    }

    private float rate() { return slowMode ? 0.7f : 1.0f; }

    private void playStd(float r) {
        if (rec.listening) rec.cancel();
        stopPlayer();
        sp.speak(cur.text, r == 1.0f ? rate() : r, null);
    }

    // ------------------------------------------------------------------ rendering

    private class TokenSpan extends ClickableSpan {
        final int color; final boolean underline; final String say;
        TokenSpan(int color, boolean underline, String say) { this.color = color; this.underline = underline; this.say = say; }
        @Override public void onClick(View widget) {
            if (rec.listening) rec.cancel();
            stopPlayer();
            if (say == null) sp.speak(cur.text, 0.65f, null);
            else sp.speak(say, 0.8f, null);
        }
        @Override public void updateDrawState(TextPaint ds) {
            ds.setColor(color);
            ds.setUnderlineText(underline);
        }
    }

    private int colorOf(int status) {
        switch (status) {
            case Scorer.OK: return UI.GREEN;
            case Scorer.NEAR: return UI.ORANGE;
            case Scorer.BAD: case Scorer.MISSING: return UI.RED;
            default: return UI.TEXT;
        }
    }

    private CharSequence plain(String text, int color) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (ja) {
            sb.append(text);
            if (sb.length() > 0) sb.setSpan(new TokenSpan(color, false, null), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return sb;
        }
        String[] words = text.trim().split("\\s+");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(" ");
            int st = sb.length();
            sb.append(words[i]);
            String n = Scorer.enNorm(words[i]);
            if (sb.length() > st) sb.setSpan(new TokenSpan(color, false, n.length() > 0 ? n : null), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    private CharSequence colored(Scorer.Result r) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < r.display.size(); i++) {
            if (!ja && i > 0) sb.append(" ");
            int st = sb.length();
            sb.append(r.display.get(i));
            int s = r.status.get(i);
            String say = ja ? null : (r.norm.get(i).length() > 0 ? r.norm.get(i) : null);
            sb.setSpan(new TokenSpan(colorOf(s), s == Scorer.MISSING, say), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    private void render(Scorer.Result r) {
        if (r == null) {
            mainText.setText(plain(cur.text, UI.TEXT));
            if (ja) readingText.setText(plain(cur.reading, UI.SUB));
        } else if (!ja) {
            mainText.setText(colored(r));
        } else if (r.byReading) {
            mainText.setText(plain(cur.text, UI.TEXT));
            readingText.setText(colored(r));
        } else {
            mainText.setText(colored(r));
            readingText.setText(plain(cur.reading, UI.SUB));
        }
        renderZh();
    }

    private void renderZh() {
        if (cur.zh == null || cur.zh.length() == 0) { zhText.setText(""); return; }
        zhText.setText(showZh ? cur.zh : "（点击显示中文翻译）");
        zhText.setTypeface(Typeface.DEFAULT, showZh ? Typeface.NORMAL : Typeface.ITALIC);
    }

    // ------------------------------------------------------------------ recognition

    private void onMic() {
        if (rec.listening) { rec.stop(); return; }
        if (!UI.ensureMic(this)) return;
        if (recording) stopRecording(false);
        stopPlayer();
        sp.stop();
        UI.micActive(this, micBtn, true);
        micStatus.setText("准备中…");
        rec.start(lang, new Rec.Cb() {
            public void onReady() { micStatus.setText("请开始朗读…（读完会自动结束，也可再点一下）"); }
            public void onPartial(String text) { micStatus.setText("“" + text + "”"); }
            public void onRms(float db) {
                float s = 1f + Math.max(0f, Math.min(10f, db)) / 45f;
                micBtn.setScaleX(s); micBtn.setScaleY(s);
            }
            public void onResult(List<String> c, float conf, long dur) {
                UI.micActive(PracticeActivity.this, micBtn, false);
                micStatus.setText("点击麦克风再读一次");
                evaluate(c, conf, dur);
            }
            public void onError(String msg) {
                UI.micActive(PracticeActivity.this, micBtn, false);
                micStatus.setText(msg.length() == 0 ? "点击麦克风，跟读上面的句子" : msg);
            }
        });
    }

    private void evaluate(List<String> cands, float conf, long dur) {
        Scorer.Result r = Scorer.scoreSpeech(cur.text, cur.reading, cur.ruby, ja, cands, conf, dur);
        last = r;
        render(r);
        resultCard.setVisibility(View.VISIBLE);
        aiText.setVisibility(View.GONE);
        aiBtn.setAlpha(Store.aiReady(this) ? 1f : 0.5f);

        int c = UI.scoreColor(r.overall);
        scoreBig.setText(String.valueOf(r.overall));
        scoreBig.setTextColor(c);
        scoreWord.setText(UI.scoreWord(r.overall));
        setBar(accBar, accVal, r.accuracy);
        setBar(compBar, compVal, r.completeness);
        setBar(fluBar, fluVal, r.fluency);
        heardText.setText("识别到：" + (r.heard.length() > 0 ? r.heard : "（无）") + "\n绿色=标准  橙色=接近  红色=读错/漏读" + (ja ? "" : "（点单词可单独听）"));

        List<String> issues = Scorer.issues(r);
        Store.addIssues(this, lang, issues);
        Store.updateWords(this, lang, r.problemTokens(), r.goodTokens());
        StringBuilder sb = new StringBuilder();
        if (issues.isEmpty()) sb.append(r.overall >= 85 ? "👍 每个词都读到位了，试试不看文字再说一遍！" : "👀 整体可以，注意语速和连贯。");
        for (String i : issues) sb.append(sb.length() > 0 ? "\n" : "").append("• ").append(i).append("：").append(Tips.of(i));
        issuesText.setText(sb.toString());

        String lvMsg = null;
        if ("word".equals(mode)) Store.tick(this);
        else lvMsg = Store.recordSentence(this, lang, cur.id, r.overall);
        sessionCount++;
        sessionSum += r.overall;
        updateSession();

        // adaptive coaching inside the session
        StringBuilder note = new StringBuilder();
        if (r.overall < 70) {
            int f = (fails.containsKey(cur.id) ? fails.get(cur.id) : 0) + 1;
            fails.put(cur.id, f);
            if (f >= 2 && !slowMode) {
                slowMode = true;
                note.append("连续两次低于 70 分：已切换到慢速示范，下面会逐个播放读错的部分，再完整慢读一遍。");
                drill(r);
            } else if (f >= 2) {
                note.append("再听一遍问题部分 👇");
                drill(r);
            }
        } else if (r.overall >= 85) {
            fails.remove(cur.id);
            if (slowMode) {
                slowMode = false;
                note.append("读得很好！已恢复正常语速。");
            }
        }
        if (lvMsg != null) note.append(note.length() > 0 ? "\n" : "").append(lvMsg);
        if (note.length() > 0) { notice.setText(note.toString()); notice.setVisibility(View.VISIBLE); }
        else notice.setVisibility(View.GONE);
    }

    private void setBar(ProgressBar b, TextView v, int val) {
        b.setProgress(val);
        b.setProgressTintList(android.content.res.ColorStateList.valueOf(UI.scoreColor(val)));
        v.setText(String.valueOf(val));
    }

    /** Plays the mis-pronounced chunks one by one, then the whole sentence slowly. */
    private void drill(Scorer.Result r) {
        List<String> items = new ArrayList<String>();
        if (!ja) {
            for (int i = 0; i < r.display.size(); i++) {
                int s = r.status.get(i);
                if ((s == Scorer.BAD || s == Scorer.MISSING || s == Scorer.NEAR) && !items.contains(r.norm.get(i))) items.add(r.norm.get(i));
            }
        } else {
            // group bad characters with one neighbour on each side
            int n = r.display.size();
            int i = 0;
            while (i < n) {
                int s = r.status.get(i);
                if (s == Scorer.BAD || s == Scorer.MISSING || s == Scorer.NEAR) {
                    int a = Math.max(0, i - 1), b = i;
                    while (b + 1 < n && r.status.get(b + 1) != Scorer.OK && r.status.get(b + 1) != Scorer.SKIP) b++;
                    b = Math.min(n - 1, b + 1);
                    StringBuilder chunk = new StringBuilder();
                    for (int k = a; k <= b; k++) if (r.status.get(k) != Scorer.SKIP) chunk.append(r.display.get(k));
                    if (chunk.length() > 0) items.add(chunk.toString());
                    i = b + 1;
                } else i++;
            }
        }
        if (items.size() > 5) items = items.subList(0, 5);
        items = new ArrayList<String>(items);
        items.add(cur.text);
        sp.speakList(items, 0.7f, null);
    }

    // ------------------------------------------------------------------ record & compare

    private void toggleRecord() {
        if (recording) { stopRecording(true); return; }
        if (!UI.ensureMic(this)) return;
        if (rec.listening) rec.cancel();
        sp.stop();
        stopPlayer();
        try {
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setOutputFile(recFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            recBtn.setText("■ 停止");
            micStatus.setText("🔴 录音中… 读完点「停止」，会先播放你的录音，再播放标准发音");
            h.postDelayed(autoStop, 15000);
        } catch (Exception e) {
            releaseRecorder();
            UI.toast(this, "无法录音：" + e.getMessage());
        }
    }

    private final Runnable autoStop = new Runnable() {
        public void run() { if (recording) stopRecording(true); }
    };

    private void stopRecording(boolean play) {
        h.removeCallbacks(autoStop);
        boolean ok = true;
        try { recorder.stop(); } catch (Exception e) { ok = false; }
        releaseRecorder();
        recording = false;
        recBtn.setText("🎙 录音对比");
        if (play && ok) {
            micStatus.setText("▶ 你的录音 → 标准发音");
            try {
                player = new MediaPlayer();
                player.setDataSource(recFile.getAbsolutePath());
                player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    public void onCompletion(MediaPlayer mp) {
                        stopPlayer();
                        h.postDelayed(new Runnable() { public void run() { sp.speak(cur.text, rate(), null); } }, 300);
                    }
                });
                player.prepare();
                player.start();
            } catch (Exception e) {
                stopPlayer();
                UI.toast(this, "播放失败：" + e.getMessage());
            }
        } else if (play) {
            micStatus.setText("录音太短了，再试一次");
        }
    }

    private void releaseRecorder() {
        if (recorder != null) { try { recorder.release(); } catch (Exception ignored) { } recorder = null; }
    }

    private void stopPlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    // ------------------------------------------------------------------ AI

    private String langName() { return ja ? "Japanese" : "English"; }

    private String levelDesc() {
        int lv = Store.level(this, lang);
        return ja ? "JLPT N" + (6 - lv) : new String[]{"", "CEFR A1", "CEFR A2", "CEFR B1", "CEFR B2"}[lv];
    }

    private void diagnose() {
        if (last == null) return;
        if (!Store.aiReady(this)) { UI.toast(this, "请先在「设置」里配置 AI 接口"); return; }
        aiText.setVisibility(View.VISIBLE);
        aiText.setText("🤖 AI 正在分析你的发音…");
        StringBuilder wrong = new StringBuilder();
        for (int i = 0; i < last.display.size(); i++) {
            int s = last.status.get(i);
            if (s == Scorer.BAD || s == Scorer.NEAR || s == Scorer.MISSING) {
                wrong.append(last.display.get(i)).append(s == Scorer.MISSING ? "(漏读)" : "(识别为:" + last.heardFor.get(i) + ")").append(ja ? " " : ", ");
            }
        }
        String prompt = "目标句子：" + cur.text + (ja ? "\n读音：" + cur.reading : "")
                + "\n语音识别结果：" + last.heard
                + "\n逐词比对中的问题：" + (wrong.length() == 0 ? "无明显错误" : wrong.toString())
                + "\n系统判断的问题类型：" + Scorer.issues(last)
                + "\n分数：综合 " + last.overall + "，准确度 " + last.accuracy + "，流利度 " + last.fluency
                + "\n\n请根据识别差异推断学习者最可能的发音问题（具体到音素、长短音、促音、重音或连读），"
                + "给出 2-3 条具体可操作的纠正方法，最后给一个专门练这个问题的短句（附中文）。";
        JSONArray msgs = new JSONArray();
        msgs.put(AiClient.msg("system", "You are an expert " + langName() + " pronunciation coach for Chinese-speaking learners. "
                + "The learner's speech was transcribed by a speech recognizer, so recognition differences hint at pronunciation problems. "
                + "Reply in Simplified Chinese, concise (under 180 Chinese characters), practical, no markdown headings."));
        msgs.put(AiClient.msg("user", prompt));
        AiClient.chat(this, msgs, 0.4, new AiClient.Cb() {
            public void ok(String content) { aiText.setText("🤖 " + content.replace("**", "")); }
            public void fail(String error) { aiText.setText("AI 请求失败：" + error); }
        });
    }

    private void generate() {
        if (!Store.aiReady(this)) { UI.toast(this, "请先在「设置」里配置 AI 接口"); return; }
        final int lv = Store.level(this, lang);
        StringBuilder focus = new StringBuilder();
        List<String[]> words = Store.weakWords(this, lang);
        for (int i = 0; i < Math.min(8, words.size()); i++) focus.append(words.get(i)[0]).append(", ");
        List<String[]> issues = Store.issues(this, lang);
        StringBuilder iss = new StringBuilder();
        for (int i = 0; i < Math.min(3, issues.size()); i++) iss.append(issues.get(i)[0]).append("、");
        String fmt = ja
                ? "Each item: {\"text\": \"a natural Japanese sentence where EVERY word that contains kanji is written in ruby markup {漢字|かな}, e.g. {切手|きって}を{三枚|さんまい}ください。\", \"zh\": \"简体中文翻译\"}. Do not use arabic digits."
                : "Each item: {\"text\": \"a natural English sentence\", \"zh\": \"简体中文翻译\"}. Spell out numbers as words.";
        String user = "Create 5 short, natural, everyday " + langName() + " sentences for shadowing practice at level " + levelDesc() + ". "
                + (focus.length() > 0 ? "Naturally include some of these words the learner mispronounces: " + focus + ". " : "")
                + (iss.length() > 0 ? "Target these pronunciation weak points: " + iss + ". " : "")
                + "Vary the topics (work, travel, daily life). " + fmt + " Return ONLY a JSON array.";
        JSONArray msgs = new JSONArray();
        msgs.put(AiClient.msg("system", "You generate language-learning content. Output strictly valid JSON, no commentary."));
        msgs.put(AiClient.msg("user", user));
        UI.toast(this, "AI 正在根据你的弱项出题…");
        AiClient.chat(this, msgs, 0.9, new AiClient.Cb() {
            public void ok(String content) {
                if (isFinishing()) return;
                JSONArray arr = AiClient.jsonArray(content);
                if (arr == null) { UI.toast(PracticeActivity.this, "AI 返回格式不对，请再试一次"); return; }
                List<Content.Sentence> added = Store.addAiSentences(PracticeActivity.this, lang, arr, lv);
                if (added.isEmpty()) { UI.toast(PracticeActivity.this, "没有生成有效句子，请再试一次"); return; }
                queue.addAll(0, added);
                UI.toast(PracticeActivity.this, "已生成 " + added.size() + " 句并加入题库，马上开始 ✨");
                next();
            }
            public void fail(String error) { UI.toast(PracticeActivity.this, "AI 出题失败：" + error); }
        });
    }

    // ------------------------------------------------------------------ lifecycle

    private void stopAll() {
        h.removeCallbacksAndMessages(null);
        if (rec != null && rec.listening) rec.cancel();
        if (recording) stopRecording(false);
        stopPlayer();
        if (sp != null) sp.stop();
        if (micBtn != null) UI.micActive(this, micBtn, false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAll();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAll();
        sp.shutdown();
        rec.destroy();
    }
}
