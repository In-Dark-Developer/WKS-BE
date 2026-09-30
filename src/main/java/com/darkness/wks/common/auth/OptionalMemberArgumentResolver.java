package com.darkness.wks.common.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class OptionalMemberArgumentResolver implements HandlerMethodArgumentResolver {

    private final JwtProvider jwtProvider;
    private final MemberExistence memberExistence;

    public OptionalMemberArgumentResolver(JwtProvider jwtProvider, MemberExistence memberExistence) {
        this.jwtProvider = jwtProvider;
        this.memberExistence = memberExistence;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(OptionalMember.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return null;
        }
        // 회원 행이 없는 토큰은 비로그인으로 본다 — 이 값으로 결과·궁합에 계정을 기록하면 FK 위반으로 익명 기능까지 500 이 된다
        return jwtProvider.verify(JwtCookie.readFrom(request))
                .filter(memberExistence::exists)
                .orElse(null);
    }
}
