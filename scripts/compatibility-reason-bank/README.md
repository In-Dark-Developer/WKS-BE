# 궁합 이유 사전 생성 (#131)

`src/main/resources/compatibility-reasons.json` 을 다시 만드는 절차. 프롬프트(`prompts/compatibility-reason-system.txt`)나
변형 수를 바꿀 때만 돌린다. 유료 Gemini 키가 필요하고 Batch API(실시간의 50%)로 5,850건 ≈ $3 든다.

```bash
export GEMINI_PAID_KEY=<유료 키>          # 채팅·커밋에 넣지 않는다
python3 scripts/compatibility-reason-bank/generate.py out/   # JSONL 생성 + 배치 제출, out/batch_name.txt
python3 scripts/compatibility-reason-bank/collect.py out/    # 완료 대기·다운로드·검사·리소스 갱신
```

- 조합: 무순서 (기운, 많은 기운) 쌍 325 × 유형 4 = 1,300. 변형은 찰떡·벗 6, 귀인·스침 3.
- 프롬프트는 `CompatibilityReasonGenerator.buildPrompt` 와 같은 형식. 팔자 글자는 기운·많은 기운에 맞춘 대표 팔자,
  점수는 유형 대표값(95·82·68·45). 글에는 둘 다 안 쓰게 프롬프트가 막는다.
- `collect.py` 는 합쇼체·A/B·"유형"·"점수"·사주 용어·팔자 글자·빈 필드를 걸러내고, 걸린 키는 `out/retry.jsonl` 로 남긴다.
  `generate.py --retry out/retry.jsonl` 로 그 키만 다시 제출한 뒤 `collect.py` 를 한 번 더 돌리면 기존 리소스에 합쳐진다.
