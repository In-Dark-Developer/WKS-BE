package com.darkness.wks.admin;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * {@code /api/admin/**} 는 환경변수 {@code ADMIN_TOKEN} 하나로 지킨다(등록은 {@link AdminWebConfig}).
 * 운영자 1~2명·축제 3일이라 역할 체계 대신 정적 토큰이다 — 토큰이 비어 있으면 관리자 API 전체가 401 이고
 * 나머지 서비스는 그대로 뜬다. 쿠키가 아니라 헤더라 CSRF 와 무관하고 브라우저가 자동으로 실어 보내지 않는다.
 */
@Slf4j
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    static final String HEADER = "X-Admin-Token";

    private final byte[] expected;

    public AdminAuthInterceptor(@Value("${app.admin.token:}") String token) {
        this.expected = token == null ? new byte[0] : token.trim().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String given = request.getHeader(HEADER);
        // 길이가 달라도 상수시간 비교 — 토큰 값은 어떤 로그에도 남기지 않는다
        if (expected.length == 0 || given == null
                || !MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.UTF_8))) {
            String forwarded = request.getHeader("X-Forwarded-For");
            log.warn("admin auth failed. path={}, ip={}", request.getRequestURI(),
                    forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr());
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return true;
    }
}
