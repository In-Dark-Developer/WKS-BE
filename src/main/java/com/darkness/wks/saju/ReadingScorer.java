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
    private static final int[] CHILD_STEM_BONUS = {2, 10, 6, 4, 0};

    /** 년·월·일·시 기둥 비중 */
    private static final double[] PILLAR_WEIGHT = {0.7, 1.3, 1.0, 0.9};
    private static final double BRANCH_WEIGHT = 0.85;
    /** 시주 포함 8글자(일간 제외 7자리)의 가중치 총합 */
    private static final double FULL_WEIGHT = 0.7 + 1.3 + 0.9 + (0.7 + 1.3 + 1.0 + 0.9) * 0.85;

    private static final String DOHWA = "자오묘유";
    private static final List<String> HAP = List.of("자축", "인해", "묘술", "진유", "사신", "오미");
    private static final List<String> CHUNG = List.of("자오", "축미", "인신", "묘유", "진술", "사해");

    public Map<ReadingCategory, Integer> score(SajuPillars pillars) {
        List<String> all = pillars.hourPillar() == null
                ? List.of(pillars.yearPillar(), pillars.monthPillar(), pillars.dayPillar())
                : List.of(pillars.yearPillar(), pillars.monthPillar(), pillars.dayPillar(), pillars.hourPillar());
        Element dayMaster = Element.ofStem(pillars.dayPillar().charAt(0));
        char spouseBranch = pillars.dayPillar().charAt(1);

        // 기둥별 비중: 월주(월령)가 가장 크고 년주가 가장 작다. 지지는 천간보다 조금 작게.
        // 가중치가 소수라 십성 합이 촘촘해져 점수 계단이 줄어든다
        double[] role = new double[5];
        double weightSum = 0;
        double dohwa = 0;
        int hap = 0;
        int chung = 0;
        for (int i = 0; i < all.size(); i++) {
            String p = all.get(i);
            double w = PILLAR_WEIGHT[i];
            boolean isDay = i == 2;
            if (!isDay) {
                role[relation(dayMaster, Element.ofStem(p.charAt(0)))] += w;
                weightSum += w;
            }
            char branch = p.charAt(1);
            role[relation(dayMaster, Element.ofBranch(branch))] += w * BRANCH_WEIGHT;
            weightSum += w * BRANCH_WEIGHT;
            if (DOHWA.indexOf(branch) >= 0) dohwa += w;
            if (!isDay) {
                String pair = "" + spouseBranch + branch;
                String reversed = "" + branch + spouseBranch;
                if (HAP.contains(pair) || HAP.contains(reversed)) hap++;
                if (CHUNG.contains(pair) || CHUNG.contains(reversed)) chung++;
            }
        }
        double scale = FULL_WEIGHT / weightSum; // 시주 없으면 글자가 적으니 같은 총량으로 보정

        double jaeGwan = (role[2] + role[3]) * scale; // 재성+관성: 이성·배우자 인연의 크기
        double love = 20 + 10 * jaeGwan + 8 * dohwa;
        double marriage = 38 + SPOUSE_BONUS[relation(dayMaster, Element.ofBranch(spouseBranch))]
                + 4 * jaeGwan + 12 * hap - 12 * chung;
        // 자녀: 식상(자식 기운)이 많을수록, 인성(식상을 누르는 기운)이 많을수록 감점. 시주는 지지(자녀궁)·천간 둘 다 본다
        double children = 30 + 12 * role[1] * scale - 4 * role[4] * scale;
        if (pillars.hourPillar() != null) {
            children += CHILD_BONUS[relation(dayMaster, Element.ofBranch(pillars.hourPillar().charAt(1)))]
                    + CHILD_STEM_BONUS[relation(dayMaster, Element.ofStem(pillars.hourPillar().charAt(0)))];
        }

        Map<ReadingCategory, Integer> result = new EnumMap<>(ReadingCategory.class);
        result.put(ReadingCategory.MARRIAGE, calibrate(marriage, 65.7, 0.86));
        result.put(ReadingCategory.CHILDREN, calibrate(children, 51, 0.76));
        result.put(ReadingCategory.LOVE, calibrate(love, 57.9, 0.95));
        return result;
    }

    /**
     * 원점수를 중앙값 74(상/하 경계 = A+ 컷), 표준편차 약 14 로 선형 보정한다.
     * ponytail: 1950~2010 고유 팔자 8,225개의 원점수 중앙값·표준편차에서 나온 상수. 가중치를 바꾸면 Stats 로 다시 잰다
     */
    private static int calibrate(double raw, double median, double scale) {
        return clamp((int) Math.round(74 + (raw - median) * scale));
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
