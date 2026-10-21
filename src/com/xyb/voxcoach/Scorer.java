package com.xyb.voxcoach;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java pronunciation / dictation scorer (no Android dependencies, unit-testable on JVM).
 * Aligns what the recognizer heard against the target sentence token-by-token
 * (words for English, characters for Japanese) and produces Voca-style sub-scores.
 */
public final class Scorer {

    public static final int OK = 0, NEAR = 1, BAD = 2, MISSING = 3, SKIP = 4;

    public static final class Result {
        public final List<String> display = new ArrayList<String>();
        public final List<String> norm = new ArrayList<String>();
        public final List<Integer> status = new ArrayList<Integer>();
        public final List<String> heardFor = new ArrayList<String>();
        public String heard = "";
        public int accuracy, completeness, fluency, overall;
        public int extra;
        public boolean byReading;
        public boolean japanese;

        public int scoredCount() {
            int n = 0;
            for (int s : status) if (s != SKIP) n++;
            return n;
        }

        /** normalized tokens that were not pronounced correctly (for weak-word stats). */
        public List<String> problemTokens() {
            List<String> out = new ArrayList<String>();
            for (int i = 0; i < status.size(); i++) {
                int s = status.get(i);
                if (s == BAD || s == MISSING || s == NEAR) out.add(norm.get(i));
            }
            return out;
        }

        public List<String> goodTokens() {
            List<String> out = new ArrayList<String>();
            for (int i = 0; i < status.size(); i++) if (status.get(i) == OK) out.add(norm.get(i));
            return out;
        }
    }

    // ------------------------------------------------------------------ public API

    /**
     * Score speech. candidates = recognizer N-best (best first), conf = confidence of first (<=0 unknown),
     * durationMs = speech duration (<=0 unknown). reading = hiragana reading for Japanese (may be null).
     */
    public static Result scoreSpeech(String target, String reading, Map<String, String> ruby, boolean ja,
                                     List<String> candidates, float conf, long durationMs) {
        Result best = null;
        double bestKey = -1e9;
        if (candidates == null || candidates.isEmpty()) {
            candidates = new ArrayList<String>();
            candidates.add("");
        }
        int maxRank = Math.min(3, candidates.size());
        for (int rank = 0; rank < maxRank; rank++) {
            String hyp = candidates.get(rank);
            Result r = scoreOne(target, ja, hyp, false);
            finishSpeech(r, rank == 0 ? conf : 0f, durationMs, rank);
            if (r.overall > bestKey) { bestKey = r.overall; best = r; }
            if (ja && reading != null && reading.length() > 0) {
                // reading mode: map the sentence's own kanji words in the hypothesis to kana -> finer, kana-level feedback
                Result r2 = scoreOne(reading, true, kanaize(hyp, ruby), true);
                r2.heard = hyp == null ? "" : hyp.trim();
                finishSpeech(r2, rank == 0 ? conf : 0f, durationMs, rank);
                if (r2.overall >= bestKey - 2) { bestKey = Math.max(bestKey, r2.overall); best = r2; }
            }
        }
        return best;
    }

    /** Score typed dictation answer. */
    public static Result scoreTyped(String target, String reading, Map<String, String> ruby, boolean ja, String answer) {
        Result r = scoreOne(target, ja, answer, false);
        finishTyped(r);
        if (ja && reading != null && reading.length() > 0) {
            Result r2 = scoreOne(reading, true, kanaize(answer, ruby), true);
            r2.heard = answer == null ? "" : answer.trim();
            finishTyped(r2);
            if (r2.overall > r.overall) r = r2;
        }
        return r;
    }

    /** Replace known kanji words (from the target's ruby dictionary) with their kana reading. */
    static String kanaize(String hyp, Map<String, String> ruby) {
        String h = jaPrepare(hyp == null ? "" : hyp);
        if (ruby == null || ruby.isEmpty()) return h;
        List<String> keys = new ArrayList<String>(ruby.keySet());
        java.util.Collections.sort(keys, new java.util.Comparator<String>() {
            public int compare(String a, String b) { return b.length() - a.length(); }
        });
        for (String k : keys) h = h.replace(k, ruby.get(k));
        return h;
    }

    /**
     * Parse "{切手|きって}を{三枚|さんまい}ください。" into surface text, full kana reading and a surface->reading map.
     */
    public static void parseRuby(String marked, StringBuilder surface, StringBuilder reading, Map<String, String> dict) {
        int i = 0;
        while (i < marked.length()) {
            char c = marked.charAt(i);
            if (c == '{') {
                int bar = marked.indexOf('|', i);
                int end = marked.indexOf('}', i);
                if (bar > i && end > bar) {
                    String sf = marked.substring(i + 1, bar);
                    String rd = marked.substring(bar + 1, end);
                    surface.append(sf);
                    reading.append(rd);
                    if (dict != null) dict.put(sf, rd);
                    i = end + 1;
                    continue;
                }
            }
            surface.append(c);
            reading.append(toHiragana(String.valueOf(c)));
            i++;
        }
    }

    // ------------------------------------------------------------------ core

    static Result scoreOne(String target, boolean ja, String hyp, boolean byReading) {
        Result r = new Result();
        r.japanese = ja;
        r.byReading = byReading;
        r.heard = hyp == null ? "" : hyp.trim();
        List<String> refNorm = new ArrayList<String>();
        List<Integer> refIdx = new ArrayList<Integer>();
        if (ja) {
            String t = jaPrepare(target);
            for (int i = 0; i < t.length(); i++) {
                String ch = t.substring(i, i + 1);
                String n = jaNormChar(ch);
                r.display.add(ch);
                r.norm.add(n);
                r.heardFor.add("");
                if (n.length() == 0) r.status.add(SKIP);
                else { r.status.add(OK); refNorm.add(n); refIdx.add(i); }
            }
        } else {
            String[] parts = target.trim().split("\\s+");
            for (String p : parts) {
                if (p.length() == 0) continue;
                String n = enNorm(p);
                r.display.add(p);
                r.norm.add(n);
                r.heardFor.add("");
                if (n.length() == 0) r.status.add(SKIP);
                else { r.status.add(OK); refNorm.add(n); refIdx.add(r.display.size() - 1); }
            }
        }
        List<String> hypTok = ja ? jaTokens(r.heard) : enTokens(r.heard, refNorm);
        int[] st = new int[refNorm.size()];
        String[] hf = new String[refNorm.size()];
        r.extra = align(refNorm, hypTok, ja, st, hf);
        for (int k = 0; k < st.length; k++) {
            r.status.set(refIdx.get(k), st[k]);
            r.heardFor.set(refIdx.get(k), hf[k] == null ? "" : hf[k]);
        }
        int n = refNorm.size();
        int ok = 0, near = 0, miss = 0;
        for (int s : st) {
            if (s == OK) ok++;
            else if (s == NEAR) near++;
            else if (s == MISSING) miss++;
        }
        if (n == 0) { r.accuracy = 0; r.completeness = 0; }
        else {
            // slightly convex curve: a single wrong sound in a short sentence should cost noticeably
            r.accuracy = (int) Math.round(100.0 * Math.pow((ok + 0.5 * near) / n, 1.6));
            r.completeness = (int) Math.round(100.0 * (n - miss) / n);
        }
        return r;
    }

    static void finishSpeech(Result r, float conf, long durationMs, int rank) {
        int n = r.scoredCount();
        int flu;
        if (n <= 3 || durationMs <= 0) {
            flu = Math.max(r.accuracy, 60);
            if (r.accuracy == 0) flu = 0;
        } else {
            double expected = r.japanese ? (r.byReading ? 4.5 : 3.2) : 2.0; // tokens per second, learner-friendly
            long speech = Math.max(300, durationMs - 600); // end-of-speech detection adds trailing silence
            double rate = n / (speech / 1000.0);
            double ratio = rate / expected;
            flu = ratio >= 1 ? 100 : (int) Math.round(100 - (1 - ratio) * 90);
            if (flu < 30) flu = 30;
            // fluency can't be good if half the words are wrong
            flu = Math.min(flu, r.completeness + 15);
        }
        flu -= Math.min(24, r.extra * 6);
        r.fluency = clamp(flu);
        double overall = 0.6 * r.accuracy + 0.2 * r.completeness + 0.2 * r.fluency;
        if (conf > 0f && conf < 0.5f) overall -= (0.5f - conf) * 20;
        overall -= rank * 6;
        r.overall = clamp((int) Math.round(overall));
    }

    static void finishTyped(Result r) {
        r.fluency = r.accuracy;
        double overall = 0.8 * r.accuracy + 0.2 * r.completeness - Math.min(20, r.extra * 4);
        r.overall = clamp((int) Math.round(overall));
    }

    /** Levenshtein alignment; returns number of inserted hyp tokens. */
    static int align(List<String> ref, List<String> hyp, boolean ja, int[] st, String[] heardFor) {
        int n = ref.size(), m = hyp.size();
        double[][] d = new double[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j * 0.9;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                double sub = d[i - 1][j - 1] + subCost(ref.get(i - 1), hyp.get(j - 1), ja);
                double del = d[i - 1][j] + 1.0;
                double ins = d[i][j - 1] + 0.9;
                d[i][j] = Math.min(sub, Math.min(del, ins));
            }
        }
        int i = n, j = m, extra = 0;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                double c = subCost(ref.get(i - 1), hyp.get(j - 1), ja);
                if (Math.abs(d[i][j] - (d[i - 1][j - 1] + c)) < 1e-9) {
                    st[i - 1] = c == 0 ? OK : (c < 1 ? NEAR : BAD);
                    heardFor[i - 1] = hyp.get(j - 1);
                    i--; j--;
                    continue;
                }
            }
            if (i > 0 && Math.abs(d[i][j] - (d[i - 1][j] + 1.0)) < 1e-9) {
                st[i - 1] = MISSING;
                heardFor[i - 1] = "";
                i--;
            } else {
                extra++;
                j--;
            }
        }
        return extra;
    }

    static double subCost(String a, String b, boolean ja) {
        if (a.equals(b)) return 0;
        if (ja) return jaNear(a, b) ? 0.6 : 1;
        int la = a.length(), lb = b.length();
        int mx = Math.max(la, lb);
        if (mx < 3) return 1;
        double sim = 1.0 - (double) lev(a, b) / mx;
        if (sim >= 0.5) return 0.6;
        return 1;
    }

    static int lev(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(prev[j - 1] + c, Math.min(prev[j] + 1, cur[j - 1] + 1));
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    static int clamp(int v) { return v < 0 ? 0 : (v > 100 ? 100 : v); }

    // ------------------------------------------------------------------ issue diagnosis

    /** Heuristic pronunciation-issue categories for the problem tokens in a result. */
    public static List<String> issues(Result r) {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < r.status.size(); i++) {
            int s = r.status.get(i);
            if (s == OK || s == SKIP) continue;
            String t = r.norm.get(i), h = r.heardFor.get(i);
            String c = r.japanese ? jaIssue(t, h, s, r.byReading) : enIssue(t, h, s);
            if (c != null && !out.contains(c)) out.add(c);
        }
        return out;
    }

    static String jaIssue(String t, String h, int s, boolean byReading) {
        if (!byReading) {
            char c = t.charAt(0);
            if ((c >= '\u4E00' && c <= '\u9FFF')) return "汉字词读音";
        }
        if (t.equals("っ") || h.equals("っ")) return "促音";
        if (t.equals("ん") || h.equals("ん")) return "拨音 ん";
        if ("ゃゅょ".contains(t) || (h.length() > 0 && "ゃゅょ".contains(h))) return "拗音";
        if (t.equals("ー") || ("あいうえお".contains(t) && s == MISSING)) return "长音";
        if (s == NEAR && h.length() > 0 && jaBase(t).equals(jaBase(h))) return "清浊音";
        if (s == MISSING) return "漏读";
        return "假名发音";
    }

    static String enIssue(String t, String h, int s) {
        if (s == MISSING || h.length() == 0) return "漏读单词";
        if (t.startsWith("th") && !h.startsWith("th") && h.length() > 0 && "stdf".indexOf(h.charAt(0)) >= 0) return "TH 音";
        if (t.length() == h.length()) {
            int diff = -1, cnt = 0;
            for (int k = 0; k < t.length(); k++) if (t.charAt(k) != h.charAt(k)) { cnt++; diff = k; }
            if (cnt == 1) {
                String pair = "" + t.charAt(diff) + h.charAt(diff);
                if (pair.equals("lr") || pair.equals("rl")) return "L/R";
                if (pair.equals("vb") || pair.equals("bv") || pair.equals("vw") || pair.equals("wv")) return "V/B/W";
                if (pair.equals("nl") || pair.equals("ln")) return "N/L";
                if (pair.equals("fv") || pair.equals("vf")) return "F/V";
                if ("aeiou".indexOf(t.charAt(diff)) >= 0 && "aeiou".indexOf(h.charAt(diff)) >= 0) return "元音";
            }
        }
        if (t.contains("th") && !h.contains("th")) return "TH 音";
        if (t.startsWith(h) && t.length() > h.length()) return "词尾辅音";
        if (h.startsWith(t) && h.length() > t.length()) return "多加尾音";
        return "单词发音";
    }

    // ------------------------------------------------------------------ English

    private static final Map<String, String> CONTRACTIONS = new HashMap<String, String>();
    static {
        String[][] c = {
                {"i'm", "i am"}, {"you're", "you are"}, {"we're", "we are"}, {"they're", "they are"},
                {"it's", "it is"}, {"that's", "that is"}, {"what's", "what is"}, {"there's", "there is"},
                {"he's", "he is"}, {"she's", "she is"}, {"where's", "where is"}, {"how's", "how is"},
                {"don't", "do not"}, {"doesn't", "does not"}, {"didn't", "did not"}, {"can't", "cannot"},
                {"won't", "will not"}, {"isn't", "is not"}, {"aren't", "are not"}, {"wasn't", "was not"},
                {"weren't", "were not"}, {"haven't", "have not"}, {"hasn't", "has not"},
                {"couldn't", "could not"}, {"wouldn't", "would not"}, {"shouldn't", "should not"},
                {"i'll", "i will"}, {"you'll", "you will"}, {"we'll", "we will"}, {"it'll", "it will"},
                {"i'd", "i would"}, {"you'd", "you would"}, {"i've", "i have"}, {"we've", "we have"},
                {"you've", "you have"}, {"let's", "let us"}, {"they'll", "they will"}
        };
        for (String[] p : c) CONTRACTIONS.put(p[0], p[1]);
    }

    private static final String[] ONES = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight",
            "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
            "eighteen", "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy",
            "eighty", "ninety"};

    static String enNorm(String w) {
        String s = w.toLowerCase().replace('’', '\'').replace('‘', '\'');
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '\'') b.append(ch);
        }
        String r = b.toString();
        while (r.startsWith("'")) r = r.substring(1);
        while (r.endsWith("'")) r = r.substring(0, r.length() - 1);
        if (r.equals("ok")) r = "okay";
        return r;
    }

    static void numWords(int n, List<String> out) {
        if (n < 20) { out.add(ONES[n]); return; }
        if (n < 100) {
            out.add(TENS[n / 10]);
            if (n % 10 != 0) out.add(ONES[n % 10]);
            return;
        }
        if (n < 1000) {
            out.add(ONES[n / 100]);
            out.add("hundred");
            if (n % 100 != 0) numWords(n % 100, out);
            return;
        }
        if (n < 1000000) {
            numWords(n / 1000, out);
            out.add("thousand");
            if (n % 1000 != 0) numWords(n % 1000, out);
            return;
        }
        out.add(String.valueOf(n));
    }

    static List<String> enTokens(String text, List<String> ref) {
        List<String> raw = new ArrayList<String>();
        if (text == null) return raw;
        java.util.Set<String> refSet = new java.util.HashSet<String>(ref);
        String t = text.replace(":", " ").replace("-", " ").replace("%", " percent").replace("&", " and ");
        for (String p : t.trim().split("\\s+")) {
            if (p.length() == 0) continue;
            boolean dollar = p.startsWith("$");
            String n = enNorm(p);
            if (n.length() == 0) continue;
            if (n.matches("\\d{1,6}") && !refSet.contains(n)) {
                numWords(Integer.parseInt(n), raw);
                if (dollar) raw.add("dollars");
            } else if (n.matches("\\d+(st|nd|rd|th)")) {
                raw.add(n); // ordinal digits: leave
            } else {
                raw.add(n);
            }
        }
        // contraction harmonisation: match the form used in the target
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < raw.size(); i++) {
            String w = raw.get(i);
            if (i + 1 < raw.size()) {
                String two = w + " " + raw.get(i + 1);
                String hit = null;
                for (Map.Entry<String, String> e : CONTRACTIONS.entrySet()) {
                    if (e.getValue().equals(two) && refSet.contains(e.getKey())) { hit = e.getKey(); break; }
                }
                if (hit == null && two.equals("can not") && refSet.contains("can't")) hit = "can't";
                if (hit != null) { out.add(hit); i++; continue; }
            }
            String exp = CONTRACTIONS.get(w);
            if (exp != null && !refSet.contains(w)) {
                for (String x : exp.split(" ")) out.add(x);
                continue;
            }
            if (w.equals("cannot") && refSet.contains("can't")) { out.add("can't"); continue; }
            out.add(w);
        }
        return out;
    }

    // ------------------------------------------------------------------ Japanese

    static String jaPrepare(String s) {
        if (s == null) return "";
        // full-width alnum -> ascii, digits -> kanji numerals
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '０' && c <= '９') c = (char) (c - '０' + '0');
            else if (c >= 'Ａ' && c <= 'Ｚ') c = (char) (c - 'Ａ' + 'a');
            else if (c >= 'ａ' && c <= 'ｚ') c = (char) (c - 'ａ' + 'a');
            b.append(c);
        }
        String t = b.toString();
        StringBuilder o = new StringBuilder();
        int i = 0;
        while (i < t.length()) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') {
                int j = i;
                while (j < t.length() && t.charAt(j) >= '0' && t.charAt(j) <= '9') j++;
                String digits = t.substring(i, j);
                if (digits.length() <= 4) o.append(kanjiNum(Integer.parseInt(digits)));
                else o.append(digits);
                i = j;
            } else {
                o.append(c);
                i++;
            }
        }
        return o.toString();
    }

    private static final String KD = "〇一二三四五六七八九";

    static String kanjiNum(int n) {
        if (n == 0) return "〇";
        StringBuilder b = new StringBuilder();
        int[] units = {1000, 100, 10, 1};
        String[] names = {"千", "百", "十", ""};
        for (int k = 0; k < 4; k++) {
            int d = n / units[k];
            n %= units[k];
            if (d == 0) continue;
            if (!(d == 1 && k < 3)) b.append(KD.charAt(d));
            b.append(names[k]);
        }
        return b.toString();
    }

    static String toHiragana(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'ァ' && c <= 'ヶ') c = (char) (c - 0x60);
            b.append(c);
        }
        return b.toString();
    }

    static boolean isJaContent(char c) {
        return (c >= 'ぁ' && c <= 'ゖ') || (c >= 'ァ' && c <= 'ヺ') || c == 'ー'
                || (c >= '一' && c <= '鿿') || (c >= '㐀' && c <= '䶿') || c == '々'
                || c == '〇' || Character.isLetterOrDigit(c);
    }

    static String jaNormChar(String ch) {
        char c = ch.charAt(0);
        if (!isJaContent(c)) return "";
        if (Character.isUpperCase(c)) c = Character.toLowerCase(c);
        return toHiragana(String.valueOf(c));
    }

    static List<String> jaTokens(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        String t = jaPrepare(text);
        for (int i = 0; i < t.length(); i++) {
            String n = jaNormChar(t.substring(i, i + 1));
            if (n.length() > 0) out.add(n);
        }
        return out;
    }

    private static final String SMALL = "ぁぃぅぇぉっゃゅょゎ";
    private static final String LARGE = "あいうえおつやゆよわ";

    static String jaBase(String s) {
        String d = Normalizer.normalize(s, Normalizer.Form.NFD).replace("゙", "").replace("゚", "");
        int k = SMALL.indexOf(d.charAt(0));
        if (k >= 0) d = String.valueOf(LARGE.charAt(k));
        return d;
    }

    static boolean jaNear(String a, String b) {
        if (a.length() == 0 || b.length() == 0) return false;
        if (jaBase(a).equals(jaBase(b))) return true; // が/か, ぱ/は, っ/つ, ゃ/や
        // long vowel mark vs vowel
        String vowels = "あいうえおー";
        return vowels.indexOf(a.charAt(0)) >= 0 && vowels.indexOf(b.charAt(0)) >= 0;
    }
}
