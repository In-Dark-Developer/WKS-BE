package com.darkness.wks.saju;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 두 팔자 + 점수 + 관계 유형 → 궁합 상세 이유 세 답. Gemini 한 번 호출 (FR-CP-13).
 * <p>
 * 프롬프트에는 팔자·점수·관계 유형과 코드가 정한 오행 사실만 들어간다 (FR-CP-14).
 * 성별은 넣지 않는다 — 궁합 이유는 성별을 보지 않고, 성별을 고려한 글은 소개팅 쪽이 따로 만든다.
 * 두 사람이 같은 글을 보므로 어느 한쪽을 "당신"으로 부르지 않게 시스템 프롬프트가 막는다.
 */
@Component
public class CompatibilityReasonGenerator {

    private static final List<String> FIELDS = List.of("why", "together", "conflict");
    private static final String SYSTEM_PROMPT = GeminiJson.loadResource("prompts/compatibility-reason-system.txt");

    private final GeminiJson gemini;

    public CompatibilityReasonGenerator(GeminiJson gemini) {
        this.gemini = gemini;
    }

    /** @param tier 관계 유형 한글(귀인·찰떡·벗·스침). compatibility 패키지 enum 을 saju 가 모르게 문자열로 받는다 */
    public CompatibilityReason generate(SajuPillars a, SajuPillars b, int score, String tier) {
        Map<String, String> m = gemini.generate(SYSTEM_PROMPT, buildPrompt(a, b, score, tier), FIELDS);
        return new CompatibilityReason(m.get("why"), m.get("together"), m.get("conflict"));
    }

    /**
     * 두 사람의 기운(일간)과 둘 사이 상생·상극 관계를 코드가 정해 넘긴다. 팔자 글자만 주면 LLM 이 관계를 지어낸다 (#48 과 같은 이유).
     * 순서(A/B)는 저장된 궁합 행의 origin/guest 를 그대로 따르고, 글은 순서에 기대지 않게 프롬프트가 막는다.
     */
    static String buildPrompt(SajuPillars a, SajuPillars b, int score, String tier) {
        Element ea = Element.ofStem(a.dayPillar().charAt(0));
        Element eb = Element.ofStem(b.dayPillar().charAt(0));
        String pa = ReadingGenerator.PLAIN[ea.ordinal()];
        String pb = ReadingGenerator.PLAIN[eb.ordinal()];
        String relation;
        if (ea == eb) {
            relation = "같은 기운";
        } else if (ea.generates(eb)) {
            relation = subject(pa) + object(pb) + " 살린다(상생)";
        } else if (eb.generates(ea)) {
            relation = subject(pb) + object(pa) + " 살린다(상생)";
        } else if (ea.controls(eb)) {
            relation = subject(pa) + object(pb) + " 누른다(상극)";
        } else {
            relation = subject(pb) + object(pa) + " 누른다(상극)";
        }
        return "관계 유형: " + tier + " / 궁합 점수: " + score + "\n"
                + "A " + ReadingGenerator.pillarsLine(a) + "\n"
                + "A의 기운: " + pa + " / A의 많은 기운: " + strong(a) + "\n"
                + "B " + ReadingGenerator.pillarsLine(b) + "\n"
                + "B의 기운: " + pb + " / B의 많은 기운: " + strong(b) + "\n"
                + "두 기운의 관계: " + relation;
    }

    /** 받침 유무로 조사 선택. 오행 이름(나무·불·흙·쇠·물)만 다루므로 한글 음절 공식으로 충분 */
    private static boolean hasFinalConsonant(String word) {
        return (word.charAt(word.length() - 1) - 0xAC00) % 28 != 0;
    }

    private static String subject(String word) {
        return word + (hasFinalConsonant(word) ? "이 " : "가 ");
    }

    private static String object(String word) {
        return word + (hasFinalConsonant(word) ? "을" : "를");
    }

    private static String strong(SajuPillars p) {
        int[] count = ReadingGenerator.elementCounts(p);
        int max = Arrays.stream(count).max().orElse(0);
        return Arrays.stream(Element.values()).filter(e -> count[e.ordinal()] == max)
                .map(e -> ReadingGenerator.PLAIN[e.ordinal()]).collect(Collectors.joining(", "));
    }
}
