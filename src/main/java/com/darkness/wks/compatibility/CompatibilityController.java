package com.darkness.wks.compatibility;

import com.darkness.wks.common.response.ApiResponse;
import com.darkness.wks.common.response.ErrorResponse;
import com.darkness.wks.compatibility.dto.CompatibilityResponse;
import com.darkness.wks.compatibility.dto.CreateCompatibilityRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Compatibility", description = "친구 궁합 API")
@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
public class CompatibilityController {

    private final CompatibilityService compatibilityService;

    @Operation(summary = "친구 궁합 생성", description = "공유 링크 주인과 친구의 저장된 사주 결과로 궁합을 계산한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "신규 궁합 생성"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "기존 궁합 반환"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "UUID 형식 오류 또는 자기 자신과의 궁합",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "SELF_COMPATIBILITY",
                                        "message": "본인과는 궁합을 볼 수 없습니다."
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사주 결과 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "RESULT_NOT_FOUND",
                                        "message": "사주 결과를 찾을 수 없습니다."
                                      }
                                    }
                                    """)))
    })
    @PostMapping("/{shareId}/compatibility")
    public ResponseEntity<ApiResponse<CompatibilityResponse>> createCompatibility(
            @Parameter(description = "공유 링크 UUID v4 ID", schema = @Schema(type = "string", format = "uuid"))
            @PathVariable String shareId,
            @Valid @RequestBody CreateCompatibilityRequest request
    ) {
        CompatibilityService.CreationResult result = compatibilityService.createCompatibility(shareId, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(result.response()));
    }
}
