package com.darkness.wks.auth;

import com.darkness.wks.common.auth.AuthProperties;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 프론트가 보낸 redirectUri 를 그대로 카카오에 넘기면, 우리 client secret 으로 임의 주소용 인가 코드를
 * 교환해 주는 셈이 된다. 등록된 값과 정확히 같을 때만 허용한다 (architecture.md §4 원칙 6).
 */
@Component
public class RedirectUriPolicy {

    private final Set<String> allowedUris;

    public RedirectUriPolicy(AuthProperties properties) {
        this.allowedUris = parse(properties.allowedRedirectUris());
    }

    /** 화이트리스트에 없으면(빈 화이트리스트 포함) INVALID_INPUT. 접두사·와일드카드 매칭은 하지 않는다 */
    public void validate(String redirectUri) {
        if (redirectUri == null || !allowedUris.contains(redirectUri)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private static Set<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(uri -> !uri.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
