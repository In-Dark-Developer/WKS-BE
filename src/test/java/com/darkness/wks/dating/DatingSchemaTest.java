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
