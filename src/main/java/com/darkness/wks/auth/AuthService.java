package com.darkness.wks.auth;

import com.darkness.wks.auth.dto.KakaoLoginRequest;
import com.darkness.wks.auth.dto.KakaoLoginResponse;
import com.darkness.wks.common.auth.AuthProperties;
import com.darkness.wks.common.auth.JwtProvider;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.member.MemberService;
import com.darkness.wks.wallet.PartnerRewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * POST /api/auth/kakao 흐름 조율. 카카오 왕복(HTTP, 수백 ms~수 초)과 DB 트랜잭션을 한 메서드에
 * 섞지 않는다 — {@link MemberService#loginAndLink} 호출 전까지는 트랜잭션을 열지 않는다
 * (커넥션 풀 10개뿐이라 외부 호출 동안 커넥션을 붙잡지 않기 위함).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthProperties properties;
    private final RedirectUriPolicy redirectUriPolicy;
    private final KakaoClient kakaoClient;
    private final MemberService memberService;
    private final JwtProvider jwtProvider;
    private final PartnerRewardService partnerRewardService;

    public LoginOutcome login(KakaoLoginRequest request) {
        if (!properties.isConfigured()) {
            // 카카오 client-id/secret·JWT_SECRET 중 하나라도 비어 있다 — 로그인만 막고 나머지 API 는
            // 그대로 기동한다(운영 .env 반영 전에 dev 가 배포되어도 서비스 전체가 죽지 않는다).
            throw new BusinessException(ErrorCode.KAKAO_UNAVAILABLE);
        }
        redirectUriPolicy.validate(request.redirectUri());
        long kakaoId = kakaoClient.fetchKakaoId(request.code(), request.redirectUri());
        MemberService.LoginResult result =
                memberService.loginAndLink(kakaoId, request.resultId(), request.resultIds());
        Long memberId = result.member().getId();
        // 제휴처(축제 사이트) 링크로 들어와 로그인하면 계정당 1회 보상. 모르는 ref 는 조용히 무시한다(plan.md §8.1)
        KakaoLoginResponse.RewardGranted reward = partnerRewardService.grant(memberId, request.ref())
                .map(granted -> new KakaoLoginResponse.RewardGranted(granted.partnerName(), granted.amount()))
                .orElse(null);
        String token = jwtProvider.issue(memberId);
        KakaoLoginResponse body = new KakaoLoginResponse(result.isNewUser(), result.restoredResultId(), reward);
        return new LoginOutcome(token, body);
    }

    /** 토큰은 쿠키로만 내려간다 — {@link AuthController} 가 여기서 꺼내 {@code Set-Cookie} 를 만든다. */
    public record LoginOutcome(String token, KakaoLoginResponse body) {
    }
}
