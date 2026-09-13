package com.darkness.wks.common.response;

import com.darkness.wks.common.exception.ErrorCode;

public record ErrorResponse(boolean success, ErrorDetail error) {

    public record ErrorDetail(String code, String message) {
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(false, new ErrorDetail(errorCode.name(), errorCode.getMessage()));
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(false, new ErrorDetail(errorCode.name(), message));
    }
}
