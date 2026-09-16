package com.darkness.wks.result;

import com.darkness.wks.result.entity.Reading;
import org.springframework.data.jpa.repository.JpaRepository;

import com.darkness.wks.common.Gender;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public interface ReadingRepository extends JpaRepository<Reading, UUID> {

    /**
     * 같은 입력·같은 버전의 최신 해석 (#62). 시간 모름(null)은 null 끼리만 맞는다.
     * 시간은 SQL 로 비교하지 않는다: {@code LocalTime} 파라미터 바인딩이 엔티티의 {@code @JdbcTypeCode(LOCAL_TIME)} 을 안 타서
     * 14:30 이 안 맞았다. 같은 날짜·성별·버전 후보(축제 규모에서 몇 건)를 가져와 자바에서 거른다.
     * ponytail: 동시 요청 둘 다 miss 면 둘 다 분석한다. 무해
     */
    default Optional<Reading> findReusable(LocalDate birthDate, LocalTime birthTime, Gender gender, int version) {
        return findReusableCandidates(birthDate, gender, version).stream()
                .filter(rd -> Objects.equals(rd.getResult().getBirthTime(), birthTime))
                .findFirst();
    }

    @Query("""
            select rd from Reading rd join fetch rd.result r
            where r.birthDate = :birthDate and r.gender = :gender and rd.version = :version
            order by rd.createdAt desc
            """)
    List<Reading> findReusableCandidates(@Param("birthDate") LocalDate birthDate,
                                         @Param("gender") Gender gender, @Param("version") int version);

}
