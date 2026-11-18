package com.xyb.voxcoach;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Speech output with four sources (Store.ttsMode):
 *  - bundled (default): built-in offline clips (AudioBank); anything without a clip goes to the fallback (Store.ttsFallback)
 *  - system:  Android TextToSpeech, preferring Google's engine
 *  - cloud:   OpenAI-compatible /audio/speech (SiliconFlow, OpenAI ...), cached as mp3
 *  - azure:   Microsoft Azure neural TTS, cached as mp3
 * Items of a list are played one by one; each item independently uses the best available source.
 */
public class Speaker implements TextToSpeech.OnInitListener {

    public interface Done { void done(); }

    public static final String GOOGLE_TTS = "com.google.android.tts";

    private final Context ctx;
    private final String lang;
    private final boolean useBank;
    private final String cloudProvider; // null = no cloud
    private TextToSpeech tts;
    private boolean ready;
    public boolean missingVoice;
    private boolean warned;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Runnable> callbacks = new HashMap<String, Runnable>();
    private Runnable pending;
    private int seq = 0;

    private MediaPlayer player;
    private volatile int token = 0;

    public Speaker(Context c, String lang) {
        this.ctx = c.getApplicationContext();
        this.lang = lang;
        String mode = Store.ttsMode(ctx);
        this.useBank = "bundled".equals(mode);
        this.cloudProvider = Store.cloudProvider(ctx);
        if (cloudProvider == null) tts = new TextToSpeech(ctx, this, engine(ctx));
        else ready = true;
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
            ready = true;
            flushPending();
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
        flushPending();
    }

    private void flushPending() {
        if (pending != null) {
            final Runnable p = pending;
            pending = null;
            main.post(p);
        }
    }

    private void fire(String id) {
        final Runnable d;
        synchronized (callbacks) { d = callbacks.remove(id); }
        if (d != null) main.post(d);
    }

    public boolean isReady() { return ready; }

    /** True if this text will play from the built-in offline recordings. */
    public boolean hasRecording(String text) { return useBank && AudioBank.has(ctx, lang, text); }

    // ------------------------------------------------------------------ public API

    public void speak(String text, float rate, Done done) {
        List<String> l = new ArrayList<String>();
        l.add(text);
        speakList(l, rate, done, 0);
    }

    /** Speak several pieces with short pauses in between (used for word-by-word drills). */
    public void speakList(List<String> items, float rate, Done done) {
        speakList(items, rate, done, 550);
    }

    private void speakList(List<String> items, float rate, Done done, int gapMs) {
        stop();
        final int my = ++token;
        chain(new ArrayList<String>(items), 0, rate, my, done, gapMs);
    }

    private void chain(final List<String> items, final int i, final float rate, final int my, final Done done, final int gapMs) {
        if (my != token) return;
        if (i >= items.size()) { if (done != null) done.done(); return; }
        final Runnable next = new Runnable() {
            public void run() {
                if (my != token) return;
                if (i + 1 < items.size()) main.postDelayed(new Runnable() {
                    public void run() { chain(items, i + 1, rate, my, done, gapMs); }
                }, gapMs);
                else chain(items, i + 1, rate, my, done, gapMs);
            }
        };
        final String text = items.get(i);
        float speed = rate * Store.ttsRate(ctx);

        // 1) built-in recording
        if (useBank && AudioBank.has(ctx, lang, text)) {
            if (playBank(text, speed, my, next)) return;
        }
        // 2) cloud provider
        if (cloudProvider != null) {
            cloudItem(text, speed, my, next);
            return;
        }
        // 3) system engine
        if (!ready) {
            pending = new Runnable() { public void run() { chain(items, i, rate, my, done, gapMs); } };
            return;
        }
        if (tts == null || missingVoice) {
            warnMissing();
            next.run();
            return;
        }
        tts.setSpeechRate(speed);
        String id = "u" + (seq++);
        synchronized (callbacks) { callbacks.put(id, next); }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), id);
    }

    private void warnMissing() {
        if (warned) return;
        warned = true;
        String l = "ja".equals(lang) ? "日语" : "英语";
        UI.toast(ctx, useBank
                ? "这句没有内置录音，当前手机的语音引擎也不支持" + l + "：可在 设置 → 语音环境 里选择在线语音或安装 Google 语音服务"
                : "当前语音引擎不支持" + l + "：请到 设置 → 语音环境 选择「内置离线音」或其他引擎");
    }

    // ------------------------------------------------------------------ media playback (bundled + cloud)

    private boolean playBank(String text, float speed, final int my, final Runnable next) {
        releasePlayer();
        try {
            player = new MediaPlayer();
            if (!AudioBank.setSource(ctx, player, lang, text)) { releasePlayer(); return false; }
            startPlayer(speed, my, next);
            return true;
        } catch (Exception e) {
            releasePlayer();
            return false;
        }
    }

    private void startPlayer(float speed, final int my, final Runnable next) throws Exception {
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mp) { if (my == token) next.run(); }
        });
        player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            public boolean onError(MediaPlayer mp, int what, int extra) { if (my == token) next.run(); return true; }
        });
        player.prepare();
        if (Math.abs(speed - 1f) > 0.02f) {
            try {
                PlaybackParams pp = new PlaybackParams();
                pp.setSpeed(Math.max(0.5f, Math.min(2f, speed)));
                pp.setPitch(1f);
                player.setPlaybackParams(pp);
            } catch (Exception ignored) { }
        }
        player.start();
    }

    private void cloudItem(final String text, final float speed, final int my, final Runnable next) {
        new Thread(new Runnable() {
            public void run() {
                File f = null;
                String err = null;
                try { f = CloudAudio.tts(ctx, text, lang, speed); }
                catch (Exception e) { err = e.getMessage() == null ? e.toString() : e.getMessage(); }
                final File ff = f;
                final String fe = err;
                main.post(new Runnable() {
                    public void run() {
                        if (my != token) return;
                        if (fe != null || ff == null) {
                            if (!warned) { warned = true; UI.toast(ctx, fe); }
                            next.run();
                            return;
                        }
                        releasePlayer();
                        try {
                            player = new MediaPlayer();
                            player.setDataSource(ff.getAbsolutePath());
                            startPlayer(1f, my, next); // speed already applied by the provider
                        } catch (Exception e) {
                            releasePlayer();
                            ff.delete(); // corrupt cache entry
                            next.run();
                        }
                    }
                });
            }
        }).start();
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    // ------------------------------------------------------------------ control

    public boolean isSpeaking() {
        try { if (player != null && player.isPlaying()) return true; } catch (Exception ignored) { }
        return ready && tts != null && tts.isSpeaking();
    }

    public void stop() {
        token++;
        pending = null;
        releasePlayer();
        if (ready && tts != null) { try { tts.stop(); } catch (Exception ignored) { } }
        synchronized (callbacks) { callbacks.clear(); }
    }

    public void shutdown() {
        stop();
        try {
            if (tts != null) tts.shutdown();
        } catch (Exception ignored) { }
        tts = null;
        ready = false;
    }
}
