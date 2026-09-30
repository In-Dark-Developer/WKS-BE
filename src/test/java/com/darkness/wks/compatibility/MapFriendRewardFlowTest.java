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
import java.util.List;
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
                new CreateCompatibilityRequest(guest.getId().toString()), null);
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

    // ---- 로그인 순서와 무관하게 같은 결과 (2026-09-30, V27 claimed_member_id) ----

    private void register(Result origin, Result guest, Long requesterMemberId) {
        compatibilityService.createCompatibility(origin.getShareId().toString(),
                new CreateCompatibilityRequest(guest.getId().toString()), requesterMemberId);
    }

    private Member memberWithResult() {
        Member member = memberRepository.saveAndFlush(new Member(kakaoId()));
        result("무진", Gender.FEMALE, member.getId());
        return member;
    }

    @Test
    void friendWhoseAccountHasResultStarsAnonymouslyThenLogsIn() {
        long owner = newMember();
        Result origin = result("갑자", Gender.MALE, owner);
        Member friend = memberWithResult();

        // 인앱 브라우저 등 비로그인으로 새 결과를 만들어 별을 남긴다 — 이때는 누구 별인지 모른다
        Result anonymous = result("을축", Gender.FEMALE, null);
        register(origin, anonymous);
        assertThat(walletService.getBalance(owner)).isZero();

        // 로그인하면 계정 결과가 복원돼 대표 결과로는 연결되지 않지만, 이 계정이 만든 결과로 기록돼 소급 지급된다
        MemberService.LoginResult login = memberService.loginAndLink(friend.getKakaoId(), anonymous.getId().toString());
        assertThat(login.restoredResultId()).isNotEqualTo(anonymous.getId().toString());
        assertThat(walletService.getBalance(owner)).isEqualTo(2);
        assertThat(resultRepository.findById(anonymous.getId()).orElseThrow().getMemberId()).isNull();
    }

    @Test
    void loggedInFriendStarsWithResultThatIsNotTheAccountResult() {
        long owner = newMember();
        Result origin = result("갑자", Gender.MALE, owner);
        Member friend = memberWithResult();

        register(origin, result("을축", Gender.FEMALE, null), friend.getId());
        assertThat(walletService.getBalance(owner)).isEqualTo(2);
    }

    @Test
    void allResultsMadeBeforeLoginCountWhenPresentedAtLogin() {
        long ownerA = newMember();
        long ownerC = newMember();
        Result mapA = result("갑자", Gender.MALE, ownerA);
        Result mapC = result("병인", Gender.MALE, ownerC);
        Member friend = memberWithResult();

        // 링크마다 '새로 작성하기' — 브라우저 세션은 마지막 결과만 기억한다
        Result first = result("을축", Gender.FEMALE, null);
        Result second = result("정묘", Gender.FEMALE, null);
        register(mapA, first);
        register(mapC, second);

        memberService.loginAndLink(friend.getKakaoId(), second.getId().toString(),
                List.of(first.getId().toString(), second.getId().toString()));
        assertThat(walletService.getBalance(ownerA)).isEqualTo(2);
        assertThat(walletService.getBalance(ownerC)).isEqualTo(2);
    }

    @Test
    void ownerWhoseAccountHasResultSharesAnonymousMapThenLogsIn() {
        Member owner = memberWithResult();
        Result anonymousMap = result("갑자", Gender.MALE, null);
        register(anonymousMap, result("을축", Gender.FEMALE, newMember()));

        memberService.loginAndLink(owner.getKakaoId(), null, List.of(anonymousMap.getId().toString()));
        assertThat(walletService.getBalance(owner.getId())).isEqualTo(2);
    }

    @Test
    void starOnOwnMapFromAnotherResultPaysNothing() {
        long owner = newMember();
        Result origin = result("갑자", Gender.MALE, owner);

        register(origin, result("을축", Gender.FEMALE, null), owner);
        assertThat(walletService.getBalance(owner)).isZero();
    }

    @Test
    void resultAlreadyClaimedIsNotTakenOverByAnotherAccount() {
        long owner = newMember();
        Result origin = result("갑자", Gender.MALE, owner);
        Member friend = memberWithResult();
        Result guest = result("을축", Gender.FEMALE, null);
        register(origin, guest, friend.getId());

        // 같은 결과 id 를 쥔 다른 계정이 로그인하며 제시해도 주인이 바뀌지 않고, 보상도 두 번 나가지 않는다
        Member other = memberWithResult();
        memberService.loginAndLink(other.getKakaoId(), null, List.of(guest.getId().toString()));
        assertThat(resultRepository.findById(guest.getId()).orElseThrow().getOwnerMemberId()).isEqualTo(friend.getId());
        assertThat(walletService.getBalance(owner)).isEqualTo(2);
    }
}
