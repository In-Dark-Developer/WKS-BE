package com.darkness.wks.feedback;

import com.darkness.wks.feedback.dto.CreateFeedbackRequest;
import com.darkness.wks.feedback.dto.FeedbackResponse;
import com.darkness.wks.feedback.entity.Feedback;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;

    @Transactional
    public FeedbackResponse create(CreateFeedbackRequest request) {
        feedbackRepository.save(new Feedback(request.content()));
        return FeedbackResponse.accepted();
    }
}
