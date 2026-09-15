package com.darkness.wks.saju;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LuckyPlaceTest {

    @Test
    void weakDayMasterGetsHelpingElement() {
        // 일간 갑(목). 나머지 전부 금·화 → 돕는 세력(목·수) 거의 없음 = 신약 → 인성(수)·비겁(목) 중 더 부족한 쪽
        Element e = LuckyPlace.luckyElement(new SajuPillars("경신", "병오", "갑신", "병오"));
        assertThat(e).isIn(Element.WATER, Element.WOOD);
    }

    @Test
    void strongDayMasterGetsDrainingElement() {
        // 일간 갑(목). 나머지 전부 목·수 → 신강 → 식상(화)·재성(토)·관성(금) 중 가장 부족한 쪽
        Element e = LuckyPlace.luckyElement(new SajuPillars("갑인", "계묘", "갑인", "임자"));
        assertThat(e).isIn(Element.FIRE, Element.EARTH, Element.METAL);
    }

    @Test
    void sameDaySamePlaceAndFromPool() {
        SajuPillars p = new SajuPillars("임오", "계묘", "신사", "을미");
        LocalDate d = LocalDate.of(2026, 9, 29);
        String place = LuckyPlace.of(p, d);
        assertThat(LuckyPlace.of(p, d)).isEqualTo(place);
        assertThat(List.of("상록원", "팔정도", "혜화관", "대운동장", "만해광장", "학관", "명진관", "본관", "원흥관", "법학만해관",
                "신공학관", "경영관", "사회과학관", "중앙도서관", "다향관", "동대입구역", "충무로역")).contains(place);
    }

    @Test
    void placeChangesAcrossDaysWithinSameElementPool() {
        SajuPillars p = new SajuPillars("임오", "계묘", "신사", "을미");
        LocalDate d = LocalDate.of(2026, 9, 29);
        long distinct = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> LuckyPlace.of(p, d.plusDays(i))).distinct().count();
        assertThat(distinct).isGreaterThan(1); // 오행 풀 안에서 날짜별로 바뀐다
    }

    @Test
    void worksWithoutHourPillar() {
        assertThat(LuckyPlace.of(new SajuPillars("경진", "기축", "무술", null), LocalDate.of(2026, 9, 29))).isNotBlank();
    }
}
