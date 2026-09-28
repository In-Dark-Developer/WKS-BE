"""궁합 이유 사전 생성 요청 JSONL 을 만들고 Gemini Batch 에 제출한다. 사용법은 README.md."""
import json, os, sys, urllib.request

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
SYSTEM = open(os.path.join(ROOT, "src/main/resources/prompts/compatibility-reason-system.txt"), encoding="utf-8").read()
PLAIN = ["나무", "불", "흙", "쇠", "물"]
STEM = ["갑", "병", "무", "경", "임"]
BRANCH = ["인", "오", "진", "신", "자"]
TIERS = [("귀인", 95, 3), ("찰떡", 82, 6), ("벗", 68, 6), ("스침", 45, 3)]
FIELDS = ["why", "together", "conflict"]
MODEL = "gemini-3.5-flash-lite"


def final(w): return (ord(w[-1]) - 0xAC00) % 28 != 0
def subj(w): return w + ("이 " if final(w) else "가 ")
def obj(w): return w + ("을" if final(w) else "를")


def pillars(e, m):
    p = STEM[m] + BRANCH[m]
    return f"년주 {p}, 월주 {p}, 일주 {STEM[e] + BRANCH[m]}, 시주 {p}"


def prompt(tier, score, ea, ma, eb, mb):
    pa, pb = PLAIN[ea], PLAIN[eb]
    if ea == eb: rel = "같은 기운"
    elif (ea + 1) % 5 == eb: rel = subj(pa) + obj(pb) + " 살린다(상생)"
    elif (eb + 1) % 5 == ea: rel = subj(pb) + obj(pa) + " 살린다(상생)"
    elif (ea + 2) % 5 == eb: rel = subj(pa) + obj(pb) + " 누른다(상극)"
    else: rel = subj(pb) + obj(pa) + " 누른다(상극)"
    return (f"관계 유형: {tier} / 궁합 점수: {score}\n"
            f"A {pillars(ea, ma)}\nA의 기운: {pa} / A의 많은 기운: {PLAIN[ma]}\n"
            f"B {pillars(eb, mb)}\nB의 기운: {pb} / B의 많은 기운: {PLAIN[mb]}\n"
            f"두 기운의 관계: {rel}")


def requests(existing=None):
    """existing: 조합 → 이미 있는 변형 수. 주면 부족분만 만든다(--fill)."""
    tuples = [(e, m) for e in range(5) for m in range(5)]
    for tier, score, nv in TIERS:
        for i, (ea, ma) in enumerate(tuples):
            for (eb, mb) in tuples[i:]:
                combo = f"{tier}|{PLAIN[ea]}:{PLAIN[ma]}|{PLAIN[eb]}:{PLAIN[mb]}"
                have = (existing or {}).get(combo, 0)
                for v in range(have, nv):
                    key = f"{combo}|{v}"
                    yield {"key": key, "request": {
                        "systemInstruction": {"parts": [{"text": SYSTEM}]},
                        "contents": [{"role": "user", "parts": [{"text": prompt(tier, score, ea, ma, eb, mb)}]}],
                        "generationConfig": {
                            "responseMimeType": "application/json", "temperature": 0.9,
                            "responseSchema": {"type": "OBJECT", "properties": {f: {"type": "STRING"} for f in FIELDS}, "required": FIELDS},
                            "thinkingConfig": {"thinkingLevel": "MINIMAL"}}}}


def submit(key, data, name):
    h = {"x-goog-api-key": key, "Content-Type": "application/json"}
    r = urllib.request.urlopen(urllib.request.Request(
        "https://generativelanguage.googleapis.com/upload/v1beta/files", data=json.dumps({"file": {"display_name": name}}).encode(),
        headers={**h, "X-Goog-Upload-Protocol": "resumable", "X-Goog-Upload-Command": "start",
                 "X-Goog-Upload-Header-Content-Length": str(len(data)), "X-Goog-Upload-Header-Content-Type": "application/jsonl"}))
    r = urllib.request.urlopen(urllib.request.Request(r.headers["x-goog-upload-url"], data=data,
        headers={"Content-Length": str(len(data)), "X-Goog-Upload-Offset": "0", "X-Goog-Upload-Command": "upload, finalize"}))
    file_name = json.loads(r.read())["file"]["name"]
    r = urllib.request.urlopen(urllib.request.Request(
        f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:batchGenerateContent",
        data=json.dumps({"batch": {"display_name": name, "input_config": {"file_name": file_name}}}).encode(), headers=h))
    return json.loads(r.read())["name"]


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    api_key = os.environ["GEMINI_PAID_KEY"]
    if "--retry" in sys.argv:
        lines = open(sys.argv[sys.argv.index("--retry") + 1], encoding="utf-8").read().splitlines()
    elif "--fill" in sys.argv:  # 기존 리소스의 부족분만. 걸러질 몫을 감안해 1.3배 뽑는다
        resource = os.path.join(ROOT, "src/main/resources/compatibility-reasons.json")
        existing = {k: len(v) for k, v in json.load(open(resource, encoding="utf-8")).items()}
        base = list(requests(existing))
        extra = list(requests({k: max(0, n - 1) for k, n in existing.items()}))[:len(base) // 3]
        lines = [json.dumps(r, ensure_ascii=False) for r in base + extra]
    else:
        lines = [json.dumps(r, ensure_ascii=False) for r in requests()]
    data = ("\n".join(lines) + "\n").encode()
    open(os.path.join(out, "requests.jsonl"), "wb").write(data)
    batch = submit(api_key, data, "wks-compatibility-reasons")
    open(os.path.join(out, "batch_name.txt"), "w").write(batch)
    print("submitted", len(lines), "requests ->", batch)
