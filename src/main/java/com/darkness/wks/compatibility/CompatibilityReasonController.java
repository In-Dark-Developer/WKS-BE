package com.darkness.wks.compatibility;

import com.darkness.wks.common.response.ApiResponse;
import com.darkness.wks.common.response.ErrorResponse;
import com.darkness.wks.compatibility.dto.CompatibilityReasonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Compatibility", description = "친구 궁합 API")
@RestController
@RequestMapping("/api/compatibilities")
@RequiredArgsConstructor
public class CompatibilityReasonController {

    private final CompatibilityReasonService compatibilityReasonService;

    @Operation(summary = "궁합 상세 이유", description = "세 질문의 답. 처음 열어볼 때 생성해 캐싱하므로 첫 호출만 느리다(최대 30초). 두 사람이 같은 내용을 본다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "궁합 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "COMPATIBILITY_NOT_FOUND",
                                        "message": "궁합을 찾을 수 없습니다."
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "생성 실패. 해당 영역만 미노출하고 다시 시도할 수 있다",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "LLM_UNAVAILABLE",
                                        "message": "해석 서비스를 일시적으로 사용할 수 없습니다."
                                      }
                                    }
                                    """)))
    })
    @GetMapping("/{id}/reason")
    public ApiResponse<CompatibilityReasonResponse> getReason(
            @Parameter(description = "궁합 ID (순번)") @PathVariable Long id
    ) {
        return ApiResponse.success(compatibilityReasonService.getReason(id));
    }
}
