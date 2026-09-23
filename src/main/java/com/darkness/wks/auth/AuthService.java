package com.darkness.wks.auth;

import com.darkness.wks.auth.dto.KakaoLoginRequest;
import com.darkness.wks.auth.dto.KakaoLoginResponse;
import com.darkness.wks.common.auth.AuthProperties;
import com.darkness.wks.common.auth.JwtProvider;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.member.MemberService;
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

    public KakaoLoginResponse login(KakaoLoginRequest request) {
        if (!properties.isConfigured()) {
            // 카카오 client-id/secret·JWT_SECRET 중 하나라도 비어 있다 — 로그인만 막고 나머지 API 는
            // 그대로 기동한다(운영 .env 반영 전에 dev 가 배포되어도 서비스 전체가 죽지 않는다).
            throw new BusinessException(ErrorCode.KAKAO_UNAVAILABLE);
        }
        redirectUriPolicy.validate(request.redirectUri());
        long kakaoId = kakaoClient.fetchKakaoId(request.code(), request.redirectUri());
        MemberService.LoginResult result = memberService.loginAndLink(kakaoId, request.resultId());
        String token = jwtProvider.issue(result.member().getId());
        return new KakaoLoginResponse(token, result.isNewUser(), result.restoredResultId(), null);
    }
}
