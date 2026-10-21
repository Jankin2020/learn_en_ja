package com.xyb.voxcoach;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** SpeechRecognizer wrapper returning N-best + confidence + speech duration. Must be used on the main thread. */
public class Rec implements RecognitionListener {

    public interface Cb {
        void onReady();
        void onPartial(String text);
        void onRms(float db);
        void onResult(List<String> candidates, float confidence, long durationMs);
        void onError(String message);
    }

    private final Context ctx;
    private SpeechRecognizer sr;
    private Cb cb;
    private long tBegin, tEnd;
    public boolean listening;

    public Rec(Context c) { ctx = c; }

    public static boolean cloudMode(Context c) { return "cloud".equals(Store.asrMode(c)); }

    public static boolean available(Context c) {
        if (cloudMode(c)) return Store.audioReady(c);
        return SpeechRecognizer.isRecognitionAvailable(c);
    }

    private static final String[] PREFERRED = {"com.google.android.googlequicksearchbox", "com.google.android.tts"};

    /** All installed recognition services as {flattenedComponent, label}. */
    public static List<String[]> services(Context c) {
        List<String[]> out = new ArrayList<String[]>();
        List<ResolveInfo> l = c.getPackageManager().queryIntentServices(new Intent(RecognitionService.SERVICE_INTERFACE), 0);
        if (l == null) return out;
        for (ResolveInfo ri : l) {
            if (ri.serviceInfo == null) continue;
            ComponentName cn = new ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name);
            CharSequence label = ri.loadLabel(c.getPackageManager());
            out.add(new String[]{cn.flattenToString(), (label == null ? ri.serviceInfo.packageName : label.toString()) + "\n" + ri.serviceInfo.packageName});
        }
        return out;
    }

    /** User choice, else a Google service if present, else null (system default). */
    public static ComponentName pickService(Context c) {
        List<String[]> all = services(c);
        String pref = Store.asrService(c);
        if (pref.length() > 0) for (String[] s : all) if (s[0].equals(pref)) return ComponentName.unflattenFromString(pref);
        for (String pkg : PREFERRED) {
            for (String[] s : all) {
                ComponentName cn = ComponentName.unflattenFromString(s[0]);
                if (cn != null && pkg.equals(cn.getPackageName())) return cn;
            }
        }
        return null;
    }

    public void start(String lang, Cb cb) {
        this.cb = cb;
        if (cloudMode(ctx)) { startCloud(lang); return; }
        if (sr == null) {
            ComponentName cn = pickService(ctx);
            sr = cn == null ? SpeechRecognizer.createSpeechRecognizer(ctx) : SpeechRecognizer.createSpeechRecognizer(ctx, cn);
            sr.setRecognitionListener(this);
        }
        String tag = "ja".equals(lang) ? "ja-JP" : ("UK".equals(Store.accent(ctx)) ? "en-GB" : "en-US");
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag);
        i.putExtra("android.speech.extra.ONLY_RETURN_LANGUAGE_PREFERENCE", true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.getPackageName());
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        tBegin = 0;
        tEnd = 0;
        listening = true;
        try {
            sr.startListening(i);
        } catch (Exception e) {
            listening = false;
            destroy();
            if (cb != null) cb.onError("无法启动语音识别：" + e.getMessage());
        }
    }

    public void stop() {
        if (cloudMode(ctx)) { stopRequested = true; return; }
        if (sr != null && listening) sr.stopListening();
    }

    /** Cancels an active session; SpeechRecognizer sends no callback after cancel(), so notify the UI ourselves. */
    public void cancel() {
        boolean was = listening;
        listening = false;
        cloudSession++; // invalidates any running cloud recording/upload
        if (sr != null) { try { sr.cancel(); } catch (Exception ignored) { } }
        if (was && cb != null) cb.onError("");
    }

    public void destroy() {
        cloudSession++;
        listening = false;
        if (sr != null) {
            try { sr.destroy(); } catch (Exception ignored) { }
            sr = null;
        }
    }

    @Override public void onReadyForSpeech(Bundle params) { if (cb != null) cb.onReady(); }
    @Override public void onBeginningOfSpeech() { tBegin = SystemClock.elapsedRealtime(); }
    @Override public void onRmsChanged(float rmsdB) { if (cb != null) cb.onRms(rmsdB); }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { tEnd = SystemClock.elapsedRealtime(); }
    @Override public void onEvent(int eventType, Bundle params) { }

    @Override
    public void onPartialResults(Bundle b) {
        ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (!listening) return;
        if (cb != null && r != null && !r.isEmpty() && r.get(0) != null && r.get(0).length() > 0) cb.onPartial(r.get(0));
    }

    @Override
    public void onResults(Bundle b) {
        if (!listening) return; // stale (after cancel) or duplicate terminal callback
        listening = false;
        ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        float[] conf = b.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
        if (r == null) r = new ArrayList<String>();
        long dur = (tBegin > 0 && tEnd > tBegin) ? tEnd - tBegin : 0;
        if (cb != null) {
            if (r.isEmpty()) cb.onError("没有听清，请靠近麦克风再说一次");
            else cb.onResult(r, conf != null && conf.length > 0 ? conf[0] : 0f, dur);
        }
    }

    @Override
    public void onError(int error) {
        boolean was = listening;
        listening = false;
        String msg;
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH: msg = "没有听清，请再说一次（可以稍微大声、清楚一些）"; break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: msg = "没有检测到声音，点麦克风后请直接开口"; break;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: msg = "语音识别网络错误：请检查网络，或在 Google 设置里下载离线语音包"; break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: msg = "没有麦克风权限，请在系统设置里允许"; break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: msg = "识别器忙，请稍后再试"; destroy(); break;
            case SpeechRecognizer.ERROR_AUDIO: msg = "录音出错，麦克风可能被其他应用占用"; break;
            case SpeechRecognizer.ERROR_CLIENT: msg = "无法连接语音识别服务：请到 设置 → 语音环境 换一个识别服务，或改用在线识别"; destroy(); break; // our own cancel() is filtered by !was below
            default: msg = "语音识别出错（代码 " + error + "）"; destroy();
        }
        if (!was) return; // e.g. ERROR_CLIENT after our cancel(), or Google's spurious error after onResults
        if (cb != null && msg != null) cb.onError(msg);
        else if (cb != null) cb.onError("");
    }

    // ------------------------------------------------------------------ cloud ASR (record WAV + /audio/transcriptions)

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile int cloudSession = 0;
    private volatile boolean stopRequested;
    private static final Object MIC_LOCK = new Object();

    private void startCloud(final String lang) {
        if (!Store.audioReady(ctx)) {
            if (cb != null) cb.onError("在线识别未配置：设置 → 语音环境 → 在线语音");
            return;
        }
        final int my = ++cloudSession;
        stopRequested = false;
        listening = true;
        final Cb callback = cb;
        new Thread(new Runnable() {
            public void run() {
                final int rate = 16000;
                ByteArrayOutputStream pcm = new ByteArrayOutputStream();
                long first = -1, lastVoice = -1;
                boolean started = false;
                String err = null;
                synchronized (MIC_LOCK) { // a previous (cancelled) session may still be releasing the mic
                    if (my != cloudSession) return;
                    AudioRecord ar = null;
                    try {
                        int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                        ar = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT, Math.max(min, rate));
                        if (ar.getState() != AudioRecord.STATE_INITIALIZED) throw new Exception("麦克风初始化失败");
                        ar.startRecording();
                        if (ar.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new Exception("麦克风可能被其他应用占用");
                        post(my, new Runnable() { public void run() { if (callback != null) callback.onReady(); } });
                        short[] buf = new short[rate / 10]; // 100 ms frames
                        long t0 = SystemClock.elapsedRealtime();
                        double floor = -60;
                        int loud = 0;
                        while (my == cloudSession) {
                            int n = ar.read(buf, 0, buf.length);
                            long now = SystemClock.elapsedRealtime() - t0;
                            if (n < 0) throw new Exception("读取麦克风出错（" + n + "）");
                            if (n == 0) {
                                if (stopRequested || now > 20000) break;
                                continue;
                            }
                            double sum = 0;
                            for (int i = 0; i < n; i++) { sum += (double) buf[i] * buf[i]; pcm.write(buf[i] & 0xff); pcm.write((buf[i] >> 8) & 0xff); }
                            double db = 10 * Math.log10(sum / n / (32768.0 * 32768.0) + 1e-12);
                            if (now < 400) floor = Math.max(floor, db); // estimate background noise
                            double thr = Math.max(-45, Math.min(-20, floor + 10));
                            final float level = (float) ((db + 55) / 4.0);
                            post(my, new Runnable() { public void run() { if (callback != null) callback.onRms(level); } });
                            if (db > thr) {
                                loud++;
                                if (loud >= 2 && !started) { started = true; first = Math.max(0, now - 200); }
                                if (started) lastVoice = now;
                            } else loud = 0;
                            if (stopRequested) break;
                            if (started && now - lastVoice > 1400) break;
                            if (!started && now > 7000) { err = "没有检测到声音，点麦克风后请直接开口"; break; }
                            if (now > 20000) break;
                        }
                    } catch (Exception e) {
                        err = "录音失败：" + e.getMessage();
                    } finally {
                        if (ar != null) { try { ar.stop(); } catch (Exception ignored) { } try { ar.release(); } catch (Exception ignored) { } }
                    }
                }
                if (my != cloudSession) return;
                if (err == null && !started) err = "没有检测到声音";
                if (err != null) { final String fe = err; finishErr(my, callback, fe); return; }
                post(my, new Runnable() { public void run() { if (callback != null) callback.onPartial("（识别中…）"); } });
                final long dur = Math.max(0, lastVoice - first);
                try {
                    final String text = CloudAudio.transcribe(ctx, CloudAudio.wav(pcm.toByteArray(), rate), lang);
                    post(my, new Runnable() {
                        public void run() {
                            listening = false;
                            if (callback == null) return;
                            if (text.length() == 0) callback.onError("没有听清，请再说一次");
                            else { List<String> l = new ArrayList<String>(); l.add(text); callback.onResult(l, 0f, dur); }
                        }
                    });
                } catch (Exception e) {
                    finishErr(my, callback, e.getMessage());
                }
            }
        }).start();
    }

    private void finishErr(final int my, final Cb callback, final String msg) {
        post(my, new Runnable() {
            public void run() {
                listening = false;
                if (callback != null) callback.onError(msg == null ? "识别失败" : msg);
            }
        });
    }

    private void post(final int my, final Runnable r) {
        main.post(new Runnable() { public void run() { if (my == cloudSession) r.run(); } });
    }
}
