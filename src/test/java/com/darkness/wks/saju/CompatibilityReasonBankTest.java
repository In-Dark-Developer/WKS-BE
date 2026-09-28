package com.darkness.wks.saju;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityReasonBankTest {

    private static final SajuPillars WOOD = new SajuPillars("갑인", "갑인", "갑인", "갑인");
    private static final SajuPillars FIRE_MANY_WATER = new SajuPillars("임자", "임자", "병자", "임자");

    @Test
    void keyIgnoresOrderAndPicksFirstStrongElementOnTie() {
        assertThat(CompatibilityReasonBank.key(WOOD, FIRE_MANY_WATER, "찰떡")).isEqualTo("찰떡|나무:나무|불:물");
        assertThat(CompatibilityReasonBank.key(FIRE_MANY_WATER, WOOD, "찰떡")).isEqualTo("찰떡|나무:나무|불:물");
        // 일간 신(쇠), 글자 수 나무 2·불 2·물 2 동점 → 오행 순서상 나무
        SajuPillars tie = new SajuPillars("임오", "계묘", "신사", "을미");
        assertThat(CompatibilityReasonBank.key(tie, WOOD, "귀인")).isEqualTo("귀인|나무:나무|쇠:나무");
    }

    @Test
    void anyWindowOfVariantCountViewsNeverRepeatsAField() {
        List<CompatibilityReason> v = List.of(r(0), r(1), r(2));
        CompatibilityReasonBank bank = new CompatibilityReasonBank(Map.of("k", v));

        assertThat(bank.pick("k", 0)).contains(new CompatibilityReason("why0", "together1", "conflict2"));
        assertThat(bank.pick("k", 4)).contains(new CompatibilityReason("why1", "together2", "conflict0"));
        for (int start = 0; start < 10; start++) {
            for (int f = 0; f < 3; f++) {
                int field = f;
                assertThat(java.util.stream.IntStream.range(start, start + 3)
                        .mapToObj(o -> field(bank.pick("k", o).orElseThrow(), field)).distinct().count()).isEqualTo(3);
            }
        }
        assertThat(bank.pick("k", -1)).isEqualTo(bank.pick("k", 0));
        assertThat(bank.pick("missing", 0)).isEmpty();
    }

    private static String field(CompatibilityReason r, int i) {
        return i == 0 ? r.why() : i == 1 ? r.together() : r.conflict();
    }

    @Test
    void startSpreadsOriginsAcrossVariantsAndIsStable() {
        UUID a = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID b = UUID.fromString("22222222-2222-4222-8222-222222222222");
        assertThat(CompatibilityReasonBank.start(a)).isEqualTo(CompatibilityReasonBank.start(a)).isBetween(0, 999);
        assertThat(CompatibilityReasonBank.start(a)).isNotEqualTo(CompatibilityReasonBank.start(b));
        assertThat(CompatibilityReasonBank.start(null)).isZero();
    }

    @Test
    void bundledResourceCoversEveryCombination() {
        CompatibilityReasonBank bank = new CompatibilityReasonBank();
        // 무순서 (기운, 많은 기운) 쌍 325 × 유형 4
        assertThat(bank.size()).isEqualTo(1300);
        CompatibilityReason picked = bank.pick("스침|불:불|흙:불", 0).orElseThrow();
        assertThat(picked.why()).isNotBlank().doesNotContain("A", "B", "당신", "합니다");
    }

    private static CompatibilityReason r(int i) {
        return new CompatibilityReason("why" + i, "together" + i, "conflict" + i);
    }
}
