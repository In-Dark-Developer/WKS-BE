package com.darkness.wks.admin;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.Gender;
import com.darkness.wks.common.auth.JwtProvider;
import com.darkness.wks.dating.DatingPhotoRepository;
import com.darkness.wks.dating.DatingPhotoService;
import com.darkness.wks.dating.DatingProfileRepository;
import com.darkness.wks.dating.DatingRecommendationRepository;
import com.darkness.wks.dating.DatingReasonGenerator;
import com.darkness.wks.dating.DatingRequestRepository;
import com.darkness.wks.dating.entity.DatingPhoto;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.dating.entity.DatingRecommendation;
import com.darkness.wks.dating.entity.DatingRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 비활성화·삭제가 기존 자격 게이트(isEligible)·FK cascade 를 실제로 타는지 DB 로 확인한다.
 * S3 는 목킹한다 — 지운 객체는 deleteObjects 호출로만 검증한다.
 */
@SpringBootTest(properties = {"gemini.api-key=test-key", "app.admin.token=" + DatingAdminFlowTest.TOKEN,
        "app.auth.jwt.secret=admin-flow-test-secret-0123456789-abcdef"})
@Testcontainers
class DatingAdminFlowTest {

    static final String TOKEN = "admin-flow-test-token";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @MockitoBean
    Client geminiClient;

    @MockitoBean
    JavaMailSender mailSender;

    @MockitoBean
    DatingPhotoService photoService;

    @MockitoBean
    DatingReasonGenerator reasonGenerator;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    DatingPhotoRepository photoRepository;

    @Autowired
    DatingProfileRepository profileRepository;

    @Autowired
    DatingRecommendationRepository recommendationRepository;

    @Autowired
    DatingRequestRepository requestRepository;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    WebApplicationContext webContext;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(webContext).build();
    }

    private DatingProfile profile(long kakaoId, Gender gender, String email) {
        Member member = memberRepository.saveAndFlush(new Member(kakaoId));
        Result result = new Result("테스트", LocalDate.of(2000, 1, 1), null, null, gender,
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

    private Cookie login(Long memberId) {
        return new Cookie("wks_token", jwtProvider.issue(memberId));
    }

    @Test
    void 토큰_없거나_틀리면_401() throws Exception {
        mvc().perform(get("/api/admin/dating/profiles").param("email", "a@dgu.ac.kr"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc().perform(get("/api/admin/dating/profiles").param("email", "a@dgu.ac.kr")
                        .header("X-Admin-Token", TOKEN + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 운영자_페이지는_토큰_없이_서빙된다() throws Exception {
        mvc().perform(get("/admin.html"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("X-Admin-Token"));
    }

    @Test
    void 조회_수정_이메일중복() throws Exception {
        DatingProfile target = profile(9101, Gender.FEMALE, "target-edit@dgu.ac.kr");
        profile(9102, Gender.FEMALE, "taken@dgu.ac.kr");
        when(photoService.originalUrl(any())).thenReturn("https://s3/original");

        mvc().perform(get("/api/admin/dating/profiles").param("email", "Target-Edit@dgu.ac.kr")
                        .header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileId").value(target.getId().toString()))
                .andExpect(jsonPath("$.data.memberId").value(target.getMemberId()))
                .andExpect(jsonPath("$.data.contactValue").value("insta_9101"))
                .andExpect(jsonPath("$.data.photoUrl").value("https://s3/original"))
                .andExpect(jsonPath("$.data.deactivatedAt").isEmpty());

        mvc().perform(patch("/api/admin/dating/profiles/" + target.getId())
                        .header("X-Admin-Token", TOKEN).contentType("application/json")
                        .content("{\"name\":\" 새이름 \",\"contactMethod\":\"PHONE\",\"contactValue\":\"010-1234-5678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새이름"))
                .andExpect(jsonPath("$.data.contactMethod").value("PHONE"))
                .andExpect(jsonPath("$.data.department").value("컴퓨터공학과"));

        // PHONE 인데 번호 형식이 아니면 등록과 같은 규칙으로 400
        mvc().perform(patch("/api/admin/dating/profiles/" + target.getId())
                        .header("X-Admin-Token", TOKEN).contentType("application/json")
                        .content("{\"contactValue\":\"not-a-phone\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        mvc().perform(patch("/api/admin/dating/profiles/" + target.getId())
                        .header("X-Admin-Token", TOKEN).contentType("application/json")
                        .content("{\"email\":\"taken@dgu.ac.kr\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DATING_PROFILE_CONFLICT"));

        mvc().perform(patch("/api/admin/dating/profiles/" + target.getId())
                        .header("X-Admin-Token", TOKEN).contentType("application/json")
                        .content("{\"email\":\"someone@gmail.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_EMAIL_DOMAIN"));
    }

    @Test
    void 비활성화하면_추천풀에서_빠지고_수락이_막히고_활성화하면_돌아온다() throws Exception {
        DatingProfile sender = profile(9201, Gender.MALE, "sender-deact@dgu.ac.kr");
        DatingProfile target = profile(9202, Gender.FEMALE, "target-deact@dgu.ac.kr");
        DatingRequest request = requestRepository.saveAndFlush(new DatingRequest(sender, target));
        when(photoService.originalUrl(any())).thenReturn("https://s3/original");

        mvc().perform(post("/api/admin/dating/profiles/" + target.getId() + "/deactivate")
                        .header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deactivatedAt").isNotEmpty());

        assertThat(profileRepository.findEligible()).extracting(DatingProfile::getId).doesNotContain(target.getId());
        assertThat(profileRepository.findById(target.getId()).orElseThrow().isEligible()).isFalse();

        // 받은 사람(비활성)이 수락하려 하면 기존 자격 검사가 막는다
        mvc().perform(post("/api/dating/requests/" + request.getId() + "/accept").cookie(login(target.getMemberId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DATING_REQUEST_CONFLICT"));

        mvc().perform(post("/api/admin/dating/profiles/" + target.getId() + "/activate")
                        .header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deactivatedAt").isEmpty());
        assertThat(profileRepository.findEligible()).extracting(DatingProfile::getId).contains(target.getId());
    }

    @Test
    void 사진_교체는_옛_사진_행과_S3_객체를_지운다() throws Exception {
        DatingProfile target = profile(9301, Gender.FEMALE, "target-photo@dgu.ac.kr");
        UUID oldPhotoId = target.getPhoto().getId();
        DatingPhoto fresh = photoRepository.saveAndFlush(
                new DatingPhoto(target.getMemberId(), "dating-photos/" + target.getMemberId() + "/new.jpg"));
        when(photoService.verifyOwnedPhoto(eq(target.getMemberId()), eq(fresh.getId()))).thenReturn(fresh);
        when(photoService.originalUrl(any())).thenReturn("https://s3/original");

        mvc().perform(patch("/api/admin/dating/profiles/" + target.getId() + "/photo")
                        .header("X-Admin-Token", TOKEN).contentType("application/json")
                        .content("{\"photoId\":\"" + fresh.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoId").value(fresh.getId().toString()));

        assertThat(profileRepository.findWithPhotoById(target.getId()).orElseThrow().getPhoto().getId())
                .isEqualTo(fresh.getId());
        assertThat(photoRepository.findById(oldPhotoId)).isEmpty();
        verify(photoService).createBlurredThumbnail(fresh);
        verify(photoService).deleteObjects(org.mockito.ArgumentMatchers.argThat(photo -> photo.getId().equals(oldPhotoId)));
    }

    @Test
    void 삭제하면_추천_요청_사진이_함께_지워지고_원장은_건드리지_않는다() throws Exception {
        DatingProfile viewer = profile(9401, Gender.MALE, "viewer-del@dgu.ac.kr");
        DatingProfile target = profile(9402, Gender.FEMALE, "target-del@dgu.ac.kr");
        UUID photoId = target.getPhoto().getId();
        DatingRecommendation recommendation = recommendationRepository.saveAndFlush(
                new DatingRecommendation(viewer.getMemberId(), target, 77));
        DatingRequest request = requestRepository.saveAndFlush(new DatingRequest(viewer, target));

        mvc().perform(delete("/api/admin/dating/profiles/" + target.getId()).header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk());

        assertThat(profileRepository.findById(target.getId())).isEmpty();
        assertThat(recommendationRepository.findById(recommendation.getId())).isEmpty();
        assertThat(requestRepository.findById(request.getId())).isEmpty();
        assertThat(photoRepository.findById(photoId)).isEmpty();
        assertThat(memberRepository.findById(target.getMemberId())).isPresent();
        verify(photoService).deleteObjects(org.mockito.ArgumentMatchers.argThat(photo -> photo.getId().equals(photoId)));

        mvc().perform(get("/api/admin/dating/profiles/" + target.getId()).header("X-Admin-Token", TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DATING_PROFILE_NOT_FOUND"));
    }
}
