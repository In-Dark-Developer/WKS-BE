package com.darkness.wks.wallet;

import com.darkness.wks.common.auth.JwtProvider;
import com.darkness.wks.member.MemberRepository;
import com.darkness.wks.member.entity.Member;
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

import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 지갑 API 를 실제 DB·HTTP 로 확인한다. 회원 행이 없는 토큰이 원장 FK 위반 500 대신 401 로 끝나는지는
 * 인터셉터·원장 FK 가 함께 있어야 드러난다(2026-09-30 dev 에서 발견).
 */
@SpringBootTest(properties = {"gemini.api-key=test-key",
        "app.auth.jwt.secret=wallet-flow-test-secret-0123456789-abcd",
        "app.partner.rewards=FESTIVAL:10:동국대 축제"})
@Testcontainers
class WalletFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @MockitoBean
    Client geminiClient;

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    WebApplicationContext webContext;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(webContext).build();
    }

    private Cookie login(long memberId) {
        return new Cookie("wks_token", jwtProvider.issue(memberId));
    }

    @Test
    void walletListsPartnerCodesThisAccountReceived() throws Exception {
        Member member = memberRepository.saveAndFlush(
                new Member(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)));
        Cookie cookie = login(member.getId());

        mvc().perform(get("/api/wallet").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.partnerRewards").isEmpty());

        mvc().perform(post("/api/wallet/partner-rewards").cookie(cookie)
                        .contentType("application/json").content("{\"ref\":\"festival\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rewardGranted.amount").value(10));

        mvc().perform(get("/api/wallet").cookie(cookie))
                .andExpect(jsonPath("$.data.balance").value(10))
                .andExpect(jsonPath("$.data.partnerRewards[0]").value("FESTIVAL"));
    }

    @Test
    void tokenOfDeletedMemberIsUnauthenticatedAndCookieIsCleared() throws Exception {
        // 서명·만료는 유효하지만 회원 행이 없다 — 전에는 /api/me 200(잔액 0), 원장 쓰기 500 이었다
        Cookie cookie = login(Long.MAX_VALUE);

        mvc().perform(get("/api/me").cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        mvc().perform(post("/api/wallet/check-in").cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc().perform(post("/api/wallet/partner-rewards").cookie(cookie)
                        .contentType("application/json").content("{\"ref\":\"FESTIVAL\"}"))
                .andExpect(status().isUnauthorized());
    }
}
