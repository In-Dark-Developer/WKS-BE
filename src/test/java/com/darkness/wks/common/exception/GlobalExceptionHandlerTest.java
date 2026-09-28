package com.darkness.wks.common.exception;

import com.darkness.wks.common.trace.TraceIdFilter;
import com.darkness.wks.compatibility.CompatibilityReasonController;
import com.darkness.wks.compatibility.CompatibilityReasonService;
import com.darkness.wks.result.ResultController;
import com.darkness.wks.result.ResultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 사용자 입력 실수가 500 이 아니라 400 INVALID_INPUT 으로 나가는지, 에러 응답에 traceId 가 붙는지 */
class GlobalExceptionHandlerTest {

    private MockMvc mvc;
    private ResultService resultService;

    @BeforeEach
    void setUp() {
        resultService = Mockito.mock(ResultService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new ResultController(resultService),
                        new CompatibilityReasonController(Mockito.mock(CompatibilityReasonService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // 없는 enum 값
            """
            {"nickname":"a","calendarType":"SOLAR","birthDate":"2002-03-14","birthTime":"14:30","gender":"XYZ"}""",
            // 형식이 틀린 시각
            """
            {"nickname":"a","calendarType":"SOLAR","birthDate":"2002-03-14","birthTime":"25:99","gender":"MALE"}""",
            // 깨진 JSON
            "{broken"
    })
    void unreadableBodyIsInvalidInput(String body) throws Exception {
        mvc.perform(post("/api/results").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void pathTypeMismatchIsInvalidInput() throws Exception {
        mvc.perform(get("/api/compatibilities/abc/reason"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void unsupportedContentTypeIsInvalidInput() throws Exception {
        mvc.perform(post("/api/results").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void errorResponseCarriesTraceId() throws Exception {
        Mockito.when(resultService.getResult("missing"))
                .thenThrow(new BusinessException(ErrorCode.RESULT_NOT_FOUND));

        mvc.perform(get("/api/results/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.traceId").value(matchesPattern("[0-9a-f]{8}")));
    }
}
