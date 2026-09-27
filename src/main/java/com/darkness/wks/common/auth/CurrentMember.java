package com.darkness.wks.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인이 필요한 컨트롤러 메서드에서 검증된 회원 id 를 받는다 (반드시 {@code Long} 타입 파라미터).
 * {@link JwtAuthInterceptor} 가 앞서 토큰을 검증해 요청에 심어 둔 값을 {@link CurrentMemberArgumentResolver}
 * 가 여기로 꺼내 준다 — 컨트롤러·서비스는 토큰을 직접 파싱하지 않는다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentMember {
}
