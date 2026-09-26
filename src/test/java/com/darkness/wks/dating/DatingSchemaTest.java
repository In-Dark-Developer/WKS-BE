package com.darkness.wks.dating;

import com.google.genai.Client;
import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.Gender;
import com.darkness.wks.common.auth.JwtProvider;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.dating.entity.DatingPhoto;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.dating.entity.DatingRequestStatus;
import com.darkness.wks.member.MemberRepository;
import com.darkness.wks.member.entity.Member;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@SpringBootTest(properties = {"gemini.api-key=test-key",
        "app.auth.jwt.secret=dating-test-secret-0123456789-abcdef", "app.auth.jwt.ttl-days=15"})
@Testcontainers
class DatingSchemaTest {

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
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

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
    DatingRecommendationService recommendationService;

    @Autowired
    DatingReasonService reasonService;

    @Autowired
    DatingRequestService requestService;

    @Autowired
    DatingRequestRepository requestRepository;

    @Autowired
    DatingUnlockChargeService unlockChargeService;

    @Autowired
    com.darkness.wks.wallet.WalletService walletService;

    @Autowired
    WebApplicationContext webContext;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @Test
    void appliesDatingMigrationAndValidatesJpaMappings() {
        Integer datingTables = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                AND table_name IN ('dating_profile', 'dating_photo', 'dating_recommendation', 'dating_request')
                """, Integer.class);
        Integer directResultLinks = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'dating_profile' AND column_name = 'result_id'
                """, Integer.class);
        Integer reasonColumns = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'dating_recommendation' AND column_name = 'reason_content'
                AND data_type = 'text'
                """, Integer.class);

        assertThat(datingTables).isEqualTo(4);
        assertThat(directResultLinks).isZero();
        assertThat(reasonColumns).isEqualTo(1);
    }

    @Test
    void datingRouteRequiresLoginCookie() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(webContext).build();
        mvc.perform(get("/api/dating/profile/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dating/profile/me")
                        .cookie(new Cookie("wks_token", jwtProvider.issue(999L))))
                .andExpect(status().isNotFound());
    }

    @Test
    void profileUpdateRouteIsNotExposed() throws Exception {
        assertThat(handlerMapping.getHandlerMethods().keySet()).noneMatch(mapping ->
                mapping.getMethodsCondition().getMethods().contains(RequestMethod.PATCH)
                        && mapping.getPathPatternsCondition() != null
                        && mapping.getPathPatternsCondition().getPatternValues()
                        .contains("/api/dating/profile/me"));
        var mvc = MockMvcBuilders.webAppContextSetup(webContext).build();
        mvc.perform(patch("/api/dating/profile/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.issue(999L)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @Transactional
    void keepsCurrentThreeWhenNewProfileJoins() {
        when(photoService.thumbnailUrl(any())).thenReturn("https://example.com/blurred.png");
        DatingProfile viewer = profile(900001L, Gender.MALE, "갑자", "을축", "병인");
        profile(900002L, Gender.FEMALE, "갑자", "을축", "병인");
        profile(900003L, Gender.FEMALE, "계해", "임술", "신유");
        profile(900004L, Gender.FEMALE, "무오", "기미", "경신");
        profile(900005L, Gender.MALE, "갑자", "을축", "병인");

        var first = recommendationService.getCurrent(viewer.getMemberId()).candidates();
        assertThat(first).hasSize(3);
        assertThat(first).allSatisfy(card -> {
            assertThat(card.blurredPhotoUrl()).isEqualTo("https://example.com/blurred.png");
            assertThat(card.fields().photo().locked()).isTrue();
        });
        assertThat(recommendationService.getCurrent(viewer.getMemberId()).candidates())
                .extracting(card -> card.candidateId()).containsExactlyElementsOf(
                        first.stream().map(card -> card.candidateId()).toList());

        DatingProfile newPerson = profile(900006L, Gender.FEMALE, "갑자", "을축", "병인");
        assertThat(recommendationService.getCurrent(viewer.getMemberId()).candidates())
                .extracting(card -> card.candidateId()).doesNotContain(newPerson.getId());

        assertThat(recommendationRepository.findAllByViewerMemberId(viewer.getMemberId())).hasSize(3);
    }

    @Test
    void datingReasonIsStoredOnlyAfterRequestedAndReused() {
        when(photoService.thumbnailUrl(any())).thenReturn("https://example.com/blurred.png");
        DatingProfile viewer = profile(950001L, Gender.MALE, "갑자", "을축", "병인");
        profile(950002L, Gender.FEMALE, "계해", "임술", "기유");
        UUID candidateId = recommendationService.getCurrent(viewer.getMemberId())
                .candidates().get(0).candidateId();
        verify(reasonGenerator, times(0)).generate(any(), any(), anyInt(), anyString());
        when(reasonGenerator.generate(any(), any(), anyInt(), anyString())).thenReturn("소개팅 이유");

        assertThat(reasonService.getOrCreate(viewer.getMemberId(), candidateId)).isEqualTo("소개팅 이유");
        assertThat(reasonService.getOrCreate(viewer.getMemberId(), candidateId)).isEqualTo("소개팅 이유");
        verify(reasonGenerator, times(1)).generate(any(), any(), anyInt(), anyString());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT reason_content FROM dating_recommendation
                WHERE viewer_member_id = ? AND candidate_profile_id = ?
                """, String.class, viewer.getMemberId(), candidateId)).isEqualTo("소개팅 이유");
    }

    /**
     * getCurrent() 의 추천 선정에 기대지 않고 추천 행을 직접 만든다. 이 클래스의 다른 테스트는
     * @Transactional 롤백 없이 프로필을 커밋하므로, verified 프로필을 만들면 이후 실행되는 다른
     * 테스트의 top-3 후보 풀을 오염시켜 그 테스트의 recipient 가 top-3 밖으로 밀려날 수 있다
     * (실제로 겪은 순서 의존 실패). 그래서 candidate 는 인증(markVerified) 하지 않는다 —
     * chargeAndMarkUnlocked 는 candidate.isEligible() 을 보지 않으므로 이 테스트엔 영향 없다.
     */
    private UUID directRecommendation(DatingProfile viewer, Long candidateKakaoId, Gender candidateGender) {
        Member candidateMember = memberRepository.saveAndFlush(new Member(candidateKakaoId));
        Result candidateResult = new Result("테스트", LocalDate.of(2000, 1, 1), null, null,
                candidateGender, "계해", "임술", "기유", null);
        candidateResult.linkMember(candidateMember.getId());
        resultRepository.saveAndFlush(candidateResult);
        DatingPhoto photo = photoRepository.saveAndFlush(new DatingPhoto(candidateMember.getId(),
                "dating-photos/" + candidateMember.getId() + "/" + UUID.randomUUID()));
        DatingProfile candidate = profileRepository.saveAndFlush(new DatingProfile(candidateMember.getId(),
                candidateKakaoId + "@dgu.ac.kr", "김후보", ContactMethod.INSTAGRAM, "test", "학과", "INFP",
                "소개", photo));
        // 일부러 markVerified() 하지 않는다 — 위 설명 참고

        recommendationRepository.saveAndFlush(
                new com.darkness.wks.dating.entity.DatingRecommendation(viewer.getMemberId(), candidate, 80));
        return candidate.getId();
    }

    @Test
    void unlockChargesOnceAndSecondCallIsFree() {
        DatingProfile viewer = profile(960001L, Gender.MALE, "갑자", "을축", "병인");
        UUID candidateId = directRecommendation(viewer, 960002L, Gender.FEMALE);
        walletService.credit(viewer.getMemberId(), com.darkness.wks.wallet.LedgerReason.SIGNUP_BONUS,
                viewer.getMemberId().toString(), 10);

        unlockChargeService.chargeAndMarkUnlocked(viewer.getMemberId(), candidateId, DatingUnlockField.NAME);
        assertThat(walletService.getBalance(viewer.getMemberId())).isEqualTo(3); // 10 - 7(NAME)

        unlockChargeService.chargeAndMarkUnlocked(viewer.getMemberId(), candidateId, DatingUnlockField.NAME);
        assertThat(walletService.getBalance(viewer.getMemberId())).isEqualTo(3); // 이미 해금 — 추가 차감 없음
    }

    @Test
    void unlockWithInsufficientBalanceThrows402() {
        DatingProfile viewer = profile(960003L, Gender.MALE, "갑자", "을축", "병인");
        UUID candidateId = directRecommendation(viewer, 960004L, Gender.FEMALE);

        assertThatThrownBy(() -> unlockChargeService.chargeAndMarkUnlocked(
                viewer.getMemberId(), candidateId, DatingUnlockField.PHOTO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_THREAD);
    }

    @Test
    @Transactional
    void acceptedRequestRevealsBothContactsAndKeepsProfilesEligible() {
        DatingProfile sender = profile(910001L, Gender.MALE, "갑자", "을축", "병인");
        DatingProfile recipient = profile(910002L, Gender.FEMALE, "갑자", "을축", "병인");
        DatingProfile anotherRecipient = profile(910003L, Gender.FEMALE, "계해", "임술", "신유");
        DatingProfile anotherViewer = profile(910004L, Gender.MALE, "무오", "기미", "경신");

        recommendationService.getCurrent(sender.getMemberId());
        var sent = requestService.send(sender.getMemberId(), recipient.getId());
        assertThat(sent.status()).isEqualTo(DatingRequestStatus.PENDING);
        assertThat(sent.contactValue()).isNull();
        assertThat(requestService.list(recipient.getMemberId(), "received")).hasSize(1);
        assertThat(requestService.list(sender.getMemberId(), "sent").get(0).contactValue()).isNull();

        var accepted = requestService.accept(recipient.getMemberId(), sent.requestId());
        assertThat(accepted.status()).isEqualTo(DatingRequestStatus.ACCEPTED);
        assertThat(accepted.contactValue()).isEqualTo(sender.getContactValue());
        assertThat(requestService.list(sender.getMemberId(), "sent").get(0).contactValue())
                .isEqualTo(recipient.getContactValue());
        assertThat(recommendationService.getCurrent(sender.getMemberId()).candidates())
                .extracting(card -> card.candidateId()).contains(recipient.getId(), anotherRecipient.getId());
        assertThat(recommendationService.getCurrent(anotherViewer.getMemberId()).candidates())
                .extracting(card -> card.candidateId()).contains(recipient.getId());
        var second = requestService.send(sender.getMemberId(), anotherRecipient.getId());
        assertThat(requestService.accept(anotherRecipient.getMemberId(), second.requestId()).status())
                .isEqualTo(DatingRequestStatus.ACCEPTED);
        assertThat(requestRepository.count()).isEqualTo(2);
    }

    @Test
    @Transactional
    void rejectedRequestKeepsBothProfilesEligibleAndContactsHidden() {
        DatingProfile sender = profile(920001L, Gender.MALE, "갑자", "을축", "병인");
        DatingProfile recipient = profile(920002L, Gender.FEMALE, "갑자", "을축", "병인");
        recommendationService.getCurrent(sender.getMemberId());

        var sent = requestService.send(sender.getMemberId(), recipient.getId());
        var rejected = requestService.reject(recipient.getMemberId(), sent.requestId());
        assertThat(rejected.status()).isEqualTo(DatingRequestStatus.REJECTED);
        assertThat(rejected.contactValue()).isNull();
        assertThat(sender.isEligible()).isTrue();
        assertThat(recipient.isEligible()).isTrue();
    }

    @Test
    @Transactional
    void duplicateRequestIsRejectedWithoutCreatingAnotherRow() {
        DatingProfile sender = profile(930001L, Gender.MALE, "갑자", "을축", "병인");
        DatingProfile recipient = profile(930002L, Gender.FEMALE, "갑자", "을축", "병인");
        recommendationService.getCurrent(sender.getMemberId());
        requestService.send(sender.getMemberId(), recipient.getId());

        assertThatThrownBy(() -> requestService.send(sender.getMemberId(), recipient.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DATING_REQUEST_CONFLICT);
    }

    @Test
    @Transactional
    void senderCanCancelAndSendAgainWhileRecipientNoLongerSeesCancelledRequest() {
        DatingProfile sender = profile(990001L, Gender.MALE, "갑자", "을축", "병인");
        DatingProfile recipient = profile(990002L, Gender.FEMALE, "갑자", "을축", "병인");
        recommendationService.getCurrent(sender.getMemberId());

        var first = requestService.send(sender.getMemberId(), recipient.getId());
        var cancelled = requestService.cancel(sender.getMemberId(), first.requestId());
        entityManager.flush();
        assertThat(cancelled.status()).isEqualTo(DatingRequestStatus.CANCELLED);
        assertThat(cancelled.respondedAt()).isNotNull();
        assertThat(cancelled.contactValue()).isNull();
        assertThat(requestService.list(sender.getMemberId(), "sent"))
                .extracting(response -> response.status()).containsExactly(DatingRequestStatus.CANCELLED);
        assertThat(requestService.list(recipient.getMemberId(), "received")).isEmpty();

        var second = requestService.send(sender.getMemberId(), recipient.getId());
        assertThat(second.requestId()).isNotEqualTo(first.requestId());
        assertThat(requestService.list(recipient.getMemberId(), "received"))
                .extracting(response -> response.requestId()).containsExactly(second.requestId());
        assertThat(requestRepository.count()).isEqualTo(2);
    }

    @Test
    @Transactional
    void onlySenderCanCancelPendingRequestOnce() {
        DatingProfile sender = profile(960001L, Gender.MALE, "갑자", "을축", "병인");
        DatingProfile recipient = profile(960002L, Gender.FEMALE, "갑자", "을축", "병인");
        recommendationService.getCurrent(sender.getMemberId());
        var sent = requestService.send(sender.getMemberId(), recipient.getId());

        assertThatThrownBy(() -> requestService.cancel(recipient.getMemberId(), sent.requestId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DATING_REQUEST_NOT_FOUND);
        requestService.cancel(sender.getMemberId(), sent.requestId());
        entityManager.flush();
        assertThatThrownBy(() -> requestService.cancel(sender.getMemberId(), sent.requestId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DATING_REQUEST_CONFLICT);
    }

    @Test
    @Transactional
    void acceptedRequestCannotBeCancelled() {
        DatingProfile sender = profile(970001L, Gender.MALE, "갑자", "을축", "병인");
        DatingProfile recipient = profile(970002L, Gender.FEMALE, "갑자", "을축", "병인");
        recommendationService.getCurrent(sender.getMemberId());
        var sent = requestService.send(sender.getMemberId(), recipient.getId());
        requestService.accept(recipient.getMemberId(), sent.requestId());
        entityManager.flush();

        assertThatThrownBy(() -> requestService.cancel(sender.getMemberId(), sent.requestId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DATING_REQUEST_CONFLICT);
    }

    @Test
    @Transactional
    void cancelRouteRequiresSenderCookie() throws Exception {
        DatingProfile sender = profile(980001L, Gender.MALE, "갑자", "을축", "병인");
        DatingProfile recipient = profile(980002L, Gender.FEMALE, "갑자", "을축", "병인");
        recommendationService.getCurrent(sender.getMemberId());
        var sent = requestService.send(sender.getMemberId(), recipient.getId());
        var mvc = MockMvcBuilders.webAppContextSetup(webContext).build();
        String path = "/api/dating/requests/" + sent.requestId() + "/cancel";

        mvc.perform(post(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).cookie(new Cookie("wks_token", jwtProvider.issue(recipient.getMemberId()))))
                .andExpect(status().isNotFound());
        mvc.perform(post(path).cookie(new Cookie("wks_token", jwtProvider.issue(sender.getMemberId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @Transactional
    void onlyRecipientCanAcceptRequest() {
        DatingProfile sender = profile(940001L, Gender.MALE, "갑자", "을축", "병인");
        DatingProfile recipient = profile(940002L, Gender.FEMALE, "갑자", "을축", "병인");
        recommendationService.getCurrent(sender.getMemberId());
        var sent = requestService.send(sender.getMemberId(), recipient.getId());

        assertThatThrownBy(() -> requestService.accept(sender.getMemberId(), sent.requestId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DATING_REQUEST_NOT_FOUND);
    }

    private DatingProfile profile(Long kakaoId, Gender gender, String year, String month, String day) {
        Member member = memberRepository.saveAndFlush(new Member(kakaoId));
        Result result = new Result("테스트", LocalDate.of(2000, 1, 1), null, null,
                gender, year, month, day, null);
        result.linkMember(member.getId());
        resultRepository.saveAndFlush(result);
        DatingPhoto photo = photoRepository.saveAndFlush(
                new DatingPhoto(member.getId(), "dating-photos/" + member.getId() + "/" + UUID.randomUUID()));
        DatingProfile profile = new DatingProfile(member.getId(), kakaoId + "@dgu.ac.kr",
                "테스트", ContactMethod.INSTAGRAM, "test", "학과", "INFP", "소개", photo);
        profile.markVerified(Instant.now());
        return profileRepository.saveAndFlush(profile);
    }
}
