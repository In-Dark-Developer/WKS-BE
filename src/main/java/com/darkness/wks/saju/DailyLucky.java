package com.darkness.wks.saju;

import com.nlf.calendar.Solar;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 오늘의 행운 오행·아이템·장소. 매일 바뀌고 사주에 묶인다. LLM·외부 API 없음.
 * <p>
 * 행운 오행 = 내 일간(일주 천간)과 오늘 일진(日辰) 천간의 관계(십성)에 대응하는 오행 — {@link Element#luckyAgainst}.
 * 천간 오행은 이틀씩 같으므로 오행은 이틀 주기, 아이템·장소는 (내 일주 번호 + 오늘 일진 번호) 로 골라 매일 바뀐다.
 * 같은 사람 같은 날은 같고, 사람·날짜가 다르면 달라진다.
 * 풀은 {@code resources/lucky/items.txt}, {@code places.txt} (기획이 편집).
 */
public record DailyLucky(Element element, String item, String place) {

    private static final Map<Element, List<String>> ITEMS = load("lucky/items.txt");
    private static final Map<Element, List<String>> PLACES = load("lucky/places.txt");

    public static DailyLucky of(SajuPillars pillars, LocalDate today) {
        String dayGanji = todayPillar(today);
        Element element = Element.ofStem(pillars.dayPillar().charAt(0)).luckyAgainst(Element.ofStem(dayGanji.charAt(0)));
        int key = ganjiIndex(pillars.dayPillar()) + ganjiIndex(dayGanji);
        return new DailyLucky(element, pick(ITEMS, element, key), pick(PLACES, element, key));
    }

    /** 오늘 일진 (한글 2글자). lunar-java 의 GMT+8 기준이지만 날짜 단위라 KST 와 같다 */
    static String todayPillar(LocalDate date) {
        return SajuCalculator.toKorean(Solar.fromYmd(date.getYear(), date.getMonthValue(), date.getDayOfMonth()).getLunar().getDayInGanZhi());
    }

    /** 60갑자 순번 (갑자=0 … 계해=59) */
    static int ganjiIndex(String ganji) {
        int stem = "갑을병정무기경신임계".indexOf(ganji.charAt(0));
        int branch = "자축인묘진사오미신유술해".indexOf(ganji.charAt(1));
        return Math.floorMod(6 * stem - 5 * branch, 60);
    }

    private static String pick(Map<Element, List<String>> pool, Element element, int key) {
        List<String> list = pool.get(element);
        return list.get(key % list.size());
    }

    private static Map<Element, List<String>> load(String path) {
        Map<Element, List<String>> map = new EnumMap<>(Element.class);
        try (InputStream in = DailyLucky.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("resource not found: " + path);
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] kv = line.split("=", 2);
                map.put(Element.valueOf(kv[0].strip()), List.of(kv[1].split("\\|")));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Element e : Element.values()) {
            if (map.getOrDefault(e, List.of()).isEmpty()) throw new IllegalStateException(path + " 에 " + e + " 항목이 없다");
        }
        return map;
    }
}
