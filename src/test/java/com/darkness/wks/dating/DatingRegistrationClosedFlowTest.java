package com.darkness.wks.dating;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.Gender;
import com.darkness.wks.common.auth.JwtProvider;
import com.darkness.wks.dating.entity.DatingPhoto;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.member.MemberRepository;
import com.darkness.wks.member.entity.Member;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import com.google.genai.Client;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 신규 소개팅 신청 마감(2026-10-02 02:00 KST). 마감 시각 설정을 덮어쓰지 않아 운영과 같은 기본값으로 돈다 —
 * 이 테스트가 도는 시점은 이미 마감 뒤다. 새 프로필 등록만 막히고 기존 프로필 보유자는 그대로 쓰는지 본다.
 */
@SpringBootTest(properties = {"gemini.api-key=test-key",
        "app.auth.jwt.secret=registration-closed-secret-0123456789-abcdef"})
@Testcontainers
class DatingRegistrationClosedFlowTest {

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
    DatingProfileRepository profileRepository;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    WebApplicationContext webContext;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(webContext).build();
    }

    private Member memberWithResult(long kakaoId) {
        Member member = memberRepository.saveAndFlush(new Member(kakaoId));
        Result result = new Result("테스트", LocalDate.of(2000, 1, 1), null, null, Gender.MALE,
                "갑자", "을축", "병인", null);
        result.linkMember(member.getId());
        resultRepository.saveAndFlush(result);
        return member;
    }

    @Test
    void 마감_뒤_새_프로필_등록은_403_기존_프로필은_그대로() throws Exception {
        Member newcomer = memberWithResult(880001L);
        mvc().perform(post("/api/dating/profile")
                        .cookie(new Cookie("wks_token", jwtProvider.issue(newcomer.getId())))
                        .contentType("application/json")
                        .content("""
                                {"email":"late@dgu.ac.kr","name":"김동국","contactMethod":"INSTAGRAM","contactValue":"my_ig",
                                 "department":"컴퓨터공학과","mbti":"INFP","bio":"자기소개","photoId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("DATING_REGISTRATION_CLOSED"));
        verifyNoInteractions(photoService);

        // 마감 전에 등록한 사람은 계속 자기 프로필을 본다
        Member existing = memberWithResult(880002L);
        DatingPhoto photo = photoRepository.saveAndFlush(
                new DatingPhoto(existing.getId(), "dating-photos/" + existing.getId() + "/" + UUID.randomUUID()));
        DatingProfile profile = new DatingProfile(existing.getId(), "early@dgu.ac.kr", "이름", ContactMethod.INSTAGRAM,
                "insta", "컴퓨터공학과", "ENFP", "안녕하세요", photo);
        profile.markVerified(Instant.now());
        profileRepository.saveAndFlush(profile);

        mvc().perform(get("/api/dating/profile/me")
                        .cookie(new Cookie("wks_token", jwtProvider.issue(existing.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailVerified").value(true));
    }
}
