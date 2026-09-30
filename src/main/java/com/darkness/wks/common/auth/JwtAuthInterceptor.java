package com.darkness.wks.common.auth;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@code /api/me/**} 등 인증이 필요한 경로에만 건다(등록은 {@link AuthWebConfig}). 그 외 API(사주·궁합·
 * 사전등록)는 이 인터셉터를 거치지 않는다 — 로그인 여부만 참고하는 곳은 {@link OptionalMember} 로
 * 쿠키를 읽되 실패해도 막지 않는다.
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    static final String MEMBER_ID_ATTRIBUTE = "wks.currentMemberId";

    private final JwtProvider jwtProvider;
    private final MemberExistence memberExistence;
    private final JwtCookie jwtCookie;

    public JwtAuthInterceptor(JwtProvider jwtProvider, MemberExistence memberExistence, JwtCookie jwtCookie) {
        this.jwtProvider = jwtProvider;
        this.memberExistence = memberExistence;
        this.jwtCookie = jwtCookie;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS preflight 는 쿠키를 안 실어 보낸다 — 여기서 막으면 브라우저가 본 요청을 아예 못 보낸다.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = JwtCookie.readFrom(request);
        Long memberId = jwtProvider.verify(token).orElse(null);
        if (memberId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        if (!memberExistence.exists(memberId)) {
            // 서명은 맞지만 회원 행이 없다(DB 에서 지워짐). 통과시키면 원장 쓰기가 FK 위반 500 이 된다 —
            // 비로그인으로 돌려보내고 쿠키도 지워 프론트가 다시 로그인하게 한다
            response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.clear().toString());
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        request.setAttribute(MEMBER_ID_ATTRIBUTE, memberId);
        return true;
    }
}
