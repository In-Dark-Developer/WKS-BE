package com.darkness.wks.signup;

import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.Gender;
import com.darkness.wks.common.auth.JwtProvider;
import com.darkness.wks.dating.DatingEmailVerificationService;
import com.darkness.wks.dating.DatingPhotoRepository;
import com.darkness.wks.dating.DatingPhotoService;
import com.darkness.wks.dating.DatingProfileRepository;
import com.darkness.wks.dating.entity.DatingPhoto;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.member.MemberRepository;
import com.darkness.wks.member.MemberService;
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
    MemberService memberService;

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
    void targetsCoverUnlinkedResultsOfAnyDomainWithoutLiveInvite() {
        Signup schoolMail = signup("school-" + UUID.randomUUID() + "@dgu.ac.kr", result(null));
        Signup gmail = signup("gmail-" + UUID.randomUUID() + "@gmail.com", result(null));
        Signup noResult = signup("no-result-" + UUID.randomUUID() + "@gmail.com", null);
        Signup alreadyInvited = signup("invited-" + UUID.randomUUID() + "@gmail.com", result(null));
        inviteRepository.saveAndFlush(new SignupReapplyInvite(UUID.randomUUID().toString(), alreadyInvited,
                Instant.now().plusSeconds(3600)));
        // 결과가 이미 계정에 연결됐으면 초대의 목적을 이룬 것이다
        Member owner = memberRepository.saveAndFlush(new Member(770010L));
        Signup alreadyLinked = signup("linked-" + UUID.randomUUID() + "@gmail.com", result(owner.getId()));
        Signup expiredInvite = signup("expired-" + UUID.randomUUID() + "@gmail.com", result(null));
        inviteRepository.saveAndFlush(new SignupReapplyInvite(UUID.randomUUID().toString(), expiredInvite,
                Instant.now().minusSeconds(1)));

        var targetIds = reapplyService.findTargets().stream()
                .map(SignupReapplyService.ReapplyTarget::signupId).toList();

        assertThat(targetIds).contains(schoolMail.getId(), gmail.getId(), expiredInvite.getId());
        assertThat(targetIds).doesNotContain(noResult.getId(), alreadyInvited.getId(), alreadyLinked.getId());
    }

    /** 초대 링크 → 폼 자동 채움의 resultId 로 카카오 로그인하면 사전신청 결과가 계정에 붙고 캠페인 대상에서 빠진다 */
    @Test
    void kakaoLoginWithInvitedResultIdLinksPreSignupResult() {
        Result result = result(null);
        Signup signup = signup("prelink-" + UUID.randomUUID() + "@gmail.com", result);
        SignupReapplyInvite invite = reapplyService.issueInvite(signup.getId());

        UUID resultId = reapplyService.prefill(invite.getToken()).resultId();
        MemberService.LoginResult login = memberService.loginAndLink(770011L, resultId.toString());

        assertThat(resultRepository.findById(resultId).orElseThrow().getMemberId())
                .isEqualTo(login.member().getId());
        assertThat(reapplyService.findTargets())
                .extracting(SignupReapplyService.ReapplyTarget::signupId)
                .doesNotContain(signup.getId());
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
    /**
     * 초대는 학교메일 인증이 아니다 — 사전신청 이메일은 gmail 등이고 인증된 적이 없다. 초대로 결과를 연결한
     * 회원도 학교메일 코드 인증 없이는 등록되지 않는다(예전 reapplyToken 을 실어 보내도 무시된다).
     */
    @Test
    void invitedMemberStillNeedsSchoolEmailCodeToRegister() throws Exception {
        Member member = memberRepository.saveAndFlush(new Member(780001L));
        SignupReapplyInvite invite = reapplyService.issueInvite(
                signup("http-invite-" + UUID.randomUUID() + "@gmail.com", result(member.getId())).getId());
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
                                """.formatted("school-" + UUID.randomUUID() + "@dgu.ac.kr", photo.getId(),
                                invite.getToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("DATING_NOT_VERIFIED"));

        assertThat(profileRepository.findByMemberId(member.getId())).isEmpty();
        verifyNoInteractions(mailSender);
    }

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
