package com.darkness.wks.saju;

import com.darkness.wks.common.Gender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ElementProfileTest {

    private static final SajuPillars P = new SajuPillars("임오", "계묘", "신사", "을미"); // 일간 신(쇠)

    @Test
    void spouseAndChildrenFollowGender() {
        ElementProfile m = ElementProfile.of(P, Gender.MALE);
        assertThat(m.mine()).isEqualTo(Element.METAL);
        assertThat(m.spouse()).isEqualTo(Element.WOOD);     // 남자 재성 = 쇠가 극하는 나무
        assertThat(m.children()).isEqualTo(Element.FIRE);   // 남자 관성 = 쇠를 극하는 불
        ElementProfile f = ElementProfile.of(P, Gender.FEMALE);
        assertThat(f.spouse()).isEqualTo(Element.FIRE);     // 여자 관성
        assertThat(f.children()).isEqualTo(Element.WATER);  // 여자 식상 = 쇠가 생하는 물
    }

    @Test
    void strengthSumsTo100AndMatchesRawOrder() {
        ElementProfile ep = ElementProfile.of(P, Gender.MALE);
        assertThat(ep.strength().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
        assertThat(ep.strength()).containsOnlyKeys(Element.values());
        double[] raw = Element.strengths(P);
        for (Element a : Element.values())
            for (Element b : Element.values())
                if (raw[a.ordinal()] > raw[b.ordinal()] + 1e-9)
                    assertThat(ep.strength().get(a)).isGreaterThanOrEqualTo(ep.strength().get(b));
    }

    @Test
    void strongIsTop2AndWeakIsUnder10Percent() {
        ElementProfile ep = ElementProfile.of(new SajuPillars("갑인", "갑인", "갑인", "갑인"), Gender.FEMALE); // 전부 나무
        assertThat(ep.strength().get(Element.WOOD)).isEqualTo(100);
        assertThat(ep.strong()).hasSize(2).startsWith(Element.WOOD);
        assertThat(ep.weak()).containsExactly(Element.FIRE, Element.EARTH, Element.METAL, Element.WATER);
    }

    @Test
    void serializesAsEnumNamesAndPercentMap() {
        String json = tools.jackson.databind.json.JsonMapper.builder().build()
                .writeValueAsString(ElementProfile.of(P, Gender.MALE));
        assertThat(json).contains("\"mine\":\"METAL\"").contains("\"spouse\":\"WOOD\"").contains("\"children\":\"FIRE\"")
                .contains("\"strength\":{\"WOOD\":").doesNotContain("korean").doesNotContain("colors");
    }

    @Test
    void worksWithoutHourPillar() {
        ElementProfile ep = ElementProfile.of(new SajuPillars("경진", "기축", "무술", null), Gender.MALE);
        assertThat(ep.strength().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
    }
}
