package com.darkness.wks.saju;

import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * 생년월일시(양력, KST)·지역으로 사주 4주(년/월/일/시주)를 계산한다.
 * <p>
 * 절기·60갑자는 lunar-java(cn.6tail:lunar) 에 맡긴다. 단, lunar-java 는 GMT+8(중국) 벽시계 기준이라
 * 절기 판정(연주·월주)에는 KST 에서 1시간을 뺀 시각을 넣는다. 일주·시주는 출생지 진태양시 기준이므로
 * 별도의 시각으로 다시 계산한다.
 * <p>
 * 관법: 야자시 — 진태양시 23시 이후는 다음날 일주·시주로 본다 (포스텔러와 동일). 음력 입력은
 * {@link KoreanLunarCalendar} 로 먼저 양력 변환한다.
 * DB(Repository/Entity)·스프링 컨텍스트에 의존하지 않는다.
 */
public class SajuCalculator {

    private static final String GAN = "甲乙丙丁戊己庚辛壬癸";
    private static final String ZHI = "子丑寅卯辰巳午未申酉戌亥";
    private static final String GAN_KO = "갑을병정무기경신임계";
    private static final String ZHI_KO = "자축인묘진사오미신유술해";

    private static final double KST_MERIDIAN = 135.0;
    private static final double SEOUL_LONGITUDE = 126.978;

    /** 시도별 대표 경도. birthRegion 문자열에 키가 포함되면 사용, 없으면 서울. */
    private static final Map<String, Double> REGION_LONGITUDE = Map.ofEntries(
            Map.entry("서울", SEOUL_LONGITUDE),
            Map.entry("인천", 126.705),
            Map.entry("경기", 127.03),
            Map.entry("강원", 127.73),
            Map.entry("충북", 127.49),
            Map.entry("충남", 126.66),
            Map.entry("대전", 127.385),
            Map.entry("세종", 127.29),
            Map.entry("전북", 127.15),
            Map.entry("전남", 126.46),
            Map.entry("광주", 126.85),
            Map.entry("경북", 128.73),
            Map.entry("대구", 128.60),
            Map.entry("경남", 128.69),
            Map.entry("부산", 129.075),
            Map.entry("울산", 129.31),
            Map.entry("제주", 126.53)
    );

    /**
     * @param birthTime   null 이면 시주 생략(null). 연·월·일주는 정오 기준으로 판정
     * @param birthRegion null 이거나 모르는 지역이면 서울 경도 기준
     */
    public SajuPillars calculate(LocalDate birthDate, LocalTime birthTime, String birthRegion) {
        LocalDateTime kst = birthDate.atTime(birthTime == null ? LocalTime.NOON : birthTime);

        Lunar forTerms = toLunar(kst.minusHours(1)); // GMT+8 보정: 절기는 절대 순간으로 판정
        // 진태양시: 경도 1° = 4분. ponytail: 균시차(±16분)·1954~61년 UTC+8:30·서머타임 미보정, 필요해지면 여기서
        LocalDateTime apparent = kst.plusSeconds(Math.round((longitude(birthRegion) - KST_MERIDIAN) * 240));
        Lunar forDay = toLunar(apparent);

        return new SajuPillars(
                toKorean(forTerms.getYearInGanZhiExact()),
                toKorean(forTerms.getMonthInGanZhiExact()),
                toKorean(forDay.getDayInGanZhiExact()),   // 야자시: 23시 이후는 다음날
                birthTime == null ? null : toKorean(forDay.getTimeInGanZhi())
        );
    }

    private static Lunar toLunar(LocalDateTime t) {
        return Solar.fromYmdHms(t.getYear(), t.getMonthValue(), t.getDayOfMonth(),
                t.getHour(), t.getMinute(), t.getSecond()).getLunar();
    }

    private static double longitude(String region) {
        if (region != null) {
            for (Map.Entry<String, Double> e : REGION_LONGITUDE.entrySet()) {
                if (region.contains(e.getKey())) {
                    return e.getValue();
                }
            }
        }
        return SEOUL_LONGITUDE;
    }

    private static String toKorean(String ganZhi) {
        return "" + GAN_KO.charAt(GAN.indexOf(ganZhi.charAt(0))) + ZHI_KO.charAt(ZHI.indexOf(ganZhi.charAt(1)));
    }
}
