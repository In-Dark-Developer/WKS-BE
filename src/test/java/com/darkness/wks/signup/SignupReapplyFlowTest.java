package com.darkness.wks.signup;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.Gender;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.common.auth.JwtProvider;
import com.darkness.wks.dating.DatingEmailVerificationService;
import com.darkness.wks.dating.DatingPhotoRepository;
import com.darkness.wks.dating.DatingPhotoService;
import com.darkness.wks.dating.DatingProfileRepository;
import com.darkness.wks.dating.DatingProfileService;
import com.darkness.wks.dating.dto.DatingProfileRequest;
import com.darkness.wks.dating.dto.DatingProfileResponse;
import com.darkness.wks.dating.entity.DatingPhoto;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.member.MemberRepository;
import com.darkness.wks.member.entity.Member;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.signup.entity.Signup;
import com.darkness.wks.signup.entity.SignupReapplyInvite;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 재신청 초대 대상 쿼리(JPQL)와 초대→프로필 등록 흐름을 실제 DB에서 확인한다. 대상 쿼리가 틀리면
 * 엉뚱한 사람에게 대량 메일이 나가므로 목킹만으로 끝내지 않는다.
 */
@SpringBootTest(properties = {"gemini.api-key=test-key",
        "app.auth.jwt.secret=reapply-test-secret-0123456789-abcdef"})
@Testcontainers
class SignupReapplyFlowTest {

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
    SignupRepository signupRepository;

    @Autowired
    SignupReapplyInviteRepository inviteRepository;

    @Autowired
    SignupReapplyService reapplyService;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    DatingPhotoRepository photoRepository;

    @Autowired
    DatingProfileRepository profileRepository;

    @Autowired
    DatingProfileService profileService;

    @Autowired
    DatingEmailVerificationService datingVerificationService;

    @Autowired
    SignupReapplyCampaignRunner campaignRunner;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    WebApplicationContext webContext;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(webContext).build();
    }

    /** JavaMailSender 목이 기본으로 null 을 주면 MimeMessageHelper 가 터진다 — 실제 MimeMessage 를 물려준다 */
    private void stubMimeMessage() {
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private Result result(Long memberId) {
        Result result = new Result("테스트", LocalDate.of(2000, 1, 1), null, null, Gender.MALE,
                "갑자", "을축", "병인", null);
        if (memberId != null) {
            result.linkMember(memberId);
        }
        return resultRepository.saveAndFlush(result);
    }

    private Signup signup(String email, Result result) {
        return signupRepository.saveAndFlush(new Signup(email, result, Gender.MALE, Gender.FEMALE,
                "김동국", ContactMethod.INSTAGRAM, "my_ig", "컴퓨터공학과", "INFP", "자기소개", null));
    }

    @Test
    void targetsOnlyCoverSignupsWithResultAndWithoutLiveInvite() {
        Signup eligible = signup("eligible-" + UUID.randomUUID() + "@dgu.ac.kr", result(null));
        Signup noResult = signup("no-result-" + UUID.randomUUID() + "@dgu.ac.kr", null);
        Signup otherDomain = signup("other-" + UUID.randomUUID() + "@gmail.com", result(null));
        Signup alreadyInvited = signup("invited-" + UUID.randomUUID() + "@dgu.ac.kr", result(null));
        inviteRepository.saveAndFlush(new SignupReapplyInvite(UUID.randomUUID().toString(), alreadyInvited,
                Instant.now().plusSeconds(3600)));
        Signup alreadyDone = signup("done-" + UUID.randomUUID() + "@dgu.ac.kr", result(null));
        SignupReapplyInvite usedInvite = new SignupReapplyInvite(UUID.randomUUID().toString(), alreadyDone,
                Instant.now().plusSeconds(3600));
        usedInvite.markUsed(Instant.now());
        inviteRepository.saveAndFlush(usedInvite);
        Signup expiredInvite = signup("expired-" + UUID.randomUUID() + "@dgu.ac.kr", result(null));
        inviteRepository.saveAndFlush(new SignupReapplyInvite(UUID.randomUUID().toString(), expiredInvite,
                Instant.now().minusSeconds(1)));

        var targetIds = reapplyService.findTargets().stream()
                .map(SignupReapplyService.ReapplyTarget::signupId).toList();

        assertThat(targetIds).contains(eligible.getId(), expiredInvite.getId());
        assertThat(targetIds).doesNotContain(noResult.getId(), otherDomain.getId(),
                alreadyInvited.getId(), alreadyDone.getId());
    }

    @Test
    void invitedReapplyCreatesVerifiedProfileWithoutVerificationMail() {
        Member member = memberRepository.saveAndFlush(new Member(770001L));
        String email = "reapply-" + UUID.randomUUID() + "@dgu.ac.kr";
        Signup signup = signup(email, result(member.getId()));
        SignupReapplyInvite invite = reapplyService.issueInvite(signup.getId());
        DatingPhoto photo = photoRepository.saveAndFlush(
                new DatingPhoto(member.getId(), "dating-photos/" + member.getId() + "/" + UUID.randomUUID()));
        when(photoService.verifyOwnedPhoto(member.getId(), photo.getId())).thenReturn(photo);

        DatingProfileResponse response = profileService.create(member.getId(),
                request(email, photo.getId(), invite.getToken()));

        assertThat(response.emailVerified()).isTrue();
        assertThat(profileRepository.findByMemberId(member.getId()).orElseThrow().isEligible()).isTrue();
        assertThat(inviteRepository.findById(invite.getToken()).orElseThrow().getUsedAt()).isNotNull();
        verifyNoInteractions(mailSender);
    }

    /**
     * 초대는 그 이메일에 묶여 있어서, 재사용 시도는 토큰 검사(INVALID_TOKEN)보다 이메일 중복 검사에
     * 먼저 걸린다 — 어느 쪽이든 두 번째 계정은 등록되지 않는다.
     */
    @Test
    void usedInviteCannotBeReplayedByAnotherAccount() {
        Member first = memberRepository.saveAndFlush(new Member(770002L));
        String email = "replay-" + UUID.randomUUID() + "@dgu.ac.kr";
        Signup signup = signup(email, result(first.getId()));
        SignupReapplyInvite invite = reapplyService.issueInvite(signup.getId());
        DatingPhoto firstPhoto = photoRepository.saveAndFlush(
                new DatingPhoto(first.getId(), "dating-photos/" + first.getId() + "/" + UUID.randomUUID()));
        when(photoService.verifyOwnedPhoto(any(), any())).thenReturn(firstPhoto);
        profileService.create(first.getId(), request(email, firstPhoto.getId(), invite.getToken()));

        Member second = memberRepository.saveAndFlush(new Member(770003L));
        result(second.getId());

        assertThatThrownBy(() -> profileService.create(second.getId(),
                request(email, firstPhoto.getId(), invite.getToken())))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DATING_PROFILE_CONFLICT));
        assertThat(profileRepository.findByMemberId(second.getId())).isEmpty();
    }

    @Test
    void forgedInviteTokenIsRejected() {
        Member member = memberRepository.saveAndFlush(new Member(770004L));
        result(member.getId());
        DatingPhoto photo = photoRepository.saveAndFlush(
                new DatingPhoto(member.getId(), "dating-photos/" + member.getId() + "/" + UUID.randomUUID()));

        assertThatThrownBy(() -> profileService.create(member.getId(),
                request("forged-" + UUID.randomUUID() + "@dgu.ac.kr", photo.getId(), "not-a-real-token")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN));
        assertThat(profileRepository.findByMemberId(member.getId())).isEmpty();
    }

    private DatingProfileRequest request(String email, UUID photoId, String reapplyToken) {
        return new DatingProfileRequest(email, "김동국", ContactMethod.INSTAGRAM, "my_ig",
                "컴퓨터공학과", "INFP", "자기소개", photoId, reapplyToken);
    }

    @Test
    void reapplyPrefillIsServedOverHttpWithoutLoginCookie() throws Exception {
        Result result = result(null);
        String email = "http-" + UUID.randomUUID() + "@dgu.ac.kr";
        SignupReapplyInvite invite = reapplyService.issueInvite(signup(email, result).getId());

        mvc().perform(get("/api/signups/reapply").param("token", invite.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.resultId").value(result.getId().toString()))
                .andExpect(jsonPath("$.data.department").value("컴퓨터공학과"));

        mvc().perform(get("/api/signups/reapply").param("token", "forged"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    /** 초대 흐름 전체를 HTTP 로 한 번 더 — 로그인 쿠키까지 실어서 프론트가 부를 그대로 확인한다 */
    @Test
    void invitedProfileIsCreatedVerifiedOverHttp() throws Exception {
        Member member = memberRepository.saveAndFlush(new Member(780001L));
        String email = "http-invite-" + UUID.randomUUID() + "@dgu.ac.kr";
        SignupReapplyInvite invite = reapplyService.issueInvite(signup(email, result(member.getId())).getId());
        DatingPhoto photo = photoRepository.saveAndFlush(
                new DatingPhoto(member.getId(), "dating-photos/" + member.getId() + "/" + UUID.randomUUID()));
        when(photoService.verifyOwnedPhoto(any(), any())).thenReturn(photo);

        mvc().perform(post("/api/dating/profile")
                        .cookie(new Cookie("wks_token", jwtProvider.issue(member.getId())))
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","name":"김동국","contactMethod":"INSTAGRAM","contactValue":"my_ig",
                                 "department":"컴퓨터공학과","mbti":"INFP","bio":"자기소개","photoId":"%s",
                                 "reapplyToken":"%s"}
                                """.formatted(email, photo.getId(), invite.getToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.emailVerified").value(true));

        verifyNoInteractions(mailSender);
    }

    /** 매직링크는 메일 앱에서 열려서 로그인 쿠키가 없다 — 인터셉터 제외가 실제로 먹는지 확인한다 */
    @Test
    void datingVerifyLinkWorksWithoutLoginCookie() throws Exception {
        // V24 이후 새 링크는 발급하지 않지만, 그 전에 나간 링크는 계속 눌려야 한다 — 미인증 프로필을 직접 만든다
        Member member = memberRepository.saveAndFlush(new Member(780002L));
        String email = "verify-" + UUID.randomUUID() + "@dgu.ac.kr";
        DatingPhoto photo = photoRepository.saveAndFlush(
                new DatingPhoto(member.getId(), "dating-photos/" + member.getId() + "/" + UUID.randomUUID()));
        DatingProfile profile = profileRepository.saveAndFlush(new DatingProfile(member.getId(), email, "김동국",
                ContactMethod.INSTAGRAM, "my_ig", "컴퓨터공학과", "INFP", "자기소개", photo));
        String token = datingVerificationService.issueToken(profile).getToken();

        mvc().perform(get("/api/dating/profile/verify").param("token", token))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:3000/dating/verify"));

        assertThat(profileRepository.findByMemberId(member.getId()).orElseThrow().isEligible()).isTrue();
        // 로그인 필요한 경로는 그대로 401 이어야 한다
        mvc().perform(get("/api/dating/profile/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void inviteMailPointsToFrontendReapplyPage() throws Exception {
        stubMimeMessage();
        String email = "mail-" + UUID.randomUUID() + "@dgu.ac.kr";
        SignupReapplyInvite invite = reapplyService.issueInvite(signup(email, result(null)).getId());

        reapplyService.sendInviteEmail(email, invite.getToken());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertThat(sent.getSubject()).contains("사전신청");
        assertThat(sent.getContent().toString())
                .contains("http://localhost:3000/dating/reapply?token=" + invite.getToken())
                .contains("48시간");
    }

    @Test
    void campaignRunnerSendsOncePerSignup() {
        stubMimeMessage();
        String email = "campaign-" + UUID.randomUUID() + "@dgu.ac.kr";
        Long signupId = signup(email, result(null)).getId();
        ReflectionTestUtils.setField(campaignRunner, "mode", "send");

        campaignRunner.run(null);
        long afterFirstRun = invitesFor(signupId);

        campaignRunner.run(null);

        assertThat(afterFirstRun).isEqualTo(1);
        assertThat(invitesFor(signupId)).isEqualTo(1);
        assertThat(reapplyService.findTargets())
                .extracting(SignupReapplyService.ReapplyTarget::signupId)
                .doesNotContain(signupId);
    }

    private long invitesFor(Long signupId) {
        return inviteRepository.findAll().stream()
                .filter(invite -> invite.getSignup().getId().equals(signupId))
                .count();
    }
}
