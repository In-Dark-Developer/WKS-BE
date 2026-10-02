package com.darkness.wks.feedback;

import com.darkness.wks.common.response.ApiResponse;
import com.darkness.wks.feedback.dto.CreateFeedbackRequest;
import com.darkness.wks.feedback.dto.FeedbackResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Feedback", description = "축제 종료 후 자유 의견 제출")
@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "피드백 제출", description = "로그인 없이 자유 의견을 제출한다. 앞뒤 공백 제거 후 1~2,000자.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "접수 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "INVALID_INPUT: 내용 누락·공백·길이 초과")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FeedbackResponse> create(@Valid @RequestBody CreateFeedbackRequest request) {
        return ApiResponse.success(feedbackService.create(request));
    }
}
