package com.darkness.wks.member;

import com.darkness.wks.common.Gender;
import com.darkness.wks.common.auth.JwtProvider;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.member.entity.Member;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import com.google.genai.Client;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"gemini.api-key=test-key",
        "app.auth.jwt.secret=link-test-secret-0123456789-abcdef", "app.auth.jwt.ttl-days=15"})
@Testcontainers
class ResultLinkIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @MockitoBean
    Client geminiClient;

    @MockitoBean
    JavaMailSender mailSender;

    @MockitoBean
    com.darkness.wks.dating.DatingPhotoService photoService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    ResultLinkService resultLinkService;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    WebApplicationContext webContext;

    @Test
    void linksNewResultAfterLoginAndAllowsDatingPrerequisite() throws Exception {
        Member member = memberRepository.saveAndFlush(new Member(104001L));
        Result result = resultRepository.saveAndFlush(result());
        var mvc = MockMvcBuilders.webAppContextSetup(webContext).build();
        String request = "{\"resultId\":\"" + result.getId() + "\"}";

        mvc.perform(post("/api/me/result").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/result").cookie(cookie(member))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultId").value(result.getId().toString()));
        mvc.perform(get("/api/me").cookie(cookie(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasResult").value(true));
        assertThat(resultRepository.findById(result.getId()).orElseThrow().getMemberId())
                .isEqualTo(member.getId());
    }

    @Test
    void keepsExistingAccountResultAndDoesNotClaimAnotherResult() {
        Member member = memberRepository.saveAndFlush(new Member(104002L));
        Result owned = result();
        owned.linkMember(member.getId());
        resultRepository.saveAndFlush(owned);
        Result unused = resultRepository.saveAndFlush(result());

        var response = resultLinkService.link(member.getId(), unused.getId().toString());

        assertThat(response.resultId()).isEqualTo(owned.getId());
        assertThat(resultRepository.findById(unused.getId()).orElseThrow().getMemberId()).isNull();
    }

    @Test
    void rejectsMissingOrAnotherAccountsResultWithoutRevealingOwnership() throws Exception {
        Member owner = memberRepository.saveAndFlush(new Member(104003L));
        Member requester = memberRepository.saveAndFlush(new Member(104004L));
        Result owned = result();
        owned.linkMember(owner.getId());
        resultRepository.saveAndFlush(owned);
        var mvc = MockMvcBuilders.webAppContextSetup(webContext).build();

        for (UUID id : new UUID[]{owned.getId(), UUID.randomUUID()}) {
            mvc.perform(post("/api/me/result").cookie(cookie(requester))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"resultId\":\"" + id + "\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("RESULT_NOT_FOUND"));
        }
        mvc.perform(post("/api/me/result").cookie(cookie(requester))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultId\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void concurrentClaimsOfSameResultAllowOnlyOneAccount() throws Exception {
        Member first = memberRepository.saveAndFlush(new Member(104005L));
        Member second = memberRepository.saveAndFlush(new Member(104006L));
        Result result = resultRepository.saveAndFlush(result());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> firstAttempt = executor.submit(() -> claim(start, first.getId(), result.getId()));
            Future<String> secondAttempt = executor.submit(() -> claim(start, second.getId(), result.getId()));
            start.countDown();

            assertThat(new String[]{firstAttempt.get(), secondAttempt.get()})
                    .containsExactlyInAnyOrder("LINKED", ErrorCode.RESULT_NOT_FOUND.name());
        } finally {
            executor.shutdownNow();
        }
        Long ownerId = resultRepository.findById(result.getId()).orElseThrow().getMemberId();
        assertThat(ownerId).isIn(first.getId(), second.getId());
    }

    private String claim(CountDownLatch start, Long memberId, UUID resultId) throws InterruptedException {
        start.await();
        try {
            resultLinkService.link(memberId, resultId.toString());
            return "LINKED";
        } catch (BusinessException exception) {
            return exception.getErrorCode().name();
        }
    }

    private Cookie cookie(Member member) {
        return new Cookie("wks_token", jwtProvider.issue(member.getId()));
    }

    private Result result() {
        return new Result("테스트", LocalDate.of(2000, 1, 1), null, null,
                Gender.MALE, "갑자", "을축", "병인", null);
    }
}
