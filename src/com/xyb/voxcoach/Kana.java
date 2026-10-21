package com.xyb.voxcoach;

import java.util.ArrayList;
import java.util.List;

/** Gojūon data: hiragana (katakana derived), romaji, example words (ruby markup) and look-alike groups. */
public final class Kana {

    public static final class K {
        public final String h, romaji, group, word, wordZh;
        public final int row, col;

        K(String h, String romaji, String group, int row, int col, String word, String wordZh) {
            this.h = h; this.romaji = romaji; this.group = group; this.row = row; this.col = col; this.word = word; this.wordZh = wordZh;
        }

        public String k() { return toKata(h); }
        public String show(boolean kata) { return kata ? k() : h; }
    }

    public static final String SEION = "清音", DAKUON = "浊音", YOUON = "拗音";

    // {kana, romaji, example word (ruby), zh} ; "" = empty grid cell
    private static final String[][][] SEION_ROWS = {
            {{"あ", "a", "{朝|あさ}", "早上"}, {"い", "i", "{犬|いぬ}", "狗"}, {"う", "u", "{海|うみ}", "大海"}, {"え", "e", "{駅|えき}", "车站"}, {"お", "o", "お{茶|ちゃ}", "茶"}},
            {{"か", "ka", "{傘|かさ}", "伞"}, {"き", "ki", "{北|きた}", "北"}, {"く", "ku", "{靴|くつ}", "鞋"}, {"け", "ke", "{今朝|けさ}", "今早"}, {"こ", "ko", "{声|こえ}", "声音"}},
            {{"さ", "sa", "{魚|さかな}", "鱼"}, {"し", "shi", "{塩|しお}", "盐"}, {"す", "su", "{寿司|すし}", "寿司"}, {"せ", "se", "{世界|せかい}", "世界"}, {"そ", "so", "{空|そら}", "天空"}},
            {{"た", "ta", "{卵|たまご}", "鸡蛋"}, {"ち", "chi", "{地図|ちず}", "地图"}, {"つ", "tsu", "{机|つくえ}", "桌子"}, {"て", "te", "{手紙|てがみ}", "信"}, {"と", "to", "{時計|とけい}", "钟表"}},
            {{"な", "na", "{夏|なつ}", "夏天"}, {"に", "ni", "{肉|にく}", "肉"}, {"ぬ", "nu", "{布|ぬの}", "布"}, {"ね", "ne", "{猫|ねこ}", "猫"}, {"の", "no", "{飲|の}み{物|もの}", "饮料"}},
            {{"は", "ha", "{花|はな}", "花"}, {"ひ", "hi", "{人|ひと}", "人"}, {"ふ", "fu", "{船|ふね}", "船"}, {"へ", "he", "{部屋|へや}", "房间"}, {"ほ", "ho", "{星|ほし}", "星星"}},
            {{"ま", "ma", "{窓|まど}", "窗户"}, {"み", "mi", "{耳|みみ}", "耳朵"}, {"む", "mu", "{虫|むし}", "虫子"}, {"め", "me", "{眼鏡|めがね}", "眼镜"}, {"も", "mo", "{森|もり}", "森林"}},
            {{"や", "ya", "{山|やま}", "山"}, {""}, {"ゆ", "yu", "{雪|ゆき}", "雪"}, {""}, {"よ", "yo", "{夜|よる}", "夜晚"}},
            {{"ら", "ra", "{来年|らいねん}", "明年"}, {"り", "ri", "りんご", "苹果"}, {"る", "ru", "{留守|るす}", "不在家"}, {"れ", "re", "{歴史|れきし}", "历史"}, {"ろ", "ro", "{廊下|ろうか}", "走廊"}},
            {{"わ", "wa", "{私|わたし}", "我"}, {""}, {""}, {""}, {"を", "wo", "{本|ほん}を{読|よ}む", "读书"}},
            {{"ん", "n", "{先生|せんせい}", "老师"}, {""}, {""}, {""}, {""}},
    };

    private static final String[][][] DAKUON_ROWS = {
            {{"が", "ga", "{学校|がっこう}", "学校"}, {"ぎ", "gi", "{銀行|ぎんこう}", "银行"}, {"ぐ", "gu", "{家具|かぐ}", "家具"}, {"げ", "ge", "{元気|げんき}", "精神"}, {"ご", "go", "ご{飯|はん}", "米饭"}},
            {{"ざ", "za", "{雑誌|ざっし}", "杂志"}, {"じ", "ji", "{時間|じかん}", "时间"}, {"ず", "zu", "{涼|すず}しい", "凉快"}, {"ぜ", "ze", "{全部|ぜんぶ}", "全部"}, {"ぞ", "zo", "{象|ぞう}", "大象"}},
            {{"だ", "da", "{大学|だいがく}", "大学"}, {"ぢ", "ji (di)", "{鼻血|はなぢ}", "鼻血"}, {"づ", "zu (du)", "{続|つづ}く", "继续"}, {"で", "de", "{電話|でんわ}", "电话"}, {"ど", "do", "{動物|どうぶつ}", "动物"}},
            {{"ば", "ba", "{晩|ばん}ご{飯|はん}", "晚饭"}, {"び", "bi", "{美術館|びじゅつかん}", "美术馆"}, {"ぶ", "bu", "ぶどう", "葡萄"}, {"べ", "be", "{勉強|べんきょう}", "学习"}, {"ぼ", "bo", "{帽子|ぼうし}", "帽子"}},
            {{"ぱ", "pa", "パン", "面包"}, {"ぴ", "pi", "ピアノ", "钢琴"}, {"ぷ", "pu", "プール", "游泳池"}, {"ぺ", "pe", "ペン", "笔"}, {"ぽ", "po", "ポスト", "邮筒"}},
    };

    private static final String[][][] YOUON_ROWS = {
            {{"きゃ", "kya", "{客|きゃく}", "客人"}, {"きゅ", "kyu", "きゅうり", "黄瓜"}, {"きょ", "kyo", "{今日|きょう}", "今天"}},
            {{"しゃ", "sha", "{写真|しゃしん}", "照片"}, {"しゅ", "shu", "{宿題|しゅくだい}", "作业"}, {"しょ", "sho", "{食堂|しょくどう}", "食堂"}},
            {{"ちゃ", "cha", "お{茶|ちゃ}", "茶"}, {"ちゅ", "chu", "{中国|ちゅうごく}", "中国"}, {"ちょ", "cho", "ちょっと", "稍微"}},
            {{"にゃ", "nya", "こんにゃく", "魔芋"}, {"にゅ", "nyu", "{牛乳|ぎゅうにゅう}", "牛奶"}, {"にょ", "nyo", "{女房|にょうぼう}", "老婆（口语）"}},
            {{"ひゃ", "hya", "{百|ひゃく}", "一百"}, {"ひゅ", "hyu", "", ""}, {"ひょ", "hyo", "{表|ひょう}", "表格"}},
            {{"みゃ", "mya", "{脈|みゃく}", "脉搏"}, {"みゅ", "myu", "ミュージカル", "音乐剧"}, {"みょ", "myo", "{名字|みょうじ}", "姓"}},
            {{"りゃ", "rya", "{略語|りゃくご}", "缩略语"}, {"りゅ", "ryu", "{留学|りゅうがく}", "留学"}, {"りょ", "ryo", "{旅行|りょこう}", "旅行"}},
            {{"ぎゃ", "gya", "{逆|ぎゃく}", "相反"}, {"ぎゅ", "gyu", "{牛肉|ぎゅうにく}", "牛肉"}, {"ぎょ", "gyo", "{金魚|きんぎょ}", "金鱼"}},
            {{"じゃ", "ja", "{邪魔|じゃま}", "打扰"}, {"じゅ", "ju", "{授業|じゅぎょう}", "上课"}, {"じょ", "jo", "{上手|じょうず}", "擅长"}},
            {{"びゃ", "bya", "{三百|さんびゃく}", "三百"}, {"びゅ", "byu", "", ""}, {"びょ", "byo", "{病気|びょうき}", "生病"}},
            {{"ぴゃ", "pya", "{八百|はっぴゃく}", "八百"}, {"ぴゅ", "pyu", "", ""}, {"ぴょ", "pyo", "{発表|はっぴょう}", "发表"}},
    };

    /** Characters that look alike (hiragana keys); used as distractors in reading quizzes. */
    private static final String[][] LOOK_H = {
            {"ぬ", "め"}, {"わ", "ね", "れ"}, {"る", "ろ"}, {"は", "ほ", "け"}, {"さ", "ち", "き"}, {"い", "り"},
            {"こ", "に", "た"}, {"あ", "お", "め"}, {"く", "へ"}, {"う", "ら"}, {"ま", "も"}, {"し", "つ"}};
    private static final String[][] LOOK_K = { // katakana shapes, expressed by their hiragana key
            {"し", "つ"}, {"そ", "ん"}, {"く", "た", "わ", "け"}, {"う", "ふ", "わ"}, {"ぬ", "す", "ふ"}, {"こ", "ろ", "ゆ"},
            {"ち", "て", "な"}, {"る", "れ"}, {"あ", "ま", "む"}, {"の", "め", "そ"}, {"を", "ら", "ち"}, {"ほ", "む"}, {"に", "こ"}};
    /** Sound pairs for listening distractors */
    private static final String VOICELESS = "かきくけこさしすせそたちつてとはひふへほはひふへほ";
    private static final String VOICED = "がぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽ";

    private static List<K> all;
    private static List<List<K>> seionGrid, dakuonGrid, youonGrid;

    private static synchronized void init() {
        if (all != null) return;
        all = new ArrayList<K>();
        seionGrid = build(SEION_ROWS, SEION);
        dakuonGrid = build(DAKUON_ROWS, DAKUON);
        youonGrid = build(YOUON_ROWS, YOUON);
    }

    private static List<List<K>> build(String[][][] rows, String group) {
        List<List<K>> grid = new ArrayList<List<K>>();
        for (int r = 0; r < rows.length; r++) {
            List<K> line = new ArrayList<K>();
            for (int c = 0; c < rows[r].length; c++) {
                String[] x = rows[r][c];
                if (x.length < 2) { line.add(null); continue; }
                K k = new K(x[0], x[1], group, r, c, x[2], x[3]);
                line.add(k);
                all.add(k);
            }
            grid.add(line);
        }
        return grid;
    }

    public static List<List<K>> grid(String group) {
        init();
        if (DAKUON.equals(group)) return dakuonGrid;
        if (YOUON.equals(group)) return youonGrid;
        return seionGrid;
    }

    /** group: 清音 / 浊音 / 拗音 / 全部 */
    public static List<K> list(String group) {
        init();
        List<K> out = new ArrayList<K>();
        for (K k : all) if ("全部".equals(group) || k.group.equals(group)) out.add(k);
        return out;
    }

    public static K find(String h) {
        init();
        for (K k : all) if (k.h.equals(h)) return k;
        return null;
    }

    public static String toKata(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'ぁ' && c <= 'ゖ') c = (char) (c + 0x60);
            b.append(c);
        }
        return b.toString();
    }

    /** Look-alike kana for a visual (reading) quiz. */
    public static List<K> lookAlikes(K k, boolean kata) {
        List<K> out = new ArrayList<K>();
        String[][] groups = kata ? LOOK_K : LOOK_H;
        for (String[] g : groups) {
            boolean in = false;
            for (String x : g) if (x.equals(k.h)) in = true;
            if (!in) continue;
            for (String x : g) {
                K o = find(x);
                if (o != null && o != k && !out.contains(o)) out.add(o);
            }
        }
        return out;
    }

    /** Similar-sounding kana for a listening quiz: voiced/voiceless partner, same row, base kana of a yōon. */
    public static List<K> soundAlikes(K k) {
        init();
        List<K> out = new ArrayList<K>();
        char first = k.h.charAt(0);
        int vi = VOICELESS.indexOf(first), vd = VOICED.indexOf(first);
        String partner = null;
        if (vi >= 0) partner = String.valueOf(VOICED.charAt(vi)) + k.h.substring(1);
        else if (vd >= 0) partner = String.valueOf(VOICELESS.charAt(vd)) + k.h.substring(1);
        if (partner != null) { K p = find(partner); if (p != null) out.add(p); }
        if (k.h.length() == 2) { K base = find(k.h.substring(0, 1)); if (base != null) out.add(base); }
        for (List<K> line : grid(k.group)) {
            if (line.isEmpty()) continue;
            K any = null;
            for (K x : line) if (x != null) { any = x; break; }
            if (any == null || any.row != k.row) continue;
            for (K x : line) if (x != null && x != k && !out.contains(x)) out.add(x);
        }
        // same vowel column in neighbouring rows (e.g. し/ち/ひ)
        for (K x : list(k.group)) if (x.col == k.col && Math.abs(x.row - k.row) <= 2 && x != k && !out.contains(x)) out.add(x);
        return out;
    }
}
