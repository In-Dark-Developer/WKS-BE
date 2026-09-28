"""배치 완료를 기다려 결과를 검사하고 src/main/resources/compatibility-reasons.json 에 합친다. 사용법은 README.md."""
import collections, json, os, re, sys, time, urllib.request

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
RESOURCE = os.path.join(ROOT, "src/main/resources/compatibility-reasons.json")
PLAIN = ["나무", "불", "흙", "쇠", "물"]
STEM = dict(zip(PLAIN, ["갑", "병", "무", "경", "임"]))
BRANCH = dict(zip(PLAIN, ["인", "오", "진", "신", "자"]))
FIELDS = ["why", "together", "conflict"]
# 프롬프트가 금지한 것: 당신·A/B·사주 용어·"유형"·점수·합쇼체·하게체
BAN = re.compile(r"당신|(?<![A-Za-z])[AB](?![A-Za-z])|일주|월주|시주|년주|십성|재성|관성|비견|인성|식상|유형|점수|\d{2,3}점|합니다|입니다|하시게|라네|하네요")


def wait_and_download(api_key, batch):
    h = {"x-goog-api-key": api_key}
    while True:
        b = json.loads(urllib.request.urlopen(urllib.request.Request(f"https://generativelanguage.googleapis.com/v1beta/{batch}", headers=h)).read())
        state = b["metadata"]["state"]
        print(time.strftime("%H:%M:%S"), state, b["metadata"].get("batchStats", {}), flush=True)
        if state == "BATCH_STATE_SUCCEEDED":
            f = b["response"]["responsesFile"]
            return urllib.request.urlopen(urllib.request.Request(
                f"https://generativelanguage.googleapis.com/download/v1beta/{f}:download?alt=media", headers=h)).read().decode()
        if state in ("BATCH_STATE_FAILED", "BATCH_STATE_CANCELLED", "BATCH_STATE_EXPIRED"):
            sys.exit(json.dumps(b)[:500])
        time.sleep(60)


def problems(key, fields):
    _, a, b, _ = key.split("|")
    (ea, ma), (eb, mb) = a.split(":"), b.split(":")
    pillars = {STEM[ma] + BRANCH[ma], STEM[mb] + BRANCH[mb], STEM[ea] + BRANCH[ma], STEM[eb] + BRANCH[mb]}
    found = []
    for f in FIELDS:
        t = fields.get(f, "")
        if not t: found.append(f"{f}:empty"); continue
        if BAN.search(t): found.append(f"{f}:ban:{BAN.search(t).group()}")
        for p in pillars:  # 팔자 글자가 낱말로 쓰였는지 (이야기해·갑자기 같은 우연 일치는 제외)
            for m in re.finditer(p, t):
                s, e = m.start(), m.end()
                if (s == 0 or t[s - 1] in " (\"'") and (e == len(t) or t[e] in " ,.)\"'의은는이가을를과와로"):
                    found.append(f"{f}:pillar:{p}")
    return found


if __name__ == "__main__":
    out = sys.argv[1]
    api_key = os.environ["GEMINI_PAID_KEY"]
    results = wait_and_download(api_key, open(os.path.join(out, "batch_name.txt")).read().strip())
    bank = json.load(open(RESOURCE, encoding="utf-8")) if os.path.exists(RESOURCE) else {}
    requests = {json.loads(l)["key"]: l for l in open(os.path.join(out, "requests.jsonl"), encoding="utf-8")}
    retry, stats = [], collections.Counter()
    for line in results.splitlines():
        d = json.loads(line); key = d["key"]
        try:
            fields = {f: json.loads(d["response"]["candidates"][0]["content"]["parts"][0]["text"])[f].strip() for f in FIELDS}
            bad = problems(key, fields)
        except Exception:
            bad = ["response:error"]
        if bad:
            stats.update(p.split(":")[1] for p in bad); retry.append(requests[key]); continue
        combo = "|".join(key.split("|")[:3])
        bank.setdefault(combo, [])
        if fields not in bank[combo]: bank[combo].append(fields)
        stats["ok"] += 1
    json.dump(dict(sorted(bank.items())), open(RESOURCE, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    open(os.path.join(out, "retry.jsonl"), "w", encoding="utf-8").write("".join(retry))
    print(dict(stats), "| combos", len(bank), "| retry", len(retry), "->", os.path.join(out, "retry.jsonl"))
