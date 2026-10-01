package com.darkness.wks.admin;

import com.darkness.wks.admin.dto.AdminNoticeMailRequest;
import com.darkness.wks.admin.dto.AdminNoticeMailResponse;
import com.darkness.wks.admin.dto.AdminNoticeMailStatusResponse;
import com.darkness.wks.common.response.ApiResponse;
import com.darkness.wks.dating.DatingNoticeMailService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소개팅 프로필 보유자 일괄 안내 메일 (#159). 공개 API 로 두면 대량 발송 트리거가 열리므로 관리자 토큰
 * ({@link AdminAuthInterceptor}) 뒤에만 둔다. 사용법은 docs/admin-api.md.
 */
@Hidden
@RestController
@RequestMapping("/api/admin/notice-mails")
public class AdminNoticeMailController {

    private final DatingNoticeMailService noticeMailService;

    public AdminNoticeMailController(DatingNoticeMailService noticeMailService) {
        this.noticeMailService = noticeMailService;
    }

    /** SEND 는 발송이 끝나기 전에 202 로 돌아온다. 결과는 GET 으로 본다 */
    @PostMapping
    public ResponseEntity<ApiResponse<AdminNoticeMailResponse>> run(
            @Valid @RequestBody AdminNoticeMailRequest request) {
        HttpStatus status = request.mode() == AdminNoticeMailRequest.Mode.SEND ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(noticeMailService.run(request)));
    }

    @GetMapping("/{campaignKey}")
    public ApiResponse<AdminNoticeMailStatusResponse> status(@PathVariable String campaignKey) {
        return ApiResponse.success(noticeMailService.status(campaignKey));
    }

    /** 시작 전 예약만 취소된다 */
    @DeleteMapping("/{campaignKey}/schedule")
    public ApiResponse<Void> cancelSchedule(@PathVariable String campaignKey) {
        noticeMailService.cancelSchedule(campaignKey);
        return ApiResponse.success(null);
    }
}
