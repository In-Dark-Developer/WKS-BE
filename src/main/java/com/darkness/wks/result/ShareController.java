package com.darkness.wks.result;

import com.darkness.wks.common.response.ApiResponse;
import com.darkness.wks.common.response.ErrorResponse;
import com.darkness.wks.result.dto.SharedResultResponse;
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

@Tag(name = "Share", description = "공유 결과 API")
@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
public class ShareController {

    private final ResultService resultService;

    @Operation(summary = "공유 결과 조회", description = "shareId로 링크 주인의 운세와 친구 궁합 지도를 조회한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "UUID v4 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "공유 결과 없음",
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
    @GetMapping("/{shareId}")
    public ApiResponse<SharedResultResponse> getSharedResult(
            @Parameter(description = "공개 링크 UUID v4 ID", schema = @Schema(type = "string", format = "uuid"))
            @PathVariable String shareId
    ) {
        return ApiResponse.success(resultService.getSharedResult(shareId));
    }
}
