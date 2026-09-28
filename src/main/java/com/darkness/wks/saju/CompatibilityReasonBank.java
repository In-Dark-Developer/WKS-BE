package com.darkness.wks.saju;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 궁합 상세 이유 사전 생성본 (#131). 프롬프트 입력이 (기운, 많은 기운) 쌍과 관계 유형뿐이라 조합이 1,300개로 유한하다.
 * 같은 프롬프트·모델로 Gemini Batch 가 미리 쓴 변형(찰떡·벗 6, 귀인·스침 3)을 리소스에 두고, 실시간 LLM 호출 없이 고른다.
 * 재생성 스크립트는 {@code scripts/compatibility-reason-bank/}.
 */
@Component
public class CompatibilityReasonBank {

    private static final String RESOURCE = "compatibility-reasons.json";
    private static final TypeReference<Map<String, List<CompatibilityReason>>> TYPE = new TypeReference<>() {
    };

    private final Map<String, List<CompatibilityReason>> variants;

    public CompatibilityReasonBank() {
        this(JsonMapper.builder().build().readValue(GeminiJson.loadResource(RESOURCE), TYPE));
    }

    CompatibilityReasonBank(Map<String, List<CompatibilityReason>> variants) {
        this.variants = Map.copyOf(variants);
    }

    /**
     * A/B 순서와 무관한 조합 키. 많은 기운이 동점이면 오행 순서(나무·불·흙·쇠·물)에서 앞선 것 하나만 쓴다 —
     * 사전 생성도 조합마다 많은 기운 하나로 만들었다.
     */
    public static String key(SajuPillars a, SajuPillars b, String tier) {
        int[] ka = side(a), kb = side(b);
        if (ka[0] * 5 + ka[1] > kb[0] * 5 + kb[1]) {
            int[] t = ka; ka = kb; kb = t;
        }
        String[] plain = ReadingGenerator.PLAIN;
        return tier + "|" + plain[ka[0]] + ":" + plain[ka[1]] + "|" + plain[kb[0]] + ":" + plain[kb[1]];
    }

    private static int[] side(SajuPillars p) {
        int[] count = ReadingGenerator.elementCounts(p);
        int max = Arrays.stream(count).max().orElse(0);
        int strong = 0;
        while (count[strong] != max) strong++;
        return new int[]{Element.ofStem(p.dayPillar().charAt(0)).ordinal(), strong};
    }

    /**
     * 공유자마다 다른 자리에서 회전을 시작한다. 모두 0번부터 돌면 0번 변형만 유독 많이 읽힌다.
     * 공유자 id 로 정하므로 같은 행의 두 사람은 여전히 같은 글을 본다.
     */
    public static int start(UUID originId) {
        return originId == null ? 0 : Math.floorMod(originId.hashCode(), 1000);
    }

    /**
     * 같은 조합을 n번째 여는 사람에게 n번째 글. 세 답은 서로 다른 변형에서 섞어 뽑아 겹침을 더 줄인다
     * (변형 3개면 답 조합 27가지). ordinal 이 변형 수를 넘으면 자리를 한 칸씩 밀어 다른 조합을 만든다.
     */
    public Optional<CompatibilityReason> pick(String key, int ordinal) {
        List<CompatibilityReason> list = variants.get(key);
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }
        int n = list.size(), o = Math.max(ordinal, 0), shift = o / n;
        return Optional.of(new CompatibilityReason(
                list.get(o % n).why(),
                list.get((o + shift + 1) % n).together(),
                list.get((o + 2 * shift + 2) % n).conflict()));
    }

    int size() {
        return variants.size();
    }
}
