package com.darkness.wks.dating;

import com.google.genai.Client;
import com.darkness.wks.common.ContactMethod;
import com.darkness.wks.common.Gender;
import com.darkness.wks.common.auth.JwtProvider;
import com.darkness.wks.dating.entity.DatingPhoto;
import com.darkness.wks.dating.entity.DatingProfile;
import com.darkness.wks.member.MemberRepository;
import com.darkness.wks.member.entity.Member;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    WebApplicationContext webContext;

    @Autowired
    JwtProvider jwtProvider;

    @Test
    void appliesDatingMigrationAndValidatesJpaMappings() {
        Integer profileCount = jdbcTemplate.queryForObject("SELECT count(*) FROM dating_profile", Integer.class);
        Integer photoCount = jdbcTemplate.queryForObject("SELECT count(*) FROM dating_photo", Integer.class);
        Integer recommendationCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM dating_recommendation", Integer.class);
        Integer directResultLinks = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'dating_profile' AND column_name = 'result_id'
                """, Integer.class);

        assertThat(profileCount).isZero();
        assertThat(photoCount).isZero();
        assertThat(recommendationCount).isZero();
        assertThat(directResultLinks).isZero();
    }

    @Test
    void datingRouteRequiresBearerToken() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(webContext).build();
        mvc.perform(get("/api/dating/profile/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dating/profile/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.issue(999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void keepsCurrentThreeAndReplacesMatchedCandidateWithUnseenPerson() {
        DatingProfile viewer = profile(900001L, Gender.MALE, "갑자", "을축", "병인");
        profile(900002L, Gender.FEMALE, "갑자", "을축", "병인");
        profile(900003L, Gender.FEMALE, "계해", "임술", "신유");
        profile(900004L, Gender.FEMALE, "무오", "기미", "경신");
        profile(900005L, Gender.MALE, "갑자", "을축", "병인");

        var first = recommendationService.getCurrent(viewer.getMemberId()).candidates();
        assertThat(first).hasSize(3);
        Set<UUID> firstIds = first.stream().map(card -> card.candidateId()).collect(Collectors.toSet());
        assertThat(recommendationService.getCurrent(viewer.getMemberId()).candidates())
                .extracting(card -> card.candidateId()).containsExactlyElementsOf(
                        first.stream().map(card -> card.candidateId()).toList());

        DatingProfile newPerson = profile(900006L, Gender.FEMALE, "갑자", "을축", "병인");
        assertThat(recommendationService.getCurrent(viewer.getMemberId()).candidates())
                .extracting(card -> card.candidateId()).doesNotContain(newPerson.getId());

        DatingProfile matched = profileRepository.findById(first.get(0).candidateId()).orElseThrow();
        matched.markMatched(Instant.now());
        var updated = recommendationService.getCurrent(viewer.getMemberId()).candidates();
        assertThat(updated).hasSize(3);
        assertThat(updated).extracting(card -> card.candidateId()).contains(newPerson.getId());
        assertThat(updated).extracting(card -> card.candidateId()).doesNotContain(matched.getId());
        assertThat(recommendationRepository.findAllByViewerMemberId(viewer.getMemberId())).hasSize(4);
        assertThat(firstIds).doesNotContain(newPerson.getId());
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
