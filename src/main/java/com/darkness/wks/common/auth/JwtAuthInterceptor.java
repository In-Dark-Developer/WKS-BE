package com.darkness.wks.common.auth;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@code /api/me/**} 등 인증이 필요한 경로에만 건다(등록은 {@link AuthWebConfig}). 그 외 API(사주·궁합·
 * 사전등록)는 이 인터셉터를 거치지 않고 쿠키를 읽지도 않는다.
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    static final String MEMBER_ID_ATTRIBUTE = "wks.currentMemberId";

    private final JwtProvider jwtProvider;

    public JwtAuthInterceptor(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
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
        request.setAttribute(MEMBER_ID_ATTRIBUTE, memberId);
        return true;
    }
}
