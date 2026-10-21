package com.xyb.voxcoach;

import java.util.HashMap;
import java.util.Map;

/** Short Chinese coaching tips per pronunciation-issue category. */
public final class Tips {
    private static final Map<String, String> M = new HashMap<String, String>();
    static {
        M.put("L/R", "L：舌尖抵住上齿龈，气流从舌两侧出；R：舌尖向后卷、不碰上颚，嘴唇略收圆。");
        M.put("TH 音", "TH：舌尖轻放在上下齿之间，送气摩擦，不要发成 s / t / d / f。");
        M.put("V/B/W", "V：上齿轻咬下唇并振动；B：双唇闭合后爆破；W：双唇收圆向前，不碰牙齿。");
        M.put("F/V", "F 是清音（声带不振动），V 是浊音（声带振动），上齿都要轻咬下唇。");
        M.put("N/L", "N 气流从鼻腔出（捏住鼻子就发不出）；L 气流从舌头两侧出。");
        M.put("元音", "注意元音口型：æ 嘴张大偏扁（cat），e 半开（bed），ʌ 短促放松（cut），ɜː 卷舌（work）。");
        M.put("长短元音", "长元音要拉长并保持紧张（sheep / pool），短元音短促放松（ship / pull）。");
        M.put("S/SH", "S：舌尖靠近上齿龈、嘴角略扁；SH：舌身后缩、嘴唇前突成圆形。");
        M.put("词尾辅音", "单词结尾的辅音（t / d / k / s / z）要发出来，但不要额外加「呃」音。");
        M.put("多加尾音", "结尾辅音后不要拖出额外的元音（例如 good 不要读成 goodo）。");
        M.put("漏读单词", "有单词没被识别到：连读时也要把每个词的核心音发出来，可以先用慢速跟读。");
        M.put("单词发音", "点击红色单词单独听标准发音，注意重音位置，模仿后再整句跟读。");
        M.put("促音", "促音「っ」要停顿一整拍（像憋住一下气），不要直接跳过：きて ≠ きって。");
        M.put("长音", "长音要拉满两拍：おばあさん 的「ばあ」比 おばさん 的「ば」长一倍。");
        M.put("拨音 ん", "「ん」要占满一拍，用鼻音收尾，不要吞掉：え ≠ えん。");
        M.put("拗音", "拗音（きゃ / しゅ / ちょ）是一拍完成的一个音，不要拆成两拍：びょういん ≠ びよういん。");
        M.put("清浊音", "日语清音（か / た）送气较弱，浊音（が / だ）要振动声带：てんき ≠ でんき。");
        M.put("汉字词读音", "有汉字词没被正确识别：先慢速听标准读音，特别注意音读里的长音和促音。");
        M.put("漏读", "有假名没读出来：放慢语速，保证每个假名都占一拍。");
        M.put("假名发音", "点击重听标准读音，注意口型和节拍，日语每个假名时长基本相等。");
    }

    public static String of(String cat) {
        String t = M.get(cat);
        return t == null ? "多听标准发音，模仿节奏和重音后再跟读。" : t;
    }
}
