package com.darkness.wks.common.response;

import com.darkness.wks.common.exception.ErrorCode;
import com.darkness.wks.common.trace.TraceIdFilter;

public record ErrorResponse(boolean success, ErrorDetail error) {

    /** {@code traceId} 는 장애 문의 시 로그 추적용이다(api-spec §1). 필터를 거치지 않은 경로면 {@code null} */
    public record ErrorDetail(String code, String message, String traceId) {
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(false, new ErrorDetail(errorCode.name(), message, TraceIdFilter.current()));
    }
}
