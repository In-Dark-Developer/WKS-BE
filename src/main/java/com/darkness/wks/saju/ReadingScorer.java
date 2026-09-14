package com.darkness.wks.saju;

import com.darkness.wks.common.Gender;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 팔자 → 결혼·자녀·연애 점수(0~100). 순수 함수, 같은 팔자는 항상 같은 점수 (FR-RD-02, FR-RD-10).
 * 등급 변환은 호출 측(API 계층)이 {@link Grade#of} 로 한다.
 * <p>
 * 일간(日干) 기준 십성 분포, 일지(배우자궁)·시지(자녀궁)의 역할, 일지 합·충, 도화살로 점수를 만든다.
 * 배우자성·자녀성은 성별로 갈린다: 남자는 재성=배우자·관성=자녀, 여자는 관성=배우자·식상=자녀.
 * 원국(8글자 전체) 기준이며 대운·세운은 보지 않는다.
 * ponytail: 명리 개론 수준의 단순 가중치. 등급 분포를 바꾸려면 아래 상수만 손댄다.
 */
public class ReadingScorer {

    /** 십성 역할 인덱스: 0 비겁, 1 식상, 2 재성, 3 관성, 4 인성 */
    private static final int BIGYEOP = 0, SIKSANG = 1, JAESEONG = 2, GWANSEONG = 3, INSEONG = 4;
    /** 일지 십성 → 결혼 보너스. 배우자성 30, 재·관 중 나머지 18. 배우자궁에 재·관·인이 있으면 배우자 인연이 뚜렷하다 */
    private static final int[] SPOUSE_BONUS = {0, 10, 18, 18, 20};
    private static final int SPOUSE_STAR_BONUS = 30;
    /** 시주 글자 → 자녀 보너스. 자녀성과의 관계 순: 같음, 자녀성을 생함, 자녀성이 생함, 자녀성을 극함, 자녀성이 극함 */
    private static final int[] CHILD_BONUS = {20, 12, 8, 0, 4};
    private static final int[] CHILD_STEM_BONUS = {10, 6, 4, 0, 2};
    /** 시주 모름이면 시주 보너스를 모집단 평균으로 채운다. 시주가 최악인 것처럼 0 을 주면 자녀운만 한 등급 낮아진다 (1950~2010 실측 13.2, 남녀 동일) */
    private static final double UNKNOWN_HOUR_CHILD_BONUS = 13.2;

    /** 년·월·일·시 기둥 비중 */
    private static final double[] PILLAR_WEIGHT = {0.7, 1.3, 1.0, 0.9};
    private static final double BRANCH_WEIGHT = 0.85;
    /** 시주 포함 8글자(일간 제외 7자리)의 가중치 총합 */
    private static final double FULL_WEIGHT = 0.7 + 1.3 + 0.9 + (0.7 + 1.3 + 1.0 + 0.9) * 0.85;

    private static final String DOHWA = "자오묘유";
    private static final List<String> HAP = List.of("자축", "인해", "묘술", "진유", "사신", "오미");
    private static final List<String> CHUNG = List.of("자오", "축미", "인신", "묘유", "진술", "사해");

    public Map<ReadingCategory, Integer> score(SajuPillars pillars, Gender gender) {
        double[] raw = raw(pillars, gender);
        Map<ReadingCategory, Integer> result = new EnumMap<>(ReadingCategory.class);
        result.put(ReadingCategory.MARRIAGE, calibrate(raw[0], 63.0, 0.93));
        result.put(ReadingCategory.CHILDREN, calibrate(raw[1], 51.9, 0.78));
        result.put(ReadingCategory.LOVE, calibrate(raw[2], 54.1, 0.95));
        return result;
    }

    /** 보정 전 원점수 [결혼, 자녀, 연애]. 분포 측정용 */
    double[] raw(SajuPillars pillars, Gender gender) {
        List<String> all = pillars.hourPillar() == null
                ? List.of(pillars.yearPillar(), pillars.monthPillar(), pillars.dayPillar())
                : List.of(pillars.yearPillar(), pillars.monthPillar(), pillars.dayPillar(), pillars.hourPillar());
        Element dayMaster = Element.ofStem(pillars.dayPillar().charAt(0));
        char spouseBranch = pillars.dayPillar().charAt(1);
        int spouseStar = gender == Gender.MALE ? JAESEONG : GWANSEONG;
        int childStar = gender == Gender.MALE ? GWANSEONG : SIKSANG;

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

        // 배우자성 + 재·관 중 나머지 절반: 이성·배우자 인연의 크기
        double mate = (role[spouseStar] + 0.5 * role[5 - spouseStar]) * scale;
        double love = 20 + 13 * mate + 8 * dohwa;
        int spouseRole = relation(dayMaster, Element.ofBranch(spouseBranch));
        double marriage = 38 + (spouseRole == spouseStar ? SPOUSE_STAR_BONUS : SPOUSE_BONUS[spouseRole])
                + 5 * mate + 12 * hap - 12 * chung;
        // 자녀: 자녀성이 많을수록, 자녀성을 극하는 기운이 많을수록 감점. 시주는 지지(자녀궁)·천간 둘 다 본다
        int suppressor = (childStar + 3) % 5; // 자녀성을 극하는 십성 (식상←인성, 관성←식상)
        double children = 30 + 12 * role[childStar] * scale - 4 * role[suppressor] * scale;
        if (pillars.hourPillar() != null) {
            children += CHILD_BONUS[offset(childStar, relation(dayMaster, Element.ofBranch(pillars.hourPillar().charAt(1))))]
                    + CHILD_STEM_BONUS[offset(childStar, relation(dayMaster, Element.ofStem(pillars.hourPillar().charAt(0))))];
        } else {
            children += UNKNOWN_HOUR_CHILD_BONUS;
        }
        return new double[] {marriage, children, love};
    }

    /** 십성 {@code star} 기준 {@code role} 의 관계: 0 같음, 1 star 를 생함, 2 star 가 생함, 3 star 를 극함, 4 star 가 극함 */
    private static int offset(int star, int role) {
        return switch ((role - star + 5) % 5) {
            case 0 -> 0;
            case 4 -> 1; // 상생 순환에서 한 칸 앞 = star 를 생함
            case 1 -> 2; // 한 칸 뒤 = star 가 생함
            case 3 -> 3; // 두 칸 앞 = star 를 극함
            default -> 4; // 두 칸 뒤 = star 가 극함
        };
    }

    /**
     * 원점수를 중앙값 74(상/하 경계 = A+ 컷), 표준편차 약 14 로 선형 보정한다.
     * ponytail: 1950~2010 시주 포함 고유 팔자 265,004개의 원점수 중앙값·표준편차 (남녀 차이 0.5 이내라 공통). 가중치를 바꾸면 다시 잰다
     */
    private static int calibrate(double raw, double median, double scale) {
        double v = 74 + (raw - median) * scale;
        // 양 끝은 잘라내지 않고 부드럽게 눌러 90~100, 0~10 안에 펼친다. 100점에 쌓이는 것 방지
        if (v > 90) v = 90 + 10 * (1 - Math.exp(-(v - 90) / 12));
        if (v < 10) v = 10 - 10 * (1 - Math.exp((v - 10) / 12));
        return clamp((int) Math.round(v));
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
