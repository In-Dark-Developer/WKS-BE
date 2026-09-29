package com.darkness.wks.admin;

import com.darkness.wks.admin.dto.AdminStatsResponse;
import com.darkness.wks.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 축제 기간 통계. 인증은 {@link AdminAuthInterceptor}, 사용법은 docs/admin-api.md. */
@Hidden
@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    private final AdminStatsService statsService;

    public AdminStatsController(AdminStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public ApiResponse<AdminStatsResponse> festival() {
        return ApiResponse.success(statsService.festivalStats());
    }
}
