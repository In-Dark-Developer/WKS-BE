package com.darkness.wks.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 익명 API 에서 "로그인했으면" 회원 id 를 받는다 ({@code Long}, 로그인 안 했으면 {@code null}).
 * {@link CurrentMember} 와 달리 인터셉터를 거치지 않는 경로에서 쓰고, 쿠키가 없거나 만료·위조여도
 * 401 을 내지 않는다 — 사주·궁합 API 는 로그인 없이 동작해야 하기 때문이다(AGENTS.md).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionalMember {
}
