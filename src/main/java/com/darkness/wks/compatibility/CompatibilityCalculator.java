package com.darkness.wks.compatibility;

import com.darkness.wks.saju.SajuPillars;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 두 팔자의 천간·지지 관계와 글자에서 즉석 변환한 오행·음양 분포로 궁합 점수를 계산한다.
 * 별도 저장 데이터 없이 동작하며 모든 관계는 방향 없이 평가한다.
 */
@Component
public class CompatibilityCalculator {

    private static final String STEMS = "갑을병정무기경신임계";
    private static final String BRANCHES = "자축인묘진사오미신유술해";
    private static final int[] STEM_WEIGHTS = {3, 5, 15, 3};
    private static final int[] BRANCH_WEIGHTS = {3, 5, 9, 4};

    private static final Set<String> STEM_COMBINATIONS = pairs("갑기", "을경", "병신", "정임", "무계");
    private static final Set<String> BRANCH_COMBINATIONS = pairs("자축", "인해", "묘술", "진유", "사신", "오미");
    private static final Set<String> BRANCH_CLASHES = pairs("자오", "축미", "인신", "묘유", "진술", "사해");
    private static final Set<String> BRANCH_HARMS = pairs("자미", "축오", "인사", "묘진", "신해", "유술");
    private static final Set<String> BRANCH_BREAKS = pairs("자유", "축진", "인해", "묘오", "사신", "미술");
    private static final List<String> TRINES = List.of("신자진", "해묘미", "인오술", "사유축");

    public int calculate(SajuPillars origin, SajuPillars guest) {
        Pillar[] originPillars = parse(origin);
        Pillar[] guestPillars = parse(guest);

        int score = 50;
        for (int i = 0; i < originPillars.length; i++) {
            if (originPillars[i] == null || guestPillars[i] == null) {
                continue;
            }
            score += stemScore(originPillars[i].stem(), guestPillars[i].stem(), STEM_WEIGHTS[i]);
            score += branchScore(originPillars[i].branch(), guestPillars[i].branch(), BRANCH_WEIGHTS[i]);
        }

        score += elementBalanceScore(originPillars, guestPillars);
        score += yinYangScore(originPillars, guestPillars);
        return clamp(score, 0, 100);
    }

    private int stemScore(char first, char second, int weight) {
        if (STEM_COMBINATIONS.contains(pair(first, second))) {
            return weight;
        }

        Element firstElement = stemElement(first);
        Element secondElement = stemElement(second);
        if (firstElement == secondElement) {
            return Math.max(1, Math.round(weight * 0.3f));
        }
        if (firstElement.generates(secondElement) || secondElement.generates(firstElement)) {
            return Math.max(1, Math.round(weight * 0.55f));
        }
        return -Math.max(1, Math.round(weight * 0.55f));
    }

    private int branchScore(char first, char second, int weight) {
        String pair = pair(first, second);
        if (BRANCH_COMBINATIONS.contains(pair)) {
            return weight;
        }
        if (BRANCH_CLASHES.contains(pair)) {
            return -weight;
        }
        if (isTrine(first, second)) {
            return Math.max(1, Math.round(weight * 0.6f));
        }
        if (first == second) {
            return Math.max(1, Math.round(weight * 0.35f));
        }
        if (BRANCH_HARMS.contains(pair) || BRANCH_BREAKS.contains(pair)) {
            return -Math.max(1, Math.round(weight * 0.5f));
        }
        return 0;
    }

    private int elementBalanceScore(Pillar[] first, Pillar[] second) {
        Map<Element, Integer> firstCounts = elementCounts(first);
        Map<Element, Integer> secondCounts = elementCounts(second);
        int score = 0;
        int maxCombinedCount = 0;

        for (Element element : Element.values()) {
            int firstCount = firstCounts.get(element);
            int secondCount = secondCounts.get(element);
            if (firstCount == 0 && secondCount >= 2) {
                score += 2;
            }
            if (secondCount == 0 && firstCount >= 2) {
                score += 2;
            }
            maxCombinedCount = Math.max(maxCombinedCount, firstCount + secondCount);
        }

        if (maxCombinedCount <= 4) {
            score += 5;
        } else if (maxCombinedCount == 5) {
            score += 2;
        } else if (maxCombinedCount >= 7) {
            score -= 5;
        }
        return clamp(score, -10, 10);
    }

    private int yinYangScore(Pillar[] first, Pillar[] second) {
        int characterCount = 0;
        int yinCount = 0;
        for (Pillar[] pillars : List.of(first, second)) {
            for (Pillar pillar : pillars) {
                if (pillar == null) {
                    continue;
                }
                characterCount += 2;
                yinCount += isYinStem(pillar.stem()) ? 1 : 0;
                yinCount += isYinBranch(pillar.branch()) ? 1 : 0;
            }
        }
        int distanceFromBalance = Math.abs(yinCount * 2 - characterCount);
        return clamp(5 - distanceFromBalance, -5, 5);
    }

    private Map<Element, Integer> elementCounts(Pillar[] pillars) {
        Map<Element, Integer> counts = new EnumMap<>(Element.class);
        for (Element element : Element.values()) {
            counts.put(element, 0);
        }
        for (Pillar pillar : pillars) {
            if (pillar == null) {
                continue;
            }
            counts.compute(stemElement(pillar.stem()), (key, value) -> value + 1);
            counts.compute(branchElement(pillar.branch()), (key, value) -> value + 1);
        }
        return counts;
    }

    private Pillar[] parse(SajuPillars pillars) {
        if (pillars == null) {
            throw new IllegalArgumentException("팔자는 null일 수 없습니다.");
        }
        return new Pillar[]{
                parsePillar(pillars.yearPillar(), "년주", false),
                parsePillar(pillars.monthPillar(), "월주", false),
                parsePillar(pillars.dayPillar(), "일주", false),
                parsePillar(pillars.hourPillar(), "시주", true)
        };
    }

    private Pillar parsePillar(String value, String name, boolean nullable) {
        if (nullable && (value == null || value.isBlank())) {
            return null;
        }
        if (value == null || value.codePointCount(0, value.length()) != 2) {
            throw new IllegalArgumentException(name + "는 천간과 지지 두 글자여야 합니다.");
        }
        char stem = value.charAt(0);
        char branch = value.charAt(1);
        if (STEMS.indexOf(stem) < 0 || BRANCHES.indexOf(branch) < 0) {
            throw new IllegalArgumentException(name + "에 올바르지 않은 천간 또는 지지가 있습니다: " + value);
        }
        return new Pillar(stem, branch);
    }

    private Element stemElement(char stem) {
        return Element.values()[STEMS.indexOf(stem) / 2];
    }

    private Element branchElement(char branch) {
        return switch (branch) {
            case '인', '묘' -> Element.WOOD;
            case '사', '오' -> Element.FIRE;
            case '진', '술', '축', '미' -> Element.EARTH;
            case '신', '유' -> Element.METAL;
            case '해', '자' -> Element.WATER;
            default -> throw new IllegalArgumentException("올바르지 않은 지지입니다: " + branch);
        };
    }

    private boolean isYinStem(char stem) {
        return STEMS.indexOf(stem) % 2 == 1;
    }

    private boolean isYinBranch(char branch) {
        return BRANCHES.indexOf(branch) % 2 == 1;
    }

    private boolean isTrine(char first, char second) {
        return first != second && TRINES.stream().anyMatch(group -> group.indexOf(first) >= 0 && group.indexOf(second) >= 0);
    }

    private static Set<String> pairs(String... values) {
        return Arrays.stream(values)
                .map(value -> pair(value.charAt(0), value.charAt(1)))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String pair(char first, char second) {
        return first <= second ? "" + first + second : "" + second + first;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record Pillar(char stem, char branch) {
    }

    private enum Element {
        WOOD, FIRE, EARTH, METAL, WATER;

        private boolean generates(Element other) {
            return (ordinal() + 1) % values().length == other.ordinal();
        }
    }
}
