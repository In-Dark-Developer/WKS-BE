package com.darkness.wks.result;

import com.darkness.wks.common.Gender;
import com.darkness.wks.common.auth.JwtProvider;
import com.darkness.wks.member.MemberRepository;
import com.darkness.wks.member.entity.Member;
import com.darkness.wks.saju.SajuPillars;
import com.google.genai.Client;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 사주 결과 생성이 해석(Gemini) 동안 트랜잭션을 열지 않고, 저장·계정 연결은 실제 DB 에서 그대로 되는지 확인한다.
 * 트랜잭션 경계는 목킹으로는 드러나지 않아 실제 컨텍스트로 본다.
 */
@SpringBootTest(properties = {"gemini.api-key=test-key",
        "app.auth.jwt.secret=result-flow-test-secret-0123456789-abcd"})
@Testcontainers
class ResultCreateFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @MockitoBean
    Client geminiClient;

    @MockitoBean
    ResultAnalysisPort analysisPort;

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    WebApplicationContext webContext;

    private MockMvc mvc;
    private final AtomicBoolean analysedInsideTransaction = new AtomicBoolean();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webContext).build();
        analysedInsideTransaction.set(false);
        when(analysisPort.analysisVersion()).thenReturn(Integer.MAX_VALUE);
        when(analysisPort.analyze(any(), any(), any())).thenAnswer(invocation -> {
            analysedInsideTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            return new ResultAnalysisPort.AnalysisResult(
                    new SajuPillars("임오", "계묘", "갑진", "신미"), "운명 설명",
                    List.of(new ResultAnalysisPort.Fortune(FortuneCategory.MARRIAGE, 90, "결혼"),
                            new ResultAnalysisPort.Fortune(FortuneCategory.CHILDREN, 70, "자녀"),
                            new ResultAnalysisPort.Fortune(FortuneCategory.LOVE, 50, "연애")),
                    "잘 맞는 기운");
        });
    }

    private static String body(String birthDate) {
        return """
                {"nickname":"도윤","calendarType":"SOLAR","birthDate":"%s","birthTime":"14:30","gender":"MALE"}
                """.formatted(birthDate);
    }

    @Test
    void analysesOutsideTransactionAndStoresResult() throws Exception {
        String response = mvc.perform(post("/api/results").contentType("application/json").content(body("2002-03-14")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fortunes[0].grade").value("S"))
                .andReturn().getResponse().getContentAsString();

        assertThat(analysedInsideTransaction).isFalse();
        UUID resultId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(response, "$.data.resultId"));
        assertThat(resultRepository.findById(resultId)).isPresent();
    }

    @Test
    void linksToLoggedInMemberOnlyWhenAccountHasNoResult() throws Exception {
        Member member = memberRepository.saveAndFlush(new Member(990001L));
        Cookie cookie = new Cookie("wks_token", jwtProvider.issue(member.getId()));

        mvc.perform(post("/api/results").cookie(cookie).contentType("application/json").content(body("2001-01-01")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/results").cookie(cookie).contentType("application/json").content(body("2001-01-02")))
                .andExpect(status().isCreated());

        // 계정당 결과 1개 — 두 번째 결과는 익명으로 남는다
        assertThat(resultRepository.findByMemberId(member.getId())).get()
                .extracting(r -> r.getBirthDate().toString()).isEqualTo("2001-01-01");
        assertThat(resultRepository.findAll()).filteredOn(r -> r.getGender() == Gender.MALE
                && "2001-01-02".equals(r.getBirthDate().toString())).allMatch(r -> r.getMemberId() == null);
    }
}
