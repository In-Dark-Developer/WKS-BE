package com.darkness.wks.dating;

import com.darkness.wks.common.Gender;
import com.darkness.wks.common.auth.JwtProvider;
import com.darkness.wks.dating.entity.DatingPhoto;
import com.darkness.wks.member.MemberRepository;
import com.darkness.wks.member.entity.Member;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import com.google.genai.Client;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 코드 발송 → 확인 → 프로필 등록을 실제 DB·HTTP 로 확인한다. 틀린 입력의 실패 횟수가 롤백되지 않고
 * 남는지(noRollbackFor)와 행 잠금 쿼리는 목킹으로는 확인할 수 없다.
 */
@SpringBootTest(properties = {"gemini.api-key=test-key",
        "app.auth.jwt.secret=email-code-test-secret-0123456789-abcdef"})
@Testcontainers
class DatingEmailCodeFlowTest {

    private static final Pattern CODE_IN_MAIL = Pattern.compile("\\b(\\d{6})\\b");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @MockitoBean
    Client geminiClient;

    @MockitoBean
    JavaMailSender mailSender;

    @MockitoBean
    DatingPhotoService photoService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    DatingPhotoRepository photoRepository;

    @Autowired
    DatingEmailCodeRepository codeRepository;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    WebApplicationContext webContext;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(webContext).build();
    }

    private Cookie login(Member member) {
        return new Cookie("wks_token", jwtProvider.issue(member.getId()));
    }

    private Member memberWithResult(long kakaoId) {
        Member member = memberRepository.saveAndFlush(new Member(kakaoId));
        Result result = new Result("테스트", LocalDate.of(2000, 1, 1), null, null, Gender.MALE,
                "갑자", "을축", "병인", null);
        result.linkMember(member.getId());
        resultRepository.saveAndFlush(result);
        return member;
    }

    /** 실제로 나간 메일 본문에서 코드를 꺼낸다 — 프론트 사용자가 메일을 보고 입력하는 것과 같다 */
    private String sendCodeAndReadMail(Cookie cookie, String email) throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        mvc().perform(post("/api/dating/email-codes").cookie(cookie)
                        .contentType("application/json")
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.resendAvailableAt").isNotEmpty());

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, atLeastOnce()).send(sent.capture());
        Matcher matcher = CODE_IN_MAIL.matcher((String) sent.getValue().getContent());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String profileJson(String email, UUID photoId) {
        return """
                {"email":"%s","name":"김동국","contactMethod":"INSTAGRAM","contactValue":"my_ig",
                 "department":"컴퓨터공학과","mbti":"INFP","bio":"자기소개","photoId":"%s"}
                """.formatted(email, photoId);
    }

    @Test
    void codeVerifiedEmailCreatesVerifiedProfile() throws Exception {
        Member member = memberWithResult(790001L);
        Cookie cookie = login(member);
        String email = "code-" + UUID.randomUUID() + "@dgu.ac.kr";
        DatingPhoto photo = photoRepository.saveAndFlush(
                new DatingPhoto(member.getId(), "dating-photos/" + member.getId() + "/" + UUID.randomUUID()));
        when(photoService.verifyOwnedPhoto(any(), any())).thenReturn(photo);

        // 인증 전 등록은 막힌다
        mvc().perform(post("/api/dating/profile").cookie(cookie)
                        .contentType("application/json").content(profileJson(email, photo.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("DATING_NOT_VERIFIED"));

        String code = sendCodeAndReadMail(cookie, email);

        mvc().perform(post("/api/dating/email-codes/verify").cookie(cookie)
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(true));

        mvc().perform(post("/api/dating/profile").cookie(cookie)
                        .contentType("application/json").content(profileJson(email, photo.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.emailVerified").value(true));
    }

    @Test
    void wrongCodeFailuresArePersistedAndResendIsThrottled() throws Exception {
        Member member = memberWithResult(790002L);
        Cookie cookie = login(member);
        String email = "wrong-" + UUID.randomUUID() + "@dgu.ac.kr";
        String code = sendCodeAndReadMail(cookie, email);
        String wrong = code.equals("000000") ? "111111" : "000000";

        mvc().perform(post("/api/dating/email-codes/verify").cookie(cookie)
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, wrong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_EMAIL_CODE"));

        // 예외로 끝난 요청이어도 실패 횟수는 커밋돼야 한다 — 롤백되면 무제한으로 찍어볼 수 있다
        assertThat(codeRepository.findById(member.getId()).orElseThrow().getFailedAttempts()).isEqualTo(1);

        mvc().perform(post("/api/dating/email-codes").cookie(cookie)
                        .contentType("application/json")
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("EMAIL_CODE_RATE_LIMITED"));
    }

    @Test
    void emailCodeRoutesRequireLogin() throws Exception {
        mvc().perform(post("/api/dating/email-codes")
                        .contentType("application/json").content("{\"email\":\"a@dgu.ac.kr\"}"))
                .andExpect(status().isUnauthorized());
        mvc().perform(post("/api/dating/email-codes/verify")
                        .contentType("application/json").content("{\"email\":\"a@dgu.ac.kr\",\"code\":\"123456\"}"))
                .andExpect(status().isUnauthorized());
    }
}
