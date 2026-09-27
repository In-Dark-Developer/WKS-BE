package com.darkness.wks.saju;

import com.darkness.wks.common.Gender;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 팔자 + 등급 → 보살 톤 해석. Gemini 는 {@link GeminiJson} 으로 **한 번만** 호출한다 (FR-GM-02).
 * <p>
 * 프롬프트에는 팔자와 등급만 들어간다. 생년월일·시간·닉네임은 이 클래스가 받지도 않는다 (FR-GM-01).
 */
@Component
public class ReadingGenerator {

    private static final List<String> FIELDS =
            List.of("destinyDescription", "marriage", "children", "love", "elementMatch");
    private static final String SYSTEM_PROMPT = GeminiJson.loadResource("prompts/reading-system.txt");

    private final GeminiJson gemini;

    public ReadingGenerator(GeminiJson gemini) {
        this.gemini = gemini;
    }

    /** 시스템 프롬프트·모델이 바뀌면 달라진다. 저장된 해석 재사용 여부 판단용 (#62) */
    public int promptVersion() {
        return (SYSTEM_PROMPT + "|" + gemini.model()).hashCode();
    }

    public Reading generate(SajuPillars pillars, Map<ReadingCategory, Grade> grades, Gender gender) {
        return toReading(gemini.generate(SYSTEM_PROMPT, buildPrompt(pillars, grades, gender), FIELDS));
    }

    /** 프롬프트용 오행 이름. 시스템 프롬프트의 "나무·불·흙·쇠·물의 기운"과 맞춘다 */
    static final String[] PLAIN = {"나무", "불", "흙", "쇠", "물"};

    /** 십성 역할(Element.roleFor: 0 비겁 1 식상 2 재성 3 관성 4 인성)을 사주 용어 없이 풀어 쓴 말 */
    private static final String[] ROLE_MEANING = {
            "나와 같은 기운", "내가 살려 주는 기운", "내가 이끄는 기운", "나를 이끌어 주는 기운", "나를 살려 주는 기운"};

    /**
     * 팔자·등급·성별·오행 사실뿐. 개인정보 미포함은 테스트로 고정한다 (TR-03).
     * 오행 사실(나의 기운·많은/없는 기운·배우자·자녀 기운)을 코드가 정해 넘긴다. 팔자 글자만 주면 LLM 이 매번 다른 오행을 집어 말한다 (#48)
     */
    static String buildPrompt(SajuPillars p, Map<ReadingCategory, Grade> grades, Gender gender) {
        Element me = Element.ofStem(p.dayPillar().charAt(0));
        int[] count = elementCounts(p);
        int max = java.util.Arrays.stream(count).max().orElse(0);
        String strong = java.util.Arrays.stream(Element.values()).filter(e -> count[e.ordinal()] == max)
                .map(e -> PLAIN[e.ordinal()]).collect(Collectors.joining(", "));
        String weak = java.util.Arrays.stream(Element.values()).filter(e -> count[e.ordinal()] == 0)
                .map(e -> PLAIN[e.ordinal()]).collect(Collectors.joining(", "));
        int spouseRole = gender == Gender.MALE ? 2 : 3; // 남 재성, 여 관성
        int childRole = gender == Gender.MALE ? 3 : 1;  // 남 관성, 여 식상
        Element spouse = null, child = null;
        for (Element e : Element.values()) {
            if (e.roleFor(me) == spouseRole) spouse = e;
            if (e.roleFor(me) == childRole) child = e;
        }
        StringBuilder sb = new StringBuilder()
                .append("성별 ").append(gender == Gender.MALE ? "남성" : "여성").append("\n")
                .append(pillarsLine(p)).append("\n")
                .append("나의 기운: ").append(PLAIN[me.ordinal()]).append("\n")
                .append("많은 기운: ").append(strong).append(" / 없는 기운: ").append(weak.isEmpty() ? "없음" : weak).append("\n")
                .append("배우자 기운: ").append(PLAIN[spouse.ordinal()])
                .append(" / 배우자 자리의 기운: ").append(PLAIN[Element.ofBranch(p.dayPillar().charAt(1)).ordinal()]).append("\n")
                .append("자녀 기운: ").append(PLAIN[child.ordinal()]).append("\n");
        // 잘 맞는 기운(기능명세 3.5)은 행운의 장소와 같은 보완 오행. 그 기운이 나에게 무슨 뜻인지도 코드가 정해 준다 (#82)
        Element match = LuckyPlace.luckyElement(p);
        sb.append("잘 맞는 기운: ").append(PLAIN[match.ordinal()])
                .append(" (").append(ROLE_MEANING[match.roleFor(me)]).append(")");
        for (ReadingCategory c : ReadingCategory.values()) {
            sb.append("\n").append(c.korean()).append(" 등급: ").append(grades.get(c).label());
        }
        return sb.toString();
    }

    static String pillarsLine(SajuPillars p) {
        return "년주 " + p.yearPillar() + ", 월주 " + p.monthPillar() + ", 일주 " + p.dayPillar()
                + ", 시주 " + (p.hourPillar() == null ? "모름" : p.hourPillar());
    }

    /**
     * 강한/약한 기운은 화면(ResultResponse.ElementResponse)과 같은 글자 개수 기준이다 (#70).
     * 자리 가중치(Element.strengths)로 고르면 "수 3개인데 왜 화 얘기?" 가 19% 에서 생긴다. 점수·행운 장소는 가중치 그대로
     */
    static int[] elementCounts(SajuPillars p) {
        int[] count = new int[5];
        for (String pillar : List.of(p.yearPillar(), p.monthPillar(), p.dayPillar(),
                p.hourPillar() == null ? "" : p.hourPillar())) {
            if (pillar.isEmpty()) continue;
            count[Element.ofStem(pillar.charAt(0)).ordinal()]++;
            count[Element.ofBranch(pillar.charAt(1)).ordinal()]++;
        }
        return count;
    }

    static Reading toReading(Map<String, String> m) {
        Map<ReadingCategory, String> contents = new EnumMap<>(ReadingCategory.class);
        contents.put(ReadingCategory.MARRIAGE, m.get("marriage"));
        contents.put(ReadingCategory.CHILDREN, m.get("children"));
        contents.put(ReadingCategory.LOVE, m.get("love"));
        return new Reading(m.get("destinyDescription"), contents, m.get("elementMatch"));
    }
}
