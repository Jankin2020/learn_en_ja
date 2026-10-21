package com.xyb.voxcoach;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Speech output. Two back-ends:
 *  - system: Android TextToSpeech, preferring Google's engine (needed for Japanese on many Chinese ROMs)
 *  - cloud:  OpenAI-compatible /audio/speech (SiliconFlow CosyVoice, OpenAI tts-1, ...), cached as mp3
 */
public class Speaker implements TextToSpeech.OnInitListener {

    public interface Done { void done(); }

    public static final String GOOGLE_TTS = "com.google.android.tts";

    private final Context ctx;
    private final String lang;
    private final boolean cloud;
    private TextToSpeech tts;
    private boolean ready;
    public boolean missingVoice;
    private boolean warned;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Done> callbacks = new HashMap<String, Done>();
    private Runnable pending;
    private int seq = 0;

    // cloud playback state
    private MediaPlayer player;
    private volatile int token = 0;

    public Speaker(Context c, String lang) {
        this.ctx = c.getApplicationContext();
        this.lang = lang;
        this.cloud = !"system".equals(Store.ttsMode(ctx)); // "cloud" (OpenAI-compatible) or "azure"
        if (cloud) {
            ready = true;
        } else {
            tts = new TextToSpeech(ctx, this, engine(ctx));
        }
    }

    /** The engine to use: user choice, else Google if installed, else system default (null). */
    public static String engine(Context c) {
        String e = Store.ttsEngine(c);
        if (e.length() > 0 && installed(c, e)) return e;
        if (installed(c, GOOGLE_TTS)) return GOOGLE_TTS;
        return null;
    }

    public static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static Locale locale(Context c, String lang) {
        if ("ja".equals(lang)) return Locale.JAPAN;
        return "UK".equals(Store.accent(c)) ? Locale.UK : Locale.US;
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) {
            missingVoice = true;
            ready = true; // let speak() show the hint instead of silently queueing
            return;
        }
        Locale loc = locale(ctx, lang);
        int r = tts.setLanguage(loc);
        missingVoice = r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED;
        try {
            Set<Voice> voices = tts.getVoices();
            Voice best = null;
            String chosen = Store.sysVoice(ctx, lang);
            if (voices != null && chosen.length() > 0) {
                for (Voice v : voices) if (chosen.equals(v.getName())) best = v;
            }
            if (voices != null && best == null) {
                for (Voice v : voices) {
                    Locale vl = v.getLocale();
                    if (vl == null || !vl.getLanguage().equals(loc.getLanguage())) continue;
                    if (!"ja".equals(lang) && !loc.getCountry().equals(vl.getCountry())) continue;
                    Set<String> f = v.getFeatures();
                    if (f != null && f.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                    if (v.isNetworkConnectionRequired()) continue;
                    if (best == null || v.getQuality() > best.getQuality()) best = v;
                }
            }
            if (best != null) tts.setVoice(best);
        } catch (Exception ignored) { }
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { fire(id); }
            @Override public void onError(String id) { fire(id); }
            @Override public void onStop(String id, boolean interrupted) { synchronized (callbacks) { callbacks.remove(id); } }
        });
        ready = true;
        if (pending != null) {
            final Runnable p = pending;
            pending = null;
            main.post(p);
        }
    }

    private void fire(String id) {
        final Done d;
        synchronized (callbacks) { d = callbacks.remove(id); }
        if (d != null) main.post(new Runnable() { public void run() { d.done(); } });
    }

    public boolean isReady() { return ready; }

    private boolean warnIfMissing() {
        if (tts == null || missingVoice) {
            if (!warned) {
                warned = true;
                UI.toast(ctx, ("ja".equals(lang) ? "当前语音引擎不支持日语" : "当前语音引擎不支持英语")
                        + "：请到 设置 → 语音环境，安装/选择 Google 语音服务，或开启在线语音");
            }
            return tts == null;
        }
        return false;
    }

    public void speak(final String text, final float rate, final Done done) {
        if (cloud) { List<String> l = new ArrayList<String>(); l.add(text); cloudSpeak(l, rate, done); return; }
        if (!ready) {
            pending = new Runnable() { public void run() { speak(text, rate, done); } };
            return;
        }
        if (warnIfMissing()) return;
        tts.setSpeechRate(rate * Store.ttsRate(ctx));
        String id = "u" + (seq++);
        if (done != null) synchronized (callbacks) { callbacks.put(id, done); }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), id);
    }

    /** Speak several pieces with short pauses in between (used for word-by-word drills). */
    public void speakList(final List<String> items, final float rate, final Done done) {
        if (cloud) { cloudSpeak(items, rate, done); return; }
        if (!ready) {
            pending = new Runnable() { public void run() { speakList(items, rate, done); } };
            return;
        }
        if (warnIfMissing()) return;
        tts.stop();
        tts.setSpeechRate(rate * Store.ttsRate(ctx));
        String lastId = null;
        for (int i = 0; i < items.size(); i++) {
            String id = "l" + (seq++);
            tts.speak(items.get(i), TextToSpeech.QUEUE_ADD, new Bundle(), id);
            lastId = "s" + (seq++);
            tts.playSilentUtterance(550, TextToSpeech.QUEUE_ADD, lastId);
        }
        if (done != null && lastId != null) synchronized (callbacks) { callbacks.put(lastId, done); }
    }

    // ------------------------------------------------------------------ cloud

    private void cloudSpeak(final List<String> items, final float rate, final Done done) {
        stopCloud();
        final int my = ++token;
        final float speed = rate * Store.ttsRate(ctx);
        new Thread(new Runnable() {
            public void run() {
                final List<File> files = new ArrayList<File>();
                String err = null;
                for (String t : items) {
                    if (my != token) return;
                    try { files.add(CloudAudio.tts(ctx, t, lang, speed)); }
                    catch (Exception e) { err = e.getMessage() != null ? e.getMessage() : "在线 TTS 失败：" + e; break; }
                }
                final String fe = err;
                main.post(new Runnable() {
                    public void run() {
                        if (my != token) return;
                        if (fe != null) {
                            if (!warned) { warned = true; UI.toast(ctx, fe); }
                            if (done != null) done.done();
                            return;
                        }
                        playChain(files, 0, my, done);
                    }
                });
            }
        }).start();
    }

    private void playChain(final List<File> files, final int idx, final int my, final Done done) {
        if (my != token) return;
        if (idx >= files.size()) { if (done != null) done.done(); return; }
        releasePlayer();
        try {
            player = new MediaPlayer();
            player.setDataSource(files.get(idx).getAbsolutePath());
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    main.postDelayed(new Runnable() {
                        public void run() { playChain(files, idx + 1, my, done); }
                    }, files.size() > 1 ? 450 : 0);
                }
            });
            player.prepare();
            player.start();
        } catch (Exception e) {
            releasePlayer();
            files.get(idx).delete(); // corrupt cache entry
            if (done != null) done.done();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    private void stopCloud() {
        token++;
        releasePlayer();
    }

    // ------------------------------------------------------------------ control

    public boolean isSpeaking() {
        if (cloud) {
            try { return player != null && player.isPlaying(); } catch (Exception e) { return false; }
        }
        return ready && tts != null && tts.isSpeaking();
    }

    public void stop() {
        if (cloud) { stopCloud(); return; }
        pending = null;
        if (ready && tts != null) tts.stop();
        synchronized (callbacks) { callbacks.clear(); }
    }

    public void shutdown() {
        stopCloud();
        try {
            if (tts != null) { tts.stop(); tts.shutdown(); }
        } catch (Exception ignored) { }
        tts = null;
        ready = false;
    }

    /** Unused helper kept for API symmetry. */
    static List<String> one(String s) { return Collections.singletonList(s); }
}
