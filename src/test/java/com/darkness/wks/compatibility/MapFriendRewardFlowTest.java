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
 * 친구 등록 실(+3)의 두 규칙을 실제 DB(원장 UNIQUE·advisory lock)로 확인한다 (2026-09-28 결정).
 * 같은 사람은 한 번만, 로그인 전에 쌓인 친구는 로그인해 결과가 연결될 때 소급 지급.
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

    private long kakaoId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    @Test
    void everyFifthDistinctFriendPaysThreeAndDuplicatesOrSelfDoNotCount() {
        long memberId = memberRepository.saveAndFlush(new Member(kakaoId())).getId();
        Result origin = result("갑자", Gender.MALE, memberId);

        register(origin, result("을축", Gender.FEMALE, null));                    // 1명
        // 같은 팔자·성별로 결과를 새로 만들어 다시 등록 — 궁합은 새로 생기지만 세지 않는다
        register(origin, result("을축", Gender.FEMALE, null));
        // 같은 팔자라도 성별이 다르면 다른 사람
        register(origin, result("을축", Gender.MALE, null));                      // 2명
        // 공유자 자신과 같은 사람(내 결과를 새로 만들어 내 링크에 등록)은 세지 않는다
        register(origin, result("갑자", Gender.MALE, null));
        register(origin, result("병인", Gender.FEMALE, null));                    // 3명
        register(origin, result("정묘", Gender.FEMALE, null));                    // 4명
        assertThat(walletService.getBalance(memberId)).isZero();

        register(origin, result("무진", Gender.FEMALE, null));                    // 5명 → +3
        assertThat(walletService.getBalance(memberId)).isEqualTo(3);
    }

    @Test
    void friendsRegisteredBeforeLoginCountWhenResultIsLinked() {
        Result origin = result("병인", Gender.FEMALE, null);
        for (String day : new String[]{"정묘", "무진", "기사", "경오", "신미", "임신"}) { // 6명
            register(origin, result(day, Gender.MALE, null));
        }
        register(origin, result("무진", Gender.MALE, null)); // 같은 사람 — 소급에서도 한 번만 센다

        MemberService.LoginResult login = memberService.loginAndLink(kakaoId(), origin.getId().toString());
        long memberId = login.member().getId();

        assertThat(walletService.getBalance(memberId)).isEqualTo(SIGNUP_BONUS + 3); // 6명 중 5번째에 +3

        // 연결 뒤 새 친구는 이어서 센다. 이미 센 사람은 다시 세지 않는다
        for (String day : new String[]{"계유", "갑술", "을해"}) { // 7·8·9명
            register(origin, result(day, Gender.MALE, null));
        }
        register(origin, result("정묘", Gender.MALE, null));
        assertThat(walletService.getBalance(memberId)).isEqualTo(SIGNUP_BONUS + 3);
        register(origin, result("병자", Gender.MALE, null)); // 10명 → +3
        assertThat(walletService.getBalance(memberId)).isEqualTo(SIGNUP_BONUS + 6);

        // 다시 로그인해도(이미 연결된 결과) 소급이 두 번 되지 않는다
        memberService.loginAndLink(memberRepository.findById(memberId).orElseThrow().getKakaoId(),
                origin.getId().toString());
        assertThat(walletService.getBalance(memberId)).isEqualTo(SIGNUP_BONUS + 6);
    }
}
