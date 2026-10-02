package com.darkness.wks.feedback;

import com.darkness.wks.common.exception.GlobalExceptionHandler;
import com.darkness.wks.feedback.entity.Feedback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedbackControllerTest {

    private FeedbackRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository = mock(FeedbackRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new FeedbackController(new FeedbackService(repository)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void acceptsAnonymousFeedbackAndRemovesSurroundingWhitespace() throws Exception {
        mvc.perform(post("/api/feedbacks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  다음 축제에도 만나요!  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value("피드백이 접수되었습니다. 감사합니다."));

        ArgumentCaptor<Feedback> feedback = ArgumentCaptor.forClass(Feedback.class);
        verify(repository).save(feedback.capture());
        assertThat(feedback.getValue().getContent()).isEqualTo("다음 축제에도 만나요!");
        assertThat(feedback.getValue().getId().version()).isEqualTo(4);
        assertThat(feedback.getValue().getCreatedAt()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"content\":null}", "{\"content\":\"\"}",
            "{\"content\":\"  \\n\\t  \"}", "{\"content\":\"　\"}", "{broken"})
    void rejectsInvalidContentWithoutSaving(String body) throws Exception {
        mvc.perform(post("/api/feedbacks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verifyNoInteractions(repository);
    }

    @Test
    void acceptsMaximumLength() throws Exception {
        mvc.perform(post("/api/feedbacks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + "가".repeat(2000) + "\"}"))
                .andExpect(status().isCreated());
        verify(repository).save(any(Feedback.class));
    }

    @Test
    void rejectsContentOverMaximumLength() throws Exception {
        mvc.perform(post("/api/feedbacks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + "가".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        verifyNoInteractions(repository);
    }

    @Test
    void doesNotReturnAcceptedWhenStorageFails() throws Exception {
        when(repository.save(any(Feedback.class))).thenThrow(new IllegalStateException("storage unavailable"));
        mvc.perform(post("/api/feedbacks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"의견\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }
}
