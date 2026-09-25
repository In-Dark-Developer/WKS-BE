package com.darkness.wks.wallet;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.member.MemberRepository;
import com.darkness.wks.member.entity.Member;
import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DB 제약(UNIQUE·advisory lock)이 실제로 동작하는지는 목으로 확인할 수 없어 Testcontainers로 검증한다
 * (plan.md §9.4·§9.5, FR-TH-02·03).
 */
@SpringBootTest(properties = {"gemini.api-key=test-key"})
@Testcontainers
class WalletServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @MockitoBean
    Client geminiClient;

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired
    WalletService walletService;

    @Autowired
    MemberRepository memberRepository;

    private long newMember() {
        return memberRepository.saveAndFlush(new Member((long) (Math.random() * Long.MAX_VALUE))).getId();
    }

    @Test
    void 잔액은_원장_합계다() {
        long memberId = newMember();

        walletService.credit(memberId, LedgerReason.SIGNUP_BONUS, String.valueOf(memberId), 10);
        walletService.credit(memberId, LedgerReason.CHECK_IN, "2026-09-26", 5);
        walletService.debit(memberId, LedgerReason.UNLOCK, "rec1:NAME", 7);

        assertThat(walletService.getBalance(memberId)).isEqualTo(8); // 10 + 5 - 7
    }

    @Test
    void 같은_ref_id로_두_번_지급해도_한_번만_들어간다() {
        long memberId = newMember();

        boolean first = walletService.credit(memberId, LedgerReason.CHECK_IN, "2026-09-26", 5);
        boolean second = walletService.credit(memberId, LedgerReason.CHECK_IN, "2026-09-26", 5);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(walletService.getBalance(memberId)).isEqualTo(5);
    }

    @Test
    void 잔액보다_많이_차감하면_INSUFFICIENT_THREAD() {
        long memberId = newMember();
        walletService.credit(memberId, LedgerReason.SIGNUP_BONUS, String.valueOf(memberId), 10);

        assertThatThrownBy(() -> walletService.debit(memberId, LedgerReason.UNLOCK, "rec1:PHOTO", 11))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_THREAD);
        assertThat(walletService.getBalance(memberId)).isEqualTo(10); // 실패한 차감은 원장에 안 남는다
    }

    @Test
    void 같은_ref_id로_두_번_차감해도_한_번만_빠진다() {
        long memberId = newMember();
        walletService.credit(memberId, LedgerReason.SIGNUP_BONUS, String.valueOf(memberId), 10);

        walletService.debit(memberId, LedgerReason.UNLOCK, "rec1:PHOTO", 10);
        walletService.debit(memberId, LedgerReason.UNLOCK, "rec1:PHOTO", 10); // 이미 처리됨 — 조용히 통과

        assertThat(walletService.getBalance(memberId)).isEqualTo(0);
    }
}
