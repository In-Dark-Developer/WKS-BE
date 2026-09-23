package com.darkness.wks.common.auth;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentMemberArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentMember.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object memberId = webRequest.getAttribute(
                JwtAuthInterceptor.MEMBER_ID_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
        if (memberId == null) {
            // 정상 경로면 여기 오기 전에 JwtAuthInterceptor 가 막는다 — 오면 그 경로에 인터셉터 등록이
            // 빠졌다는 뜻(설정 실수)이다. 그래도 인증 없이 통과시키지 않는다(fail-closed).
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return memberId;
    }
}
