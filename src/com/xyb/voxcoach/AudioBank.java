package com.xyb.voxcoach;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Built-in offline voice clips (pre-generated with the open-source Kokoro model, see tools/voicegen),
 * plus clips downloaded together with content packs.
 *   assets/voice/<md5(lang|text)>.mp3   and   files/voice/<md5(lang|text)>.mp3
 */
public final class AudioBank {

    private static Set<String> assetKeys;

    public static String key(String lang, String text) {
        return CloudAudio.md5(lang + "|" + text.trim());
    }

    private static synchronized Set<String> assets(Context c) {
        if (assetKeys == null) {
            assetKeys = new HashSet<String>();
            try {
                String[] names = c.getAssets().list("voice");
                if (names != null) for (String n : names) if (n.endsWith(".mp3")) assetKeys.add(n.substring(0, n.length() - 4));
            } catch (Exception ignored) { }
        }
        return assetKeys;
    }

    public static int assetCount(Context c) { return assets(c).size(); }

    public static File dlDir(Context c) {
        File d = new File(c.getFilesDir(), "voice");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** English clips are recorded with a US voice: skip them when the learner chose a British accent. */
    private static boolean usable(Context c, String lang) {
        return !"en".equals(lang) || !"UK".equals(Store.accent(c));
    }

    public static boolean has(Context c, String lang, String text) {
        if (text == null || text.trim().length() == 0 || !usable(c, lang)) return false;
        String k = key(lang, text);
        return assets(c).contains(k) || new File(dlDir(c), k + ".mp3").exists();
    }

    /** Sets the clip as the player's data source. Returns false if there is no clip. */
    public static boolean setSource(Context c, MediaPlayer mp, String lang, String text) throws Exception {
        String k = key(lang, text);
        File f = new File(dlDir(c), k + ".mp3");
        if (f.exists() && f.length() > 0) {
            mp.setDataSource(f.getAbsolutePath());
            return true;
        }
        if (assets(c).contains(k)) {
            AssetFileDescriptor fd = c.getAssets().openFd("voice/" + k + ".mp3");
            mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            fd.close();
            return true;
        }
        return false;
    }
}
