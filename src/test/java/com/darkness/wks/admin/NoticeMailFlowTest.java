package com.darkness.wks.admin;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.Gender;
import com.darkness.wks.dating.DatingNoticeMailService;
import com.darkness.wks.dating.DatingPhotoRepository;
import com.darkness.wks.dating.DatingProfileRepository;
import com.darkness.wks.dating.entity.DatingPhoto;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.member.MemberRepository;
import com.darkness.wks.member.entity.Member;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import com.google.genai.Client;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 일괄 안내 메일(#159)이 대상 필터·중복 방지·실패 재시도를 DB 로 지키는지 확인한다. SMTP 는 목킹한다.
 * 프로필은 클래스 안에서 공유되므로 대상 수를 보는 검증은 한 테스트에 모았다.
 */
@SpringBootTest(properties = {"gemini.api-key=test-key", "app.admin.token=" + NoticeMailFlowTest.TOKEN,
        "app.auth.jwt.secret=notice-mail-test-secret-0123456789-abcdef", "app.notice-mail.interval-ms=0",
        "app.notice-mail.poll-ms=3600000"})
@Testcontainers
class NoticeMailFlowTest {

    static final String TOKEN = "notice-mail-test-token";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @MockitoBean
    Client geminiClient;

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    DatingPhotoRepository photoRepository;

    @Autowired
    DatingProfileRepository profileRepository;

    @Autowired
    WebApplicationContext webContext;

    @Autowired
    DatingNoticeMailService noticeMailService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final List<String> recipients = Collections.synchronizedList(new ArrayList<>());
    private final List<String> subjects = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> failingRecipients = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void stubMail() throws Exception {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(invocation -> {
            MimeMessage message = invocation.getArgument(0);
            String to = message.getAllRecipients()[0].toString();
            if (failingRecipients.contains(to)) {
                throw new MailSendException("smtp down");
            }
            recipients.add(to);
            subjects.add(message.getSubject());
            return null;
        }).when(mailSender).send(any(MimeMessage.class));
    }

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(webContext).build();
    }

    private DatingProfile profile(long kakaoId, String email) {
        Member member = memberRepository.saveAndFlush(new Member(kakaoId));
        Result result = new Result("테스트", LocalDate.of(2000, 1, 1), null, null, Gender.FEMALE,
                "갑자", "을축", "병인", null);
        result.linkMember(member.getId());
        resultRepository.saveAndFlush(result);
        DatingPhoto photo = photoRepository.saveAndFlush(
                new DatingPhoto(member.getId(), "dating-photos/" + member.getId() + "/" + UUID.randomUUID() + ".jpg"));
        DatingProfile profile = new DatingProfile(member.getId(), email, "이름", ContactMethod.INSTAGRAM,
                "insta_" + kakaoId, "컴퓨터공학과", "ENFP", "안녕하세요", photo);
        profile.markVerified(Instant.now());
        return profileRepository.saveAndFlush(profile);
    }

    private ResultActions request(String campaignKey, String mode, String testTo) throws Exception {
        String testToJson = testTo == null ? "null" : "\"" + testTo + "\"";
        return mvc().perform(post("/api/admin/notice-mails").header("X-Admin-Token", TOKEN)
                .contentType("application/json")
                .content("""
                        {"campaignKey":"%s","subject":"제목","body":"내용","mode":"%s","testTo":%s}
                        """.formatted(campaignKey, mode, testToJson)));
    }

    // SEND 는 백그라운드로 돌므로 집계가 기대치와 같아질 때까지 기다린다. "처리 건수 ≥ n" 으로 기다리면
    // 재시도 전 집계가 이미 그 값이라 재시도 루프가 돌기도 전에 통과한다
    private void awaitCounts(String campaignKey, long expectedSent, long expectedFailed) throws Exception {
        for (int i = 0; i < 100; i++) {
            String body = mvc().perform(get("/api/admin/notice-mails/" + campaignKey).header("X-Admin-Token", TOKEN))
                    .andReturn().getResponse().getContentAsString();
            long sent = Long.parseLong(body.replaceAll(".*\"sent\":(\\d+).*", "$1"));
            long failed = Long.parseLong(body.replaceAll(".*\"failed\":(\\d+).*", "$1"));
            long sending = Long.parseLong(body.replaceAll(".*\"sending\":(\\d+).*", "$1"));
            if (sending == 0 && sent == expectedSent && failed == expectedFailed) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("notice mail did not finish. campaignKey=" + campaignKey);
    }

    @Test
    void 비활성_빼고_보내고_실패한_사람만_다시_보내고_두번_눌러도_한번만_간다() throws Exception {
        profile(9501, "alive@dgu.ac.kr");
        profile(9502, "flaky@dgu.ac.kr");
        DatingProfile hidden = profile(9503, "hidden@dgu.ac.kr");
        hidden.deactivate(Instant.now());
        profileRepository.saveAndFlush(hidden);
        // 다른 테스트가 만든 프로필도 같은 DB 에 있으므로 기대 대상은 DB 에서 센다
        Set<String> alive = profileRepository.findByDeactivatedAtIsNull().stream()
                .map(DatingProfile::getEmail).collect(Collectors.toSet());
        assertThat(alive).contains("alive@dgu.ac.kr", "flaky@dgu.ac.kr").doesNotContain("hidden@dgu.ac.kr");
        int total = alive.size();

        request("first", "DRY_RUN", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targets").value(total));
        assertThat(recipients).isEmpty();

        // 한 사람 실패가 나머지를 막지 않는다
        failingRecipients.add("flaky@dgu.ac.kr");
        request("first", "SEND", null)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.targets").value(total));
        awaitCounts("first", total - 1, 1);
        assertThat(recipients).hasSize(total - 1).contains("alive@dgu.ac.kr").doesNotContain("flaky@dgu.ac.kr");

        // 같은 키로 다시 보내면 실패한 사람에게만 간다
        recipients.clear();
        failingRecipients.clear();
        request("first", "SEND", null).andExpect(status().isAccepted());
        awaitCounts("first", total, 0);
        assertThat(recipients).containsExactly("flaky@dgu.ac.kr");

        // 연달아 두 번 눌러도 수신자마다 한 번이다
        recipients.clear();
        request("second", "SEND", null).andExpect(status().isAccepted());
        request("second", "SEND", null).andExpect(status().isAccepted());
        awaitCounts("second", total, 0);
        Thread.sleep(300); // 늦게 시작한 두 번째 루프가 남은 선점을 끝내도록 둔다
        assertThat(recipients).containsExactlyInAnyOrderElementsOf(alive);
    }

    private ResultActions schedule(String campaignKey, String subject, String sendAt) throws Exception {
        return mvc().perform(post("/api/admin/notice-mails").header("X-Admin-Token", TOKEN)
                .contentType("application/json")
                .content("""
                        {"campaignKey":"%s","subject":"%s","body":"내용","mode":"SCHEDULE","sendAt":"%s"}
                        """.formatted(campaignKey, subject, sendAt)));
    }

    // 예약 시각을 기다릴 수 없으니 DB 의 send_at 을 직접 옮긴다. 스케줄러 주기는 테스트에서 1시간이라 직접 부른다
    private void moveSendAt(String campaignKey, String interval) {
        jdbcTemplate.update("UPDATE dating_notice_campaign SET send_at = now() - CAST(? AS INTERVAL) "
                + "WHERE campaign_key = ?", interval, campaignKey);
    }

    private String future() {
        return OffsetDateTime.now(ZoneOffset.ofHours(9)).plusHours(1).toString();
    }

    @Test
    void 예약은_시각이_지나면_한번만_보내고_시작한_예약은_고칠_수_없다() throws Exception {
        profile(9601, "scheduled@dgu.ac.kr");
        Set<String> alive = profileRepository.findByDeactivatedAtIsNull().stream()
                .map(DatingProfile::getEmail).collect(Collectors.toSet());

        schedule("timed", "처음 제목", future()).andExpect(status().isOk());
        // 시작 전에는 같은 키로 다시 보내 문구를 고친다
        schedule("timed", "고친 제목", future()).andExpect(status().isOk());
        mvc().perform(get("/api/admin/notice-mails/timed").header("X-Admin-Token", TOKEN))
                .andExpect(jsonPath("$.data.state").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.sendAt").isNotEmpty());

        // 아직 시각 전이면 아무것도 안 나간다
        noticeMailService.runDueCampaigns();
        assertThat(recipients).isEmpty();

        moveSendAt("timed", "1 minute");
        noticeMailService.runDueCampaigns();
        assertThat(recipients).containsExactlyInAnyOrderElementsOf(alive);
        assertThat(subjects).containsOnly("고친 제목");
        mvc().perform(get("/api/admin/notice-mails/timed").header("X-Admin-Token", TOKEN))
                .andExpect(jsonPath("$.data.state").value("STARTED"))
                .andExpect(jsonPath("$.data.sent").value(alive.size()));

        // 다시 확인해도 두 번 가지 않는다
        noticeMailService.runDueCampaigns();
        assertThat(recipients).hasSize(alive.size());

        schedule("timed", "또 고친 제목", future())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        mvc().perform(delete("/api/admin/notice-mails/timed/schedule").header("X-Admin-Token", TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 한시간_넘게_늦은_예약은_보내지_않는다_취소한_예약도() throws Exception {
        profile(9701, "late@dgu.ac.kr");

        schedule("late", "제목", future()).andExpect(status().isOk());
        moveSendAt("late", "61 minutes");
        schedule("cancelled", "제목", future()).andExpect(status().isOk());
        mvc().perform(delete("/api/admin/notice-mails/cancelled/schedule").header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk());
        moveSendAt("cancelled", "1 minute"); // 지워졌으니 아무 행도 안 바뀐다

        noticeMailService.runDueCampaigns();
        assertThat(recipients).isEmpty();
        mvc().perform(get("/api/admin/notice-mails/late").header("X-Admin-Token", TOKEN))
                .andExpect(jsonPath("$.data.state").value("EXPIRED"))
                .andExpect(jsonPath("$.data.sent").value(0));
        mvc().perform(get("/api/admin/notice-mails/cancelled").header("X-Admin-Token", TOKEN))
                .andExpect(jsonPath("$.data.state").isEmpty());
    }

    @Test
    void 예약_시각이_없거나_지났으면_400() throws Exception {
        schedule("past", "제목", OffsetDateTime.now(ZoneOffset.ofHours(9)).minusMinutes(1).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        request("no-time", "SCHEDULE", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void TEST_는_지정한_주소로만_보내고_기록하지_않는다() throws Exception {
        request("preview", "TEST", "me@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targets").value(1));
        assertThat(recipients).containsExactly("me@example.com");
        mvc().perform(get("/api/admin/notice-mails/preview").header("X-Admin-Token", TOKEN))
                .andExpect(jsonPath("$.data.sent").value(0));

        request("preview", "TEST", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        failingRecipients.add("down@example.com");
        request("preview", "TEST", "down@example.com")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("MAIL_UNAVAILABLE"));
    }

    @Test
    void 토큰_없으면_401_키_형식이_틀리면_400() throws Exception {
        mvc().perform(post("/api/admin/notice-mails").contentType("application/json")
                        .content("{\"campaignKey\":\"k\",\"subject\":\"s\",\"body\":\"b\",\"mode\":\"SEND\"}"))
                .andExpect(status().isUnauthorized());
        request("Bad Key", "DRY_RUN", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
}
