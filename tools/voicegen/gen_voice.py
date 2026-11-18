"""
Pre-generates the app's built-in offline voice clips with Kokoro-82M (Apache-2.0) via kokoro-onnx.
  manifest.json : [{key, lang, text, synth, kind}]   (key = md5(lang + "|" + text), same as AudioBank.key in the app)
  output        : <outdir>/<key>.mp3  (mono 24 kHz, 48 kbps)
Resumable: existing files are skipped. Optional 4th arg = time budget in seconds.

Setup (Linux / WSL):
  pip install kokoro-onnx soundfile "misaki[ja]" unidic-lite spacy num2words
  pip install https://github.com/explosion/spacy-models/releases/download/en_core_web_sm-3.8.0/en_core_web_sm-3.8.0-py3-none-any.whl
  model: https://github.com/thewh1teagle/kokoro-onnx/releases/tag/model-files-v1.0  (kokoro-v1.0.onnx, voices-v1.0.bin)
Usage: python gen_voice.py manifest.json out_dir model_dir [budget_seconds]
"""
import json, os, re, subprocess, sys, tempfile, time

import pyopenjtalk
import soundfile as sf
from kokoro_onnx import Kokoro
from misaki import en, espeak, ja

VOICE = {"en": "af_heart", "ja": "jf_alpha"}
LANG = {"en": "en-us", "ja": "ja"}


def kata(s):
    return "".join(chr(ord(c) + 0x60) if 0x3041 <= ord(c) <= 0x3096 else c for c in s)


VOWEL = {}
for row, v in [("アカサタナハマヤラワガザダバパャァ", "a"), ("イキシチニヒミリギジヂビピィ", "i"), ("ウクスツヌフムユルグズヅブプュゥ", "u"),
               ("エケセテネヘメレゲゼデベペェ", "e"), ("オコソトノホモヨロヲゴゾドボポョォ", "o")]:
    for ch in row:
        VOWEL[ch] = v


def pron(s):
    """Normalise a katakana reading to pronunciation form (long vowels as ー) so it can be compared with OpenJTalk."""
    s = kata(s).replace("ヅ", "ズ").replace("ヂ", "ジ").replace("ヲ", "オ")
    out = []
    for ch in s:
        prev = VOWEL.get(out[-1]) if out else None
        if prev and ((ch == "ウ" and prev in "ou") or (ch == "イ" and prev == "e") or VOWEL.get(ch) == prev and ch in "アイウエオ"):
            out.append("ー")
        else:
            out.append(ch)
    return "".join(out)


def ja_input(marked):
    """Use the kanji text, but swap in the katakana reading for any ruby word OpenJTalk would read differently."""
    def rep(m):
        surface, reading = m.group(1), m.group(2)
        try:
            oj = pyopenjtalk.g2p(surface, kana=True)
        except Exception:
            oj = ""
        return surface if pron(oj) == pron(reading) else kata(reading)
    return re.sub(r"\{([^|}]*)\|([^}]*)\}", rep, marked)


def main():
    manifest, outdir, model_dir = sys.argv[1], sys.argv[2], sys.argv[3]
    budget = float(sys.argv[4]) if len(sys.argv) > 4 else 1e9
    t0 = time.time()
    os.makedirs(outdir, exist_ok=True)
    items = json.load(open(manifest, encoding="utf-8"))
    todo = [x for x in items if not os.path.exists(os.path.join(outdir, x["key"] + ".mp3"))]
    print("total", len(items), "todo", len(todo), flush=True)
    if not todo:
        return
    k = Kokoro(os.path.join(model_dir, "kokoro-v1.0.onnx"), os.path.join(model_dir, "voices-v1.0.bin"))
    g_ja = ja.JAG2P()
    g_en = en.G2P(trf=False, british=False, fallback=espeak.EspeakFallback(british=False))
    done = 0
    tmp = tempfile.mkdtemp()
    for x in todo:
        if time.time() - t0 > budget:
            break
        lang = x["lang"]
        text = ja_input(x["synth"]) if lang == "ja" else x["synth"]
        try:
            ph, _ = (g_ja if lang == "ja" else g_en)(text)
            samples, sr = k.create(ph, voice=VOICE[lang], speed=1.0, lang=LANG[lang], is_phonemes=True)
            wav = os.path.join(tmp, x["key"] + ".wav")
            sf.write(wav, samples, sr)
            dst = os.path.join(outdir, x["key"] + ".mp3")
            subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-af", "apad=pad_dur=0.12",
                            "-ac", "1", "-ar", "24000", "-b:a", "48k", dst + ".part.mp3"], check=True)
            os.replace(dst + ".part.mp3", dst)
            os.remove(wav)
            done += 1
        except Exception as e:
            print("FAIL", x["key"], x["text"], e, flush=True)
    print("generated", done, "remaining", len(todo) - done, "in", round(time.time() - t0), "s", flush=True)


if __name__ == "__main__":
    main()
