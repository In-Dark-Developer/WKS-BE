package com.darkness.wks.auth;

import com.darkness.wks.auth.dto.KakaoLoginRequest;
import com.darkness.wks.auth.dto.KakaoLoginResponse;
import com.darkness.wks.common.auth.AuthProperties;
import com.darkness.wks.common.auth.JwtProvider;
import com.darkness.wks.member.MemberService;
import com.darkness.wks.member.entity.Member;
import com.darkness.wks.wallet.PartnerRewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 로그인 요청의 ref(제휴 코드)가 보상으로 이어지는지 — 카카오·DB 는 목으로 둔다 */
class AuthServiceTest {

    private static final String REDIRECT = "https://threadoffate.site/auth/kakao/callback";

    private final KakaoClient kakaoClient = mock(KakaoClient.class);
    private final MemberService memberService = mock(MemberService.class);
    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final PartnerRewardService partnerRewardService = mock(PartnerRewardService.class);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(REDIRECT, true,
                new AuthProperties.Kakao("id", "secret", "t", "u", 1000, 1000),
                new AuthProperties.Jwt("secret-0123456789-0123456789-abcdef", 15));
        authService = new AuthService(properties, new RedirectUriPolicy(properties), kakaoClient, memberService,
                jwtProvider, partnerRewardService);
        Member member = new Member(123L);
        ReflectionTestUtils.setField(member, "id", 7L);
        when(kakaoClient.fetchKakaoId("code", REDIRECT)).thenReturn(123L);
        when(memberService.loginAndLink(123L, null)).thenReturn(new MemberService.LoginResult(member, false, null));
        when(jwtProvider.issue(7L)).thenReturn("jwt");
    }

    @Test
    void festivalRefFillsRewardGranted() {
        when(partnerRewardService.grant(7L, "FESTIVAL"))
                .thenReturn(Optional.of(new PartnerRewardService.Granted("동국대 축제", 10)));

        AuthService.LoginOutcome outcome = authService.login(new KakaoLoginRequest("code", REDIRECT, null, "FESTIVAL"));

        assertThat(outcome.body().rewardGranted()).isEqualTo(new KakaoLoginResponse.RewardGranted("동국대 축제", 10));
    }

    @Test
    void alreadyRewardedOrUnknownRefStillLogsIn() {
        when(partnerRewardService.grant(7L, "WRONG")).thenReturn(Optional.empty());

        AuthService.LoginOutcome outcome = authService.login(new KakaoLoginRequest("code", REDIRECT, null, "WRONG"));

        assertThat(outcome.token()).isEqualTo("jwt");
        assertThat(outcome.body().rewardGranted()).isNull();
    }
}
