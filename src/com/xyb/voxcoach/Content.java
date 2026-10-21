package com.xyb.voxcoach;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Built-in graded content: sentences (with ruby for Japanese), minimal pairs and role-play scenarios. */
public final class Content {

    public static final class Sentence {
        public String id;
        public String marked;   // Japanese uses {漢字|かな} ruby markup
        public String text;     // surface text
        public String reading;  // hiragana reading (ja) or null
        public Map<String, String> ruby = new HashMap<String, String>();
        public String zh;
        public int level;
        public String lang;

        public Sentence(String lang, String id, int level, String marked, String zh) {
            this.lang = lang;
            this.id = id;
            this.level = level;
            this.marked = marked;
            this.zh = zh;
            if ("ja".equals(lang)) {
                StringBuilder s = new StringBuilder(), r = new StringBuilder();
                Scorer.parseRuby(marked, s, r, ruby);
                text = s.toString();
                reading = r.toString();
            } else {
                text = marked;
                reading = null;
            }
        }
    }

    public static final class Pair {
        public String cat, a, b, aSay, bSay, aZh, bZh;

        Pair(String cat, String a, String aSay, String aZh, String b, String bSay, String bZh) {
            this.cat = cat; this.a = a; this.aSay = aSay; this.aZh = aZh; this.b = b; this.bSay = bSay; this.bZh = bZh;
        }
    }

    public static final class Scenario {
        public String id, icon, title, sub, role, opener;
        public String source;   // null = built-in
        public boolean custom;

        Scenario(String id, String icon, String title, String sub, String role, String opener) {
            this.id = id; this.icon = icon; this.title = title; this.sub = sub; this.role = role; this.opener = opener;
        }
    }

    // ------------------------------------------------------------------ English sentences

    private static final String[][] EN = {
            // level 1
            {"1", "Hello, nice to meet you.", "你好，很高兴认识你。"},
            {"1", "Can I have a glass of water, please?", "可以给我一杯水吗？"},
            {"1", "Where is the nearest station?", "最近的车站在哪里？"},
            {"1", "I'd like a large coffee, please.", "我要一杯大杯咖啡。"},
            {"1", "How much is this jacket?", "这件夹克多少钱？"},
            {"1", "I'm from China, and I work in Tokyo.", "我来自中国，在东京工作。"},
            {"1", "What time is it now?", "现在几点了？"},
            {"1", "Could you say that again, please?", "可以请你再说一遍吗？"},
            {"1", "I usually get up at seven.", "我通常七点起床。"},
            {"1", "Thank you very much for your help.", "非常感谢你的帮助。"},
            {"1", "Excuse me, where is the restroom?", "打扰一下，洗手间在哪里？"},
            {"1", "The weather is really nice today.", "今天天气真好。"},
            // level 2
            {"2", "I've been learning English for two years.", "我学英语两年了。"},
            {"2", "Could you speak a little more slowly?", "你能说慢一点吗？"},
            {"2", "I'm looking forward to seeing you next week.", "期待下周见到你。"},
            {"2", "Do you mind if I open the window?", "你介意我开窗吗？"},
            {"2", "It takes about ten minutes to walk there.", "走过去大约十分钟。"},
            {"2", "I'd like to book a table for two at eight.", "我想订一张八点的两人桌。"},
            {"2", "What do you usually do on weekends?", "你周末通常做什么？"},
            {"2", "I think it's going to rain this afternoon.", "我觉得今天下午会下雨。"},
            {"2", "Can you recommend a good restaurant nearby?", "你能推荐附近一家好餐厅吗？"},
            {"2", "I'm afraid I can't make it tomorrow.", "恐怕我明天去不了。"},
            {"2", "Let's grab some lunch together.", "我们一起去吃个午饭吧。"},
            {"2", "She works three days a week from home.", "她每周在家办公三天。"},
            // level 3
            {"3", "I was stuck in traffic, so I missed the beginning of the meeting.", "我堵在路上了，所以错过了会议的开头。"},
            {"3", "Would it be possible to change my reservation to Friday?", "可以把我的预约改到周五吗？"},
            {"3", "The thing is, I'm not really sure what he meant.", "问题是，我不太确定他是什么意思。"},
            {"3", "If I had more time, I would travel around the world.", "如果我有更多时间，我会去环游世界。"},
            {"3", "Thanks for getting back to me so quickly.", "谢谢你这么快回复我。"},
            {"3", "I'd rather stay in tonight and watch a movie.", "今晚我宁愿待在家看电影。"},
            {"3", "Could you walk me through the main points of the report?", "你能给我讲一下报告的要点吗？"},
            {"3", "I've never been to a concert like that before.", "我以前从没去过那样的音乐会。"},
            {"3", "It's worth trying at least once.", "至少值得试一次。"},
            {"3", "He should have told us about the problem earlier.", "他应该早点告诉我们这个问题。"},
            {"3", "What's the best way to get to the airport from here?", "从这里去机场最好怎么走？"},
            {"3", "I really appreciate you taking the time to help me.", "非常感谢你抽时间帮我。"},
            // level 4
            {"4", "Let me get back to you once I've checked the details.", "等我核对完细节再回复你。"},
            {"4", "To be fair, I think we're overthinking this.", "说句公道话，我觉得我们想太多了。"},
            {"4", "The project ran over budget, but the results were worth it.", "项目超支了，但结果是值得的。"},
            {"4", "I couldn't agree more with what you just said.", "我完全同意你刚才说的。"},
            {"4", "We need to prioritize the features that matter most to our users.", "我们需要优先做对用户最重要的功能。"},
            {"4", "It's not exactly what I expected, but it'll do for now.", "这不完全是我预期的，但暂时可以了。"},
            {"4", "Would you be open to rescheduling the call for later this week?", "你介意把电话会议改到这周晚些时候吗？"},
            {"4", "Their customer service has improved significantly over the past year.", "过去一年他们的客服明显改善了。"},
            {"4", "I'm leaning towards the second option, to be honest.", "说实话，我比较倾向第二个方案。"},
            {"4", "Let's take a step back and look at the bigger picture.", "我们退一步，从大局来看。"},
            {"4", "She has a remarkable ability to explain complex ideas simply.", "她有把复杂的想法讲简单的非凡能力。"},
            {"4", "I'll keep you posted if anything changes.", "如果有任何变化我会随时告诉你。"},
    };

    // ------------------------------------------------------------------ Japanese sentences (ruby markup)

    private static final String[][] JA = {
            // N5
            {"1", "{駅|えき}はどこですか。", "车站在哪里？"},
            {"1", "コーヒーを{一|ひと}つください。", "请给我一杯咖啡。"},
            {"1", "{今日|きょう}はいい{天気|てんき}ですね。", "今天天气真好啊。"},
            {"1", "すみません、これはいくらですか。", "不好意思，这个多少钱？"},
            {"1", "{私|わたし}は{中国|ちゅうごく}から{来|き}ました。", "我来自中国。"},
            {"1", "トイレはどこにありますか。", "厕所在哪里？"},
            {"1", "{毎朝|まいあさ}{七時|しちじ}に{起|お}きます。", "我每天早上七点起床。"},
            {"1", "はじめまして、どうぞよろしくお{願|ねが}いします。", "初次见面，请多关照。"},
            {"1", "{水|みず}をもう{一杯|いっぱい}お{願|ねが}いします。", "请再给我一杯水。"},
            {"1", "{明日|あした}は{何時|なんじ}に{会|あ}いますか。", "明天几点见面？"},
            {"1", "この{電車|でんしゃ}は{東京|とうきょう}{駅|えき}に{行|い}きますか。", "这趟电车去东京站吗？"},
            {"1", "{切手|きって}を{三枚|さんまい}ください。", "请给我三张邮票。"},
            // N4
            {"2", "{日本語|にほんご}を{勉強|べんきょう}して{二年|にねん}になります。", "我学日语两年了。"},
            {"2", "{週末|しゅうまつ}は{友達|ともだち}と{映画|えいが}を{見|み}に{行|い}きました。", "周末和朋友去看了电影。"},
            {"2", "{道|みち}に{迷|まよ}ってしまいました。", "我迷路了。"},
            {"2", "{窓|まど}を{開|あ}けてもいいですか。", "可以开窗吗？"},
            {"2", "{駅|えき}まで{歩|ある}いて{十分|じゅっぷん}ぐらいです。", "走到车站大约十分钟。"},
            {"2", "{会議|かいぎ}は{三時|さんじ}から{始|はじ}まります。", "会议三点开始。"},
            {"2", "{少|すこ}しゆっくり{話|はな}してもらえますか。", "能说慢一点吗？"},
            {"2", "{雨|あめ}が{降|ふ}りそうなので、{傘|かさ}を{持|も}っていきます。", "好像要下雨，我带伞去。"},
            {"2", "この{料理|りょうり}はとてもおいしいです。", "这道菜非常好吃。"},
            {"2", "{先週|せんしゅう}、{初|はじ}めて{富士山|ふじさん}に{登|のぼ}りました。", "上周第一次爬了富士山。"},
            {"2", "{予約|よやく}をキャンセルしたいんですが。", "我想取消预约。"},
            {"2", "{病院|びょういん}は{何時|なんじ}まで{開|あ}いていますか。", "医院开到几点？"},
            // N3
            {"3", "{電車|でんしゃ}が{遅|おく}れたせいで、{会議|かいぎ}に{間|ま}に{合|あ}いませんでした。", "因为电车晚点，没赶上会议。"},
            {"3", "{仕事|しごと}が{終|お}わったら、{一緒|いっしょ}に{飲|の}みに{行|い}きませんか。", "下班后一起去喝一杯吧？"},
            {"3", "{資料|しりょう}を{確認|かくにん}していただけますか。", "能帮我确认一下资料吗？"},
            {"3", "{日本|にほん}に{来|き}てから、{料理|りょうり}をするようになりました。", "来日本后开始自己做饭了。"},
            {"3", "{彼|かれ}の{話|はなし}によると、{来月|らいげつ}{引|ひ}っ{越|こ}すそうです。", "听他说，他下个月要搬家。"},
            {"3", "{忙|いそが}しくて、{昼|ひる}ご{飯|はん}を{食|た}べる{時間|じかん}もありません。", "忙得连吃午饭的时间都没有。"},
            {"3", "もし{時間|じかん}があれば、{京都|きょうと}にも{行|い}ってみたいです。", "如果有时间，也想去京都看看。"},
            {"3", "{申|もう}し{訳|わけ}ありませんが、{今|いま}{満席|まんせき}です。", "非常抱歉，现在满座了。"},
            {"3", "{健康|けんこう}のために、{毎日|まいにち}{歩|ある}くようにしています。", "为了健康，我坚持每天走路。"},
            {"3", "この{問題|もんだい}について、{皆|みな}さんの{意見|いけん}を{聞|き}かせてください。", "关于这个问题，请大家谈谈意见。"},
            {"3", "{思|おも}ったより{簡単|かんたん}でした。", "比想象的简单。"},
            {"3", "{荷物|にもつ}を{預|あず}かっていただけませんか。", "能帮我寄存一下行李吗？"},
            // N2
            {"4", "お{忙|いそが}しいところ{恐|おそ}れ{入|い}りますが、ご{確認|かくにん}をお{願|ねが}いいたします。", "百忙之中打扰了，请您确认。"},
            {"4", "{検討|けんとう}した{結果|けっか}、{今回|こんかい}は{見送|みおく}ることになりました。", "经过研究，这次决定暂缓。"},
            {"4", "{先日|せんじつ}はお{世話|せわ}になりました。", "前几天承蒙关照了。"},
            {"4", "{納期|のうき}に{間|ま}に{合|あ}うかどうか、{確認|かくにん}してから{折|お}り{返|かえ}しご{連絡|れんらく}します。", "确认能否赶上交期后，再回电联系您。"},
            {"4", "{彼|かれ}の{提案|ていあん}は{実現|じつげん}{可能性|かのうせい}が{高|たか}いと{思|おも}います。", "我认为他的提案可行性很高。"},
            {"4", "{差|さ}し{支|つか}えなければ、ご{連絡先|れんらくさき}を{教|おし}えていただけますか。", "如果方便的话，能告诉我您的联系方式吗？"},
            {"4", "{円安|えんやす}の{影響|えいきょう}で、{海外|かいがい}からの{観光客|かんこうきゃく}が{増|ふ}えています。", "受日元贬值影响，海外游客在增加。"},
            {"4", "{一度|いちど}{決|き}めたからには、{最後|さいご}までやり{遂|と}げるつもりです。", "既然决定了，就打算坚持到底。"},
            {"4", "{結論|けつろん}から{申|もう}し{上|あ}げますと、{予算|よさん}が{足|た}りません。", "先说结论：预算不够。"},
            {"4", "{詳細|しょうさい}につきましては、{添付|てんぷ}の{資料|しりょう}をご{覧|らん}ください。", "详情请参阅附件资料。"},
            {"4", "ご{不明|ふめい}な{点|てん}がございましたら、お{気軽|きがる}にお{問|と}い{合|あ}わせください。", "如有不明之处，请随时咨询。"},
            {"4", "{今後|こんご}ともどうぞよろしくお{願|ねが}い{申|もう}し{上|あ}げます。", "今后也请多多关照。"},
    };

    private static List<Sentence> enList, jaList;

    public static synchronized List<Sentence> sentences(String lang) {
        if ("ja".equals(lang)) {
            if (jaList == null) jaList = build("ja", JA);
            return jaList;
        }
        if (enList == null) enList = build("en", EN);
        return enList;
    }

    private static List<Sentence> build(String lang, String[][] src) {
        List<Sentence> out = new ArrayList<Sentence>();
        for (int i = 0; i < src.length; i++) {
            out.add(new Sentence(lang, lang + "_" + i, Integer.parseInt(src[i][0]), src[i][1], src[i][2]));
        }
        return out;
    }

    public static String levelName(String lang, int level) {
        if ("ja".equals(lang)) return new String[]{"", "N5 入门", "N4 初级", "N3 中级", "N2 中高级"}[level];
        return new String[]{"", "A1 入门", "A2 初级", "B1 中级", "B2 中高级"}[level];
    }

    // ------------------------------------------------------------------ minimal pairs

    public static List<Pair> pairs(String lang) {
        List<Pair> p = new ArrayList<Pair>();
        if ("ja".equals(lang)) {
            p.add(new Pair("长音", "おばさん", "おばさん", "阿姨", "おばあさん", "おばあさん", "奶奶"));
            p.add(new Pair("长音", "おじさん", "おじさん", "叔叔", "おじいさん", "おじいさん", "爷爷"));
            p.add(new Pair("长音", "雪 ゆき", "ゆき", "雪", "勇気 ゆうき", "ゆうき", "勇气"));
            p.add(new Pair("长音", "鳥 とり", "とり", "鸟", "通り とおり", "とおり", "大街"));
            p.add(new Pair("长音", "ビル", "ビル", "大楼", "ビール", "ビール", "啤酒"));
            p.add(new Pair("长音", "ここ", "ここ", "这里", "高校 こうこう", "こうこう", "高中"));
            p.add(new Pair("促音", "来て きて", "きて", "来吧", "切手 きって", "きって", "邮票"));
            p.add(new Pair("促音", "坂 さか", "さか", "坡", "作家 さっか", "さっか", "作家"));
            p.add(new Pair("促音", "音 おと", "おと", "声音", "夫 おっと", "おっと", "丈夫"));
            p.add(new Pair("促音", "かこ", "かこ", "过去", "かっこ", "かっこ", "括号"));
            p.add(new Pair("清浊音", "天気 てんき", "てんき", "天气", "電気 でんき", "でんき", "电灯"));
            p.add(new Pair("清浊音", "柿 かき", "かき", "柿子", "鍵 かぎ", "かぎ", "钥匙"));
            p.add(new Pair("清浊音", "体 からだ", "からだ", "身体", "ガラス", "がらす", "玻璃"));
            p.add(new Pair("拗音", "自由 じゆう", "じゆう", "自由", "十 じゅう", "じゅう", "十"));
            p.add(new Pair("拗音", "美容院 びよういん", "びよういん", "美容院", "病院 びょういん", "びょういん", "医院"));
            p.add(new Pair("拨音 ん", "記入 きにゅう", "きにゅう", "填写", "禁輸 きんゆ", "きんゆ", "禁运"));
            p.add(new Pair("拨音 ん", "絵 え", "え", "画", "円 えん", "えん", "日元"));
        } else {
            p.add(new Pair("L/R", "light", "light", "光", "right", "right", "右；正确"));
            p.add(new Pair("L/R", "lock", "lock", "锁", "rock", "rock", "岩石"));
            p.add(new Pair("L/R", "fly", "fly", "飞", "fry", "fry", "炸"));
            p.add(new Pair("L/R", "glass", "glass", "玻璃", "grass", "grass", "草"));
            p.add(new Pair("L/R", "collect", "collect", "收集", "correct", "correct", "正确"));
            p.add(new Pair("TH 音", "think", "think", "想", "sink", "sink", "水槽"));
            p.add(new Pair("TH 音", "thank", "thank", "感谢", "sank", "sank", "沉没"));
            p.add(new Pair("TH 音", "three", "three", "三", "tree", "tree", "树"));
            p.add(new Pair("TH 音", "mouth", "mouth", "嘴", "mouse", "mouse", "老鼠"));
            p.add(new Pair("V/B/W", "vest", "vest", "背心", "best", "best", "最好"));
            p.add(new Pair("V/B/W", "very", "very", "非常", "berry", "berry", "浆果"));
            p.add(new Pair("V/B/W", "vine", "vine", "藤", "wine", "wine", "葡萄酒"));
            p.add(new Pair("F/V", "fan", "fan", "风扇", "van", "van", "面包车"));
            p.add(new Pair("长短元音", "ship", "ship", "船", "sheep", "sheep", "羊"));
            p.add(new Pair("长短元音", "live", "live", "居住", "leave", "leave", "离开"));
            p.add(new Pair("长短元音", "full", "full", "满的", "fool", "fool", "傻瓜"));
            p.add(new Pair("长短元音", "pull", "pull", "拉", "pool", "pool", "水池"));
            p.add(new Pair("元音", "bad", "bad", "坏的", "bed", "bed", "床"));
            p.add(new Pair("元音", "cat", "cat", "猫", "cut", "cut", "切"));
            p.add(new Pair("元音", "hat", "hat", "帽子", "hot", "hot", "热"));
            p.add(new Pair("元音", "work", "work", "工作", "walk", "walk", "走路"));
            p.add(new Pair("S/SH", "see", "see", "看见", "she", "she", "她"));
            p.add(new Pair("S/SH", "seat", "seat", "座位", "sheet", "sheet", "床单"));
            p.add(new Pair("N/L", "night", "night", "夜晚", "light", "light", "光"));
            p.add(new Pair("N/L", "know", "know", "知道", "low", "low", "低"));
            p.add(new Pair("词尾辅音", "bed", "bed", "床", "bet", "bet", "打赌"));
            p.add(new Pair("词尾辅音", "card", "card", "卡片", "cart", "cart", "推车"));
            p.add(new Pair("词尾辅音", "rice", "rice", "米饭", "rise", "rise", "上升"));
        }
        // drop entries we marked as placeholders
        List<Pair> out = new ArrayList<Pair>();
        for (Pair x : p) if (!"-".equals(x.bZh)) out.add(x);
        return out;
    }

    // ------------------------------------------------------------------ role-play scenarios

    public static List<Scenario> scenarios(String lang) {
        List<Scenario> s = new ArrayList<Scenario>();
        if ("ja".equals(lang)) {
            s.add(new Scenario("ja_konbini", "🏪", "便利店", "コンビニで買い物",
                    "You are a friendly convenience store clerk in Tokyo. The user is a customer buying things.",
                    "いらっしゃいませ！お弁当、温めますか？"));
            s.add(new Scenario("ja_rest", "🍜", "餐厅点餐", "レストランで注文",
                    "You are a waiter at a ramen restaurant in Tokyo. Take the user's order, answer questions about the menu.",
                    "いらっしゃいませ。何名様ですか？"));
            s.add(new Scenario("ja_michi", "🚉", "问路", "道を聞く",
                    "You are a kind passer-by near Shinjuku station. The user is lost and asks for directions.",
                    "はい、どうしましたか？"));
            s.add(new Scenario("ja_office", "💼", "职场寒暄 / 会议", "会社での会話",
                    "You are the user's Japanese colleague (a project manager) at a Tokyo IT company. Use polite business Japanese (です/ます, 敬語 when natural). Chat about work, schedules and meetings.",
                    "おはようございます。昨日の会議の資料、もう見ましたか？"));
            s.add(new Scenario("ja_hotel", "🏨", "酒店入住", "ホテルのチェックイン",
                    "You are a hotel front desk clerk in Kyoto. Help the user check in.",
                    "いらっしゃいませ。チェックインでございますか？"));
            s.add(new Scenario("ja_free", "🗣", "自由聊天", "何でも話そう",
                    "You are a friendly Japanese friend. Chat casually about anything (hobbies, food, travel, daily life). Ask follow-up questions.",
                    "こんにちは！最近、何か面白いことありましたか？"));
        } else {
            s.add(new Scenario("en_cafe", "☕", "咖啡店点单", "Order at a coffee shop",
                    "You are a barista at a busy coffee shop in New York. Take the user's order, offer sizes and add-ons.",
                    "Hi there! What can I get for you today?"));
            s.add(new Scenario("en_airport", "✈️", "机场值机", "Airport check-in",
                    "You are an airline check-in agent at the airport. Ask for the passport, baggage, seat preference.",
                    "Good morning! Where are you flying to today?"));
            s.add(new Scenario("en_hotel", "🏨", "酒店入住", "Hotel check-in",
                    "You are a hotel receptionist. Help the user check in and answer questions about the hotel.",
                    "Welcome to the Grand Hotel! Do you have a reservation with us?"));
            s.add(new Scenario("en_interview", "💼", "英文面试", "Job interview",
                    "You are an interviewer at an international tech company. Interview the user for a software/e-commerce related position. Ask one question at a time.",
                    "Thanks for coming in today. Could you start by telling me a little about yourself?"));
            s.add(new Scenario("en_colleague", "🤝", "同事闲聊", "Small talk at work",
                    "You are the user's friendly coworker. Make small talk at the office: weekend plans, projects, lunch.",
                    "Hey! How was your weekend?"));
            s.add(new Scenario("en_free", "🗣", "自由聊天", "Talk about anything",
                    "You are a friendly native English speaker. Chat casually about anything. Ask follow-up questions.",
                    "Hi! What would you like to talk about today?"));
        }
        return s;
    }
}
