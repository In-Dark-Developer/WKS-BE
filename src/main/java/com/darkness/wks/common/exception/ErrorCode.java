package com.darkness.wks.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "사주 결과를 찾을 수 없습니다."),
    SELF_COMPATIBILITY(HttpStatus.BAD_REQUEST, "본인과는 궁합을 볼 수 없습니다."),
    COMPATIBILITY_NOT_FOUND(HttpStatus.NOT_FOUND, "궁합을 찾을 수 없습니다."),
    DUPLICATE_SIGNUP(HttpStatus.CONFLICT, "이미 신청된 이메일입니다."),
    INVALID_EMAIL_DOMAIN(HttpStatus.BAD_REQUEST, "허용되지 않는 이메일 도메인입니다."),
    INVALID_TOKEN(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 토큰입니다."),
    LLM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "해석 서비스를 일시적으로 사용할 수 없습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    KAKAO_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "카카오 로그인을 일시적으로 사용할 수 없습니다."),
    DATING_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "소개팅 프로필을 찾을 수 없습니다."),
    DATING_PROFILE_CONFLICT(HttpStatus.CONFLICT, "이미 등록된 소개팅 프로필 또는 이메일입니다."),
    DATING_NOT_VERIFIED(HttpStatus.FORBIDDEN, "학교 이메일 인증이 필요합니다."),
    DATING_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "소개팅 요청을 찾을 수 없습니다."),
    DATING_REQUEST_CONFLICT(HttpStatus.CONFLICT, "소개팅 요청을 처리할 수 없습니다."),
    INSUFFICIENT_THREAD(HttpStatus.PAYMENT_REQUIRED, "실이 부족합니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청하신 경로를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
