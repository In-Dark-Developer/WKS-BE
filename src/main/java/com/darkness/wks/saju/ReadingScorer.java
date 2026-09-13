package com.darkness.wks.saju;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 팔자 → 결혼·자녀·연애 점수(0~100). 순수 함수, 같은 팔자는 항상 같은 점수 (FR-RD-02, FR-RD-10).
 * 등급 변환은 호출 측(API 계층)이 {@link Grade#of} 로 한다.
 * <p>
 * 일간(日干) 기준 십성 분포, 일지(배우자궁)·시지(자녀궁)의 역할, 일지 합·충, 도화살로 점수를 만든다.
 * 원국(8글자 전체) 기준이며 대운·세운은 보지 않는다.
 * ponytail: 명리 개론 수준의 단순 가중치. 등급 분포를 바꾸려면 아래 상수만 손댄다.
 */
public class ReadingScorer {

    /** 십성 역할 인덱스: 0 비겁, 1 식상, 2 재성, 3 관성, 4 인성 */
    private static final int[] SPOUSE_BONUS = {0, 10, 30, 30, 20};
    private static final int[] CHILD_BONUS = {0, 20, 12, 8, 4};

    private static final String DOHWA = "자오묘유";
    private static final List<String> HAP = List.of("자축", "인해", "묘술", "진유", "사신", "오미");
    private static final List<String> CHUNG = List.of("자오", "축미", "인신", "묘유", "진술", "사해");

    public Map<ReadingCategory, Integer> score(SajuPillars pillars) {
        List<String> all = pillars.hourPillar() == null
                ? List.of(pillars.yearPillar(), pillars.monthPillar(), pillars.dayPillar())
                : List.of(pillars.yearPillar(), pillars.monthPillar(), pillars.dayPillar(), pillars.hourPillar());
        Element dayMaster = Element.ofStem(pillars.dayPillar().charAt(0));
        char spouseBranch = pillars.dayPillar().charAt(1);

        int[] role = new int[5];
        int chars = 0;
        int dohwa = 0;
        int hap = 0;
        int chung = 0;
        for (String p : all) {
            boolean isDay = p.equals(pillars.dayPillar());
            if (!isDay) {
                role[relation(dayMaster, Element.ofStem(p.charAt(0)))]++;
                chars++;
            }
            char branch = p.charAt(1);
            role[relation(dayMaster, Element.ofBranch(branch))]++;
            chars++;
            if (DOHWA.indexOf(branch) >= 0) dohwa++;
            if (!isDay) {
                String pair = "" + spouseBranch + branch;
                String reversed = "" + branch + spouseBranch;
                if (HAP.contains(pair) || HAP.contains(reversed)) hap++;
                if (CHUNG.contains(pair) || CHUNG.contains(reversed)) chung++;
            }
        }
        double scale = 7.0 / chars; // 시주 없으면 5글자라 7/5 로 보정

        double jaeGwan = (role[2] + role[3]) * scale; // 재성+관성: 이성·배우자 인연의 크기
        int love = (int) Math.round(20 + 10 * jaeGwan + 8 * dohwa);
        int marriage = (int) Math.round(38 + SPOUSE_BONUS[relation(dayMaster, Element.ofBranch(spouseBranch))]
                + 4 * jaeGwan + 12 * hap - 12 * chung);
        int children = (int) Math.round(30 + 16 * role[1] * scale);
        if (pillars.hourPillar() != null) {
            children += CHILD_BONUS[relation(dayMaster, Element.ofBranch(pillars.hourPillar().charAt(1)))];
        }

        Map<ReadingCategory, Integer> result = new EnumMap<>(ReadingCategory.class);
        result.put(ReadingCategory.MARRIAGE, clamp(marriage));
        result.put(ReadingCategory.CHILDREN, clamp(children));
        result.put(ReadingCategory.LOVE, clamp(love));
        return result;
    }

    /** 일간 오행 → 대상 오행의 십성 역할 (0 비겁, 1 식상, 2 재성, 3 관성, 4 인성). 상생 순환에서 몇 칸 뒤인지 */
    private static int relation(Element dayMaster, Element target) {
        return switch ((target.ordinal() - dayMaster.ordinal() + 5) % 5) {
            case 0 -> 0; // 같은 오행
            case 1 -> 1; // 내가 생함
            case 2 -> 2; // 내가 극함
            case 3 -> 3; // 나를 극함
            default -> 4; // 나를 생함
        };
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
