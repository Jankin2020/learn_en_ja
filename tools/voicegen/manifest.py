import json, hashlib, re, sys, os
base = sys.argv[1]; packdir = sys.argv[2]; out = sys.argv[3]
items = {}
def key(lang, text): return hashlib.md5((lang + "|" + text.strip()).encode("utf-8")).hexdigest()
def surface(m): return re.sub(r"\{([^|}]*)\|[^}]*\}", r"\1", m)
def reading(m):
    r = re.sub(r"\{[^|}]*\|([^}]*)\}", r"\1", m)
    return "".join(chr(ord(c)-0x60) if 0x30A1 <= ord(c) <= 0x30F6 else c for c in r)
def kata(s): return "".join(chr(ord(c)+0x60) if 0x3041 <= ord(c) <= 0x3096 else c for c in s)
def ennorm(w):
    s = w.lower().replace("’","'").replace("‘","'")
    s = "".join(ch for ch in s if ch.isalnum() or ch == "'").strip("'")
    return "okay" if s == "ok" else s
def add(lang, text, synth, kind):
    text = text.strip()
    if not text: return
    k = key(lang, text)
    if k not in items: items[k] = {"key": k, "lang": lang, "text": text, "synth": synth, "kind": kind}
en_texts = []
def sentence(lang, marked):
    if lang == "ja": add("ja", surface(marked), marked, "sentence")
    else: add("en", marked, marked, "sentence"); en_texts.append(marked)
for line in open(base, encoding="utf-8"):
    lang, kind, t = line.rstrip("\n").split("\t", 2)
    if kind == "sentence": sentence(lang, t)
    elif kind == "say": add(lang, t, t, "word")
    elif kind == "kana": add("ja", t, kata(t), "kana")
    elif kind == "word": add("ja", surface(t), reading(t), "word")
for fn in sorted(os.listdir(packdir)):
    if not fn.endswith(".json") or fn == "index.json": continue
    p = json.load(open(os.path.join(packdir, fn), encoding="utf-8"))
    for s in p.get("sentences", []): sentence(s.get("lang","en"), s["text"])
    for c in p.get("scenarios", []): sentence(c.get("lang","en"), c["opener"])
    for q in p.get("pairs", []):
        lang = q.get("lang","en")
        for side in ("a","b"):
            say = q.get(side+"_say") or q[side].split(" ")[-1]
            add(lang, say, say, "word")
add("en", "Hello! Nice to meet you.", "Hello! Nice to meet you.", "sentence")
add("ja", "こんにちは。はじめまして、よろしくお願いします。", "こんにちは。はじめまして、よろしくお願いします。", "sentence")
for t in en_texts:
    for w in t.split():
        n = ennorm(w)
        if n: add("en", n, n, "word")
lst = sorted(items.values(), key=lambda x: (x["lang"], x["kind"], x["key"]))
json.dump(lst, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=0)
from collections import Counter
print(len(lst), Counter((x["lang"], x["kind"]) for x in lst))
