package com.darkness.wks.result.entity;

import com.darkness.wks.saju.CalendarType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.darkness.wks.common.Gender;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Result {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "share_id", updatable = false, nullable = false, unique = true)
    private UUID shareId = UUID.randomUUID();

    @Column(name = "nickname", length = 20, nullable = false)
    private String nickname;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @JdbcTypeCode(SqlTypes.LOCAL_TIME)
    @Column(name = "birth_time")
    private LocalTime birthTime;

    @Column(name = "birth_region", length = 50)
    private String birthRegion;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10, nullable = false)
    private Gender gender;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "year_pillar", length = 2, nullable = false)
    private String yearPillar;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "month_pillar", length = 2, nullable = false)
    private String monthPillar;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "day_pillar", length = 2, nullable = false)
    private String dayPillar;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "hour_pillar", length = 2)
    private String hourPillar;

    /** 입력 폼 자동 채움용 원본 입력값 (#66). birth_date 는 양력 변환값이라 음력 입력을 복원할 수 없다 */
    @Enumerated(EnumType.STRING)
    @Column(name = "calendar_type", length = 10, nullable = false)
    private CalendarType calendarType = CalendarType.SOLAR;

    /** 입력한 날짜 문자열 그대로. LUNAR 면 음력 날짜 */
    @Column(name = "birth_date_input", length = 10)
    private String birthDateInput;

    @Column(name = "is_leap_month", nullable = false)
    private boolean leapMonth;

    /**
     * 로그인 회원이 "저장"한 결과 (V11, 2026-09-22). 플랫 컬럼만 두고 {@code member} 패키지는
     * 참조하지 않는다(architecture.md §4) — 익명 조회·궁합은 이 필드와 무관하게 그대로 동작한다.
     * 계정당 결과 1개(부분 unique, DB 가 강제). 회원이 삭제되면 NULL 로 돌아간다.
     */
    @Column(name = "member_id")
    private Long memberId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 닉네임만 바꾼다 (#68). 팔자·점수·해석은 그대로라 재생성이 필요 없고 `resultId`·`shareId`·궁합 기록이 유지된다.
     * 닉네임은 compatibility 에 복사돼 있지 않아 궁합 목록·공유 페이지에도 자동 반영된다.
     */
    public void rename(String nickname) {
        this.nickname = nickname;
    }

    /**
     * 로그인 회원이 이 결과를 "내 결과"로 연결한다 (plan.md §1.1). 호출 전에 이미 다른 회원 것이 아닌지
     * 확인돼 있어야 한다({@code ResultRepository.findByIdAndMemberIdIsNull}) — 여기서는 그냥 덮어쓴다.
     */
    public void linkMember(Long memberId) {
        this.memberId = memberId;
    }

    public Result(String nickname, LocalDate birthDate, LocalTime birthTime, String birthRegion,
                  Gender gender, String yearPillar, String monthPillar, String dayPillar, String hourPillar) {
        this(nickname, birthDate, birthTime, birthRegion, gender, yearPillar, monthPillar, dayPillar, hourPillar,
                CalendarType.SOLAR, birthDate.toString(), false);
    }

    public Result(String nickname, LocalDate birthDate, LocalTime birthTime, String birthRegion,
                  Gender gender, String yearPillar, String monthPillar, String dayPillar, String hourPillar,
                  CalendarType calendarType, String birthDateInput, boolean leapMonth) {
        this.calendarType = calendarType;
        this.birthDateInput = birthDateInput;
        this.leapMonth = leapMonth;
        this.nickname = nickname;
        this.birthDate = birthDate;
        this.birthTime = birthTime;
        this.birthRegion = birthRegion;
        this.gender = gender;
        this.yearPillar = yearPillar;
        this.monthPillar = monthPillar;
        this.dayPillar = dayPillar;
        this.hourPillar = hourPillar;
    }
}
