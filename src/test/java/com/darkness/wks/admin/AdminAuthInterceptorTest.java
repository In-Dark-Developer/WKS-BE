package com.darkness.wks.admin;

import com.darkness.wks.common.exception.BusinessException;
import com.darkness.wks.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAuthInterceptorTest {

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/dating/profiles");
        if (token != null) {
            request.addHeader(AdminAuthInterceptor.HEADER, token);
        }
        return request;
    }

    private static void assertRejected(AdminAuthInterceptor interceptor, String token) {
        assertThatThrownBy(() -> interceptor.preHandle(request(token), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void 맞는_토큰만_통과한다() {
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor("secret-token");
        assertThat(interceptor.preHandle(request("secret-token"), new MockHttpServletResponse(), new Object())).isTrue();
        assertRejected(interceptor, "secret-toke");
        assertRejected(interceptor, "secret-token-longer");
        assertRejected(interceptor, null);
    }

    @Test
    void 토큰이_비어_있으면_전부_막는다() {
        assertRejected(new AdminAuthInterceptor(""), "");
        assertRejected(new AdminAuthInterceptor(""), "anything");
        assertRejected(new AdminAuthInterceptor(null), null);
    }
}
