package com.darkness.wks.common;

import io.swagger.v3.oas.annotations.Operation;
import com.darkness.wks.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    public record HealthStatus(String status) {
    }

    @Operation(summary = "헬스 체크", description = "서버 상태를 확인한다.")
    @GetMapping("/api/health")
    public ApiResponse<HealthStatus> health() {
        return ApiResponse.success(new HealthStatus("UP"));
    }
}
