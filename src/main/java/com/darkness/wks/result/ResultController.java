package com.darkness.wks.result;

import com.darkness.wks.common.response.ApiResponse;
import com.darkness.wks.common.response.ErrorResponse;
import com.darkness.wks.result.dto.CreateResultRequest;
import com.darkness.wks.result.dto.ResultResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Result", description = "사주 결과 API")
@RestController
@RequestMapping("/api/results")
@RequiredArgsConstructor
public class ResultController {

    private final ResultService resultService;

    @Operation(summary = "사주 결과 생성", description = "사주를 계산하고 운명, 운세 등급과 해석을 생성한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "INVALID_INPUT",
                                        "message": "입력값이 올바르지 않습니다."
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "해석 생성 실패",
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
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ResultResponse> createResult(@Valid @RequestBody CreateResultRequest request) {
        return ApiResponse.success(resultService.createResult(request));
    }

    @Operation(summary = "사주 결과 조회", description = "저장된 운명과 궁합을 조회한다. 외부 API를 호출하지 않는다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "UUID v4 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "INVALID_INPUT",
                                        "message": "입력값이 올바르지 않습니다."
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "결과 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "RESULT_NOT_FOUND",
                                        "message": "사주 결과를 찾을 수 없습니다."
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "저장 데이터 불일치 등 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "INTERNAL_ERROR",
                                        "message": "서버 오류가 발생했습니다."
                                      }
                                    }
                                    """)))
    })
    @GetMapping("/{resultId}")
    public ApiResponse<ResultResponse> getResult(
            @Parameter(description = "조회할 UUID v4 결과 ID", schema = @Schema(type = "string", format = "uuid"))
            @PathVariable String resultId
    ) {
        return ApiResponse.success(resultService.getResult(resultId));
    }
}
