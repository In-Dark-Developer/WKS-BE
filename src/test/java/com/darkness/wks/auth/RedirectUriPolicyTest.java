package com.darkness.wks.auth;

import com.darkness.wks.common.auth.AuthProperties;
import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedirectUriPolicyTest {

    private RedirectUriPolicy policyFor(String allowed) {
        return new RedirectUriPolicy(new AuthProperties(allowed, true, null, null));
    }

    @Test
    void 화이트리스트에_정확히_같은_값이면_통과한다() {
        RedirectUriPolicy policy = policyFor("https://threadoffate.site/auth/kakao/callback");

        assertThatCode(() -> policy.validate("https://threadoffate.site/auth/kakao/callback"))
                .doesNotThrowAnyException();
    }

    @Test
    void 콤마로_여러_값을_등록할_수_있다() {
        RedirectUriPolicy policy = policyFor(
                "http://localhost:5173/dev/kakao-callback, https://threadoffate.site/auth/kakao/callback");

        assertThatCode(() -> policy.validate("https://threadoffate.site/auth/kakao/callback"))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("http://localhost:5173/dev/kakao-callback"))
                .doesNotThrowAnyException();
    }

    @Test
    void 화이트리스트에_없으면_INVALID_INPUT() {
        RedirectUriPolicy policy = policyFor("https://threadoffate.site/auth/kakao/callback");

        assertThatThrownBy(() -> policy.validate("https://evil.example.com/callback"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void 접두사만_같아도_와일드카드처럼_허용하지_않는다() {
        RedirectUriPolicy policy = policyFor("https://threadoffate.site/auth/kakao/callback");

        assertThatThrownBy(() -> policy.validate("https://threadoffate.site/auth/kakao/callback/evil"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 화이트리스트가_비어_있으면_전부_거부한다() {
        RedirectUriPolicy policy = policyFor("");

        assertThatThrownBy(() -> policy.validate("https://threadoffate.site/auth/kakao/callback"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void null_이면_거부한다() {
        RedirectUriPolicy policy = policyFor("https://threadoffate.site/auth/kakao/callback");

        assertThatThrownBy(() -> policy.validate(null)).isInstanceOf(BusinessException.class);
    }
}
