package com.darkness.wks.compatibility;

import com.darkness.wks.common.Gender;
import com.darkness.wks.compatibility.dto.CreateCompatibilityRequest;
import com.darkness.wks.member.MemberRepository;
import com.darkness.wks.member.MemberService;
import com.darkness.wks.member.entity.Member;
import com.darkness.wks.result.ResultRepository;
import com.darkness.wks.result.entity.Result;
import com.darkness.wks.wallet.WalletService;
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

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 친구 등록 실(+2)의 규칙을 실제 DB(원장 UNIQUE·advisory lock)로 확인한다 (2026-09-29 결정).
 * 로그인 친구만 센다, 같은 계정은 한 번만, 공유자·친구 어느 쪽이 나중에 로그인해도 결과가 연결될 때 소급 지급.
 */
@SpringBootTest(properties = {"gemini.api-key=test-key",
        "app.auth.jwt.secret=wallet-test-secret-0123456789-abcdef", "app.auth.jwt.ttl-days=15"})
@Testcontainers
class MapFriendRewardFlowTest {

    private static final int SIGNUP_BONUS = 10;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @MockitoBean
    Client geminiClient;

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired
    CompatibilityService compatibilityService;

    @Autowired
    MemberService memberService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    WalletService walletService;

    private Result result(String day, Gender gender, Long memberId) {
        Result result = new Result("친구", LocalDate.of(2001, 1, 1), null, null, gender, "경진", "무인", day, null);
        if (memberId != null) {
            result.linkMember(memberId);
        }
        return resultRepository.saveAndFlush(result);
    }

    private void register(Result origin, Result guest) {
        compatibilityService.createCompatibility(origin.getShareId().toString(),
                new CreateCompatibilityRequest(guest.getId().toString()));
    }

    private long newMember() {
        return memberRepository.saveAndFlush(new Member(kakaoId())).getId();
    }

    private long kakaoId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    @Test
    void loggedInFriendPaysTwoOncePerAccountAndAnonymousDoesNotCount() {
        long memberId = newMember();
        Result origin = result("갑자", Gender.MALE, memberId);

        register(origin, result("을축", Gender.FEMALE, null));          // 익명 친구 — 안 센다
        assertThat(walletService.getBalance(memberId)).isZero();

        long friend = newMember();
        register(origin, result("병인", Gender.FEMALE, friend));        // 로그인 친구 → +2
        assertThat(walletService.getBalance(memberId)).isEqualTo(2);

        // 계정당 결과가 하나(uq_result_member)라 같은 계정을 다시 등록하면 기존 궁합이 돌아온다 — 지급 없음
        register(origin, resultRepository.findByMemberId(friend).orElseThrow());
        assertThat(walletService.getBalance(memberId)).isEqualTo(2);

        register(origin, result("기사", Gender.FEMALE, newMember()));   // 다른 계정 → +2
        assertThat(walletService.getBalance(memberId)).isEqualTo(4);
    }

    @Test
    void friendsRegisteredBeforeSharerLoginCountWhenResultIsLinked() {
        Result origin = result("병인", Gender.FEMALE, null);
        register(origin, result("정묘", Gender.MALE, newMember()));
        register(origin, result("기사", Gender.MALE, newMember()));
        register(origin, result("경오", Gender.MALE, null));            // 익명 — 안 센다

        MemberService.LoginResult login = memberService.loginAndLink(kakaoId(), origin.getId().toString());
        long memberId = login.member().getId();
        assertThat(walletService.getBalance(memberId)).isEqualTo(SIGNUP_BONUS + 4);

        // 다시 로그인해도(이미 연결된 결과) 소급이 두 번 되지 않는다
        memberService.loginAndLink(memberRepository.findById(memberId).orElseThrow().getKakaoId(),
                origin.getId().toString());
        assertThat(walletService.getBalance(memberId)).isEqualTo(SIGNUP_BONUS + 4);
    }

    @Test
    void anonymousFriendCountsWhenFriendLogsInLater() {
        long memberId = newMember();
        Result origin = result("갑자", Gender.MALE, memberId);
        Result guest = result("을축", Gender.FEMALE, null);
        register(origin, guest);
        assertThat(walletService.getBalance(memberId)).isZero();

        // 친구가 나중에 로그인해 그 결과를 계정에 연결 → 공유자에게 +2
        memberService.loginAndLink(kakaoId(), guest.getId().toString());
        assertThat(walletService.getBalance(memberId)).isEqualTo(2);
    }
}
