package com.darkness.wks.feedback.dto;

public record FeedbackResponse(String message) {

    public static FeedbackResponse accepted() {
        return new FeedbackResponse("피드백이 접수되었습니다. 감사합니다.");
    }
}
