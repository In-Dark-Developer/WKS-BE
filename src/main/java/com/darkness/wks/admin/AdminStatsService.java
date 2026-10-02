package com.darkness.wks.admin;

import com.darkness.wks.admin.dto.AdminStatsResponse;
import com.darkness.wks.admin.dto.AdminStatsResponse.DailyCount;
import com.darkness.wks.admin.dto.AdminStatsResponse.Series;
import com.darkness.wks.common.Gender;
import com.darkness.wks.compatibility.entity.CompatibilityTier;
import com.darkness.wks.dating.entity.DatingRequestStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 운영자 통계. 여러 도메인 테이블을 읽기만 하므로 각 패키지 Repository 를 늘리지 않고 여기서 집계 SQL 로 센다.
 *
 * <p>시간대: 운영 EC2(=JVM·컨테이너)와 DB 세션의 시간대가 KST 가 아니다. 그래서 어느 쪽 기본값에도 기대지 않는다 —
 * 기간 경계는 Java 에서 KST 로 만든 절대 시각({@link OffsetDateTime})으로 넘기고, 날짜·시 버킷은 SQL 에서
 * {@code AT TIME ZONE 'Asia/Seoul'} 로 자른다. 날짜는 {@code getObject(LocalDate)} 로 읽는다
 * ({@code getDate} 는 JVM 기본 시간대로 변환해 하루가 밀릴 수 있다).
 */
@Service
public class AdminStatsService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // 축제 3일(2026-09-29 ~ 10-01, AGENTS.md) + 축제 뒤 이틀(10-02·10-03). 소개팅이 축제 뒤에도 이어져
    // 그 추이도 보려고 늘렸다(2026-10-02). 통계는 이 기간만 본다(2026-09-29 결정)
    static final LocalDate FESTIVAL_FIRST_DAY = LocalDate.of(2026, 9, 29);
    static final int FESTIVAL_DAYS = 5;

    // 테이블·컬럼 이름은 아래 상수에서만 온다 — 사용자 입력이 SQL 문자열에 들어가지 않는다
    private static final String SERIES_SQL = """
            SELECT (%2$s AT TIME ZONE 'Asia/Seoul')::date AS day,
                   EXTRACT(HOUR FROM %2$s AT TIME ZONE 'Asia/Seoul')::int AS hour,
                   count(*) AS cnt
            FROM %1$s
            WHERE %2$s >= ? AND %2$s < ? %3$s
            GROUP BY 1, 2
            """;
    // 프로필 성별은 회원의 사주 결과에서 온다(추천도 같은 방식, DatingRecommendationService). 회원당 결과는 1개
    private static final String PROFILE_GENDER_SQL = """
            SELECT COALESCE(r.gender, 'UNKNOWN') AS k, count(*) AS cnt
            FROM dating_profile p LEFT JOIN result r ON r.member_id = p.member_id
            WHERE %s
            GROUP BY 1
            """;
    private static final String GROUP_SQL = """
            SELECT %s AS k, count(*) AS cnt
            FROM %s
            WHERE created_at >= ? AND created_at < ?
            GROUP BY 1
            """;

    private final JdbcTemplate jdbcTemplate;

    public AdminStatsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse festivalStats() {
        LocalDate last = FESTIVAL_FIRST_DAY.plusDays(FESTIVAL_DAYS - 1);
        OffsetDateTime start = FESTIVAL_FIRST_DAY.atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime end = last.plusDays(1).atStartOfDay(KST).toOffsetDateTime();

        var saju = new AdminStatsResponse.Saju(
                series("result", start, end),
                groupCount("gender", "result", keys(Gender.values()), start, end));
        var compatibility = new AdminStatsResponse.Compatibility(
                series("compatibility", start, end),
                groupCount("tier", "compatibility", keys(CompatibilityTier.values()), start, end));

        Map<String, Object> profileStates = jdbcTemplate.queryForMap("""
                SELECT count(*) FILTER (WHERE verified_at IS NOT NULL) AS verified,
                       count(*) FILTER (WHERE deactivated_at IS NOT NULL) AS deactivated
                FROM dating_profile
                WHERE created_at >= ? AND created_at < ?
                """, start, end);
        Long viewers = jdbcTemplate.queryForObject("""
                SELECT count(DISTINCT viewer_member_id) FROM dating_recommendation
                WHERE created_at >= ? AND created_at < ?
                """, Long.class, start, end);
        Long acceptedMembers = jdbcTemplate.queryForObject("""
                SELECT count(DISTINCT profile_id) FROM (
                    SELECT sender_profile_id AS profile_id, responded_at FROM dating_request WHERE status = 'ACCEPTED'
                    UNION ALL
                    SELECT recipient_profile_id, responded_at FROM dating_request WHERE status = 'ACCEPTED'
                ) accepted
                WHERE responded_at >= ? AND responded_at < ?
                """, Long.class, start, end);
        var dating = new AdminStatsResponse.Dating(
                series("dating_profile", start, end),
                ((Number) profileStates.get("verified")).longValue(),
                ((Number) profileStates.get("deactivated")).longValue(),
                viewers == null ? 0 : viewers,
                profileGender("p.created_at >= ? AND p.created_at < ?", start, end),
                profileGender("p.verified_at IS NOT NULL AND p.deactivated_at IS NULL"),
                series("dating_request", start, end),
                groupCount("status", "dating_request", keys(DatingRequestStatus.values()), start, end),
                requestsBySenderGender(start, end),
                series("dating_request", "responded_at", "AND status = 'ACCEPTED'", start, end),
                acceptedMembers == null ? 0 : acceptedMembers);

        return new AdminStatsResponse(KST.getId(), FESTIVAL_FIRST_DAY, last, Instant.now(),
                saju, compatibility, dating);
    }

    /** 축제 날짜마다 24칸을 0 으로 깔고 DB 결과를 채운다. 기록이 없는 날·시간도 빠지지 않는다. */
    private Series series(String table, OffsetDateTime start, OffsetDateTime end) {
        return series(table, "created_at", "", start, end);
    }

    private Series series(String table, String timeColumn, String condition, OffsetDateTime start, OffsetDateTime end) {
        Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
        for (int i = 0; i < FESTIVAL_DAYS; i++) {
            byDay.put(FESTIVAL_FIRST_DAY.plusDays(i), new long[24]);
        }
        jdbcTemplate.query(SERIES_SQL.formatted(table, timeColumn, condition), rs -> {
            long[] hours = byDay.get(rs.getObject("day", LocalDate.class));
            if (hours != null) {
                hours[rs.getInt("hour")] = rs.getLong("cnt");
            }
        }, start, end);

        long total = 0;
        List<DailyCount> days = new ArrayList<>(FESTIVAL_DAYS);
        for (Map.Entry<LocalDate, long[]> entry : byDay.entrySet()) {
            long dayTotal = Arrays.stream(entry.getValue()).sum();
            total += dayTotal;
            days.add(new DailyCount(entry.getKey(), dayTotal, Arrays.stream(entry.getValue()).boxed().toList()));
        }
        return new Series(total, Collections.unmodifiableList(days));
    }

    private Map<String, Long> groupCount(String column, String table, List<String> keys,
                                         OffsetDateTime start, OffsetDateTime end) {
        Map<String, Long> counts = new LinkedHashMap<>();
        keys.forEach(key -> counts.put(key, 0L));
        jdbcTemplate.query(GROUP_SQL.formatted(column, table),
                rs -> { counts.put(rs.getString("k"), rs.getLong("cnt")); }, start, end);
        return counts;
    }

    /** MALE·FEMALE 는 0 이어도 채우고, 결과 없는 회원(UNKNOWN)은 있을 때만 키가 생긴다. */
    private Map<String, Long> profileGender(String condition, Object... args) {
        Map<String, Long> counts = new LinkedHashMap<>();
        keys(Gender.values()).forEach(key -> counts.put(key, 0L));
        jdbcTemplate.query(PROFILE_GENDER_SQL.formatted(condition),
                rs -> { counts.put(rs.getString("k"), rs.getLong("cnt")); }, args);
        return counts;
    }

    private Map<String, AdminStatsResponse.RequestOutcome> requestsBySenderGender(OffsetDateTime start,
                                                                                  OffsetDateTime end) {
        Map<String, AdminStatsResponse.RequestOutcome> outcomes = new LinkedHashMap<>();
        keys(Gender.values()).forEach(key -> outcomes.put(key, new AdminStatsResponse.RequestOutcome(0, 0, 0, 0)));
        jdbcTemplate.query("""
                SELECT COALESCE(r.gender, 'UNKNOWN') AS k,
                       count(*) AS sent,
                       count(*) FILTER (WHERE q.status = 'ACCEPTED') AS accepted,
                       count(*) FILTER (WHERE q.status = 'REJECTED') AS rejected,
                       count(*) FILTER (WHERE q.status = 'PENDING') AS pending
                FROM dating_request q
                JOIN dating_profile p ON p.id = q.sender_profile_id
                LEFT JOIN result r ON r.member_id = p.member_id
                WHERE q.created_at >= ? AND q.created_at < ?
                GROUP BY 1
                """, rs -> {
            outcomes.put(rs.getString("k"), new AdminStatsResponse.RequestOutcome(rs.getLong("sent"),
                    rs.getLong("accepted"), rs.getLong("rejected"), rs.getLong("pending")));
        }, start, end);
        return outcomes;
    }

    private static List<String> keys(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
