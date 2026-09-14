package com.darkness.wks.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.darkness.wks.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "헬스체크 API")
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
