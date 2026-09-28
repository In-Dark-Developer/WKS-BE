package com.darkness.wks.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 요청마다 짧은 추적 id 를 만들어 MDC 에 둔다. 에러 응답의 {@code traceId}(api-spec §1)와 로그 한 줄 한 줄에
 * 같은 값이 찍혀서, 사용자가 화면의 traceId 를 알려주면 그 요청의 로그만 골라낼 수 있다.
 * 가장 먼저 실행해야 인증 인터셉터·예외 핸들러가 남기는 로그에도 id 가 붙는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        MDC.put(MDC_KEY, newTraceId());
        try {
            chain.doFilter(request, response);
        } finally {
            // 스레드 풀 재사용으로 다음 요청에 이전 id 가 새지 않게 반드시 지운다
            MDC.remove(MDC_KEY);
        }
    }

    // 사람이 불러 주기 쉬운 길이면 충분하다 — 전역 유일성이 아니라 짧은 시간 창 안의 로그 검색용이다
    private static String newTraceId() {
        return "%08x".formatted(ThreadLocalRandom.current().nextInt());
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
