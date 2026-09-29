package com.darkness.wks.admin;

import com.google.genai.Client;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.TimeZone;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 축제 통계가 KST 날짜·시 경계로 잘리는지 실제 PostgreSQL 로 확인한다.
 * 운영 EC2 가 KST 가 아니라서, JVM 기본 시간대(= JDBC 세션 시간대)를 KST 도 UTC 도 아닌 값으로 바꿔 놓고 돌린다 —
 * 어느 기본값에 기대는 코드가 있으면 날짜 버킷이 밀려 실패한다.
 */
@SpringBootTest(properties = {"gemini.api-key=test-key", "app.admin.token=" + AdminStatsFlowTest.TOKEN,
        "app.auth.jwt.secret=admin-stats-test-secret-0123456789-abcdef"})
@Testcontainers
class AdminStatsFlowTest {

    static final String TOKEN = "admin-stats-test-token";

    private static TimeZone originalZone;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @MockitoBean
    Client geminiClient;

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    WebApplicationContext webContext;

    @BeforeAll
    static void 시간대를_KST도_UTC도_아니게() {
        // 스프링 컨텍스트(커넥션 풀)가 뜨기 전에 바꿔야 JDBC 세션 시간대에도 적용된다
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
    }

    @AfterAll
    static void 시간대_복구() {
        TimeZone.setDefault(originalZone);
    }

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(webContext).build();
    }

    private static Timestamp at(String instant) {
        return Timestamp.from(Instant.parse(instant));
    }

    private UUID result(String gender, String createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO result (id, nickname, birth_date, gender, year_pillar, month_pillar, day_pillar, created_at)
                VALUES (?, '테스트', DATE '2000-01-01', ?, '갑자', '을축', '병인', ?)
                """, id, gender, at(createdAt));
        return id;
    }

    /** 회원의 사주 결과 — 소개팅 성별의 출처. 사주 통계에 안 섞이게 축제 전에 만든다. */
    private void memberResult(long memberId, String gender) {
        jdbc.update("""
                INSERT INTO result (id, nickname, birth_date, gender, year_pillar, month_pillar, day_pillar,
                                    member_id, created_at)
                VALUES (?, '테스트', DATE '2000-01-01', ?, '갑자', '을축', '병인', ?, ?)
                """, UUID.randomUUID(), gender, memberId, at("2026-09-01T00:00:00Z"));
    }

    private void compatibility(UUID origin, UUID guest, String tier, String createdAt) {
        jdbc.update("""
                INSERT INTO compatibility (origin_id, guest_id, score, tier, created_at)
                VALUES (?, ?, 80, ?, ?)
                """, origin, guest, tier, at(createdAt));
    }

    private long member(long kakaoId) {
        return jdbc.queryForObject("INSERT INTO member (kakao_id) VALUES (?) RETURNING id", Long.class, kakaoId);
    }

    private UUID profile(long memberId, String createdAt, String deactivatedAt) {
        UUID photoId = UUID.randomUUID();
        jdbc.update("INSERT INTO dating_photo (id, member_id, object_key) VALUES (?, ?, ?)",
                photoId, memberId, "dating-photos/" + photoId + ".jpg");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dating_profile (id, member_id, email, name, contact_method, contact_value, department,
                                            mbti, bio, photo_id, verified_at, deactivated_at, created_at)
                VALUES (?, ?, ?, '이름', 'INSTAGRAM', 'insta', '컴퓨터공학과', 'ENFP', '안녕', ?, ?, ?, ?)
                """, id, memberId, "m" + memberId + "@dgu.ac.kr", photoId, at(createdAt),
                deactivatedAt == null ? null : at(deactivatedAt), at(createdAt));
        return id;
    }

    private void request(UUID sender, UUID recipient, String status, String createdAt, String respondedAt) {
        jdbc.update("""
                INSERT INTO dating_request (id, sender_profile_id, recipient_profile_id, status, created_at, responded_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), sender, recipient, status, at(createdAt),
                respondedAt == null ? null : at(respondedAt));
    }

    private void recommendation(long viewer, UUID candidate, String createdAt) {
        jdbc.update("""
                INSERT INTO dating_recommendation (viewer_member_id, candidate_profile_id, score, active, created_at)
                VALUES (?, ?, 70, true, ?)
                """, viewer, candidate, at(createdAt));
    }

    @Test
    void 토큰_없으면_401() throws Exception {
        mvc().perform(get("/api/admin/stats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 축제_3일을_KST_날짜와_시로_센다() throws Exception {
        // KST = UTC+9. 경계: 09-29 00:00 KST = 09-28T15:00Z, 10-02 00:00 KST = 10-01T15:00Z
        UUID r1 = result("MALE", "2026-09-28T15:00:00Z");      // 09-29 00시 — 첫 칸
        UUID r2 = result("FEMALE", "2026-09-29T15:30:00Z");    // 09-30 00시 — UTC 로는 아직 09-29
        UUID r3 = result("FEMALE", "2026-10-01T14:59:59Z");    // 10-01 23시 — 마지막 칸
        result("FEMALE", "2026-09-28T14:59:59Z");              // 09-28 23:59 KST — 제외
        result("MALE", "2026-10-01T15:00:00Z");                // 10-02 00:00 KST — 제외

        compatibility(r1, r2, "GUIIN", "2026-09-29T03:00:00Z"); // 09-29 12시
        compatibility(r1, r3, "BEOT", "2026-09-30T05:00:00Z");  // 09-30 14시
        compatibility(r2, r3, "GUIIN", "2026-09-28T10:00:00Z"); // 축제 전 — 제외

        long m1 = member(1), m2 = member(2), m3 = member(3), m4 = member(4);
        memberResult(m1, "MALE");
        memberResult(m2, "FEMALE");
        memberResult(m3, "FEMALE");                                             // m4 는 결과 없음 → UNKNOWN
        UUID p1 = profile(m1, "2026-09-29T01:00:00Z", null);                    // 09-29 10시
        UUID p2 = profile(m2, "2026-09-30T01:00:00Z", "2026-09-30T02:00:00Z");  // 09-30 10시, 비활성
        UUID p3 = profile(m3, "2026-09-20T01:00:00Z", null);                    // 축제 전 — 등록 수 제외, 풀에는 포함
        UUID p4 = profile(m4, "2026-09-21T01:00:00Z", null);                    // 축제 전, 성별 모름

        request(p1, p2, "ACCEPTED", "2026-09-30T03:00:00Z", "2026-09-30T04:00:00Z");  // 수락 09-30 13시
        request(p3, p1, "PENDING", "2026-10-01T03:00:00Z", null);
        request(p2, p3, "REJECTED", "2026-09-30T06:00:00Z", "2026-09-30T06:30:00Z");
        request(p3, p2, "CANCELLED", "2026-09-28T03:00:00Z", "2026-09-28T04:00:00Z"); // 축제 전 — 제외
        request(p4, p3, "ACCEPTED", "2026-09-27T03:00:00Z", "2026-09-29T16:00:00Z");  // 요청은 축제 전, 수락 09-30 01시

        recommendation(m1, p2, "2026-09-30T02:30:00Z");
        recommendation(m1, p3, "2026-09-30T02:30:00Z");        // 같은 조회자 — 한 명으로 센다
        recommendation(m2, p1, "2026-10-01T02:30:00Z");
        recommendation(m3, p1, "2026-09-28T02:30:00Z");        // 축제 전 — 제외

        mvc().perform(get("/api/admin/stats").header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.data.from").value("2026-09-29"))
                .andExpect(jsonPath("$.data.to").value("2026-10-01"))

                .andExpect(jsonPath("$.data.saju.results.total").value(3))
                .andExpect(jsonPath("$.data.saju.results.days.length()").value(3))
                .andExpect(jsonPath("$.data.saju.results.days[0].date").value("2026-09-29"))
                .andExpect(jsonPath("$.data.saju.results.days[0].count").value(1))
                .andExpect(jsonPath("$.data.saju.results.days[0].hourly.length()").value(24))
                .andExpect(jsonPath("$.data.saju.results.days[0].hourly[0]").value(1))
                .andExpect(jsonPath("$.data.saju.results.days[1].hourly[0]").value(1))
                .andExpect(jsonPath("$.data.saju.results.days[2].hourly[23]").value(1))
                .andExpect(jsonPath("$.data.saju.byGender.MALE").value(1))
                .andExpect(jsonPath("$.data.saju.byGender.FEMALE").value(2))

                .andExpect(jsonPath("$.data.compatibility.created.total").value(2))
                .andExpect(jsonPath("$.data.compatibility.created.days[0].hourly[12]").value(1))
                .andExpect(jsonPath("$.data.compatibility.created.days[1].hourly[14]").value(1))
                .andExpect(jsonPath("$.data.compatibility.byTier.GUIIN").value(1))
                .andExpect(jsonPath("$.data.compatibility.byTier.BEOT").value(1))
                .andExpect(jsonPath("$.data.compatibility.byTier.CHALTTEOK").value(0))
                .andExpect(jsonPath("$.data.compatibility.byTier.SEUCHIM").value(0))

                .andExpect(jsonPath("$.data.dating.profiles.total").value(2))
                .andExpect(jsonPath("$.data.dating.profiles.days[0].hourly[10]").value(1))
                .andExpect(jsonPath("$.data.dating.verifiedProfiles").value(2))
                .andExpect(jsonPath("$.data.dating.deactivatedProfiles").value(1))
                .andExpect(jsonPath("$.data.dating.recommendationViewers").value(2))
                .andExpect(jsonPath("$.data.dating.requests.total").value(3))
                .andExpect(jsonPath("$.data.dating.requestsByStatus.PENDING").value(1))
                .andExpect(jsonPath("$.data.dating.requestsByStatus.ACCEPTED").value(1))
                .andExpect(jsonPath("$.data.dating.requestsByStatus.REJECTED").value(1))
                .andExpect(jsonPath("$.data.dating.requestsByStatus.CANCELLED").value(0))

                // 성비: 기간 안 등록(p1·p2) / 지금 추천 풀(p1·p3·p4, p2 는 비활성)
                .andExpect(jsonPath("$.data.dating.profilesByGender.MALE").value(1))
                .andExpect(jsonPath("$.data.dating.profilesByGender.FEMALE").value(1))
                .andExpect(jsonPath("$.data.dating.profilesByGender.UNKNOWN").doesNotExist())
                .andExpect(jsonPath("$.data.dating.poolByGender.MALE").value(1))
                .andExpect(jsonPath("$.data.dating.poolByGender.FEMALE").value(1))
                .andExpect(jsonPath("$.data.dating.poolByGender.UNKNOWN").value(1))

                // 보낸 쪽 성별: MALE p1(수락 1) / FEMALE p3(대기 1)·p2(거절 1)
                .andExpect(jsonPath("$.data.dating.requestsBySenderGender.MALE.sent").value(1))
                .andExpect(jsonPath("$.data.dating.requestsBySenderGender.MALE.accepted").value(1))
                .andExpect(jsonPath("$.data.dating.requestsBySenderGender.FEMALE.sent").value(2))
                .andExpect(jsonPath("$.data.dating.requestsBySenderGender.FEMALE.accepted").value(0))
                .andExpect(jsonPath("$.data.dating.requestsBySenderGender.FEMALE.rejected").value(1))
                .andExpect(jsonPath("$.data.dating.requestsBySenderGender.FEMALE.pending").value(1))

                // 성사: 기간 안에 수락된 것(축제 전 요청 포함), 수락 시각 KST 로
                .andExpect(jsonPath("$.data.dating.accepted.total").value(2))
                .andExpect(jsonPath("$.data.dating.accepted.days[1].hourly[1]").value(1))
                .andExpect(jsonPath("$.data.dating.accepted.days[1].hourly[13]").value(1))
                .andExpect(jsonPath("$.data.dating.acceptedMembers").value(4));
    }
}
