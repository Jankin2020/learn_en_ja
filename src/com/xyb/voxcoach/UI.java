package com.xyb.voxcoach;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Tiny programmatic UI kit (no XML layouts). */
public final class UI {
    public static final int PRIMARY = 0xFF5B5BD6;
    public static final int PRIMARY_DARK = 0xFF4343B0;
    public static final int ACCENT = 0xFF8E6CF0;
    public static final int BG = 0xFFF4F4FA;
    public static final int CARD = 0xFFFFFFFF;
    public static final int TEXT = 0xFF1C1C28;
    public static final int SUB = 0xFF6E6E82;
    public static final int HINT = 0xFFA3A3B5;
    public static final int GREEN = 0xFF1F9D63;
    public static final int ORANGE = 0xFFE08A00;
    public static final int RED = 0xFFE5484D;
    public static final int LIGHT = 0xFFECECFB;
    public static final int BORDER = 0xFFE4E4EE;

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    public static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static GradientDrawable roundStroke(Context c, int color, float radiusDp, int stroke) {
        GradientDrawable g = round(c, color, radiusDp);
        g.setStroke(dp(c, 1), stroke);
        return g;
    }

    public static Drawable ripple(Context c, Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(0x22000000), content, null);
    }

    public static GradientDrawable gradient(Context c, int a, int b, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpw(Context c, float weight, float marginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        int m = dp(c, marginDp);
        p.setMargins(m, m, m, m);
        return p;
    }

    public static LinearLayout.LayoutParams matchWrap(Context c, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = vbox(c);
        l.setBackground(round(c, CARD, 18));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        l.setElevation(dp(c, 1));
        return l;
    }

    public static TextView button(Context c, String label, boolean primary) {
        TextView b = text(c, label, 15, primary ? Color.WHITE : PRIMARY, true);
        b.setGravity(Gravity.CENTER);
        int ph = dp(c, 14), pv = dp(c, 11);
        b.setPadding(ph, pv, ph, pv);
        b.setBackground(ripple(c, primary ? round(c, PRIMARY, 14) : round(c, LIGHT, 14)));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    public static TextView chip(Context c, String label, boolean on) {
        TextView b = text(c, label, 14, on ? Color.WHITE : TEXT, on);
        b.setGravity(Gravity.CENTER);
        int ph = dp(c, 14), pv = dp(c, 8);
        b.setPadding(ph, pv, ph, pv);
        b.setBackground(ripple(c, on ? round(c, PRIMARY, 20) : roundStroke(c, CARD, 20, BORDER)));
        b.setClickable(true);
        return b;
    }

    public static void setChip(Context c, TextView b, boolean on) {
        b.setTextColor(on ? Color.WHITE : TEXT);
        b.setTypeface(Typeface.DEFAULT, on ? Typeface.BOLD : Typeface.NORMAL);
        b.setBackground(ripple(c, on ? round(c, PRIMARY, 20) : roundStroke(c, CARD, 20, BORDER)));
    }

    public static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setTextColor(TEXT);
        e.setHintTextColor(HINT);
        e.setBackground(roundStroke(c, CARD, 12, BORDER));
        int p = dp(c, 12);
        e.setPadding(p, p, p, p);
        return e;
    }

    public static ProgressBar bar(Context c, int max, int value, int color) {
        ProgressBar pb = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(max);
        pb.setProgress(value);
        pb.setProgressTintList(ColorStateList.valueOf(color));
        pb.setProgressBackgroundTintList(ColorStateList.valueOf(LIGHT));
        return pb;
    }

    public static int scoreColor(int s) {
        if (s >= 85) return GREEN;
        if (s >= 65) return ORANGE;
        return RED;
    }

    public static String scoreWord(int s) {
        if (s >= 95) return "完美！";
        if (s >= 85) return "很棒！";
        if (s >= 70) return "不错，继续加油";
        if (s >= 55) return "还差一点";
        return "再试一次";
    }

    /** Standard screen: colored status area + top bar + scrolling body. Returns the body container. */
    public static LinearLayout screen(final Activity a, String title, View right) {
        LinearLayout root = vbox(a);
        root.setBackgroundColor(BG);
        LinearLayout bar = hbox(a);
        int p = dp(a, 8);
        bar.setPadding(p, dp(a, 6), dp(a, 12), dp(a, 6));
        TextView back = text(a, "‹", 30, TEXT, false);
        back.setPadding(dp(a, 10), 0, dp(a, 14), dp(a, 4));
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { a.finish(); }
        });
        bar.addView(back);
        TextView t = text(a, title, 19, TEXT, true);
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (right != null) bar.addView(right);
        root.addView(bar, matchWrap(a, 0, 0, 0, 0));

        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(true);
        LinearLayout body = vbox(a);
        int bp = dp(a, 16);
        body.setPadding(bp, dp(a, 4), bp, dp(a, 24));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        a.setContentView(root);
        return body;
    }

    /** Returns true if mic permission is granted; otherwise requests it. */
    public static boolean ensureMic(Activity a) {
        if (a.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) return true;
        a.requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 7);
        toast(a, "需要麦克风权限才能评测发音");
        return false;
    }

    public static void ensureMicSilently(Activity a) {
        if (!Store.onboarded(a)) return;
        if (a.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            a.requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 7);
    }

    /** Big round mic button. */
    public static TextView micButton(Context c, float sizeDp) {
        TextView m = text(c, "🎤", sizeDp * 0.36f, Color.WHITE, false);
        m.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{ACCENT, PRIMARY});
        g.setShape(GradientDrawable.OVAL);
        m.setBackground(ripple(c, g));
        m.setElevation(dp(c, 6));
        m.setClickable(true);
        return m;
    }

    public static void micActive(Context c, TextView m, boolean active) {
        GradientDrawable g = active
                ? new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFFF6B6B, RED})
                : new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{ACCENT, PRIMARY});
        g.setShape(GradientDrawable.OVAL);
        m.setBackground(ripple(c, g));
        m.setText(active ? "■" : "🎤");
        if (!active) { m.setScaleX(1f); m.setScaleY(1f); }
    }

    public static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_LONG).show();
    }

    public static View space(Context c, float h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, h)));
        return v;
    }
}
